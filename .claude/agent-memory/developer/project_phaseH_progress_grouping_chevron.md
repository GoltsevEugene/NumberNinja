---
name: project_phaseH_progress_grouping_chevron
description: Phase H (final) of the fix batch — Progress screen tile chevron + one-card-per-operation weak-spot grouping
metadata:
  type: project
---

Phase H landed on 2026-08-09, touching only `ui/progress/ProgressViewModel.kt` and
`ui/progress/ProgressScreen.kt` (no domain/data changes — `WeakSpot`'s shape from
[[project_phaseA_domain_fixes]] and [[project_phaseC_settings_home_progress]]'s
`OperationDetailsDialog`/`observeLevelStats` wiring were left untouched).

**5.1 — tappable-tile cue**: `OperationStatTile` wraps its existing `Column` in a `Box`
and adds `Icons.Default.ExpandMore` (not a rightward chevron) pinned
`Alignment.BottomEnd`, tinted `onSurfaceVariant`. Chose `ExpandMore` over
`KeyboardArrowRight` because the tap opens an in-place `AlertDialog`, not a navigation
push — "reveals more" reads more accurately than "navigates". Bottom-end keeps it clear
of the accuracy-percent `Text`, which stays the primary visual weight of the tile.

**5.2 — one card per operation, decided in the UI layer (`ProgressViewModel.kt`), not
the repository**: added `WeakSpotGroup(operation, levels: List<Level>, rangeLabels:
List<String>)` and a pure `groupWeakSpots(weakSpots: List<WeakSpot>)` function, both in
`ProgressViewModel.kt`. `StatsRepository.observeWeakSpots()` and `WeakSpot` itself are
byte-for-byte unchanged — grouping is a presentation concern collapsing an
intentionally-redundant domain list (bucketed entries + a whole-operation catch-all,
see [[project_phaseA_domain_fixes]]) down to one entry per operation. Rule: if any
level/range-bucketed `WeakSpot` exists for an operation, the whole-operation catch-all
for that operation is dropped; the catch-all only surfaces (as an empty-lists
`WeakSpotGroup`, rendered with a no-detail phrasing) when it is the *only* entry. Group
ordering: groupBy→map→sortedBy(Operation.entries.indexOf) — no separate sort needed for
the levels/ranges *within* a group since `observeWeakSpots()` already emits them in
ascending bucket/`Level`-declaration order and `.distinct()` preserves encounter order.

`ProgressUiState.weakSpots: List<WeakSpot>` was replaced with
`weakSpotGroups: List<WeakSpotGroup>` (not additive) — `ProgressScreen` has no other
caller of the raw list.

**New string keys** (all 3 locales, parity-confirmed via key-diff): removed the old
3-arg `progress_weak_spot_item` (symbol+rangeLabel, no parens) and replaced with four
keys, all parenthetical-style for visual consistency with the existing
`progress_weak_spot_item_level`:
- `progress_weak_spot_item_general` — operation only, no parenthetical (whole-op
  catch-all with zero specific detail).
- `progress_weak_spot_item_level` — unchanged, singular, one level.
- `progress_weak_spot_item_levels` — new, plural noun agreement needed for
  ru/uk ("уровень"→"уровни", "рівень"→"рівні"); %2$s is a pre-joined
  `", "`-separated list of localized level labels built in `WeakSpotGroupCard`.
- `progress_weak_spot_item_range` — replaces the old item; %2$s is a pre-joined
  `", "`-separated list of `"${symbol}${rangeLabel}"` strings (e.g. "×7-9, ×10"). No
  singular/plural split needed here — ranges carry no grammatical-number noun, just a
  symbol+label pair, so one key covers 1..N ranges.

**Gotcha hit and fixed**: `group.levels.joinToString(", ") { stringResource(it.labelRes()) }`
fails to compile — "`@Composable` invocations can only happen from the context of a
`@Composable` function" — because the lambda argument to `joinToString` isn't itself
`@Composable`/inline-transparent to the compose compiler. Fix: `val levelLabels =
group.levels.map { stringResource(it.labelRes()) }` first (a plain `@Composable`
function body loop is fine), then `levelLabels.joinToString(", ")` outside any lambda.
The range-label join was fine as-is since its lambda only does string interpolation, no
`stringResource` call inside it.

**Why:** User-reported bug (screenshot) showed redundant per-operation cards — e.g.
separate "Сложение (уровень «Лёгкий»)" and "Сложение" cards — caused by
[[project_phaseA_domain_fixes]]'s whole-operation catch-all coexisting with
[[project_phaseC_settings_home_progress]]'s per-level/range cards without any
dedup/grouping step.

**How to apply:** If a future phase touches weak-spot display again, `WeakSpotGroup` +
`groupWeakSpots()` in `ProgressViewModel.kt` is the presentation-side collapse point —
don't reach for a domain/repository change for grouping/display-only requirements; the
domain layer's `WeakSpot` list is deliberately flat/redundant by design (it also backs
other potential consumers), and `ProgressViewModel` is where redundancy gets resolved
before it reaches Compose. Verified on-device in Russian: "Сложение (уровни «Лёгкий,
Средний») — стоит повторить!" is one card, matching the dialog's per-level breakdown
(Лёгкий 50%/2 of 4, Средний 25%/1 of 4).
