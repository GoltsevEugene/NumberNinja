package number.ninja.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiInteractionGuardsTest {

    @Test
    fun `correct answer uses short feedback window`() {
        assertEquals(1_000L, answerFeedbackDurationMillis(isCorrect = true))
    }

    @Test
    fun `wrong answer keeps the longer feedback window`() {
        assertEquals(2_300L, answerFeedbackDurationMillis(isCorrect = false))
    }

    @Test
    fun `click gate accepts only the first click until unlocked`() {
        val gate = ClickGate()

        assertTrue(gate.tryLock())
        assertFalse(gate.tryLock())

        gate.unlock()
        assertTrue(gate.tryLock())
    }

    @Test
    fun `answer option states distinguish selected and revealed answers`() {
        assertEquals(
            AnswerOptionState.NEUTRAL,
            answerOptionStateFor(option = 7, selected = null, correctAnswer = 7),
        )
        assertEquals(
            AnswerOptionState.SELECTED_CORRECT,
            answerOptionStateFor(option = 7, selected = 7, correctAnswer = 7),
        )
        assertEquals(
            AnswerOptionState.SELECTED_WRONG,
            answerOptionStateFor(option = 5, selected = 5, correctAnswer = 7),
        )
        assertEquals(
            AnswerOptionState.REVEALED_CORRECT,
            answerOptionStateFor(option = 7, selected = 5, correctAnswer = 7),
        )
        assertEquals(
            AnswerOptionState.NEUTRAL,
            answerOptionStateFor(option = 3, selected = 5, correctAnswer = 7),
        )
    }
}
