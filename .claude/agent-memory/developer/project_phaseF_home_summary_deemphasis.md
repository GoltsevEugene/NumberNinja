---
name: project_phaseF_home_summary_deemphasis
description: Phase F — de-emphasized and made tappable the Home screen's settings summary block
metadata:
  type: project
---

Phase F landed on 2026-08-09, touching only `ui/home/HomeScreen.kt` (+ one new
string key in all 3 `strings.xml`). Follows phase E
([[project_phaseE_settings_firstrun_reset]]).

**User feedback (translated from Ukrainian):** the Home screen's settings summary
line ("Сложение, Вычитание · Прогрессия сложности · Викторина") read as a heavy
run-on sentence next to the title/Start button, and it wasn't obvious the gear icon
is how you change those values. User offered two alternative directions (separate
lines at smaller style, or make the block clickable/hint-labeled) — explicitly an
open design call, not a spec.

**What changed:**
- `UserSettings.summaryText(): String` → `UserSettings.summaryLines(): List<String>`.
  Same 3 values (operations, `summaryModeDetailLabel()`, mode label), same
  computation — only the assembly changed from one `joinToString(separator)` line to
  a `List<String>` rendered one-line-per-`Text`. `home_summary_separator` string is
  now unused by this screen but left defined (harmless, some future screen might
  want the inline form).
- The summary block is now a `Column` with `Modifier.clickable(onClick =
  onSettingsClick)` (same lambda the gear `IconButton` already used) plus
  `.semantics { role = Role.Button }`, each line at `labelMedium` /
  `onSurfaceVariant` (down from `bodyLarge`/default color), followed by a new small
  `labelSmall`/`primary`-colored hint line, `home_summary_edit_hint` ("Tap to
  change" / ru "Нажмите, чтобы изменить" / uk "Натисніть, щоб змінити").
- Chose the **hybrid** of the user's two directions: separate lines *and*
  clickable-with-hint, since the task explicitly allowed combining "one clean
  approach" — one clickable block with an integrated hint text is still one
  affordance, not a redundant second edit button next to the existing gear icon.

**Why:** Splitting into per-setting lines at a muted, smaller style de-emphasizes it
relative to title/Start (constraint 1). Making the whole block clickable (not just
the far away gear icon) plus a one-line hint satisfies discoverability (constraint
2) without adding a second, competing edit control.

**How to apply:** If a future phase touches the Home summary again, the
computation logic (`summaryModeDetailLabel()`, operations sort/join) is unchanged
and documented in [[project_phaseC_settings_home_progress]] — don't re-derive it,
only `summaryLines()`'s consumer-side rendering changed in this phase. Verified on
an AVD (`Pixel_6_NO_Google_APIs`, no physical device was touched — see
[[feedback_device_screenshot_testing]]) in both English and Russian; the Russian
lines wrap cleanly onto 3 separate lines without clipping (Russian labels are
noticeably longer than English but each now gets its own full-width line, so there
was no overflow risk to check as there would be with a segmented control — see the
"vertical RadioButton+Text list" precedent in [[project_phaseC_settings_home_progress]]
for why long Russian labels favor one-item-per-line layouts generally).
