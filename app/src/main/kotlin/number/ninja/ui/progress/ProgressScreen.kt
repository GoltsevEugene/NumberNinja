package number.ninja.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import number.ninja.R
import number.ninja.domain.LevelStats
import number.ninja.domain.Operation
import number.ninja.domain.OperationStats
import number.ninja.ui.components.AdaptiveCenteredColumn
import number.ninja.ui.components.rememberThrottledClick
import number.ninja.ui.labelRes
import org.koin.androidx.compose.koinViewModel

/**
 * "My Progress" screen (spec §9) — a single screen meant for both the child and parents, so
 * language stays simple/friendly and there are no dense tables. Reads live from Room via
 * [ProgressViewModel]'s combined [ProgressUiState], so it updates automatically as new attempts
 * are recorded elsewhere in the app (free practice, quiz).
 *
 * Uses a plain scrolling [Column] of [Card]s, not a [androidx.compose.foundation.lazy.LazyColumn]
 * — nesting a lazy list inside a `verticalScroll` Column crashes at runtime with an infinite
 * height measurement, and this screen's content (4 tiles + a handful of weak spots) never needs
 * lazy layout. Same precedent as `SettingsScreen`'s scrolling Column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backClick = rememberThrottledClick(onClick = onBack)
    var selectedOperation by remember { mutableStateOf<Operation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.progress_title)) },
                navigationIcon = {
                    IconButton(onClick = backClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.progress_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        uiState?.let { current ->
            AdaptiveCenteredColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                innerModifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OperationStatsGrid(
                    stats = current.operationStats,
                    onTileClick = { operation -> selectedOperation = operation },
                )

                if (current.weakSpotGroups.isNotEmpty()) {
                    WeakSpotsSection(current.weakSpotGroups)
                } else {
                    Text(
                        text = stringResource(R.string.progress_no_weak_spots),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            selectedOperation?.let { operation ->
                current.operationStats.find { it.operation == operation }?.let { operationStats ->
                    OperationDetailsDialog(
                        operationStats = operationStats,
                        levelStats = current.levelStatsByOperation[operation].orEmpty(),
                        onDismiss = { selectedOperation = null },
                    )
                }
            }
        }
    }
}

/**
 * 2x2 grid of per-operation tiles, in [Operation] declaration order (addition..division). Each
 * tile is tappable — [onTileClick] opens the per-level breakdown dialog for that operation, so
 * the detailed per-level percentages/counts (spec follow-up: percentages alone aren't meaningful
 * to a young child) stay off the main screen and don't crowd "Worth a bit more practice".
 */
@Composable
private fun OperationStatsGrid(stats: List<OperationStats>, onTileClick: (Operation) -> Unit) {
    val byOperation = stats.associateBy { it.operation }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Operation.entries.chunked(2).forEach { rowOperations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowOperations.forEach { operation ->
                    byOperation[operation]?.let { operationStats ->
                        OperationStatTile(
                            stats = operationStats,
                            onClick = { onTileClick(operation) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationStatTile(stats: OperationStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(stats.operation.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                )
                // Branch on totalAttempts, not accuracyPercent — accuracyPercent is 0 for "no
                // attempts yet" too, and showing "0%" there reads as a failing grade rather than
                // "hasn't tried this yet".
                if (stats.totalAttempts == 0) {
                    Text(
                        text = stringResource(R.string.progress_no_practice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.progress_accuracy_percent, stats.accuracyPercent),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Muted "reveals more" cue — the tile opens a dialog (not a navigation
            // destination), so ExpandMore reads more accurately than a rightward chevron.
            // Bottom-end keeps it clear of the accuracy percent, which is the primary content.
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

@Composable
private fun WeakSpotsSection(weakSpotGroups: List<WeakSpotGroup>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.progress_weak_spots_label),
            style = MaterialTheme.typography.titleMedium,
        )
        weakSpotGroups.forEach { group -> WeakSpotGroupCard(group) }
    }
}

/**
 * Renders one [WeakSpotGroup] — exactly one card per operation (see
 * [number.ninja.ui.progress.groupWeakSpots] for why this replaced one-card-per-[WeakSpot]).
 * Picks the most specific phrasing available: a single level/range, a comma-joined list of
 * several, or (only when neither is present — the whole-operation catch-all with no bucket
 * detail) a plain no-detail phrasing.
 */
@Composable
private fun WeakSpotGroupCard(group: WeakSpotGroup) {
    val operationLabel = stringResource(group.operation.labelRes())
    val text = when {
        group.levels.size == 1 -> stringResource(
            R.string.progress_weak_spot_item_level,
            operationLabel,
            stringResource(group.levels.single().labelRes()),
        )
        group.levels.size > 1 -> {
            val levelLabels = group.levels.map { "«${stringResource(it.labelRes())}»" }
            stringResource(R.string.progress_weak_spot_item_levels, operationLabel, levelLabels.joinToString(", "))
        }
        group.rangeLabels.isNotEmpty() -> stringResource(
            R.string.progress_weak_spot_item_range,
            operationLabel,
            group.rangeLabels.joinToString(", ") { "${group.operation.rangeSymbol()}$it" },
        )
        else -> stringResource(R.string.progress_weak_spot_item_general, operationLabel)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** Operator glyph for range-based weak-spot phrasing, e.g. "Multiplication ×7-9". */
private fun Operation.rangeSymbol(): String = when (this) {
    Operation.MULTIPLICATION -> "×"
    Operation.DIVISION -> "÷"
    else -> ""
}

/**
 * Per-level accuracy breakdown for one operation, shown as a popup on tile tap rather than on
 * the main screen (per user feedback: raw percentages alone aren't meaningful to a young child,
 * but showing every level's counts inline for all 4 operations would crowd out "Worth a bit more
 * practice"). Levels with zero attempts are skipped — a meaningless "0%, correct 0 of 0" line for
 * a level nobody has tried yet reads the same way [OperationStatTile] avoids it on the main tile.
 */
@Composable
private fun OperationDetailsDialog(
    operationStats: OperationStats,
    levelStats: List<LevelStats>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(operationStats.operation.labelRes())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (operationStats.totalAttempts == 0) {
                    Text(
                        text = stringResource(R.string.progress_no_practice),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.progress_stats_line,
                            stringResource(operationStats.operation.labelRes()),
                            operationStats.accuracyPercent,
                            operationStats.correctAttempts,
                            operationStats.totalAttempts,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    levelStats.filter { it.totalAttempts > 0 }.forEach { level ->
                        Text(
                            text = stringResource(
                                R.string.progress_stats_line,
                                stringResource(level.level.labelRes()),
                                level.accuracyPercent,
                                level.correctAttempts,
                                level.totalAttempts,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.progress_details_dismiss))
            }
        },
    )
}
