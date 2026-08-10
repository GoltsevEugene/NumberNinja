---
name: architecture-ui-layer
description: Foundational UI-layer conventions established in the first Compose implementation phase (theme, nav, screens, DI)
metadata:
  type: project
---

Established during the first Compose UI phase (theme + MainActivity + Home + first-run
quick setup + placeholder Settings/Session screens):

- **Package layout**: `number.ninja.ui.<feature>` per screen (`ui/home`, `ui/firstrun`,
  `ui/settings`, `ui/session`), `ui/theme` for the Material3 theme, `ui/nav` for routes
  and the NavHost, `ui/components` for shared composables (currently `BigActionButton`),
  `ui/EnumLabels.kt` (top-level, not a subpackage) for domain-enum → `@StringRes` mapping.
- **Enum display names live in `ui/`, not `domain/`** — domain stays Android/resource-free.
  Pattern: `fun Operation.labelRes(): Int` style extension functions in `ui/EnumLabels.kt`.
- **MVVM**: one Koin-provided ViewModel per screen (`HomeViewModel`, `QuickSetupViewModel`),
  injected via `koinViewModel()`. ViewModels are registered in a new `uiModule` in
  `di/AppModule.kt` (added to the `appModules` list) using `viewModel { Foo(get()) }` from
  `org.koin.core.module.dsl.viewModel` — NOT `org.koin.androidx.viewmodel.dsl` (deprecated,
  moved package in Koin 4.1.0).
- **Navigation**: type-safe routes via `sealed interface Route { @Serializable data object
  Home : Route; ... }` in `ui/nav/Routes.kt`, consumed with `composable<Route.X>` /
  `navController.navigate(Route.X)`. Nesting routes under a sealed interface for
  organization works fine with kotlinx.serialization here because no polymorphic
  serialization is needed — each route is independently `@Serializable`.
- **Startup gating**: `NumberNinjaNavHost` collects `SettingsRepository.settings` with
  `initialValue = null` (not a default `UserSettings()`) and renders an empty themed
  `Surface` until the first real DataStore emission arrives. Only then does it `remember`
  the start destination once (`Route.Home` vs `Route.QuickSetup` based on
  `hasCompletedFirstRun`). This avoids two bugs: flashing quick-setup for returning users
  (because `UserSettings()`'s default has `hasCompletedFirstRun = false`), and resetting
  the NavHost's graph when settings change after start (changing `startDestination` at
  runtime resets the graph — so it must be captured once, not derived live). Quick setup
  navigates away with `popUpTo(Route.QuickSetup) { inclusive = true }`.
- **Repository writes**: `SettingsRepository.update` rewrites all 8 DataStore keys per
  call. Screens with multiple toggles (quick setup) must batch edits into local
  ViewModel/UI state and commit ONE `update { it.copy(...) }` call on submit, not one
  call per toggle.
- **MainActivity extends `AppCompatActivity`** (not plain `ComponentActivity`), even
  though nothing in this phase needs it. Reason: spec §4 requires
  `AppCompatDelegate.setApplicationLocales()` for in-app language switching in a later
  phase, which needs AppCompatActivity on API < 33 (minSdk is 24). Decided now to avoid
  reworking the Activity + XML theme parent later.
- **XML theme**: `values/themes.xml` / `values-night/themes.xml` parent is
  `Theme.Material3.DayNight.NoActionBar` from `com.google.android.material:material`
  (NOT an `android:Theme.Material3...` platform theme — those are API 31+ only and
  minSdk here is 24). Real theming happens in Compose `MaterialTheme` (`ui/theme/Theme.kt`,
  `NumberNinjaTheme`), which currently only follows `isSystemInDarkTheme()` —
  `UserSettings.theme` (SYSTEM/LIGHT/DARK) is NOT wired to a manual override yet.
- Edge-to-edge: `enableEdgeToEdge()` in `MainActivity.onCreate`, screens use `Scaffold`
  and consume `innerPadding`. No window-size-class / adaptive layout work done yet.
