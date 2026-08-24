package number.ninja.ui.practice

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import number.ninja.R
import number.ninja.domain.MathExample
import number.ninja.ui.components.AnswerOptionsGrid
import number.ninja.ui.components.ANSWER_OPTIONS_ARM_DELAY_MILLIS
import number.ninja.ui.components.BigActionButton
import number.ninja.ui.components.LifecycleAwareFeedbackAutoAdvance
import number.ninja.ui.components.adaptiveContentWidth
import number.ninja.ui.components.rememberThrottledClick
import number.ninja.ui.render
import org.koin.androidx.compose.koinViewModel

/**
 * Free practice screen (spec §6.1). Infinite examples, one at a time; a fact card every 4th
 * completed example instead of the next question. [onExit] backs out to Home — wired from
 * [number.ninja.ui.session.SessionScreen] the same way Settings wires its back arrow.
 *
 * Answering is 4-option multiple choice, not manual numeric entry: tapping an option *is* the
 * answer (calls [FreePracticeViewModel.submitAnswer] immediately, no Check button), the tapped
 * option highlights green/red in place, and then the screen auto-advances — after 1 second for a
 * correct answer, or the existing 2.3 seconds for a wrong answer so there is time to compare it
 * with the highlighted correct option. This auto-advance is deliberately scoped to `Feedback`
 * only: the `Fact` interstitial still requires a manual "Continue" tap, unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreePracticeScreen(
    onExit: () -> Unit,
    viewModel: FreePracticeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exitClick = rememberThrottledClick(onClick = onExit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.free_practice_title)) },
                navigationIcon = {
                    IconButton(onClick = exitClick) {
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
                        // Auto-advance after the answer highlight appears. Correct answers need
                        // only a quick acknowledgement; wrong ones remain longer for review.
                        // This branch only
                        // (re)composes once per distinct Feedback instance — the state moves on to
                        // Question/Fact right after, leaving the branch entirely — so this fires
                        // exactly once per answered question, same one-shot pattern the quiz
                        // screen's terminal-state LaunchedEffect already uses.
                        LifecycleAwareFeedbackAutoAdvance(
                            feedbackKey = state,
                            isCorrect = state.selectedAnswer == state.correctAnswer,
                            onAdvance = viewModel::onFeedbackDismissed,
                        )
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

@Composable
private fun QuestionContent(example: MathExample, options: List<Int>, onSelect: (Int) -> Unit) {
    var answersEnabled by remember(example) { mutableStateOf(false) }
    LaunchedEffect(example) {
        delay(ANSWER_OPTIONS_ARM_DELAY_MILLIS)
        answersEnabled = true
    }

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
            enabled = answersEnabled,
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
 * asking [FactKnowledgeButtons], then shows a one-line response ("Great job!" / "You can learn
 * it") below those now-disabled buttons plus the same "Continue" tap as before. The response and
 * Continue areas are measured from the start even while invisible, so revealing them cannot
 * recenter or move the fact text. [knewIt] is reset per distinct fact via the `remember(text)`
 * key, purely local UI state — nothing is recorded to stats, this is just encouragement, not a
 * graded attempt.
 */
@Composable
private fun FactContent(text: String, onContinue: () -> Unit) {
    var knewIt by remember(text) { mutableStateOf<Boolean?>(null) }
    Column(
        // When the complete constant-height card does not fit (landscape, split-screen, or large
        // font scale), verticalScroll constrains it to the viewport and makes every action
        // reachable. On taller screens it still measures at content height and remains centered.
        // Because feedback and Continue keep their reserved slots, selecting an answer does not
        // change the scroll range or move the fact text.
        modifier = Modifier
            .adaptiveContentWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.free_practice_fact_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        FactKnowledgeButtons(
            selected = knewIt,
            onKnewIt = { if (knewIt == null) knewIt = true },
            onDidNotKnow = { if (knewIt == null) knewIt = false },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Both alternatives stay composed so this slot's measured height is constant in all
            // states and locales. Hidden text is removed from accessibility semantics.
            FactFeedbackText(
                text = stringResource(R.string.fact_knew_it_response),
                visible = knewIt == true,
            )
            FactFeedbackText(
                text = stringResource(R.string.fact_did_not_know_response),
                visible = knewIt == false,
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            if (knewIt != null) {
                BigActionButton(
                    text = stringResource(R.string.free_practice_continue),
                    onClick = onContinue,
                )
            }
        }
    }
}

@Composable
private fun FactFeedbackText(text: String, visible: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
    )
}

@Composable
private fun FactKnowledgeButtons(selected: Boolean?, onKnewIt: () -> Unit, onDidNotKnow: () -> Unit) {
    val enabled = selected == null
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onDidNotKnow, enabled = enabled) {
            Text(stringResource(R.string.fact_did_not_know_button))
        }
        Button(onClick = onKnewIt, enabled = enabled) {
            Text(stringResource(R.string.fact_knew_it_button))
        }
    }
}
