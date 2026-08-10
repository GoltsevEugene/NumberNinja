---
name: project_phaseE_settings_firstrun_reset
description: Phase E (first fix batch, phase 1) — first run reuses Settings content, Mode selector deviation, quiz default, language checkmark fix, reset-to-defaults
metadata:
  type: project
---

Landed 2026-08-09, following on from [[project_phaseC_settings_home_progress]] and
[[project_phaseD_locale_insets_icon]]. Fixed 5 items from translated Ukrainian user
feedback: first-run screen was a curated subset of Settings (no quiz-style picker),
Mode segmented control overflowed with long Russian labels, Quiz wasn't the default
mode, the "level is automatic" text read awkwardly, and there was no reset-to-defaults.

**What changed:**
- `ui/settings/SettingsScreen.kt` now exports a public `SettingsContent(current:
  UserSettings, viewModel: SettingsViewModel, modifier, showResetButton = true, header
  = null, footer = null)` — the extracted body (Operations/Level/Mode/QuizSubMode+Length
  /Language + optional reset button+dialog) that both `SettingsScreen` (Scaffold +
  TopAppBar, no header/footer, `showResetButton = true`) and `ui/firstrun/
  QuickSetupScreen.kt` (plain Scaffold, `header` = title text, `footer` = "Начать!"
  `BigActionButton`, `showResetButton = false`) call. All the individual section
  composables (`OperationToggles`, `LevelChips`, `ModeOptions`, `QuizSubModeOptions`,
  `LanguageSegmentedControl`, `QuizLengthSlider`) stayed `private` to this one file —
  `QuickSetupScreen` only ever calls the public `SettingsContent`, same "one file owns
  the section composables" shape as before this phase, just with one more caller.
- **`QuickSetupViewModel` and its batch-then-submit pattern are gone entirely.**
  `QuickSetupScreen` now takes `viewModel: SettingsViewModel = koinViewModel()` directly
  — first run is just Settings live-editing per-interaction, same as the real Settings
  screen, plus one first-run-only action. Removed from `di/AppModule.kt`'s `uiModule`
  (`SettingsViewModel` was already registered there, no new registration needed — Koin's
  `viewModel {}` DSL naturally gives `QuickSetupScreen`'s composable<Route.QuickSetup>
  and `SettingsScreen`'s composable<Route.Settings> their own separate instances, same
  as any other per-nav-entry-scoped ViewModel here). If a future phase needs
  first-run-only UI state again, don't resurrect a whole second ViewModel for it — add
  it to `SettingsViewModel` behind a first-run-only method, following
  `markFirstRunComplete`'s shape.
- **`SettingsViewModel.markFirstRunComplete(onDone: () -> Unit)` awaits the DataStore
  write before calling `onDone`** — it is NOT a fire-and-forget `update {}` like every
  other setter in this ViewModel. Caught in review before shipping: `onDone` (wired in
  `NumberNinjaNavHost`) pops the `Route.QuickSetup` back-stack entry with `inclusive =
  true`, which clears this ViewModel's `ViewModelStore` and cancels `viewModelScope` —
  if `onDone()` ran before the write landed (as a plain `update { ... }; onDone()` two-
  liner at the call site would allow), the write could be cancelled mid-flight and the
  user would see first run again next launch. Same class of hazard as `QuizViewModel`
  being cleared the instant `Route.Session` pops (see [[architecture_ui_layer]]'s Quiz
  section) — anywhere a ViewModel's own back-stack entry gets popped as a direct result
  of one of its own methods, that method must await its write before triggering the pop,
  not fire-and-forget then let the caller navigate.
- **`SettingsViewModel.resetToDefaults()` preserves `hasCompletedFirstRun` AND
  `language`**, resetting every other field to `UserSettings()`'s default:
  `UserSettings(hasCompletedFirstRun = current.hasCompletedFirstRun, language =
  current.language)`. `hasCompletedFirstRun` is preserved so a reset can't send an
  existing user back through first run. `language` is preserved (not reset to null/
  "system") because [[project_phaseD_locale_insets_icon]]'s rule is "never assert an
  empty locale list from app code" — resetting the persisted value to null here without
  also touching `AppCompatDelegate` would leave Settings claiming "follow system" while
  the app keeps rendering whatever language was last actually applied, a real and
  confusing divergence. If a future spec explicitly wants "reset also clears the
  language override," that needs to go through the same `NumberNinjaNavHost` locale
  effect as every other locale change, not just `UserSettings(language = null)`.
- **Mode selector (`SingleChoiceSegmentedButtonRow` → vertical `RadioButton`+`Text`
  list, `ModeOptions`)** — same "vertical list" pattern `QuizSubModeOptions` already
  established in phase C, applied here because "Свободная практика" (Russian Free
  Practice, the longest Mode label) plus the selected-state checkmark icon didn't
  comfortably fit a 2-wide segmented cell. Confirmed via on-device (well, on-AVD)
  screenshot both unselected and selected. `LanguageSegmentedControl` (3 short options,
  no checkmark-vs-text collision even in Russian/Ukrainian) is the one remaining
  `SingleChoiceSegmentedButtonRow` in this file — the rule of thumb from phase C stands:
  reserve segmented rows for options that are both few and short.
- **Language checkmark now resolves via a new private `resolveActiveLanguage()`** in
  `SettingsScreen.kt` when `current.language == null` ("follow system"): checks
  `AppCompatDelegate.getApplicationLocales()` first (non-empty → `[0]`), falls back to
  `Locale.getDefault()`, then maps the resulting locale's `.language` tag to one of the
  3 `AppLanguage` entries, defaulting to `ENGLISH` if none match (mirrors the app's real
  resource-fallback behavior for e.g. a French device). Purely a display computation —
  never writes anything, so merely opening Settings/QuickSetup can't turn a null/
  "system" setting into an explicit persisted choice. `LanguageSegmentedControl`'s
  `selected` param is `AppLanguage` (non-null) now, not `AppLanguage?` — the null case
  is resolved one level up before the composable ever sees it.
- `domain/Settings.kt`: `UserSettings.mode` default is now `TrainingMode.QUIZ` (was
  `FREE_PRACTICE`). No test/other code depended on the old default (checked via grep).
- `settings_level_not_applicable` string rephrased in all 3 locales (exact wording
  supplied by the task, not machine-translated) — key name unchanged, only the string
  value changed.
- New string keys (all 3 locales, parity-checked with a `sort -u` diff — clean):
  `settings_reset_button`, `settings_reset_confirm_title`, `settings_reset_confirm`,
  `settings_reset_cancel`. Removed keys (now unused after QuickSetupScreen stopped
  hand-rolling its own section labels): `quick_setup_operations_label`,
  `quick_setup_level_label`, `quick_setup_mode_label` (all 3 locales).

**Reset button placement:** below Language, separated by a `HorizontalDivider()` plus
an error-tinted `OutlinedButton` (not just extra spacing) — addresses the same
"accidentally tapping too much" worry the user raised elsewhere, since an unconfirmed
reset would itself be an easy-to-regret accidental tap. Confirmation is a real
Material3 `AlertDialog` (title + confirm + cancel), not a Snackbar/undo pattern.

**Verification note:** first-run's "Начать!" CTA sits below the fold on a phone-height
viewport once Quiz mode (the new default) is selected — quiz style + quiz length add
two more sections above the CTA compared to the old FREE_PRACTICE-default first run.
Left as scrollable (not moved to a Scaffold `bottomBar`) since it's consistent with
Settings' own scrolling behavior and the button is still reachable with one swipe —
flag if a future design review wants it pinned.
