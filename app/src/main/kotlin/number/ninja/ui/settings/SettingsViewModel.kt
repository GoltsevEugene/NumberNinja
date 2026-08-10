package number.ninja.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
 * this single ViewModel rather than quick setup having its own batching ViewModel. Every
 * control is live-editing — there is no confirm step, so each interaction commits its own
 * [SettingsRepository.update] call immediately. [markFirstRunComplete] is the one
 * first-run-only action (flips `hasCompletedFirstRun`, called from quick setup's CTA).
 */
class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun selectLanguage(language: AppLanguage) = update { it.copy(language = language) }

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
     * Resets every setting except [UserSettings.hasCompletedFirstRun] and [UserSettings.language]
     * back to its default. `hasCompletedFirstRun` is preserved so a reset can't send an existing
     * user back through first run. `language` is preserved (not reset to null/"system") because
     * [number.ninja.ui.nav.NumberNinjaNavHost]'s locale effect deliberately never asserts an empty
     * locale list from app code (see that file's doc comment) — resetting it to null here would
     * leave the persisted setting claiming "follow system" while the app keeps rendering whatever
     * language was last actually applied, a real divergence between what Settings shows and what
     * AppCompatDelegate reports until the user explicitly picks a language again.
     */
    fun resetToDefaults() = update { current ->
        UserSettings(hasCompletedFirstRun = current.hasCompletedFirstRun, language = current.language)
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
