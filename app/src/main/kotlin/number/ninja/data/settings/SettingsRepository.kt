package number.ninja.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.ThemeMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data.map { it.toUserSettings() }

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

    private object Keys {
        val OPERATIONS = stringPreferencesKey("operations")
        val LEVEL = stringPreferencesKey("level")
        val MODE = stringPreferencesKey("mode")
        val QUIZ_SUB_MODE = stringPreferencesKey("quiz_sub_mode")
        val QUIZ_LENGTH = intPreferencesKey("quiz_length")
        val THEME = stringPreferencesKey("theme")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }
}
