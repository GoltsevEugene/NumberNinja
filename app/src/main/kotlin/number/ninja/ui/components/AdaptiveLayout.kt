package number.ninja.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ambient [WindowWidthSizeClass], computed once in `MainActivity` via
 * `calculateWindowSizeClass` and provided at the composition root (above [NumberNinjaTheme] /
 * `NumberNinjaNavHost`). Screens read this directly instead of receiving it as a constructor or
 * composable parameter — same "read a cross-cutting dependency locally, don't thread it through
 * every signature" precedent `SettingsRepository`/`koinInject()` already sets throughout this
 * codebase (see `ui/nav/NumberNinjaNavHost.kt`, `ui/session/SessionScreen.kt`,
 * `ui/quiz/QuizResultsScreen.kt`). Threading it as a param through every `composable<Route.X>`
 * body and every screen's constructor would touch far more call sites for no benefit, since
 * nothing here is a ViewModel concern (no business logic depends on window size).
 *
 * Defaults to [WindowWidthSizeClass.Compact] so anything composed without the provider (e.g. a
 * future `@Preview`, or a unit test that renders a screen composable directly) still gets the
 * phone-identical layout rather than crashing on a missing ambient.
 */
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * Max width for centered content on medium/expanded windows (spec §2 / §12 step 9). ~600dp
 * comfortably fits a settings form or a single math example without feeling cramped, while
 * leaving visible margins on a 10" tablet in landscape (expanded, ~840dp+ wide).
 */
private val AdaptiveMaxContentWidth = 600.dp

/**
 * Drop-in replacement for a screen's root content [Column] (the one placed directly inside a
 * `Scaffold`'s content slot). On [WindowWidthSizeClass.Compact] this renders byte-for-byte what
 * a plain `Column` with the same [modifier]/[horizontalAlignment]/[verticalArrangement] would —
 * it is wrapped in a [Box] that itself always fills [modifier]'s bounds, and the inner `Column`
 * always fills that `Box` too (`fillMaxHeight` + `fillMaxWidth`), so `Box`'s `contentAlignment`
 * never has room to move anything; the inner `Column`'s own alignment/arrangement do 100% of the
 * work, exactly as before. On medium/expanded, the inner `Column`'s width is additionally capped
 * at [maxWidth] (`widthIn(max = maxWidth).fillMaxWidth()`), which gives the outer `Box` real
 * slack to center it horizontally.
 *
 * [innerModifier] is appended after the width constraint, for callers that need e.g.
 * `verticalScroll` on the scrolling content itself rather than on the centering `Box`.
 */
@Composable
fun AdaptiveCenteredColumn(
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxWidth: Dp = AdaptiveMaxContentWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isCompact = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Compact
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (isCompact) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(max = maxWidth).fillMaxWidth()
                    },
                )
                .then(innerModifier),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * For content nested *inside* an already-centering container (e.g. free practice's/quiz's
 * `Box(contentAlignment = Alignment.Center) { Crossfade { ... } }`), where only the innermost
 * question/feedback/fact `Column` needs its width capped — the outer `Box` already centers
 * whatever width that inner `Column` ends up reporting. Replaces a plain `Modifier.fillMaxWidth()`
 * one-for-one: identical on [WindowWidthSizeClass.Compact], additionally capped at [maxWidth]
 * otherwise.
 */
@Composable
fun Modifier.adaptiveContentWidth(maxWidth: Dp = AdaptiveMaxContentWidth): Modifier {
    val isCompact = LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Compact
    return if (isCompact) this.fillMaxWidth() else this.widthIn(max = maxWidth).fillMaxWidth()
}
