package number.ninja.ui.quiz

import number.ninja.domain.MathExample

/**
 * One answered quiz question, kept for the results/review screen (spec §6.2): every example is
 * shown there, not just the wrong ones, so this is recorded for correct answers too.
 */
data class QuizAttempt(
    val example: MathExample,
    val givenAnswer: Int,
    val correct: Boolean,
    val correctAnswer: Int,
)
