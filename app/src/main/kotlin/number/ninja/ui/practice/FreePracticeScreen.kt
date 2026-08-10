package number.ninja.ui.practice

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import number.ninja.R
import number.ninja.domain.MathExample
import number.ninja.ui.components.AnswerOptionsGrid
import number.ninja.ui.components.BigActionButton
import number.ninja.ui.components.adaptiveContentWidth
import number.ninja.ui.render
import org.koin.androidx.compose.koinViewModel

/**
 * Free practice screen (spec §6.1). Infinite examples, one at a time; a fact card every 4th
 * completed example instead of the next question. [onExit] backs out to Home — wired from
 * [number.ninja.ui.session.SessionScreen] the same way Settings wires its back arrow.
 *
 * Answering is 4-option multiple choice, not manual numeric entry: tapping an option *is* the
 * answer (calls [FreePracticeViewModel.submitAnswer] immediately, no Check button), the tapped
 * option highlights green/red in place, and after a 3s pause
 * ([ANSWER_HIGHLIGHT_DURATION_MILLIS]) the screen auto-advances — see the `Feedback` branch's
 * `LaunchedEffect` below. This auto-advance is deliberately scoped to `Feedback` only: the `Fact`
 * interstitial still requires a manual "Continue" tap, unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePracticeScreen(
    onExit: () -> Unit,
    viewModel: FreePracticeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.free_practice_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.free_practice_exit),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            // Crossfade keeps question -> feedback -> fact transitions gentle, never jarring
            // (spec §6.2's no-negative-animations requirement applies here too, see task notes).
            // Crossfade's own Box sizes itself to whichever children are currently composed,
            // which briefly includes BOTH the outgoing and incoming content during the fade;
            // once the outgoing content is disposed at the end of the animation, that box shrinks
            // to just the incoming content's size, and — because it used to sit inside a
            // `contentAlignment = Center` Box — re-centering on that size change reads as the
            // content visibly jumping a moment after it settles in (reported bug: fact text
            // appears, then jumps down). Fix: pin Crossfade's own box to fillMaxSize so it never
            // changes size as children enter/exit, and let each branch center itself within that
            // fixed area instead.
            Crossfade(targetState = uiState, modifier = Modifier.fillMaxSize(), label = "free_practice_content") { state ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state) {
                    null -> Unit // first settings snapshot not loaded yet
                    is FreePracticeUiState.Question -> QuestionContent(
                        example = state.example,
                        options = state.options,
                        onSelect = { answer -> viewModel.submitAnswer(state.example, answer) },
                    )
                    is FreePracticeUiState.Feedback -> {
                        // Auto-advance 3s after the answer highlight appears. This branch only
                        // (re)composes once per distinct Feedback instance — the state moves on to
                        // Question/Fact right after, leaving the branch entirely — so this fires
                        // exactly once per answered question, same one-shot pattern the quiz
                        // screen's terminal-state LaunchedEffect already uses.
                        LaunchedEffect(state) {
                            delay(ANSWER_HIGHLIGHT_DURATION_MILLIS)
                            viewModel.onFeedbackDismissed()
                        }
                        FeedbackContent(state = state)
                    }
                    is FreePracticeUiState.Fact -> FactContent(
                        text = state.text,
                        onContinue = viewModel::onFactDismissed,
                    )
                }
                }
            }
        }
    }
}

private const val ANSWER_HIGHLIGHT_DURATION_MILLIS = 2300L

@Composable
private fun QuestionContent(example: MathExample, options: List<Int>, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.adaptiveContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = example.render(), style = MaterialTheme.typography.displayMedium)
        AnswerOptionsGrid(
            options = options,
            selected = null,
            correctAnswer = example.answer(),
            onSelect = onSelect,
        )
    }
}

@Composable
private fun FeedbackContent(state: FreePracticeUiState.Feedback) {
    // No feedback text here by design (user feedback: it pushed the grid down and read as a
    // layout jump) — layout is IDENTICAL to QuestionContent (same Text + AnswerOptionsGrid, same
    // spacing), and the answer's correctness is communicated purely via AnswerOptionsGrid's
    // green/red highlight (selected != null branch), not via any text appearing/disappearing.
    Column(
        modifier = Modifier.adaptiveContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(text = state.example.render(), style = MaterialTheme.typography.displayMedium)
        AnswerOptionsGrid(
            options = state.options,
            selected = state.selectedAnswer,
            correctAnswer = state.correctAnswer,
            onSelect = {}, // non-interactive once answered; grid disables taps itself
        )
    }
}

/**
 * Fact card with a "did you know this already?" follow-up (spec follow-up request): starts by
 * asking [FactKnowledgeButtons], then swaps to a one-line response ("Great job!" / "You can learn
 * it") plus the same "Continue" tap as before. [knewIt] is reset per distinct fact via the
 * `remember(text)` key, purely local UI state — nothing is recorded to [StatsRepository], this is
 * just encouragement, not a graded attempt.
 */
@Composable
private fun FactContent(text: String, onContinue: () -> Unit) {
    var knewIt by remember(text) { mutableStateOf<Boolean?>(null) }
    Column(
        modifier = Modifier.adaptiveContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.free_practice_fact_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        when (knewIt) {
            null -> FactKnowledgeButtons(onKnewIt = { knewIt = true }, onDidNotKnow = { knewIt = false })
            true -> {
                Text(
                    text = stringResource(R.string.fact_knew_it_response),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BigActionButton(text = stringResource(R.string.free_practice_continue), onClick = onContinue)
            }
            false -> {
                Text(
                    text = stringResource(R.string.fact_did_not_know_response),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BigActionButton(text = stringResource(R.string.free_practice_continue), onClick = onContinue)
            }
        }
    }
}

@Composable
private fun FactKnowledgeButtons(onKnewIt: () -> Unit, onDidNotKnow: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDidNotKnow) {
            Text(stringResource(R.string.fact_did_not_know_button))
        }
        Button(onClick = onKnewIt) {
            Text(stringResource(R.string.fact_knew_it_button))
        }
    }
}
