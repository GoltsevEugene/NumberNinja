package number.ninja.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import number.ninja.data.db.StatsRepository
import number.ninja.domain.Level
import number.ninja.domain.LevelStats
import number.ninja.domain.Operation
import number.ninja.domain.OperationStats
import number.ninja.domain.WeakSpot

/**
 * Snapshot the "My Progress" screen renders. Null until all combined flows have emitted once.
 * [levelStatsByOperation] backs the per-level breakdown dialog shown when a tile is tapped —
 * fetched eagerly for all operations up front (the data volume is tiny — 4 operations × 4
 * levels) rather than lazily re-collecting a flow only when a tile is tapped. [weakSpotGroups]
 * is [WeakSpot] pre-collapsed to one entry per [Operation] — see [groupWeakSpots].
 */
data class ProgressUiState(
    val operationStats: List<OperationStats>,
    val weakSpotGroups: List<WeakSpotGroup>,
    val levelStatsByOperation: Map<Operation, List<LevelStats>>,
)

/**
 * One weak-spot card's worth of data for a single [Operation] — the presentation-side collapse
 * of [StatsRepository.observeWeakSpots]'s flat, possibly-redundant [WeakSpot] list (see
 * [groupWeakSpots]). Addition/subtraction populate [levels]; multiplication/division populate
 * [rangeLabels] (bare, e.g. "7-9" — the ×/÷ symbol is applied at display time since that's
 * operation-dependent, not data). Both empty means only the whole-operation catch-all
 * [WeakSpot] (no level/range) applies, and the UI falls back to a no-detail phrasing.
 */
data class WeakSpotGroup(
    val operation: Operation,
    val levels: List<Level> = emptyList(),
    val rangeLabels: List<String> = emptyList(),
)

/**
 * Collapses a flat [WeakSpot] list to one [WeakSpotGroup] per [Operation] that has any weak
 * spot at all. [StatsRepository.observeWeakSpots] can emit, for one operation, several
 * level/range-bucketed entries AND a whole-operation catch-all (added to fix a coexistence bug
 * where a tile's overall accuracy was low but no individual bucket had enough samples to flag on
 * its own) — showing all of those as separate cards reads as confusing duplication (e.g.
 * "Addition (Easy level)" and "Addition" both appearing). Here, if any bucketed (level/range)
 * entry exists for an operation, the catch-all is dropped in favor of the specific ones; the
 * catch-all only surfaces (as an empty-lists group) when it's the *only* entry for that
 * operation. Bucket order within a group is preserved from [weakSpots]'s existing order
 * (ascending range bucket / ascending [Level] declaration order), so no extra sort is needed
 * beyond deduping and re-ordering the groups themselves into [Operation] declaration order.
 */
private fun groupWeakSpots(weakSpots: List<WeakSpot>): List<WeakSpotGroup> =
    weakSpots.groupBy { it.operation }
        .map { (operation, spots) ->
            val specific = spots.filter { it.level != null || it.rangeLabel != null }
            WeakSpotGroup(
                operation = operation,
                levels = specific.mapNotNull { it.level }.distinct(),
                rangeLabels = specific.mapNotNull { it.rangeLabel }.distinct(),
            )
        }
        .sortedBy { Operation.entries.indexOf(it.operation) }

/**
 * Backs the Progress screen (spec §9). Combines [StatsRepository.observeOperationStats],
 * [StatsRepository.observeWeakSpots], and one [StatsRepository.observeLevelStats] flow per
 * [Operation] into a single nullable [ProgressUiState] — same null-until-first-emission gating
 * [number.ninja.ui.home.HomeViewModel] and `NumberNinjaNavHost` use for
 * `SettingsRepository.settings` — so the UI never has to render a default/empty state (e.g. "no
 * weak spots — nice work!") before Room's first real emission, which would misreport an empty
 * *unknown* state as an empty *confirmed* one.
 */
class ProgressViewModel(statsRepository: StatsRepository) : ViewModel() {

    val uiState: StateFlow<ProgressUiState?> = combine(
        statsRepository.observeOperationStats(),
        statsRepository.observeWeakSpots(),
        levelStatsByOperationFlow(statsRepository),
    ) { operationStats, weakSpots, levelStatsByOperation ->
        ProgressUiState(operationStats, groupWeakSpots(weakSpots), levelStatsByOperation)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private fun levelStatsByOperationFlow(
    statsRepository: StatsRepository,
): Flow<Map<Operation, List<LevelStats>>> =
    combine(Operation.entries.map { op -> statsRepository.observeLevelStats(op).map { op to it } }) { pairs ->
        pairs.toMap()
    }
