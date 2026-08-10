package number.ninja.domain

/** '+', '-', '*', '/' */
data class OpTerm(val op: Char, val value: Int) {
    init {
        require(op == '+' || op == '-' || op == '*' || op == '/') { "Unsupported operator: $op" }
    }
}

/** Which number in the expression is hidden and must be solved for. */
sealed class UnknownSlot {
    /** The final result is hidden — the classic "solve it" task. */
    data object Result : UnknownSlot()

    /** One of the operands is hidden. Index 0 = [MathExample.initial], index i>=1 = terms[i-1]. */
    data class Operand(val index: Int) : UnknownSlot()
}

/**
 * A left-to-right chain `initial (op1 value1) (op2 value2) ...` evaluated with standard
 * `*`/`/` precedence over `+`/`-`. Brackets are cosmetic (grouping display only) because every
 * generated expression is constructed so evaluation order never needs them to change the result.
 */
data class MathExample(
    val initial: Int,
    val terms: List<OpTerm>,
    val unknown: UnknownSlot,
    val operation: Operation,
    val level: Level,
    /** Inclusive term-position range (0=initial, i=terms[i-1]) wrapped in parentheses for display, or null. */
    val bracketedRange: IntRange? = null,
) {
    /** All operand values in left-to-right order, including the hidden one (we know it — we generated it). */
    val values: List<Int> get() = listOf(initial) + terms.map { it.value }

    /** The full left-to-right expression evaluated with `*`/`/` precedence. */
    fun result(): Int = evaluate(initial, terms)

    /** The value the learner must produce. */
    fun answer(): Int = when (val u = unknown) {
        UnknownSlot.Result -> result()
        is UnknownSlot.Operand -> values[u.index]
    }

    companion object {
        fun evaluate(initial: Int, terms: List<OpTerm>): Int {
            val factors = mutableListOf(initial)
            for (term in terms) {
                when (term.op) {
                    '+' -> factors.add(term.value)
                    '-' -> factors.add(-term.value)
                    '*' -> factors[factors.lastIndex] = factors.last() * term.value
                    '/' -> factors[factors.lastIndex] = factors.last() / term.value
                }
            }
            return factors.sum()
        }
    }
}
