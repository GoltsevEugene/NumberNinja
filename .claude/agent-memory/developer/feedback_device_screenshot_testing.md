---
name: feedback_device_screenshot_testing
description: Two adb/emulator gotchas that waste time when tapping through screenshots to verify a UI change
metadata:
  type: feedback
---

When verifying a UI change via `adb shell input tap` + `adb exec-out screencap`, two
things cost real time during the Settings/Home/Progress phase and are worth checking
first next time:

1. **Never eyeball tap coordinates from the `Read`-tool-displayed screenshot directly.**
   The Read tool returns images scaled down (e.g. 900x2000 for a 1080x2400 device) and
   says "multiply by N to map to original" — but it's easy to forget the multiply on
   some taps and not others in a long sequence, producing confusing "the tap did
   nothing" results that look like a UI/environment bug but are just bad coordinates.
   Prefer `adb shell uiautomator dump` + `grep bounds=` for the real element bounds in
   device pixels, then tap the center of those bounds. Reserve the screenshot for
   visual confirmation, not coordinate picking.
2. **A tap in the very top of the screen (y roughly 0–128px on a 1080x2400/420dpi
   device, i.e. within the status bar's cutout inset) can be silently swallowed by
   SystemUI's status bar window instead of reaching the app**, even when the app draws
   an edge-to-edge `IconButton` there and `uiautomator dump`/`dumpsys window` both show
   the app window focused and the button's accessibility bounds overlapping that
   region. Tap near the *bottom* of a top-bar icon's bounds (e.g. y≈140 instead of the
   bounds' vertical center y≈85 when top=22/bottom=148) to land below the status bar's
   swallow zone. Confirmed on a Pixel 6 AVD (`Pixel_6_NO_Google_APIs(AVD)`); don't
   assume it's an app bug (missing `statusBarsPadding()` etc.) before testing this.

**Why:** Repeated taps at the segmented icon's geometric center (y≈85) on Home's
top-right icon row silently failed for many attempts, while the same x at y≈140 worked
immediately — this looked exactly like a broken `onClick`/nav-graph wiring bug until
isolated as a status-bar touch-interception + coordinate-scaling combination.

**How to apply:** Any future phase that verifies UI via adb screenshots + taps should
dump bounds first and bias top-bar taps a bit below the bounds' vertical midpoint.

3. **Check `adb devices` output for what you're actually targeting before running ANY
   adb command that touches settings/security/app-data — a real personal phone
   (identified by a long alphanumeric serial reachable over `adb-tls-connect`, plus
   `dumpsys power`/wakelock logs full of WhatsApp/Viber/incallui/routineplus — real
   installed apps, not AVD boilerplate) can be attached in the same session as the
   project's AVD.** During the Settings/first-run phase this session initially targeted
   the wrong (real, personal Samsung) device and: ran `pm clear number.ninja` (wiped
   that device's real settings + Room stats for the app), set `screen_off_timeout` to
   1800000 without recording the prior value, and briefly ran
   `locksettings set-disabled true` while trying to dismiss its keyguard (reverted
   immediately once recognized, but should never have been attempted at all — never try
   to bypass a real device's lock screen). Real device wakefulness also kept flipping
   back to `Dozing` almost immediately (screen timeout/AOD/proximity policy outside
   this session's control), which is a strong tell you're not on an AVD.
   **Always screenshot-verify on the project's AVD** (`Pixel_6_NO_Google_APIs`, per the
   note above) — boot it with
   `~/Library/Android/sdk/emulator/emulator -avd Pixel_6_NO_Google_APIs -no-snapshot-save &`,
   wait for `adb devices` to list an `emulator-XXXX` entry, and pass `-s emulator-XXXX`
   on every subsequent adb call so a simultaneously-attached real device is never the
   implicit target. For a specific locale (e.g. Russian) use
   `adb -s emulator-XXXX shell cmd locale set-app-locales <pkg> --locales ru-RU` instead
   of relying on whatever the device's system language happens to be.