- ~~English-only strings so far~~ **STALE as of Phase D**: `values-uk`/`values-ru` now exist
  and are fully wired (see [[project_phaseD_locale_insets_icon]] for the language-default and
  per-app-language work done on top of them).

Added during the full Settings screen phase (`ui/settings/SettingsScreen.kt` +
`SettingsViewModel.kt`, replacing the placeholder):

- **Per-interaction commits, not batching** — `SettingsViewModel` deliberately deviates
  from `QuickSetupViewModel`'s batch-then-submit pattern: every control (operation
  toggle, level/mode/quiz-submode select, language select, quiz-length slider release)
  calls `settingsRepository.update { it.copy(...) }` immediately, one call per
  interaction. Why: the spec's settings UX has no "confirm"/"Save" step — it's a
  live-editing screen — so batching would need an artificial commit trigger that isn't
  there. Accepted tradeoff: `update()` rewrites all 8 DataStore keys per call (see the
  `Repository writes` note above), so rapid interactions (e.g. slider dragging) must
  debounce to one write per gesture, not one per tick — implemented by only calling
  the ViewModel from `Slider`'s `onValueChangeFinished`, with `onValueChange` mutating
  local `remember { mutableFloatStateOf(...) }` state for live visual feedback only.
- **First back-navigation pattern in the app** — `SettingsScreen(onBack: () -> Unit)`
  is the first screen with a `TopAppBar` + back arrow
  (`Icons.AutoMirrored.Filled.ArrowBack`). `NumberNinjaNavHost` wires it as
  `SettingsScreen(onBack = navController::popBackStack)`. `TopAppBar` requires
  `@OptIn(ExperimentalMaterial3Api::class)` in this Compose BOM. Follow this pattern
  for any future non-root/non-start destination.
- **In-app language switching (spec §4)**: `AppCompatDelegate.setApplicationLocales(
  LocaleListCompat.forLanguageTags(language.tag))` is called from two places —
  (1) the language selector in `SettingsScreen`, ordered *after* the
  `settingsRepository.update` call (not before), because on API < 33
  `setApplicationLocales` synchronously triggers `applyLocalesToActiveDelegates()`,
  which recreates the Activity — the persist must already be in flight first; and
  (2) a `LaunchedEffect(current.language)` in `NumberNinjaNavHost`, right after the
  first non-null settings snapshot arrives, to re-apply the persisted language on every
  cold start. This second call is required because AppCompat only auto-restores a
  stored locale below API 33 if `autoStoreLocales` meta-data is declared in the
  manifest — it is NOT declared here — so without the `LaunchedEffect`, the
  DataStore-persisted `UserSettings.language` would be write-only (saved, but the
  system/app locale would silently revert to default after process death on API
  24–32). On API 33+ this second call is a no-op in practice since the framework
  `LocaleManager` already persists across process death, but calling it anyway is
  cheap and keeps behavior uniform across API levels.

Added during the Free Practice screen phase (`ui/practice/FreePracticeScreen.kt` +
`FreePracticeViewModel.kt`, replacing the `SessionScreen` placeholder body):

- **`Route.Session` is now a mode router, not a single screen** — `ui/session/SessionScreen.kt`
  reads `UserSettings.mode` and renders `FreePracticeScreen` (mode `FREE_PRACTICE`) or a
  `QuizComingSoon` placeholder (mode `QUIZ`). Future quiz work replaces only the placeholder
  branch inside `SessionScreen`, not the routing itself — `Route.Session`/`NumberNinjaNavHost`
  don't need to change.
- **`SessionScreen` deliberately has no ViewModel** — it takes `SettingsRepository = koinInject()`
  directly and branches on `settings.mode`, breaking the usual one-ViewModel-per-screen rule.
  Justification: it has no state or business logic of its own, purely a router — same
  reasoning `NumberNinjaNavHost` already uses for reading `SettingsRepository` directly at the
  nav-root level. Don't "fix" this by adding a `SessionViewModel` unless it grows real logic.
