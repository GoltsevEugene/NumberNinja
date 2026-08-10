---
name: project_phaseC_settings_home_progress
description: Phase C of the 4-phase fix batch — Settings quiz-style selector + Level gating, Home summary, Progress per-level dialog
metadata:
  type: project
---

Phase C landed on 2026-08-08, wiring UI to the domain primitives phase A added
([[project_phaseA_domain_fixes]]) and following on from phase B
([[project_phaseB_mcq_answering]]).

**What changed:**
- `ui/settings/SettingsScreen.kt`: `QuizSubModeSegmentedControl` (3-option
  `SingleChoiceSegmentedButtonRow`) replaced with `QuizSubModeOptions` — a vertical
  `Column` of `Row { RadioButton + Text }` rows using `Modifier.selectable(role =
  Role.RadioButton)`. Reason: Russian's `QuizSubMode` labels ("Прогрессия сложности",
  "Перемешивание", "Выбранный уровень") don't fit 3-wide in a segmented row without
  wrapping/clipping — confirmed via on-device screenshot in Russian, the vertical list
  gives each label a full-width line and never wraps. Apply this "vertical
  RadioButton+Text list" pattern for any future 3+-option single-select control with
  long/localized labels — reserve `SingleChoiceSegmentedButtonRow` for 2-3 short options
  only (Mode, Language already fit).
- **Level selector gating**: `SettingsScreen` now computes `levelApplicable = mode ==
  FREE_PRACTICE || (mode == QUIZ && quizSubMode == FIXED_LEVEL)` inline (no ViewModel
  change needed — `current: UserSettings` already has both fields). When false, the
  `LevelChips` row is replaced entirely by an explanatory `Text`
  (`settings_level_not_applicable`), not shown disabled — per explicit task guidance,
  hiding + explaining reads more clearly to a child than a dimmed control. Don't
  reintroduce a disabled `LevelChips` for this case.
- `ui/home/HomeScreen.kt`: `summaryText()`'s middle segment now branches via a new
  `summaryModeDetailLabel()` extension: `FREE_PRACTICE` → level label (unchanged);
  `QUIZ` + `FIXED_LEVEL` → `home_summary_fixed_level_format` ("%1$s: %2$s", i.e.
  "Selected level: Hard"); `QUIZ` + other sub-modes → just the sub-mode label
  (Progression/Shuffle). Reason: Quiz ignores `UserSettings.level` entirely except in
  `FIXED_LEVEL` (see [[architecture_ui_layer]]'s "Quiz ignores `UserSettings.level`"
  note), so showing the raw level for Progression/Shuffle previously misreported a
  setting the quiz never read.
- `ui/progress/ProgressViewModel.kt`: `ProgressUiState` gained
  `levelStatsByOperation: Map<Operation, List<LevelStats>>`, computed eagerly (not
  lazily on tap) via a private `levelStatsByOperationFlow` that `combine`s one
  `StatsRepository.observeLevelStats(op)` flow per `Operation` into a
  `Map<Operation, List<LevelStats>>`, then 3-way `combine`d with the existing
  operation-stats/weak-spots flows. Data volume is tiny (4 ops × 4 levels) so eager
  beat lazy-on-tap — no extra per-tap flow collection/state needed.
- `ui/progress/ProgressScreen.kt`: `OperationStatTile` is now `Card(onClick = ...)`
  (was a plain non-clickable `Card`) — tapping opens `OperationDetailsDialog`, a plain
  `AlertDialog` (title = operation name, body = overall line + one line per level with
  attempts > 0, confirm/dismiss button = "Close"). Both the overall line and each
  level line reuse one template string, `progress_stats_line` ("%1$s — %2$d%%, correct
  %3$d of %4$d") — %1$s is either the operation label (overall line) or a level label
  (per-level lines). Levels with zero attempts are filtered out (not grayed) — task
  gave "skip or gray" as options, skip was simpler and matches the tile's existing
  zero-attempts-hides-percent precedent. If the *operation* itself has zero attempts,
  the dialog shows `progress_no_practice` instead of a "0%, correct 0 of 0" line,
  mirroring `OperationStatTile`'s own zero-attempts branch.
- New string keys added to all 3 `strings.xml` (parity-checked, diff clean):
  `settings_level_not_applicable`, `home_summary_fixed_level_format`,
  `progress_stats_line`, `progress_details_dismiss`.

**Why:** User feedback (translated from Ukrainian) wanted raw percentages accompanied
by absolute counts ("75%, correct 3 of 4") broken out per level, without cluttering the
main Progress screen's "Worth practicing more" section — hence tap-to-open-dialog
rather than always-expanded per-level rows.

**How to apply:** If a future phase touches `QuizSubMode`/`Level`/`TrainingMode`
gating logic again, check this note before re-deriving the applicability rule — it's
`mode == FREE_PRACTICE || (mode == QUIZ && quizSubMode == FIXED_LEVEL)`, computed
inline in the composable, not exposed as a `SettingsViewModel` property (no ViewModel
change was needed for gating since `UserSettings` already carries both fields read
together).
