package number.ninja.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import number.ninja.data.db.StatsRepository
import number.ninja.data.facts.FactsRepository
import number.ninja.data.settings.SettingsRepository
import number.ninja.domain.ExampleGenerator
import number.ninja.domain.MathExample
import number.ninja.domain.UserSettings
import number.ninja.domain.distractors

/**
 * UI states for the free-practice screen, rendered one at a time via `Crossfade`. [Question] and
 * [Feedback] share the same [MathExample]/`options` pair — `options` (the shuffled 4-choice set:
 * [MathExample.answer] plus 3 [distractors]) is computed once per example in the ViewModel and
 * carried through both states, rather than shuffled inside the composable, so the multiple-choice
 * grid renders in the exact same order across the Question -> Feedback transition (two different
 * composables on either side of the screen's `Crossfade`, so a composable-local `remember` can't
 * be shared between them).
 */
sealed interface FreePracticeUiState {
    data class Question(val example: MathExample, val options: List<Int>) : FreePracticeUiState
    data class Feedback(
        val example: MathExample,
        val options: List<Int>,
        val correctAnswer: Int,
        val selectedAnswer: Int,
    ) : FreePracticeUiState
    data class Fact(val text: String) : FreePracticeUiState
}

/**
 * Backs the free-practice screen (spec §6.1): infinite example generation, one enabled
 * [number.ninja.domain.Operation] picked uniformly at random per example (spec doesn't specify
 * weighting), no immediate repeat (passes [previousExample] into the generator), a fact card
 * every 4th completed example (spec §8, session-scoped — the counter is plain ViewModel state,
 * not persisted, so it resets whenever this ViewModel is recreated), and friendly
 * correct/incorrect feedback recorded to [StatsRepository] either way.
 *
 * Deliberately snapshots [UserSettings] once in [init] rather than staying reactive like
 * [number.ninja.ui.settings.SettingsViewModel] — this screen isn't reachable while Settings is
 * open, so settings can't change mid-session, and a stable snapshot keeps `nextExample()`
 * synchronous instead of needing to re-collect a Flow per example.
 */
class FreePracticeViewModel(
    private val settingsRepository: SettingsRepository,
    private val exampleGenerator: ExampleGenerator,
    private val statsRepository: StatsRepository,
    private val factsRepository: FactsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FreePracticeUiState?>(null)
    val uiState: StateFlow<FreePracticeUiState?> = _uiState.asStateFlow()

    private var settingsSnapshot: UserSettings = UserSettings()
    private var previousExample: MathExample? = null
    private var examplesCompleted = 0

    init {
        viewModelScope.launch {
            settingsSnapshot = settingsRepository.settings.first()
            _uiState.value = nextQuestion()
        }
    }

    /**
     * Called exactly once per example, the instant an answer option is tapped (the tap itself
     * *is* the answer — there's no separate Check step). [FreePracticeScreen]'s
     * `AnswerOptionsGrid` already goes non-interactive after the first tap, but this guard is the
     * real backstop: `Crossfade` keeps the outgoing Question composable alive (and clickable) for
     * the fade duration, so a fast double-tap could otherwise double-record the same attempt.
     */
    fun submitAnswer(example: MathExample, answer: Int) {
        val current = _uiState.value
        if (current !is FreePracticeUiState.Question || current.example != example) return

        val correctAnswer = example.answer()
        val correct = answer == correctAnswer
        viewModelScope.launch {
            statsRepository.recordAttempt(example, correct, System.currentTimeMillis())
        }
        examplesCompleted++
        _uiState.value = FreePracticeUiState.Feedback(example, current.options, correctAnswer, answer)
    }

    /** Auto-advance after the 3s answer-highlight window (`FreePracticeScreen`'s `LaunchedEffect`). */
    fun onFeedbackDismissed() {
        // Same double-tap-during-Crossfade guard as submitAnswer, kept even though this is no
        // longer button-triggered: the timer could in theory fire twice if this were ever called
        // from two places.
        if (_uiState.value !is FreePracticeUiState.Feedback) return
        _uiState.value = if (examplesCompleted % FACT_EVERY == 0) {
            FreePracticeUiState.Fact(factsRepository.nextFact())
        } else {
            nextQuestion()
        }
    }

    fun onFactDismissed() {
        if (_uiState.value !is FreePracticeUiState.Fact) return
        _uiState.value = nextQuestion()
    }

    private fun nextQuestion(): FreePracticeUiState.Question {
        val operation = settingsSnapshot.operations.random()
        val example = exampleGenerator.generate(operation, settingsSnapshot.level, previousExample)
        previousExample = example
        val options = (listOf(example.answer()) + distractors(example)).shuffled()
        return FreePracticeUiState.Question(example, options)
    }

    private companion object {
        const val FACT_EVERY = 4
    }
}
