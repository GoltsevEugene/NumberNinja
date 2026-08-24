package number.ninja.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import number.ninja.data.settings.AppLanguageManager
import number.ninja.data.settings.SettingsRepository
import number.ninja.domain.AppLanguage
import number.ninja.domain.Level
import number.ninja.domain.Operation
import number.ninja.domain.QuizSubMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings

/**
 * Backs the full Settings screen AND (as of the "first run is just Settings" phase) the
 * first-run quick-setup screen — both share [number.ninja.ui.settings.SettingsContent] and
 * this single ViewModel rather than quick setup having its own batching ViewModel. Controls are
 * live-editing: regular settings use [SettingsRepository], while language goes directly through
 * [AppLanguageManager], the same source used by Android's per-app language settings.
 * [markFirstRunComplete] is the one first-run-only action (flips `hasCompletedFirstRun`, called
 * from quick setup's CTA).
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appLanguageManager: AppLanguageManager,
) : ViewModel() {

    val settings: StateFlow<UserSettings?> = settingsRepository.settings

    fun toggleOperation(operation: Operation) = update { current ->
        val next = current.operations.toMutableSet()
        if (operation in next) {
            // Keep at least one operation selected — the app can't generate examples otherwise.
            if (next.size > 1) next.remove(operation)
        } else {
            next.add(operation)
        }
        current.copy(operations = next)
    }

    fun selectLevel(level: Level) = update { it.copy(level = level) }

    fun selectMode(mode: TrainingMode) = update { it.copy(mode = mode) }

    fun selectQuizSubMode(quizSubMode: QuizSubMode) = update { it.copy(quizSubMode = quizSubMode) }

    fun setQuizLength(length: Int) = update {
        val clamped = length.coerceIn(UserSettings.MIN_QUIZ_LENGTH, UserSettings.MAX_QUIZ_LENGTH)
        it.copy(quizLength = clamped)
    }

    val selectedLanguage: StateFlow<AppLanguage?> = appLanguageManager.selectedLanguage

    fun selectLanguage(language: AppLanguage?) = appLanguageManager.selectLanguage(language)

    fun refreshSelectedLanguage() = appLanguageManager.refreshSelectedLanguage()

    /**
     * Persists `hasCompletedFirstRun = true` and only THEN calls [onDone] — deliberately not a
     * fire-and-forget [update] like every other setter here. [onDone] (in
     * [number.ninja.ui.nav.NumberNinjaNavHost]) pops the QuickSetup back-stack entry with
     * `inclusive = true`, which clears this ViewModel's `ViewModelStore` and cancels
     * [viewModelScope] — if [onDone] ran before the DataStore write landed (as it would with a
     * plain `update { ... }; onDone()` at the call site), the write could be cancelled mid-flight
     * and the user would land back on first run next launch. Same hazard already documented for
     * `QuizViewModel` being cleared the instant `Route.Session` is popped — awaiting the write
     * before navigating is the fix there too.
     */
    fun markFirstRunComplete(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(hasCompletedFirstRun = true) }
            onDone()
        }
    }

    /**
     * Resets every setting except [UserSettings.hasCompletedFirstRun] back to its default,
     * including returning the language choice to "same as system".
     * `hasCompletedFirstRun` is preserved so a reset can't send an existing user back through
     * first run. Persist the regular settings before changing locale so all non-language defaults
     * are durable before the locale configuration is dispatched to the UI.
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.update { current ->
                UserSettings(hasCompletedFirstRun = current.hasCompletedFirstRun)
            }
            appLanguageManager.selectLanguage(null)
        }
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
