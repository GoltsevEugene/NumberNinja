---
name: phase-d-locale-insets-icon
description: Locale/language default fix, locale-flicker fix, per-app-language wiring, edge-to-edge insets, and adaptive icon done in Phase D — read before touching AppLanguage/UserSettings.language, NumberNinjaNavHost's locale effect, or the launcher icon.
metadata:
  type: project
---

Phase D (final phase of the initial 4-phase fix batch) touched locale handling, edge-to-edge
insets, and the app icon. Key decisions, in case a future change re-touches this area:

- **`UserSettings.language` is now `AppLanguage?` (nullable), default `null`**, not
  `AppLanguage.ENGLISH`. `null` means "no explicit override — follow the device's system
  locale." `AppLanguage` itself stays a 3-entry enum (uk/ru/en) — no 4th `SYSTEM` case was
  added, to avoid it leaking into `SettingsScreen`'s explicit 3-way language picker.
  `SettingsRepository` persists `null` by removing the DataStore key (`prefs.remove(Keys.LANGUAGE)`)
  rather than writing a sentinel, and reads an absent/unrecognized key back as `null` (not
  `UserSettings().language`, which is itself `null` — same thing, but don't reintroduce a
  `?: UserSettings().language` fallback pattern here, it's meaningless for a nullable default).
  `QuickSetupScreen` has no language step at all (checked — nothing to fix there for
  first-run defaults).

- **`NumberNinjaNavHost`'s `LaunchedEffect(current.language)` is the SOLE caller of
  `AppCompatDelegate.setApplicationLocales()` in the app.** `SettingsScreen`'s language
  selector only calls `viewModel.selectLanguage(...)` (persist-only) — it used to also call
  `setApplicationLocales` directly, which combined with the NavHost effect firing on the same
  DataStore change caused a double-apply → double Activity-recreate → visible flicker on
  API < 33. Confirmed fixed on-device (Pixel 6 emulator, API 36): logcat showed exactly one
  `WindowManager: finishDrawing of relaunch` per language change after the fix, vs. several
  before.

- **The effect only calls `setApplicationLocales` when `current.language != null`; it does
  a bare `return@LaunchedEffect` for the null case — it does NOT call
  `setApplicationLocales(LocaleListCompat.getEmptyLocaleList())`.** This was a real bug caught
  during device verification, not just theoretical: on API 33+, calling `setApplicationLocales`
  with an empty list unconditionally on every cold start would forcibly reset whatever locale
  the user picked via the OS's own Settings > Apps > NumberNinja > Language screen back to the
  true system default, the instant the app relaunches — silently undoing the OS-level per-app
  language feature this same phase wired up (see below). Confirmed via
  `adb shell cmd locale set-app-locales number.ninja --locales uk-UA` immediately getting
  reverted before this fix, and holding correctly across `am force-stop` + relaunch after it.
  Rule of thumb: only assert a locale from app code when the app's OWN persisted setting is
  non-null/explicit; when it has no opinion, don't touch `AppCompatDelegate` at all, on any
  API level — leave whatever the framework/system already has.

- **Every `setApplicationLocales` call site (there's exactly one now) is guarded by
  `if (AppCompatDelegate.getApplicationLocales() != target)`** before calling — re-applying an
  already-current value still recreates the Activity on API < 33 without this guard.

- **AGP's `generateLocaleConfig` (not a hand-written `res/xml/locales_config.xml`) wires
  Android 13+ per-app language support.** `app/build.gradle.kts`: `android { androidResources {
  generateLocaleConfig = true } }`. Requires `app/src/main/res/resources.properties` with
  `unqualifiedResLocale=en` (tells AGP what the unqualified `values/` dir represents) — AGP fails
  loudly if you also hand-write a `LocaleConfig` xml while this is on, so don't add one. Confirmed
  via `developer.android.com/guide/topics/resources/app-languages` (Context7 had no indexed
  content for this specific AGP DSL page — WebFetch was used instead, worth knowing Context7
  coverage gap here) and via building: `generateDebugLocaleConfig` task runs, output
  `app/build/generated/res/localeConfig/debug/xml/_generated_res_locale_config.xml` lists
  en/ru/uk, and the merged manifest gets `android:localeConfig="@xml/_generated_res_locale_config"`
  automatically — no manual manifest edit needed. `values-uk`/`values-ru` already existed by this
  phase (an earlier memory claiming they were "deliberately not created yet" is stale/wrong —
  removed).

- **`HomeScreen`'s topBar is a plain `Box`, not a `TopAppBar`** (see `architecture_ui_layer.md`'s
  earlier notes on Home's icon row) — unlike every other screen, Scaffold does NOT inset a custom
  topBar composable for you; only real Material3 app-bar components (`TopAppBar` et al.) consume
  status-bar insets internally. Fixed by adding `Modifier.windowInsetsPadding(WindowInsets.statusBars)`
  to that `Box` before its own `.padding(8.dp)`. `QuickSetupScreen` (`Scaffold` with no topBar at
  all) did NOT need a fix — Scaffold's default `contentWindowInsets = WindowInsets.safeDrawing`
  applies in full to `innerPadding` when nothing subtracts a topBar's height from it, so its
  content already sat correctly below the status bar. Don't assume every Scaffold-less-topBar
  screen is broken — check whether Scaffold is present at all first.

- **Launcher icon is now a real custom adaptive icon**: solid orange background
  (`#B4560A`, `ui/theme/Color.kt`'s `md_light_primary`) + a bold white "×" (two 12dp-stroke
  diagonal lines, round caps, endpoints at 30..78 on the 108dp canvas — well inside the ~66dp
  safe zone) in `drawable/ic_launcher_foreground.xml`, reused as-is for the monochrome variant.
  Verified visually on-device (Recents header + launcher app drawer both render it correctly),
  not just build-verified. Legacy pre-API-26 raster `mipmap-*dpi/ic_launcher(_round).webp` files
  were deliberately left as the unmodified Android Studio template — no matching raster art was
  generated for them; flagged as a known gap rather than silently mismatched.

See also [[architecture_ui_layer]] for the pre-existing locale-switching/AppCompatActivity
rationale this phase built on top of, and [[reference_stack_versions]] for the AGP/Compose
version context.
