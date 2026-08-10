---
name: reference-stack-versions
description: Exact dependency versions/aliases in this project's version catalog, and API gotchas tied to them
metadata:
  type: reference
---

Source of truth: `gradle/libs.versions.toml` and `app/build.gradle.kts` — always re-check
there before trusting this, since the stack is unusually recent and will move fast.

Snapshot as of the first Compose UI phase:
- AGP 9.2.1 with built-in Kotlin (no `kotlin-android` plugin applied in
  `app/build.gradle.kts` — that's correct, not a missing-plugin bug).
- Kotlin 2.2.20, KSP 2.3.6, Compose BOM 2026.06.00, Navigation Compose 2.9.8,
  kotlinx-serialization-json 1.7.3, Koin BOM 4.1.0, Room 2.8.4, DataStore 1.2.1.
- `androidx.compose.foundation.layout.FlowRow` is still `@ExperimentalLayoutApi` at this
  BOM version — needs `@OptIn(ExperimentalLayoutApi::class)` on every function that
  calls it directly (opt-in doesn't propagate from caller to a private helper composable
  that also calls FlowRow — annotate each one).
- Koin `viewModel { }` classic-DSL lambda form: import from `org.koin.core.module.dsl`,
  not `org.koin.androidx.viewmodel.dsl` (deprecated in 4.1.0, package moved).
  `koinViewModel()` in a `@Composable` comes from `org.koin.androidx.compose` (import
  `org.koin.compose.viewmodel.koinViewModel`); `koinInject()` for non-ViewModel deps is
  `org.koin.compose.koinInject`.
- Navigation Compose type-safe routes: `@Serializable` route objects/data classes +
  `composable<RouteType> { ... }` + `navController.navigate(RouteInstance)`. Confirmed
  working with routes nested inside a `sealed interface` wrapper (no polymorphic
  serialization needed since each nested route is independently `@Serializable`).
- `@Composable` extension functions (e.g. `UserSettings.summaryText()`) that call
  `stringResource` must not do so inside a stdlib higher-order-function lambda like
  `.joinToString(transform = ...)` — the Compose compiler rejects it ("@Composable
  invocations can only happen from the context of a @Composable function") even though
  `joinToString` is `inline`. Map to a `List<String>` first with `.map { stringResource(...) }`,
  then join the plain strings.
- `androidx.compose.material3:material3-window-size-class` (`libs.compose.material3.wsc`) was
  declared in the catalog/`app/build.gradle.kts` since an earlier phase but unused until the
  adaptive-layout phase. Its `calculateWindowSizeClass(activity: Activity): WindowSizeClass`
  entry point is a `@Composable` function still marked `@ExperimentalMaterial3WindowSizeClassApi`
  in this BOM (2026.06.00) — needs `@OptIn`. Confirmed current (not superseded) via Context7
  against `developer.android.com/reference/kotlin/androidx/compose/material3/windowsizeclass`
  as of this phase. Note: the bundled `adaptive` skill's guidance leans on newer
  Navigation-3/multi-pane/`MediaQuery` APIs and doesn't mention this artifact at all — that
  guidance is for nav-rail/multi-pane restructuring, not applicable here since this app's spec
  explicitly pins the older `windowsizeclass` artifact and this phase is single-pane
  width-capping only, not multi-pane.
