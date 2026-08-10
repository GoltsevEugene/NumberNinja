package number.ninja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import number.ninja.ui.theme.onSuccessContainerColor
import number.ninja.ui.theme.successColor
import number.ninja.ui.theme.successContainerColor

/**
 * 2x2 grid of tappable answer options for free practice / quiz (replaces manual numeric entry).
 * A tap on any option calls [onSelect] exactly once — [enabled] must be flipped to `false` by the
 * caller the moment an option is chosen (via [selected] going non-null) so a fast second tap
 * during a state transition can't fire [onSelect] again; this composable itself doesn't debounce,
 * it just becomes non-interactive once [selected] != null.
 *
 * Coloring (spec: tap-to-answer, instant highlight, no separate Check step):
 * - [selected] == null: every option neutral (`surfaceVariant`).
 * - [selected] == [correctAnswer]: that option highlights green (`successContainer`).
 * - [selected] != [correctAnswer]: the tapped option highlights red (`errorContainer`) AND
 *   [correctAnswer] simultaneously highlights green, so a wrong tap always reveals the right
 *   answer too. All other options stay neutral.
 *
 * [options] must already be in final display order (shuffled once by the caller/ViewModel when
 * the question was generated) — this composable does not shuffle, so it renders identically
 * across the Question -> Feedback state transition without needing to smuggle a `remember` key
 * across two different composables on either side of a `Crossfade`.
 */
@Composable
fun AnswerOptionsGrid(
    options: List<Int>,
    selected: Int?,
    correctAnswer: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    AnswerOptionCard(
                        value = option,
                        highlight = highlightFor(option, selected, correctAnswer),
                        enabled = selected == null,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private enum class AnswerOptionHighlight { NONE, CORRECT, WRONG }

private fun highlightFor(option: Int, selected: Int?, correctAnswer: Int): AnswerOptionHighlight = when {
    selected == null -> AnswerOptionHighlight.NONE
    option == correctAnswer -> AnswerOptionHighlight.CORRECT
    option == selected -> AnswerOptionHighlight.WRONG
    else -> AnswerOptionHighlight.NONE
}

@Composable
private fun AnswerOptionCard(
    value: Int,
    highlight: AnswerOptionHighlight,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (highlight) {
        AnswerOptionHighlight.CORRECT -> successContainerColor()
        AnswerOptionHighlight.WRONG -> MaterialTheme.colorScheme.errorContainer
        AnswerOptionHighlight.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (highlight) {
        AnswerOptionHighlight.CORRECT -> onSuccessContainerColor()
        AnswerOptionHighlight.WRONG -> MaterialTheme.colorScheme.onErrorContainer
        AnswerOptionHighlight.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor: Color? = when (highlight) {
        AnswerOptionHighlight.CORRECT -> successColor()
        AnswerOptionHighlight.WRONG -> MaterialTheme.colorScheme.error
        AnswerOptionHighlight.NONE -> null
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp),
        // `disabledContainerColor`/`disabledContentColor` must be set explicitly too: once an
        // option is picked, every card in the grid (including the highlighted correct/wrong
        // ones) has `enabled = false`, and Card silently falls back to M3's default *dimmed*
        // disabled colors for any slot not given here — which would hide the green/red highlight
        // entirely behind the disabled-state dimming. Mirroring containerColor/contentColor here
        // keeps the highlight visible while still disabling the click itself.
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        border = borderColor?.let { BorderStroke(2.dp, it) },
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium)
        }
    }
}