- **`FreePracticeViewModel` snapshots `UserSettings` once in `init` instead of `stateIn`-ing a
  live `Flow`** like `SettingsViewModel`/`HomeViewModel` do. Justification: this screen isn't
  reachable while Settings is open (you have to back out first), so settings can't change
  mid-session, and a plain snapshot keeps example generation (`operations.random()` +
  `exampleGenerator.generate(...)`) synchronous instead of needing to re-collect a Flow per
  example. Don't assume every ViewModel here follows the reactive-`StateFlow<UserSettings?>`
  pattern — check whether the screen can plausibly race with a settings change first.
- **Domain→display rendering formatter lives in `ui/MathExampleFormatter.kt`** (top-level,
  `fun MathExample.render(): String`), not inside `ui/practice/`, even though only free
  practice currently calls it — the future quiz screen and the quiz results-review screen
  (spec §6.2) will both need it too, so it follows the same top-level-`ui/`-for-cross-feature
  pattern as `ui/EnumLabels.kt`. It hardcodes `×`/`÷`/`□`/parens directly (no string
  resources) since those are universal glyphs, not localized text — don't add
  `stringResource` calls here.
- **`BigActionButton` gained an `enabled: Boolean = true` param** (default preserves every
  existing call site). Use it instead of guarding only inside `onClick` — a lambda-only guard
  leaves the button visually enabled while doing nothing on tap (e.g. after typing a
  non-numeric or too-long answer), which reads as broken.
- **ViewModel state-transition guard against double-taps**: `FreePracticeViewModel`'s
  `submitAnswer`/`onFeedbackDismissed`/`onFactDismissed` each check `_uiState.value` is still
  the expected sealed-state variant before transitioning. Needed because `Crossfade` (and
  `AnimatedContent`) keep the outgoing composable alive and clickable for the fade duration,
  so a fast double-tap during a transition can otherwise double-fire an action (double-record
  a `StatsRepository.recordAttempt`, or skip/duplicate an example). Apply this guard to any
  future screen with a `Crossfade`/`AnimatedContent`-driven state machine and per-state
  one-shot actions.
- **Fact-every-N counter is plain unpersisted ViewModel state** (`examplesCompleted` in
  `FreePracticeViewModel`), matching spec §8's "session-scoped" requirement — it resets
  whenever the ViewModel is recreated (e.g. leaving and re-entering free practice), not via
  any DataStore/Room key. Don't persist it.

Added during the Quiz + Quiz Results phase (`ui/quiz/` — `QuizViewModel.kt`, `QuizScreen.kt`,
`QuizResultsScreen.kt`, `QuizAttempt.kt`, `QuizResultsHolder.kt`; `Route.QuizResults` added;
`SessionScreen`'s `QUIZ` branch now routes to `QuizScreen` instead of the old `QuizComingSoon`
placeholder, which was deleted along with the now-unused `session_coming_soon` string):

- **Cross-screen result payloads go through a Koin `single` holder, not a `@Serializable`
  route argument.** `QuizResultsHolder` (registered in `uiModule`) carries the finished quiz's
  `List<QuizAttempt>` from `QuizViewModel` to `QuizResultsScreen`. Reason: `QuizAttempt` wraps
  `MathExample`, which is not `@Serializable` (it holds `IntRange` and a sealed-class
  `UnknownSlot`, i.e. exactly the polymorphic-serialization case `Routes.kt` already avoids) —
  making it a route arg would mean annotating domain classes plus a custom `NavType`. A holder
  also sidesteps a real bug: `QuizViewModel` is scoped to the `Route.Session` back-stack entry
  and is cleared the instant that entry is popped (`popUpTo(Route.Session) { inclusive = true }`
  when navigating to `Route.QuizResults`), so results couldn't ride on the ViewModel itself —
  they have to live somewhere that outlives that pop. Any future "pass a result from a finished
  flow to the next screen" need should default to this pattern (Koin single written once by the
  producer, read once by the consumer, no `Flow`) rather than reaching for route args.
