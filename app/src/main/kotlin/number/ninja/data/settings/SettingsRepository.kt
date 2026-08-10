package number.ninja.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import number.ninja.domain.AppLanguage
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.ThemeMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            operations = prefs[Keys.OPERATIONS]?.split(",")?.filter { it.isNotBlank() }
                ?.mapNotNull { runCatching { Operation.valueOf(it) }.getOrNull() }?.toSet()
                ?.takeIf { it.isNotEmpty() } ?: UserSettings().operations,
            level = prefs[Keys.LEVEL]?.let { runCatching { Level.valueOf(it) }.getOrNull() } ?: UserSettings().level,
            mode = prefs[Keys.MODE]?.let { runCatching { TrainingMode.valueOf(it) }.getOrNull() } ?: UserSettings().mode,
            quizSubMode = prefs[Keys.QUIZ_SUB_MODE]?.let { runCatching { QuizSubMode.valueOf(it) }.getOrNull() }
                ?: UserSettings().quizSubMode,
            quizLength = prefs[Keys.QUIZ_LENGTH] ?: UserSettings().quizLength,
            // Absent/unrecognized key -> null ("follow system"), NOT UserSettings().language
            // (which is itself null) — no fallback-to-English here, per the language default fix.
            language = prefs[Keys.LANGUAGE]?.let { tag -> AppLanguage.entries.find { it.tag == tag } },
            theme = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: UserSettings().theme,
            hasCompletedFirstRun = prefs[Keys.FIRST_RUN_DONE] ?: false,
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(settings.first())
        dataStore.edit { prefs ->
            prefs[Keys.OPERATIONS] = next.operations.joinToString(",") { it.name }
            prefs[Keys.LEVEL] = next.level.name
            prefs[Keys.MODE] = next.mode.name
            prefs[Keys.QUIZ_SUB_MODE] = next.quizSubMode.name
            prefs[Keys.QUIZ_LENGTH] = next.quizLength
            // null ("system") means no persisted override — remove the key rather than writing
            // a sentinel value, so a future read-back sees "absent" (-> null) again.
            if (next.language != null) {
                prefs[Keys.LANGUAGE] = next.language.tag
            } else {
                prefs.remove(Keys.LANGUAGE)
            }
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.FIRST_RUN_DONE] = next.hasCompletedFirstRun
        }
    }

    private object Keys {
        val OPERATIONS = stringPreferencesKey("operations")
        val LEVEL = stringPreferencesKey("level")
        val MODE = stringPreferencesKey("mode")
        val QUIZ_SUB_MODE = stringPreferencesKey("quiz_sub_mode")
        val QUIZ_LENGTH = intPreferencesKey("quiz_length")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }
}
