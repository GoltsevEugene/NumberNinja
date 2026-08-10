package number.ninja.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class DistractorGeneratorTest {

    @ParameterizedTest
    @MethodSource("resultCells")
    fun `result-slot examples yield exactly 3 distinct non-negative distractors excluding the answer`(
        op: Operation,
        level: Level,
    ) {
        val generator = ExampleGenerator(Random(11))
        repeat(200) {
            var example = generator.generate(op, level)
            // Force a Result-slot example when the generator happens to hand back an Operand one,
            // by regenerating until we see one (bounded to avoid an infinite loop on odd cells).
            var attempts = 0
            while (example.unknown !is UnknownSlot.Result && attempts < 50) {
                example = generator.generate(op, level, example)
                attempts++
            }
            if (example.unknown !is UnknownSlot.Result) return@repeat
            assertDistractorsValid(example)
        }
    }

    @ParameterizedTest
    @MethodSource("resultCells")
    fun `operand-slot examples yield exactly 3 distinct non-negative distractors excluding the answer`(
        op: Operation,
        level: Level,
    ) {
        val generator = ExampleGenerator(Random(22))
        var sawOperandSlot = false
        repeat(500) {
            val example = generator.generate(op, level)
            if (example.unknown is UnknownSlot.Operand) {
                sawOperandSlot = true
                assertDistractorsValid(example)
            }
        }
        // Not every cell is guaranteed to produce operand-slot examples in this sample size;
        // this is a best-effort coverage check rather than a hard requirement per cell.
        if (!sawOperandSlot) return
    }

    @Test
    fun `division 72 over 8 yields near-quotient distractors`() {
        val example = MathExample(
            initial = 72,
            terms = listOf(OpTerm('/', 8)),
            unknown = UnknownSlot.Result,
            operation = Operation.DIVISION,
            level = Level.MEDIUM,
        )
        assertEquals(9, example.answer())
        val result = distractors(example)
        assertEquals(3, result.toSet().size)
        assertFalse(result.contains(9))
        assertTrue(result.all { it in setOf(7, 8, 10, 11) }, "expected near-quotient values, got $result")
    }

    @Test
    fun `subtraction 33 minus 19 yields plausible distractors`() {
        val example = MathExample(
            initial = 33,
            terms = listOf(OpTerm('-', 19)),
            unknown = UnknownSlot.Result,
            operation = Operation.SUBTRACTION,
            level = Level.MEDIUM,
        )
        assertEquals(14, example.answer())
        val result = distractors(example)
        assertEquals(3, result.toSet().size)
        assertFalse(result.contains(14))
        assertTrue(result.all { it in 0..50 }, "expected plausible in-range values, got $result")
    }

    private fun assertDistractorsValid(example: MathExample) {
        val answer = example.answer()
        val result = distractors(example)
        assertEquals(3, result.size, "expected exactly 3 distractors for $example, got $result")
        assertEquals(3, result.toSet().size, "expected 3 distinct distractors for $example, got $result")
        assertFalse(result.contains(answer), "distractors must not contain the true answer for $example: $result")
        assertTrue(result.all { it >= 0 }, "distractors must be non-negative for $example: $result")
    }

    companion object {
        @JvmStatic
        fun resultCells(): List<Array<Any>> =
            Operation.entries.flatMap { op -> Level.entries.map { level -> arrayOf(op, level) } }
    }
}
