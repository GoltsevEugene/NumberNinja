package number.ninja.ui.quiz

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
 * Quiz screen (spec §6.2): a fixed-length, pre-generated run of examples, one at a time, with no
 * ability to revisit or change a submitted answer, no timer anywhere in this UI, and friendly
 * non-punitive feedback throughout — same visual language as
 * [number.ninja.ui.practice.FreePracticeScreen]. [onExit] backs out to Home (mid-quiz exit,
 * wired the same way free practice's back arrow is). [onFinished] fires exactly once, when
 * [QuizUiState.Finished] is reached, to navigate to the results screen.
 *
 * Answering is 4-option multiple choice, not manual numeric entry: tapping an option *is* the
 * answer (no Check button), highlights green/red in place, and after a 3s pause
 * ([ANSWER_HIGHLIGHT_DURATION_MILLIS]) auto-advances — including straight to [onFinished] after
 * the last example's highlight window, replacing the old manual "See results" tap. Same pattern
 * as [number.ninja.ui.practice.FreePracticeScreen]; the `Fact` interstitial is unaffected and
 * still requires a manual "Continue" tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onExit: () -> Unit,
    onFinished: () -> Unit,
    viewModel: QuizViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.quiz_title)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.session_back),
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
            // Crossfade keeps question -> feedback -> fact transitions gentle, matching free
            // practice (spec §6.2's no-negative-animations requirement). Crossfade's own Box
            // sizes itself to whichever children are currently composed, which briefly includes
            // BOTH the outgoing and incoming content during the fade; once the outgoing content
            // is disposed at the end of the animation, that box shrinks to just the incoming
            // content's size, and — because it used to sit inside a `contentAlignment = Center`
            // Box — re-centering on that size change reads as the content visibly jumping a
            // moment after it settles in (reported bug: fact text appears, then jumps down).
            // Fix: pin Crossfade's own box to fillMaxSize so it never changes size as children
            // enter/exit, and let each branch center itself within that fixed area instead.
            Crossfade(targetState = uiState, modifier = Modifier.fillMaxSize(), label = "quiz_content") { state ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state) {
                    null -> Unit // examples not generated yet
                    is QuizUiState.Question -> QuestionContent(
                        example = state.example,
                        options = state.options,
                        position = state.position,
                        total = state.total,
                        onSelect = { answer -> viewModel.submitAnswer(state.example, answer) },
                    )
                    is QuizUiState.Feedback -> {
                        // Auto-advance 3s after the answer highlight appears — on the last
                        // example this calls onFeedbackDismissed() straight into Finished, with
                        // no intermediate "See results" tap. This branch only (re)composes once
                        // per distinct Feedback instance (state always moves on right after), so
                        // the effect fires exactly once per answered question.
                        LaunchedEffect(state) {
                            delay(ANSWER_HIGHLIGHT_DURATION_MILLIS)
                            viewModel.onFeedbackDismissed()
                        }
                        FeedbackContent(state = state)
                    }
                    is QuizUiState.Fact -> FactContent(
                        text = state.text,
                        onContinue = viewModel::onFactDismissed,
                    )
                    is QuizUiState.Finished -> {
                        // Fires once: this branch only composes once, since Finished is terminal
                        // and the state never reverts, so onFinished can't double-navigate even
                        // if the preceding auto-advance raced a rapid recomposition (submitAnswer's
                        // and onFeedbackDismissed's state guards already prevent that).
                        LaunchedEffect(Unit) { onFinished() }
                    }
                }
                }
            }
        }
    }
}

private const val ANSWER_HIGHLIGHT_DURATION_MILLIS = 2300L

@Composable
private fun QuestionContent(
    example: MathExample,
    options: List<Int>,
    position: Int,
    total: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.adaptiveContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.quiz_progress, position + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun FeedbackContent(state: QuizUiState.Feedback) {
    // No feedback text here by design (user feedback: it pushed the grid down and read as a
    // layout jump) — layout is IDENTICAL to QuestionContent (same progress/render Text +
    // AnswerOptionsGrid, same spacing), and correctness is communicated purely via
    // AnswerOptionsGrid's green/red highlight (selected != null branch), not via any text
    // appearing/disappearing.
    Column(
        modifier = Modifier.adaptiveContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.quiz_progress, state.position + 1, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
 * `remember(text)` key, purely local UI state — nothing is recorded to stats, this is just
 * encouragement, not a graded attempt. Same behavior as
 * [number.ninja.ui.practice.FreePracticeScreen]'s `FactContent`.
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
            text = stringResource(R.string.quiz_fact_label),
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
                BigActionButton(text = stringResource(R.string.quiz_continue), onClick = onContinue)
            }
            false -> {
                Text(
                    text = stringResource(R.string.fact_did_not_know_response),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BigActionButton(text = stringResource(R.string.quiz_continue), onClick = onContinue)
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
