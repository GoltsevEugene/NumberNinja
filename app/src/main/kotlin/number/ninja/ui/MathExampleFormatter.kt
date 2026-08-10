package number.ninja.ui

import number.ninja.domain.MathExample
import number.ninja.domain.UnknownSlot

/**
 * Renders a [MathExample] as a compact display string, e.g. "5+7=?", "5+□=12",
 * "(34+16)-27=?". Deliberately pure/no-Android: operator glyphs (×, ÷), □ and parens are
 * universal, not localized text, so this doesn't need string resources.
 *
 * Lives at the top level of `ui/` (alongside [labelRes]-style enum mappings in
 * `ui/EnumLabels.kt`), not inside `ui/practice/`, because both the future quiz screen and
 * the quiz results-review screen (spec §6.2) will need it too — a screen-scoped package
 * would make them import from a sibling feature package.
 */
fun MathExample.render(): String {
    val sb = StringBuilder()
    for (position in 0..terms.size) {
        if (bracketedRange?.first == position) sb.append('(')
        if (position > 0) sb.append(opSymbol(terms[position - 1].op))
        val hidden = (unknown as? UnknownSlot.Operand)?.index == position
        sb.append(if (hidden) HIDDEN_SLOT else values[position].toString())
        if (bracketedRange?.last == position) sb.append(')')
    }
    sb.append('=')
    sb.append(if (unknown == UnknownSlot.Result) "?" else result().toString())
    return sb.toString()
}

private fun opSymbol(op: Char): Char = when (op) {
    '*' -> '×'
    '/' -> '÷'
    else -> op
}

private const val HIDDEN_SLOT = "□"
