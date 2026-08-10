package number.ninja.domain

import kotlin.random.Random

/**
 * Generates [MathExample]s per §5.2 of the spec. All rejection-sampling loops below terminate
 * quickly in practice (the constraints they enforce are satisfied by a large fraction of random
 * draws) but are capped defensively so a future constraint change can never hang the app.
 */
class ExampleGenerator(private val random: Random = Random.Default) {

    /** Generates one example, retrying until it differs from [previous] (spec §5.4: no immediate repeat). */
    fun generate(operation: Operation, level: Level, previous: MathExample? = null): MathExample {
        repeat(MAX_NO_REPEAT_ATTEMPTS) {
            val candidate = generateOne(operation, level)
            if (previous == null || !sameExample(candidate, previous)) return candidate
        }
        return generateOne(operation, level)
    }

    private fun sameExample(a: MathExample, b: MathExample): Boolean =
        a.initial == b.initial && a.terms == b.terms && a.unknown == b.unknown

    private fun generateOne(operation: Operation, level: Level): MathExample = when (operation) {
        Operation.ADDITION, Operation.SUBTRACTION -> generateAdditive(operation, level)
        Operation.MULTIPLICATION, Operation.DIVISION -> generateMultiplicative(operation, level)
    }

    // ---------------------------------------------------------------- additive family

    private fun generateAdditive(op: Operation, level: Level): MathExample = when (level) {
        Level.EASY -> if (random.nextBoolean()) additiveWithin20NoCarry(op) else twoDigitPlusOneDigitNoCarry(op)
        Level.MEDIUM -> when (random.nextInt(3)) {
            0 -> additiveWithin100NoCarry(op)
            1 -> additiveMultistep(level, termCount = 3, maxOperand = 20)
            else -> additiveUnknownAddend(maxSum = 100)
        }
        Level.HARD -> if (random.nextBoolean()) {
            additiveWithin100WithCarry(op)
        } else if (random.nextBoolean()) {
            additiveMultistep(level, termCount = 3, maxOperand = 60, unknownSlotChance = 0.5)
        } else {
            additiveUnknownAddend(maxSum = 100)
        }
        Level.STAR -> when (random.nextInt(4)) {
            0 -> additiveWithin1000(op)
            1 -> additiveMultistepCrossHundred()
            2 -> additiveUnknownMinuendOrSubtrahend()
            else -> additiveWithBrackets()
        }
        // Addition/subtraction have no "times table" concept — fall back to MEDIUM's mix so
        // picking this level with an additive operation enabled still generates something
        // reasonable instead of throwing.
        Level.TABLES -> generateAdditive(op, Level.MEDIUM)
    }

    private fun additiveWithin20NoCarry(op: Operation): MathExample = rejectionSample {
        val a = random.nextIntInclusive(2, 20)
        val b = random.nextIntInclusive(1, 18)
        when (op) {
            Operation.ADDITION -> if (a + b <= 20 && noCarry(a, b)) twoTermExample(a, '+', b, op, Level.EASY) else null
            else -> if (a >= b && noBorrow(a, b)) twoTermExample(a, '-', b, op, Level.EASY) else null
        }
    }

    private fun twoDigitPlusOneDigitNoCarry(op: Operation): MathExample = rejectionSample {
        val a = random.nextIntInclusive(10, 98)
        val b = random.nextIntInclusive(1, 9)
        when (op) {
            Operation.ADDITION -> if (noCarry(a, b) && a + b <= 99) twoTermExample(a, '+', b, op, Level.EASY) else null
            else -> if (a >= b && noBorrow(a, b)) twoTermExample(a, '-', b, op, Level.EASY) else null
        }
    }

    private fun additiveWithin100NoCarry(op: Operation): MathExample = rejectionSample {
        val a = random.nextIntInclusive(10, 89)
        val b = random.nextIntInclusive(10, 89)
        when (op) {
            Operation.ADDITION -> if (a + b <= 99 && noCarry(a, b)) twoTermExample(a, '+', b, op, Level.MEDIUM) else null
            else -> if (a >= b && noBorrow(a, b)) twoTermExample(a, '-', b, op, Level.MEDIUM) else null
        }
    }

    private fun additiveWithin100WithCarry(op: Operation): MathExample = rejectionSample {
        val a = random.nextIntInclusive(10, 89)
        val b = random.nextIntInclusive(10, 89)
        when (op) {
            Operation.ADDITION -> if (a + b <= 99 && !noCarry(a, b)) twoTermExample(a, '+', b, op, Level.HARD) else null
            else -> if (a >= b && !noBorrow(a, b)) twoTermExample(a, '-', b, op, Level.HARD) else null
        }
    }

    private fun additiveWithin1000(op: Operation): MathExample = rejectionSample {
        val a = random.nextIntInclusive(100, 899)
        val b = random.nextIntInclusive(100, 899)
        when (op) {
            Operation.ADDITION -> if (a + b <= 999) twoTermExample(a, '+', b, op, Level.STAR) else null
            else -> if (a >= b) twoTermExample(a, '-', b, op, Level.STAR) else null
        }
    }

