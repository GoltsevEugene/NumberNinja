package number.ninja.domain

import kotlin.random.Random

/**
 * Generates exactly 3 distinct wrong-answer options for a multiple-choice quiz UI, plausible
 * given the kind of mistake a child would actually make for the given [MathExample.operation] —
 * not scattered random noise. Combined with [MathExample.answer] the caller has 4 total options.
 *
 * [random] is accepted for API symmetry with other generators and to allow deterministic,
 * seeded variation in the future; the current algorithm is otherwise deterministic given the
 * same [example] so tests can assert exact/near-exact output sets.
 */
fun distractors(example: MathExample, random: Random = Random.Default): List<Int> {
    val answer = example.answer()
    val unknown = example.unknown

    val priorityCandidates: List<Int> = if (unknown is UnknownSlot.Operand) {
        operandCandidates(answer)
    } else {
        when (example.operation) {
            Operation.DIVISION -> divisionResultCandidates(answer)
            Operation.SUBTRACTION -> subtractionResultCandidates(example, answer)
            Operation.ADDITION -> additionResultCandidates(example, answer)
            Operation.MULTIPLICATION -> multiplicationResultCandidates(example, answer)
        }
    }

    return pickThree(priorityCandidates, answer)
}

/** Missing-operand slots have no "plausible mistake" semantics — just perturb the true value. */
private fun operandCandidates(answer: Int): List<Int> = listOf(
    answer - 1, answer + 1,
    answer - 2, answer + 2,
    answer - 3, answer + 3,
)

/** e.g. 72÷8=9 → child answers a nearby quotient: 7, 8, 10, or 11. */
private fun divisionResultCandidates(answer: Int): List<Int> = listOf(
    answer - 1, answer + 1,
    answer - 2, answer + 2,
)

/**
 * e.g. 33-19=14 → common mistakes: off-by-one/ten, or forgetting to borrow and subtracting only
 * the ones (or only the tens) digit of the subtrahend, e.g. 33-9=24.
 */
private fun subtractionResultCandidates(example: MathExample, answer: Int): List<Int> {
    val a = example.initial
    val b = example.terms.getOrNull(0)?.value ?: 0
    val onesOnlyError = a - (b % 10)
    val tensOnlyError = a - (b - b % 10)
    return listOf(
        onesOnlyError, tensOnlyError,
        answer - 10, answer + 10,
        answer - 1, answer + 1,
    )
}

/** e.g. per-digit addition without carrying, plus off-by-one/ten slips. */
private fun additionResultCandidates(example: MathExample, answer: Int): List<Int> {
    val a = example.initial
    val b = example.terms.getOrNull(0)?.value ?: 0
    val onesNoCarry = (a % 10 + b % 10) % 10
    val tensNoCarry = (a / 10 + b / 10) * 10
    val noCarrySum = tensNoCarry + onesNoCarry
    return listOf(
        noCarrySum,
        answer + 1, answer - 1,
        answer + 10, answer - 10,
    )
}

/** e.g. 6×7=42 → adjacent multiplication-table facts: 5×7, 7×7, 6×6, 6×8. */
private fun multiplicationResultCandidates(example: MathExample, answer: Int): List<Int> {
    val a = example.initial
    val b = example.terms.getOrNull(0)?.value ?: 1
    return listOf(
        (a - 1) * b, (a + 1) * b,
        a * (b - 1), a * (b + 1),
        answer + 1, answer - 1,
    )
}

/**
 * Picks 3 distinct, non-negative candidates (excluding [answer]) from [priorityCandidates] in
 * priority order, then pads with widening true±k perturbations if fewer than 3 survive filtering.
 */
private fun pickThree(priorityCandidates: List<Int>, answer: Int): List<Int> {
    val chosen = mutableListOf<Int>()

    fun tryAdd(candidate: Int) {
        if (chosen.size < 3 && candidate >= 0 && candidate != answer && candidate !in chosen) {
            chosen.add(candidate)
        }
    }

    for (candidate in priorityCandidates) {
        if (chosen.size == 3) break
        tryAdd(candidate)
    }

    var k = 1
    while (chosen.size < 3) {
        tryAdd(answer - k)
        if (chosen.size < 3) tryAdd(answer + k)
        k++
    }

    return chosen
}
