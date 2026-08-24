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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import number.ninja.R
import number.ninja.ui.theme.onSuccessContainerColor
import number.ninja.ui.theme.successColor
import number.ninja.ui.theme.successContainerColor

/**
 * 2x2 grid of tappable answer options for free practice / quiz (replaces manual numeric entry).
 * A tap on any option calls [onSelect] exactly once — [enabled] must be flipped to `false` by the
 * caller while the grid should not accept input. The grid also closes a synchronous local gate
 * before invoking [onSelect], so a second event arriving before the caller's state recomposes
 * cannot escape. ViewModels retain their own state guard as the final business-logic backstop.
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
    enabled: Boolean = true,
) {
    // This state changes synchronously inside the click callback, one frame earlier than a
    // selected-answer state propagated back through ViewModel -> Flow -> recomposition can.
    val selectionGate = remember(options, correctAnswer) { ClickGate() }
    val interactionEnabled = enabled && selected == null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    AnswerOptionCard(
                        value = option,
                        state = answerOptionStateFor(option, selected, correctAnswer),
                        enabled = interactionEnabled,
                        onClick = {
                            if (interactionEnabled && selectionGate.tryLock()) {
                                onSelect(option)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal enum class AnswerOptionState {
    NEUTRAL,
    SELECTED_CORRECT,
    SELECTED_WRONG,
    REVEALED_CORRECT,
}

internal fun answerOptionStateFor(option: Int, selected: Int?, correctAnswer: Int): AnswerOptionState = when {
    selected == null -> AnswerOptionState.NEUTRAL
    option == selected && option == correctAnswer -> AnswerOptionState.SELECTED_CORRECT
    option == selected -> AnswerOptionState.SELECTED_WRONG
    option == correctAnswer -> AnswerOptionState.REVEALED_CORRECT
    else -> AnswerOptionState.NEUTRAL
}

@Composable
private fun AnswerOptionCard(
    value: Int,
    state: AnswerOptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (state) {
        AnswerOptionState.SELECTED_CORRECT,
        AnswerOptionState.REVEALED_CORRECT -> successContainerColor()
        AnswerOptionState.SELECTED_WRONG -> MaterialTheme.colorScheme.errorContainer
        AnswerOptionState.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (state) {
        AnswerOptionState.SELECTED_CORRECT,
        AnswerOptionState.REVEALED_CORRECT -> onSuccessContainerColor()
        AnswerOptionState.SELECTED_WRONG -> MaterialTheme.colorScheme.onErrorContainer
        AnswerOptionState.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor: Color? = when (state) {
        AnswerOptionState.SELECTED_CORRECT,
        AnswerOptionState.REVEALED_CORRECT -> successColor()
        AnswerOptionState.SELECTED_WRONG -> MaterialTheme.colorScheme.error
        AnswerOptionState.NEUTRAL -> null
    }
    val accessibilityState = when (state) {
        AnswerOptionState.SELECTED_CORRECT -> stringResource(R.string.answer_option_selected_correct)
        AnswerOptionState.SELECTED_WRONG -> stringResource(R.string.answer_option_selected_incorrect)
        AnswerOptionState.REVEALED_CORRECT -> stringResource(R.string.answer_option_correct)
        AnswerOptionState.NEUTRAL -> null
    }
    val highlighted = state != AnswerOptionState.NEUTRAL
    val cardModifier = modifier
        .height(72.dp)
        .then(
            if (accessibilityState == null) {
                Modifier
            } else {
                Modifier.semantics {
                    stateDescription = accessibilityState
                    liveRegion = LiveRegionMode.Assertive
                }
            },
        )
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = cardModifier,
        // `disabledContainerColor`/`disabledContentColor` must be set explicitly too: once an
        // option is picked, every card in the grid (including the highlighted correct/wrong
        // ones) has `enabled = false`, and Card silently falls back to M3's default *dimmed*
        // disabled colors for any slot not given here — which would hide the green/red highlight
        // entirely behind the disabled-state dimming. Preserve highlighted colors, but visibly dim
        // neutral disabled cards so the 350 ms arming window never looks tappable while ignoring
        // input, and so unselected options recede during feedback.
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (highlighted) {
                containerColor
            } else {
                containerColor.copy(alpha = 0.55f)
            },
            disabledContentColor = if (highlighted) contentColor else contentColor.copy(alpha = 0.38f),
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
