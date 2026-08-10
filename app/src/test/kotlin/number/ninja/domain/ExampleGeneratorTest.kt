package number.ninja.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

class ExampleGeneratorTest {

    private val samplesPerCell = 500

    @ParameterizedTest
    @MethodSource("cells")
    fun `answer is always non-negative and matches evaluated result`(op: Operation, level: Level) {
        val generator = ExampleGenerator(Random(42))
        repeat(samplesPerCell) {
            val example = generator.generate(op, level)
            assertTrue(example.answer() >= 0, "negative answer for $op/$level: $example")
            assertTrue(example.result() >= 0, "negative result for $op/$level: $example")
            assertEquals(MathExample.evaluate(example.initial, example.terms), example.result())
        }
    }

    @ParameterizedTest
    @MethodSource("cells")
    fun `operands stay within the level's declared range`(op: Operation, level: Level) {
        val generator = ExampleGenerator(Random(7))
        repeat(samplesPerCell) {
            val example = generator.generate(op, level)
            val bound = maxOperandBound(op, level)
            example.values.forEach { v ->
                assertTrue(v in 0..bound, "operand $v out of range 0..$bound for $op/$level: $example")
            }
        }
    }

    @ParameterizedTest
    @MethodSource("additiveEasyMediumCells")
    fun `easy and medium plain two-term addition-subtraction never carries or borrows`(op: Operation, level: Level) {
        val generator = ExampleGenerator(Random(123))
        var sawPlainTwoTerm = false
        repeat(samplesPerCell) {
            val example = generator.generate(op, level)
            if (example.terms.size == 1 && example.unknown == UnknownSlot.Result) {
                sawPlainTwoTerm = true
                val a = example.initial
                val b = example.terms[0].value
                when (example.terms[0].op) {
                    '+' -> assertTrue((a % 10 + b % 10) <= 9, "carry in plain addition at $op/$level: $example")
                    '-' -> assertTrue((a % 10) >= (b % 10), "borrow in plain subtraction at $op/$level: $example")
                }
            }
        }
        assertTrue(sawPlainTwoTerm, "expected to see at least one plain two-term example for $op/$level")
    }

    @ParameterizedTest
    @MethodSource("divisionCells")
    fun `division is always exact, never uses a remainder`(level: Level) {
        val generator = ExampleGenerator(Random(99))
        repeat(samplesPerCell) {
            val example = generator.generate(Operation.DIVISION, level)
            example.terms.forEach { term ->
                if (term.op == '/') {
                    assertTrue(term.value != 0, "division by zero: $example")
                }
            }
            // Every intermediate factor produced by evaluate() must be an exact integer division;
            // MathExample.evaluate performs Int division, so verify no remainder was silently dropped.
            var running = example.initial
            for (term in example.terms) {
                if (term.op == '/') {
                    assertEquals(0, running % term.value, "division with remainder: $example")
                    running /= term.value
                } else if (term.op == '*') {
                    running *= term.value
                } else {
                    running = if (term.op == '+') running + term.value else running - term.value
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    fun `no-repeat generation avoids the immediately preceding example`() {
        val generator = ExampleGenerator(Random(1))
        var previous = generator.generate(Operation.ADDITION, Level.EASY)
        repeat(200) {
            val next = generator.generate(Operation.ADDITION, Level.EASY, previous)
            assertFalse(
                next.initial == previous.initial && next.terms == previous.terms && next.unknown == previous.unknown,
                "generator repeated the immediately preceding example: $next"
            )
            previous = next
        }
    }

    @org.junit.jupiter.api.Test
    fun `unknown-operand examples resolve to the correct hidden value`() {
        val generator = ExampleGenerator(Random(55))
        var sawUnknownOperand = false
        repeat(2000) {
            val example = generator.generate(Operation.SUBTRACTION, Level.STAR)
            if (example.unknown is UnknownSlot.Operand) {
                sawUnknownOperand = true
                val idx = (example.unknown as UnknownSlot.Operand).index
                assertEquals(example.values[idx], example.answer())
            }
        }
        assertTrue(sawUnknownOperand, "expected at least one unknown-operand STAR subtraction example")
    }

    private fun maxOperandBound(op: Operation, level: Level): Int = when {
        op == Operation.ADDITION || op == Operation.SUBTRACTION -> when (level) {
            Level.EASY -> 98
            Level.MEDIUM -> 100
            Level.HARD -> 100
            Level.STAR -> 999
            Level.TABLES -> 100 // falls back to MEDIUM-equivalent generation
        }
        else -> when (level) { // multiplication/division
            Level.EASY -> 50
            Level.MEDIUM -> 100
            Level.HARD -> 891 // 99*9
            Level.STAR -> 2000 // covers ×100 star variant headroom
            Level.TABLES -> 100 // 10*10, full 2..10 table recall
        }
    }

    companion object {
        @JvmStatic
        fun cells(): List<Array<Any>> =
            Operation.entries.flatMap { op -> Level.entries.map { level -> arrayOf(op, level) } }

        @JvmStatic
        fun additiveEasyMediumCells(): List<Array<Any>> = listOf(
            arrayOf(Operation.ADDITION, Level.EASY),
            arrayOf(Operation.SUBTRACTION, Level.EASY),
            arrayOf(Operation.ADDITION, Level.MEDIUM),
            arrayOf(Operation.SUBTRACTION, Level.MEDIUM),
        )

        @JvmStatic
        fun divisionCells(): List<Array<Any>> = Level.entries.map { arrayOf(it) }
    }
}
