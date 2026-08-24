package number.ninja.data.settings

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.ThemeMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    applicationScope: CoroutineScope,
) {

    /**
     * A long-lived DataStore collector needs to retry an [IOException] itself: DataStore retries a
     * failed read when it is collected again, but [stateIn] otherwise ends its sole upstream job
     * after the first exception. Keeping the previous StateFlow value during the retry means an
     * initial failure remains honestly unloaded, while a later failure does not reset already
     * loaded settings to guessed defaults.
     *
     * Other exceptions deliberately escape. Cancellation and programming errors must keep their
     * normal coroutine semantics rather than being mistaken for recoverable storage failures.
     */
    private val recoveringSettings: Flow<UserSettings> = dataStore.data
        .retryReadFailures()
        .map { it.toUserSettings() }

    /**
     * Process-scoped cache of the latest persisted settings.
     *
     * The first app screen still waits for a real DataStore value instead of guessing defaults,
     * hence the nullable initial value. Once loaded, [SharingStarted.Lazily] keeps the upstream
     * collection alive in [applicationScope], so a newly created UI can render immediately from
     * [StateFlow.value] instead of briefly returning to an unloaded state.
     */
    val settings: StateFlow<UserSettings?> = recoveringSettings
        .stateIn(applicationScope, SharingStarted.Lazily, null)

    /**
     * Reads DataStore directly instead of the UI cache. DataStore guarantees that after an edit
     * completes, a new collection reflects it; the process cache can trail that emission briefly.
     * If storage remains temporarily unavailable after bounded retries, an already confirmed
     * process snapshot is safer than leaving a session in its loading state forever.
     */
    suspend fun awaitSettings(): UserSettings = try {
        dataStore.data
            .retryReadFailures(maxRetries = SESSION_READ_RETRIES)
            .first()
            .toUserSettings()
    } catch (error: IOException) {
        if (error is CorruptionException) throw error

        settings.value?.also {
            Log.w(TAG, "Unable to refresh settings; using the last confirmed snapshot", error)
        } ?: throw error
    }

    /**
     * Reads and writes under DataStore's single edit transaction. The old implementation called
     * settings.first() before edit, allowing two quick updates to transform the same stale
     * snapshot and overwrite each other in completion order.
     */
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toUserSettings())
            prefs[Keys.OPERATIONS] = next.operations.joinToString(",") { it.name }
            prefs[Keys.LEVEL] = next.level.name
            prefs[Keys.MODE] = next.mode.name
            prefs[Keys.QUIZ_SUB_MODE] = next.quizSubMode.name
            prefs[Keys.QUIZ_LENGTH] = next.quizLength
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.FIRST_RUN_DONE] = next.hasCompletedFirstRun
        }
    }

    private fun Preferences.toUserSettings(): UserSettings =
        UserSettings(
            operations = this[Keys.OPERATIONS]?.split(",")?.filter { it.isNotBlank() }
                ?.mapNotNull { runCatching { Operation.valueOf(it) }.getOrNull() }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: UserSettings().operations,
            level = this[Keys.LEVEL]?.let { runCatching { Level.valueOf(it) }.getOrNull() } ?: UserSettings().level,
            mode = this[Keys.MODE]?.let { runCatching { TrainingMode.valueOf(it) }.getOrNull() } ?: UserSettings().mode,
            quizSubMode = this[Keys.QUIZ_SUB_MODE]?.let { runCatching { QuizSubMode.valueOf(it) }.getOrNull() }
                ?: UserSettings().quizSubMode,
            quizLength = this[Keys.QUIZ_LENGTH] ?: UserSettings().quizLength,
            theme = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: UserSettings().theme,
            hasCompletedFirstRun = this[Keys.FIRST_RUN_DONE] ?: false,
        )

    private fun Flow<Preferences>.retryReadFailures(
        maxRetries: Long = Long.MAX_VALUE,
    ): Flow<Preferences> = flow {
        var consecutiveFailures = 0L
        emitAll(
            this@retryReadFailures
                .onEach { consecutiveFailures = 0L }
                .retryWhen { cause, _ ->
                    // Corruption is repaired by PreferencesDataStore's corruption handler. If a
                    // different DataStore reaches here with corruption still unresolved, surface
                    // it instead of hiding a permanent problem behind an endless retry loop.
                    if (cause !is IOException || cause is CorruptionException) {
                        return@retryWhen false
                    }
                    if (consecutiveFailures >= maxRetries) return@retryWhen false

                    Log.w(TAG, "Unable to read settings; retrying", cause)
                    delay(readRetryDelayMillis(consecutiveFailures))
                    consecutiveFailures = (consecutiveFailures + 1L).coerceAtMost(MAX_READ_RETRY_SHIFT)
                    true
                },
        )
    }

    private object Keys {
        val OPERATIONS = stringPreferencesKey("operations")
        val LEVEL = stringPreferencesKey("level")
        val MODE = stringPreferencesKey("mode")
        val QUIZ_SUB_MODE = stringPreferencesKey("quiz_sub_mode")
        val QUIZ_LENGTH = intPreferencesKey("quiz_length")
        val THEME = stringPreferencesKey("theme")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }

    private companion object {
        const val TAG = "SettingsRepository"
        const val INITIAL_READ_RETRY_DELAY_MILLIS = 250L
        const val MAX_READ_RETRY_DELAY_MILLIS = 30_000L
        const val MAX_READ_RETRY_SHIFT = 7L
        const val SESSION_READ_RETRIES = 2L

        fun readRetryDelayMillis(consecutiveFailures: Long): Long {
            val shift = consecutiveFailures.coerceAtMost(MAX_READ_RETRY_SHIFT).toInt()
            return (INITIAL_READ_RETRY_DELAY_MILLIS shl shift)
                .coerceAtMost(MAX_READ_RETRY_DELAY_MILLIS)
        }
    }
}
