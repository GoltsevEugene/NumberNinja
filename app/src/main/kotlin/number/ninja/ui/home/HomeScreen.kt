package number.ninja.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import number.ninja.R
import number.ninja.domain.QuizSubMode
import number.ninja.domain.TrainingMode
import number.ninja.domain.UserSettings
import number.ninja.ui.components.AdaptiveCenteredColumn
import number.ninja.ui.components.BigActionButton
import number.ninja.ui.labelRes
import org.koin.androidx.compose.koinViewModel

/**
 * Main Home screen (shown on every launch after first run). Compact settings summary,
 * a large Start button, and a top-right icon row (progress, then settings) so neither
 * icon crowds the Start button per spec §7's "compact settings summary + big Start button
 * + settings icon" layout.
 */
@Composable
fun HomeScreen(
    onStartClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProgressClick: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            // Unlike every other screen's TopAppBar (which consumes status-bar insets
            // internally), this is a plain Box — Scaffold does NOT inset a custom topBar for
            // you, so without this the icon row renders under the status bar clock/icons.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp),
            ) {
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = onProgressClick) {
                        Icon(
                            imageVector = Icons.Filled.Insights,
                            contentDescription = stringResource(R.string.home_progress),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.home_settings),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AdaptiveCenteredColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )

            settings?.let { current ->
                // Deliberately de-emphasized vs. the title/Start button (labelMedium, muted
                // color, one line per setting) per user feedback that the old single-line
                // comma-separated summary read as visually heavy secondary info. The whole
                // block is clickable — same destination as the gear icon — with a small
                // trailing hint making that discoverable instead of relying on the icon alone.
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 32.dp)
                        .clickable(onClick = onSettingsClick)
                        .semantics { role = Role.Button }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    current.summaryLines().forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_summary_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            BigActionButton(
                text = stringResource(R.string.home_start),
                onClick = onStartClick,
            )
        }
    }
}

/**
 * Middle segment used to show [Level] as-is only for [TrainingMode.FREE_PRACTICE] — a Quiz's
 * Progression/Shuffle sub-modes ignore the configured [UserSettings.level] entirely (see
 * `QuizViewModel`), so showing e.g. "Easy" there would misreport a setting the quiz never reads.
 * [QuizSubMode.FIXED_LEVEL] is the one quiz sub-mode that *does* pin a level, so it shows both
 * the sub-mode label and the level ("Selected level: Hard") rather than either alone.
 */
@Composable
private fun UserSettings.summaryModeDetailLabel(): String = when {
    mode == TrainingMode.FREE_PRACTICE -> stringResource(level.labelRes())
    quizSubMode == QuizSubMode.FIXED_LEVEL -> stringResource(
        R.string.home_summary_fixed_level_format,
        stringResource(quizSubMode.labelRes()),
        stringResource(level.labelRes()),
    )
    else -> stringResource(quizSubMode.labelRes())
}

/**
 * One line per setting (operations, level-or-quiz-sub-mode detail, mode) rather than the
 * previous single comma/dot-joined sentence — same three values, computed the same way,
 * just no longer collapsed into one run-on line. [R.string.home_summary_separator] is no
 * longer used here but stays defined in case a future screen wants the inline form.
 */
@Composable
private fun UserSettings.summaryLines(): List<String> {
    val operationLabels = operations.sortedBy { it.ordinal }.map { stringResource(it.labelRes()) }
    val operationsLabel = operationLabels.joinToString(", ")
    return listOf(operationsLabel, summaryModeDetailLabel(), stringResource(mode.labelRes()))
}