    /** Chain of [termCount] operands joined by random +/- with a non-negative running total throughout. */
    private fun additiveMultistep(level: Level, termCount: Int, maxOperand: Int, unknownSlotChance: Double = 0.0): MathExample {
        while (true) {
            val initial = random.nextIntInclusive(2, maxOperand)
            var running = initial
            val terms = mutableListOf<OpTerm>()
            var ok = true
            repeat(termCount - 1) {
                val value = random.nextIntInclusive(1, maxOperand)
                val addSign = if (running <= value) true else random.nextBoolean()
                val nextRunning = if (addSign) running + value else running - value
                if (nextRunning < 0) {
                    ok = false
                } else {
                    terms.add(OpTerm(if (addSign) '+' else '-', value))
                    running = nextRunning
                }
            }
            if (!ok) continue
            val unknown = if (unknownSlotChance > 0 && random.nextDouble() < unknownSlotChance) {
                UnknownSlot.Operand(random.nextIntInclusive(0, terms.size))
            } else {
                UnknownSlot.Result
            }
            return MathExample(initial, terms, unknown, Operation.ADDITION, level)
        }
    }

    /** 5+□=12: one operand of a plain addition fact is hidden. */
    private fun additiveUnknownAddend(maxSum: Int): MathExample = rejectionSample {
        val a = random.nextIntInclusive(1, maxSum - 1)
        val b = random.nextIntInclusive(1, maxSum - a)
        val slot = if (random.nextBoolean()) UnknownSlot.Operand(0) else UnknownSlot.Operand(1)
        MathExample(a, listOf(OpTerm('+', b)), slot, Operation.ADDITION, Level.MEDIUM)
    }

    /** 23+□+46=93: three-term addition, sum intentionally crosses a hundred boundary, middle term hidden. */
    private fun additiveMultistepCrossHundred(): MathExample = rejectionSample {
        val a = random.nextIntInclusive(15, 60)
        val c = random.nextIntInclusive(15, 60)
        val total = random.nextIntInclusive(maxOf(a + c + 1, 90), 199)
        val b = total - a - c
        if (b < 1 || b > 90) null else {
            val slotIndex = random.nextIntInclusive(0, 2)
            val unknown = UnknownSlot.Operand(slotIndex)
            MathExample(a, listOf(OpTerm('+', b), OpTerm('+', c)), unknown, Operation.ADDITION, Level.STAR)
        }
    }

    /** □-15=27 (unknown minuend) or 82-□=45 (unknown subtrahend). */
    private fun additiveUnknownMinuendOrSubtrahend(): MathExample = rejectionSample {
        val minuend = random.nextIntInclusive(20, 99)
        val subtrahend = random.nextIntInclusive(1, minuend - 1)
        val slot = if (random.nextBoolean()) UnknownSlot.Operand(0) else UnknownSlot.Operand(1)
        MathExample(minuend, listOf(OpTerm('-', subtrahend)), slot, Operation.SUBTRACTION, Level.STAR)
    }

    /** (34+16)-27: brackets are cosmetic — grouping the first two terms never changes a pure +/- result. */
    private fun additiveWithBrackets(): MathExample = rejectionSample {
        val a = random.nextIntInclusive(10, 50)
        val b = random.nextIntInclusive(10, 50)
        val c = random.nextIntInclusive(1, a + b)
        MathExample(a, listOf(OpTerm('+', b), OpTerm('-', c)), UnknownSlot.Result, Operation.ADDITION, Level.STAR, bracketedRange = 0..1)
    }

    private fun noCarry(a: Int, b: Int): Boolean = (a % 10 + b % 10) <= 9
    private fun noBorrow(a: Int, b: Int): Boolean = (a % 10) >= (b % 10)

    private fun twoTermExample(a: Int, op: Char, b: Int, operation: Operation, level: Level): MathExample =
        MathExample(a, listOf(OpTerm(op, b)), UnknownSlot.Result, operation, level)

    // ------------------------------------------------------------ multiplicative family

    private fun generateMultiplicative(op: Operation, level: Level): MathExample = when (level) {
        Level.EASY -> tableFact(op, level, factorRange = 2..5, otherRange = 1..10)
        Level.MEDIUM -> tableFact(op, level, factorRange = 2..10, otherRange = 1..10)
        Level.HARD -> when (op) {
            Operation.MULTIPLICATION -> twoDigitTimesOneDigit(carryRequired = false, level = Level.HARD)
            // Intermediate step between MEDIUM's table-recall division and STAR's compound variants:
            // guarantees a genuine two-digit dividend (unlike MEDIUM, where it's incidental).
            else -> twoDigitDivOneDigit(Level.HARD)
        }
        Level.STAR -> when (op) {
            Operation.MULTIPLICATION -> when (random.nextInt(4)) {
                0 -> twoDigitTimesOneDigit(carryRequired = true, level = Level.STAR)
                1 -> byTenOrHundred(Operation.MULTIPLICATION)
                2 -> orderOfOperations()
                else -> reverseMultiplication()
            }
            else -> when (random.nextInt(3)) {
                0 -> twoDigitDivOneDigit(Level.STAR)
                1 -> byTenOrHundred(Operation.DIVISION)
                else -> reverseDivision()
            }
        }
        // Dedicated table-recall drilling across the full 2..10 range (spec follow-up: "check
        // the multiplication/division table"), wider than EASY's 2..5 and plain recall rather
        // than MEDIUM's occasional multistep framing.
        Level.TABLES -> tableFact(op, level, factorRange = 2..10, otherRange = 2..10)
    }

