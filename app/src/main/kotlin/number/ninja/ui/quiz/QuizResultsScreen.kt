package number.ninja.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import number.ninja.R
import number.ninja.ui.components.AdaptiveCenteredColumn
import number.ninja.ui.components.BigActionButton
import number.ninja.ui.render
import org.koin.compose.koinInject

/**
 * Quiz results/review screen (spec §6.2): lists every example from the just-finished quiz, in
 * order, each with a correct/incorrect indicator and — for wrong ones — the correct answer
 * alongside. Reads [QuizResultsHolder] directly via [koinInject] rather than through a dedicated
 * ViewModel, same precedent [number.ninja.ui.session.SessionScreen] sets for router-only /
 * no-business-logic screens: this one only renders what [QuizViewModel] already computed.
 *
 * [onDone] is the only way out — reachable back-stack-wise only from Home, since
 * `Route.Session` (and the finished [QuizViewModel]) was removed from the back stack when this
 * screen was navigated to (see `ui/nav/NumberNinjaNavHost.kt`), so back-press here already lands
 * on Home without any extra handling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultsScreen(onDone: () -> Unit) {
    val resultsHolder: QuizResultsHolder = koinInject()
    val attempts = resultsHolder.results

    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.quiz_results_title)) }) },
    ) { innerPadding ->
        if (attempts.isEmpty()) {
            // Defensive: only reachable if this destination is restored after process death
            // with no in-memory results (the holder is not persisted). Nothing useful to show.
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(text = stringResource(R.string.quiz_results_empty))
                    BigActionButton(text = stringResource(R.string.quiz_results_done), onClick = onDone)
                }
            }
            return@Scaffold
        }

        val correctCount = attempts.count { it.correct }
        // Kept single-column (not gridded) per spec §6.2/§12 step 9 — the review list is a
        // sequential log of the quiz, and a multi-column grid would break that reading order for
        // no space-efficiency win on a list of at most 15 short rows. Still width-capped and
        // centered on medium/expanded, like every other screen, so rows don't stretch edge-to-edge.
        AdaptiveCenteredColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.quiz_results_score, correctCount, attempts.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(attempts) { attempt -> ResultRow(attempt) }
            }
            BigActionButton(
                text = stringResource(R.string.quiz_results_done),
                onClick = onDone,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun ResultRow(attempt: QuizAttempt) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = attempt.example.render(), style = MaterialTheme.typography.titleMedium)
            ResultAnswerRow(attempt)
            if (!attempt.correct) {
                Text(
                    text = stringResource(R.string.quiz_results_correct_answer, attempt.correctAnswer),
                    style = MaterialTheme.typography.bodyMedium,
                    // Informational, not punitive — same non-error color choice as the quiz
                    // screen's wrong-answer feedback.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultAnswerRow(attempt: QuizAttempt) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attempt.correct) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.quiz_results_correct),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.quiz_results_incorrect),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.quiz_results_your_answer, attempt.givenAnswer),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
