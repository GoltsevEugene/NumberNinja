package number.ninja.ui.quiz

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
import number.ninja.domain.Level
import number.ninja.domain.MathExample
import number.ninja.domain.QuizSubMode
import number.ninja.domain.UserSettings
import number.ninja.domain.distractors

/**
 * UI states for the quiz screen, rendered one at a time via `Crossfade`. [Question] and
 * [Feedback] share the same `options` (shuffled 4-choice set: [MathExample.answer] plus 3
 * [distractors]), computed once per example in [QuizQuestion] rather than reshuffled inside a
 * composable — same reasoning as `FreePracticeUiState` — so the multiple-choice grid renders
 * identically across the Question -> Feedback transition.
 */
sealed interface QuizUiState {
    data class Question(val example: MathExample, val options: List<Int>, val position: Int, val total: Int) :
        QuizUiState
    data class Feedback(
        val example: MathExample,
        val options: List<Int>,
        val correctAnswer: Int,
        val selectedAnswer: Int,
        val position: Int,
        val total: Int,
        val isLast: Boolean,
    ) : QuizUiState
    data class Fact(val text: String) : QuizUiState

    /** Terminal state: the quiz screen reacts to this once (via `LaunchedEffect`) to navigate away. */
    data object Finished : QuizUiState
}

/** One pre-generated quiz example plus its (already shuffled) 4-choice answer set. */
private data class QuizQuestion(val example: MathExample, val options: List<Int>)

/**
 * Backs the quiz screen (spec §6.2): a fixed, fully pre-generated list of
 * `UserSettings.quizLength` examples (so the results screen can show every one of them, in
 * order), no going back to change a submitted answer, a fact card every 4th completed example
 * (same mechanic as [number.ninja.ui.practice.FreePracticeViewModel]) except on the very last
 * example — see [onFeedbackDismissed] — and friendly, never-punitive feedback throughout, with
 * no timer/time-pressure UI at all.
 *
 * Deliberately ignores `UserSettings.level` — spec §6.2 has the quiz span all four [Level]s
 * regardless of the single level configured for free practice, distributed per [QuizSubMode].
 *
 * Snapshots [UserSettings] once in [init], same justification as `FreePracticeViewModel`: this
 * screen isn't reachable while Settings is open, so a stable snapshot is fine and keeps
 * generation synchronous.
 */
class QuizViewModel(
    private val settingsRepository: SettingsRepository,
    private val exampleGenerator: ExampleGenerator,
    private val statsRepository: StatsRepository,
    private val factsRepository: FactsRepository,
    private val resultsHolder: QuizResultsHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuizUiState?>(null)
    val uiState: StateFlow<QuizUiState?> = _uiState.asStateFlow()

    private var questions: List<QuizQuestion> = emptyList()
    private val attempts = mutableListOf<QuizAttempt>()

    /** Index to resume at after a [QuizUiState.Fact] card is dismissed. */
    private var pendingIndex = 0

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            questions = generateQuizQuestions(settings)
            _uiState.value = questionState(0)
        }
    }

    /**
     * Called exactly once per example, the instant an answer option is tapped (the tap itself
     * *is* the answer — there's no separate Check step). [QuizScreen]'s `AnswerOptionsGrid`
     * already goes non-interactive after the first tap, but this guard is the real backstop:
     * `Crossfade` keeps the outgoing Question composable alive (and clickable) for the fade
     * duration, so a fast double-tap could otherwise double-record the same attempt.
     */
    fun submitAnswer(example: MathExample, answer: Int) {
        val current = _uiState.value
        if (current !is QuizUiState.Question || current.example != example) return

        val correctAnswer = example.answer()
        val correct = answer == correctAnswer
        viewModelScope.launch {
            statsRepository.recordAttempt(example, correct, System.currentTimeMillis())
        }
        attempts.add(QuizAttempt(example, answer, correct, correctAnswer))
        val isLast = current.position == questions.lastIndex
        _uiState.value = QuizUiState.Feedback(
            example = example,
            options = current.options,
            correctAnswer = correctAnswer,
            selectedAnswer = answer,
            position = current.position,
            total = current.total,
            isLast = isLast,
        )
    }

    /** Auto-advance after the lifecycle-aware answer-highlight window in `QuizScreen` — this
     * also replaces the old manual "See results" tap: on the last example this goes straight to
     * [finish], not to a button.
     */
    fun onFeedbackDismissed() {
        // Same double-tap-during-Crossfade guard as submitAnswer, kept even though this is no
        // longer button-triggered: the timer could in theory fire twice if this were ever called
        // from two places.
        val current = _uiState.value
        if (current !is QuizUiState.Feedback) return

        val nextIndex = current.position + 1
        _uiState.value = when {
            // Never show a fact after the last example — there'd be nothing to return to
            // (this check runs before the FACT_EVERY check below regardless of quizLength, so a
            // fact and the final example can never collide no matter how the two divide).
            nextIndex >= questions.size -> finish()
            attempts.size % FACT_EVERY == 0 -> {
                pendingIndex = nextIndex
                QuizUiState.Fact(factsRepository.nextFact())
            }
            else -> questionState(nextIndex)
        }
    }

    fun onFactDismissed() {
        if (_uiState.value !is QuizUiState.Fact) return
        _uiState.value = questionState(pendingIndex)
    }

    private fun questionState(index: Int): QuizUiState {
        val question = questions[index]
        return QuizUiState.Question(question.example, question.options, index, questions.size)
    }

    private fun finish(): QuizUiState.Finished {
        resultsHolder.results = attempts.toList()
        return QuizUiState.Finished
    }

    private fun generateQuizQuestions(settings: UserSettings): List<QuizQuestion> {
        val length = settings.quizLength.coerceIn(UserSettings.MIN_QUIZ_LENGTH, UserSettings.MAX_QUIZ_LENGTH)
        val levels = levelSequence(settings.quizSubMode, length, settings.level)
        var previous: MathExample? = null
        return levels.map { level ->
            val operation = settings.operations.random()
            val example = exampleGenerator.generate(operation, level, previous)
            previous = example
            val options = (listOf(example.answer()) + distractors(example)).shuffled()
            QuizQuestion(example, options)
        }
    }

    private fun levelSequence(subMode: QuizSubMode, length: Int, fixedLevel: Level): List<Level> = when (subMode) {
        QuizSubMode.PROGRESSION -> progressionLevels(length)
        QuizSubMode.SHUFFLE -> List(length) { PROGRESSION_ORDER.random() }
        QuizSubMode.FIXED_LEVEL -> List(length) { fixedLevel }
    }

    /**
     * Spreads [length] examples roughly evenly across EASY,MEDIUM,HARD,STAR in that fixed block
     * order. When not evenly divisible, the remainder goes to the LATER (harder) levels — e.g.
     * 10 -> 2,2,3,3 and 13 -> 3,3,3,4.
     */
    private fun progressionLevels(length: Int): List<Level> {
        val base = length / PROGRESSION_ORDER.size
        val remainder = length % PROGRESSION_ORDER.size
        return PROGRESSION_ORDER.flatMapIndexed { i, level ->
            val extra = if (i >= PROGRESSION_ORDER.size - remainder) 1 else 0
            List(base + extra) { level }
        }
    }

    private companion object {
        const val FACT_EVERY = 4
        val PROGRESSION_ORDER = listOf(Level.EASY, Level.MEDIUM, Level.HARD, Level.STAR)
    }
}