    /** A basic multiplication-table fact, framed as multiplication or (remainder-free) division. */
    private fun tableFact(op: Operation, level: Level, factorRange: IntRange, otherRange: IntRange): MathExample {
        val factor = random.nextIntInclusive(factorRange.first, factorRange.last)
        val other = random.nextIntInclusive(otherRange.first, otherRange.last)
        val product = factor * other
        return when (op) {
            Operation.MULTIPLICATION -> MathExample(factor, listOf(OpTerm('*', other)), UnknownSlot.Result, op, level)
            else -> MathExample(product, listOf(OpTerm('/', factor)), UnknownSlot.Result, op, level)
        }
    }

    private fun twoDigitTimesOneDigit(carryRequired: Boolean, level: Level): MathExample = rejectionSample {
        val a = random.nextIntInclusive(10, 99)
        val b = random.nextIntInclusive(2, 9)
        val carries = (a % 10) * b >= 10
        if (carryRequired != carries) null else MathExample(a, listOf(OpTerm('*', b)), UnknownSlot.Result, Operation.MULTIPLICATION, level)
    }

    /** 78:6 — a two-digit dividend divided by a one-digit divisor with an exact (remainder-free) quotient. */
    private fun twoDigitDivOneDigit(level: Level): MathExample = rejectionSample {
        val divisor = random.nextIntInclusive(2, 9)
        val quotient = random.nextIntInclusive(2, 9)
        val dividend = divisor * quotient
        if (dividend < 10) null else MathExample(dividend, listOf(OpTerm('/', divisor)), UnknownSlot.Result, Operation.DIVISION, level)
    }

    /** 23×10, 400:100. */
    private fun byTenOrHundred(op: Operation): MathExample {
        val factor = if (random.nextBoolean()) 10 else 100
        return if (op == Operation.MULTIPLICATION) {
            val a = if (factor == 10) random.nextIntInclusive(2, 99) else random.nextIntInclusive(2, 20)
            MathExample(a, listOf(OpTerm('*', factor)), UnknownSlot.Result, op, Level.STAR)
        } else {
            val quotient = if (factor == 10) random.nextIntInclusive(2, 99) else random.nextIntInclusive(2, 20)
            val dividend = quotient * factor
            MathExample(dividend, listOf(OpTerm('/', factor)), UnknownSlot.Result, op, Level.STAR)
        }
    }

    /** 6×3+15, 50-4×8: order-of-operations chain, precedence-evaluated, never goes negative. */
    private fun orderOfOperations(): MathExample = rejectionSample {
        val a = random.nextIntInclusive(2, 9)
        val b = random.nextIntInclusive(2, 9)
        val product = a * b
        val c = random.nextIntInclusive(1, 30)
        val leadsWithProduct = random.nextBoolean()
        val (initial, terms) = if (leadsWithProduct) {
            a to listOf(OpTerm('*', b), OpTerm('+', c))
        } else {
            val addSign = c >= product
            c to listOf(OpTerm(if (addSign) '+' else '-', a), OpTerm('*', b))
        }
        val example = MathExample(initial, terms, UnknownSlot.Result, Operation.MULTIPLICATION, Level.STAR)
        if (example.result() < 0) null else example
    }

    /** □×8=64 or 96:□=12. */
    private fun reverseMultiplication(): MathExample = rejectionSample {
        val a = random.nextIntInclusive(2, 9)
        val b = random.nextIntInclusive(2, 9)
        val slot = if (random.nextBoolean()) UnknownSlot.Operand(0) else UnknownSlot.Operand(1)
        MathExample(a, listOf(OpTerm('*', b)), slot, Operation.MULTIPLICATION, Level.STAR)
    }

    private fun reverseDivision(): MathExample = rejectionSample {
        val divisor = random.nextIntInclusive(2, 9)
        val quotient = random.nextIntInclusive(2, 9)
        val dividend = divisor * quotient
        MathExample(dividend, listOf(OpTerm('/', divisor)), UnknownSlot.Operand(1), Operation.DIVISION, Level.STAR)
    }

    private fun Random.nextIntInclusive(min: Int, max: Int): Int =
        if (min >= max) min else this.nextInt(min, max + 1)

    private fun <T> rejectionSample(produce: () -> T?): T {
        repeat(MAX_REJECTION_ATTEMPTS) {
            produce()?.let { return it }
        }
        error("Failed to satisfy generation constraints after $MAX_REJECTION_ATTEMPTS attempts")
    }

    private companion object {
        const val MAX_REJECTION_ATTEMPTS = 1000
        const val MAX_NO_REPEAT_ATTEMPTS = 20
    }
}