- **`Route.QuizResults` is a plain `@Serializable data object`** — no typed args at all, since
  the payload travels via `QuizResultsHolder` instead. Back-press from it lands on Home for
  free: popping `Route.Session` with `inclusive = true` before navigating to `Route.QuizResults`
  leaves the back stack as `[Home, QuizResults]`, so no extra `popBackStack(Route.Home, ...)`
  handling is needed — plain `navController::popBackStack` on `onDone` is enough. This is the
  first route in the app with this "remove the flow you came from, then push the summary"
  pattern; follow it for any future finished-flow-then-summary screen instead of just pushing
  the summary on top.
- **`SessionScreen` gained a second required callback, `onQuizFinished`**, alongside `onExit` —
  it's still deliberately VM-less (see the earlier `SessionScreen` note), but now has two exit
  paths since only the QUIZ branch ever fires `onQuizFinished`. Kept required (not
  nullable/optional) specifically so `NumberNinjaNavHost` can't forget to wire it.
- **No going back to change a submitted quiz answer** — `QuizViewModel` has no
  "return to previous question" affordance at all (unlike free practice, which is an open-ended
  loop with nothing to "go back" to anyway). This is a deliberate reading of spec §6.2 ("a
  running answer log"), not an oversight — if a future spec revision wants edit-in-place,
  it's a real design change, not a bug fix.
- **Quiz ignores `UserSettings.level` entirely** — free practice uses the single configured
  `Level`, but the quiz spans all four levels every time (distributed per `QuizSubMode`:
  `PROGRESSION` spreads them in fixed EASY→MEDIUM→HARD→STAR blocks, remainder going to the
  *later* (harder) levels — `base = length/4, extra to last (length%4) levels`, e.g. 10→2,2,3,3
  and 13→3,3,3,4; `SHUFFLE` picks each example's level independently at random from all four).
  This is intentional per spec §6.2, not a bug — don't "fix" a future quiz screen to respect the
  settings-screen level.
- **Fact-every-5 is explicitly skipped on the last example.** `quizLength` is 10..15, and both
  bounds are multiples of 5, so a literal port of free practice's `examplesCompleted % 5 == 0`
  check would try to show a fact card with nowhere to go right when the quiz ends.
  `QuizViewModel.onFeedbackDismissed` checks "is this the last example" first and goes straight
  to the terminal `Finished` state, before ever checking the fact-every-N condition.
  `quizLength.coerceIn(MIN_QUIZ_LENGTH, MAX_QUIZ_LENGTH)` is also applied defensively in case a
  stale/out-of-range DataStore value is ever read.
- **Terminal ViewModel state (`QuizUiState.Finished`) drives navigation via a
  `LaunchedEffect(Unit)` inside that state's `Crossfade` branch**, not from the "See results"
  button's `onClick` directly. Since `Finished` never reverts, that branch of the `when` only
  composes once, so the effect fires exactly once even if a double-tap on the last "See results"
  button raced the `Crossfade` fade — combined with the existing double-tap state guards in
  `submitAnswer`/`onFeedbackDismissed`, this prevents a double-`navigate()` (which would have
  pushed two `QuizResults` entries). Use this pattern for any future screen whose last user
  action should trigger a one-shot navigation rather than a repeatable one.
- **`BigActionButton` gained a `modifier: Modifier = Modifier` param**, applied *before* the
  fixed `fillMaxWidth().height(64.dp)` chain (`modifier.fillMaxWidth().height(64.dp)`), so
  callers can add outer spacing (`QuizResultsScreen` needed `Modifier.padding(24.dp)` around its
  bottom "Back to Home" button) without fighting the fixed size. Default preserves every
  existing call site.
- **`LazyColumn`'s `weight()` modifier needs no import** — it's a `ColumnScope`/`RowScope`
  interface member, resolved automatically from the enclosing `Column {}` lambda's receiver.
  Importing `androidx.compose.foundation.layout.weight` directly is wrong and fails to compile
  ("internal in file") — this tripped the build once during the quiz results screen; don't
  import `weight` explicitly, just call it inside a `ColumnScope`/`RowScope` content lambda.

Added during the Progress screen phase (`ui/progress/ProgressScreen.kt` +
`ProgressViewModel.kt`; `Route.Progress` added; `HomeScreen` gained a second top-right icon):

- **Two independent read-only `Flow`s feeding one screen get `combine`d into a single
  nullable state, not exposed as two separate `StateFlow`s.** `ProgressViewModel.uiState`
  is `StateFlow<ProgressUiState?>` combining `StatsRepository.observeOperationStats()` and
  `observeWeakSpots()`, gated null-until-first-emission exactly like `HomeViewModel.settings`
  and `NumberNinjaNavHost`'s settings gate. Reason: with two separately-collected flows, the
  weak-spots section could render its `initialValue = emptyList()` "no weak spots — nice
  work!" for one frame before Room's real (possibly non-empty) list arrives — a false
  "confirmed empty" claim shown to a parent. Apply this pattern whenever a screen combines
  more than one live read-only source and must never show a default in place of "not loaded
  yet."
- **Home's `topBar` `Box` now holds a `Row` of icons (progress, then settings), not a single
  `IconButton`** — `HomeScreen(onStartClick, onSettingsClick, onProgressClick, viewModel)`
  gained a required `onProgressClick` param. Follow this `Row`-in-`Box` shape (not two
  separately-aligned `IconButton`s) for any future third top-right icon.
- **`Operation`-keyed weak-spot glyphs (`×`/`÷`) live in a small private `Operation.rangeSymbol()`
  in `ProgressScreen.kt`, not reused from `ui/MathExampleFormatter.kt`'s `opSymbol`** — that
  helper is `private` and keyed on a raw `Char` op (`'*'`/`'/'`), not `Operation`, so reusing it
  would mean exporting it for a one-off caller. `WeakSpot` is only ever produced for
  MULTIPLICATION/DIVISION (`StatsRepository.observeWeakSpots`), so the `else -> ""` branch is
  defensive, not a real case.
- **A lone `%` next to a format arg breaks string resources** — `progress_accuracy_percent`
  is `"%1$d%%"` (escaped `%%`), not `"%1$d%"`; aapt2/`String.format` reject the unescaped form.
- **Zero-attempt tiles branch on `OperationStats.totalAttempts == 0`, never on
  `accuracyPercent`** — `accuracyPercent` is defined to return `0` for zero attempts too, so
  branching on it would render "0%" (a failing grade) for an operation nobody has tried yet.
  Show a neutral "No practice yet" (`onSurfaceVariant`) instead, same non-punitive color choice
  `QuizResultsScreen` already uses for wrong answers.
- **`material-icons-extended` (`Icons.Filled.Insights` for the new Home icon) is already an
  `implementation` dependency** (`app/build.gradle.kts`) even though only core icons had been
  used before this phase — confirmed via `libs.versions.toml`'s
  `compose-material-icons-extended` alias before relying on an extended-set icon; check that
  alias/dependency line before assuming a non-core icon (e.g. `BarChart`, `ShowChart`,
  `TrendingUp`) is available.

Added during the adaptive-layout phase (spec §2 / §12 step 9 — `WindowSizeClass` for
compact/medium/expanded; `ui/components/AdaptiveLayout.kt` added; `MainActivity.kt`,
`HomeScreen.kt`, `SettingsScreen.kt`, `FreePracticeScreen.kt`, `QuizScreen.kt`,
`QuizResultsScreen.kt` touched; `ProgressScreen.kt` deliberately NOT touched):

- **`WindowWidthSizeClass` is threaded via a `CompositionLocal`
  (`LocalWindowWidthSizeClass` in `ui/components/AdaptiveLayout.kt`), not as a parameter through
  `NumberNinjaNavHost`/screen constructors.** Computed once in `MainActivity.onCreate` via
  `calculateWindowSizeClass(this)` (`androidx.compose.material3.windowsizeclass`, still
  `@ExperimentalMaterial3WindowSizeClassApi` in this BOM — class-level `@OptIn` on
  `MainActivity`), provided immediately above `NumberNinjaNavHost` inside `NumberNinjaTheme {}`.
  Justification: this codebase's existing precedent for cross-cutting, non-ViewModel
  dependencies is "read it locally via Koin" (`SettingsRepository` via `koinInject()` in
  `NumberNinjaNavHost`/`SessionScreen`/`QuizResultsScreen`), not "thread it through every
  `composable<Route.X>` body and constructor" — a `CompositionLocal` is the non-Koin equivalent
  of that same pattern, and threading a param instead would touch every screen signature and
  every nav-graph composable lambda for a value no ViewModel/business logic needs. Defaults to
  `WindowWidthSizeClass.Compact` (so a future `@Preview` without the provider still renders
  phone-identical, not a crash).
- **Two small helpers in `ui/components/AdaptiveLayout.kt` do all the adaptation — no per-screen
  bespoke layout branching**: `AdaptiveCenteredColumn` (drop-in replacement for a screen's root
  `Column`, wraps it in a `Box` that only gets slack to center once the inner `Column`'s width is
  capped at ~600dp on medium/expanded — on Compact the inner `Column` still fills the `Box`
  completely, so nothing visually changes) and `Modifier.adaptiveContentWidth()` (one-for-one
  replacement for a lone `Modifier.fillMaxWidth()` on content already living inside another
  centering container, e.g. free practice/quiz's `Box(contentAlignment = Center) { Crossfade
  { ... } }`). Reuse these for any new screen instead of writing a new width-clamp — don't
  reintroduce ad hoc `WindowWidthSizeClass` checks in individual screens.
- **600dp was chosen for `AdaptiveMaxContentWidth`** (private const in `AdaptiveLayout.kt`) —
  matches the spec's own suggested "~600dp" and comfortably fits a settings form or one math
  example without feeling cramped on an expanded (~840dp+) tablet landscape. Open question for
  product: no design review has confirmed this exact value.
- **`ProgressScreen.kt`'s grid logic was left untouched, but it still got the same
  `AdaptiveCenteredColumn` width cap every other screen got.** Its 4 operation tiles already
  render 2-per-row (`Operation.entries.chunked(2)` + `Row` + `weight(1f)`, from the Progress
  screen phase) — that part is genuinely fine as-is on a wider window (the existing `Row`s just
  get proportionally wider) and was NOT restructured, per the task's explicit "don't restructure
  grid logic that already works." But the *root* `Column` (containing the grid + weak-spots
  section) had no width cap at all, which would have made Progress the one screen in the app
  that stretches edge-to-edge on a tablet while Home/Settings/Practice/Quiz/Results all sit in a
  600dp column — a real "no-op-on-compact adaptation... missing" per the task's own escape
  clause, not a restructure. Fixed by wrapping the root `Column` the same way `SettingsScreen`'s
  is (`innerModifier = Modifier.verticalScroll(...)`), leaving `OperationStatsGrid`'s internals
  completely untouched. Don't re-litigate this by reflexively *removing* the wrapper for
  "leave it alone" reasons — the grid itself is what's off-limits, not the outer width cap.
- **`QuizResultsScreen`'s results list was kept single-column (not gridded)**, but still wrapped
  in `AdaptiveCenteredColumn` for the width cap. Reasoning: it's a sequential, in-order review
  log of the quiz (spec §6.2) — a grid would break that reading order for a list that's at most
  15 short rows, so there's no real space-efficiency win to justify it. If a future spec revision
  wants a denser results view, gridding it is a real design change, not a bug fix.
- **`WindowWidthSizeClass.Medium` starts at 600dp width, not "tablet" specifically** — a phone
  turned to landscape at ~600dp+ width also gets the centered/capped column, not just tablets.
  "Compact stays pixel-identical" is only true for compact *width* (portrait phones); a landscape
  phone crossing the Medium breakpoint will see the new centered column. This is almost
  certainly the intended reading of spec §2 ("responsive based on current viewport dimensions"),
  but flag it if a future PO conversation assumes "phones are never affected."
- **`LocalWindowWidthSizeClass` uses `staticCompositionLocalOf`, not `compositionLocalOf`** —
  it's read in many places (every adapted screen) and only changes on a real configuration
  change (rotation/resize), so the fine-grained-invalidation tracking `compositionLocalOf`
  provides isn't worth its overhead here. Follow `staticCompositionLocalOf` for any future
  ambient with the same "read-often, changes-rarely" shape.
