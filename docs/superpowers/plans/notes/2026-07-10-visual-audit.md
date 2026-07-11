# HR Bridge v2.0 — On-Device Visual Audit

Date: 2026-07-10/11
Devices:
- PHONE: Pixel 9 Pro, serial `192.168.0.115:45027`, v2.0 mobile app (`com.example.runh10`)
- WATCH: Pixel Watch 3, serial `192.168.0.120:45471`, v2.0 wear app (`com.example.runh10`, round display, 456×456)

Screenshots: `/private/tmp/claude-501/-Users-bobbywhiteley-Documents-Claude-Projects-WatchApp/47bed566-0ba0-4220-819b-bb0628f9c2a3/scratchpad/audit/` (filenames referenced per screen below).

Design reference: `HRBridge Phone.dc.html`, `HRBridge Watch.dc.html`, `README.md` in `.../scratchpad/mocks/design_handoff_fable5/`.

## Status

**PHONE: BLOCKED.** The phone dropped off wireless ADB before any phone screenshots could be captured and never came back despite ~20 reconnect attempts over 6+ minutes (`adb connect` refused every time; mDNS kept advertising the `_adb-tls-connect._tcp` service at the same address/port, but the TCP connection itself was refused throughout — consistent with the device being asleep/screen-locked, which on Pixel phones suspends the wireless-debugging listener even though the mDNS record lingers). No USB or physical access was available to restore it. **None of the 8 P1 insets checks could be performed.** This needs a re-run once the phone is confirmed awake and reconnectable.

**WATCH: DONE.** Every reachable watch screen was captured and inspected, including several screens found only by trial taps against invisible/undocumented UI elements. Two P1-severity navigation/visibility bugs were found.

## Summary table

| Screen | Verdict | Findings |
|---|---|---|
| Phone — Feed | NOT REACHABLE (phone offline) | — |
| Phone — Run Detail | NOT REACHABLE (phone offline) | — |
| Phone — Trends | NOT REACHABLE (phone offline) | — |
| Phone — Watch tab | NOT REACHABLE (phone offline) | — |
| Phone — Profile | NOT REACHABLE (phone offline) | — |
| Phone — Settings | NOT REACHABLE (phone offline) | — |
| Phone — Resting-HR measure | NOT REACHABLE (phone offline) | — |
| Phone — Record Ready | NOT REACHABLE (phone offline) | — |
| Watch — Home / Ready | PASS (visual) / FAIL (findability) | 2 (V1 P1, V2 P2) |
| Watch — Settings (full scroll) | PASS with notes | 3 (V3 P3, V4 P2, V5 P3) |
| Watch — Strap pairing | PASS | 0 |
| Watch — Resting-HR (inline) | FAIL | 1 (V6 P2) |
| Watch — Navigation architecture (cross-screen) | FAIL | 1 (V7 P1) |
| Watch — Tile carousel | NOT REACHABLE (no HR Bridge tile registered) | — |
| Watch — Music / Metrics / Laps / Controls | NOT REACHABLE (in-run only; run not started per instructions) | — |

Total findings: **7** (P1: 2, P2: 3, P3: 2).

---

## Phone — all screens

**Not reachable.** The phone (`192.168.0.115:45027`) was online at task start but went offline before the first screenshot and never accepted an ADB connection again despite repeated attempts spaced over 6+ minutes, including two dedicated polling windows (8 tries @ 8s, 12 tries @ 12s) plus manual retries. `adb mdns services` confirmed the service was still being advertised at the same address the whole time, but every `adb connect` returned `Connection refused` — i.e. this is not a stale-address problem, the device's ADB listener itself was not accepting connections (most consistent with the phone's screen being off/locked; Pixel wireless debugging typically stops accepting new connections in that state even though the mDNS record is cached/still broadcast). No physical or USB access was available to wake it or re-enable wireless debugging.

**Consequence: all 8 P1 insets-fix checks (Feed, Run Detail, Trends, Watch tab, Profile, Settings, Resting-HR, Record Ready) are unverified this session.** This is the primary gap in this audit — the phone side needs to be re-run.

---

## Watch — Home / Ready screen

Files: `w_home.png`, `w_home3.png`, `w_appswipeup.png`, `w_home_bottom.png`, `w_home_bottom_bright.png` (2×/3× brightness+contrast crop of the lower third).

Layout matches the mock's "Ready" screen reasonably well: "HR BRIDGE" wordmark + heartbeat glyph top, large circular brand-gradient START/RUN button centered. This screen appeared automatically on the very first `am start` of the session (app was already running from install).

**V1 (P1 — invisible/undiscoverable control).** `uiautomator dump` on this screen reveals a `clickable=true, enabled=true` text element labeled `"SETTINGS"` at bounds `[163,366][294,456]` (bottom quarter of the screen, mostly under/behind the START circle). It renders as **completely invisible** — confirmed by cropping and boosting brightness 3× / contrast 2× on that region (`w_home_bottom_bright.png`): no glyph or text is visible at any level. A blind tap sweep across that bounding box (5 points) eventually landed on it and did navigate to Settings, so the control is functional, just entirely unlabeled to a real user — there is no visual affordance at all indicating Settings is reachable from Home. A user has no way to discover it short of trial-and-error tapping under the Start button.

**V2 (P2 — mock deviation, missing sensor/GPS status).** The mock's watch "Ready" screen (README §"7. Ready") specifies "logo, big circular START, 'POLAR H10 · 87%' + 'GPS locked'" status lines under/around the button. The actual Home screen shows no sensor or GPS status at all — just the logo and the button. A runner has no glanceable confirmation the H10 strap or GPS is ready before starting.

---

## Watch — Settings (full scroll, 7 frames)

Files: `w_settings.png` (top), `w_settings_scrolled.png` → `w_settings_scrolled6.png` (incremental scroll), `w_settings_bottom.png` (bottom, showing BACK).

Content, top to bottom: title "Settings" → Polar H10 device row (name + "tap to change strap" + status dot) → GPS "High" row → Units "Miles" row → **HEART RATE** section (Age stepper, Max HR stepper, "Measure resting HR" button) → **AUDIO COACHING** section (Voice coach / Split time / Pace / HR zone toggles, all on/orange) → **RUN** section (Auto-pause toggle, on) → BACK button.

**V3 (P3 — scope beyond mock).** The mock's watch Settings screen (README §"9. Settings") specs only 3 rows: Polar H10 / GPS / Units. The real screen is considerably richer (Age, Max HR, resting-HR measurement, 4 audio-coaching toggles, auto-pause) — not a defect, but worth a design-team sync since the swipe-canvas mock never depicted this content; nothing to compare it against for visual-hierarchy correctness, and it's a much longer scroll than the round-display "keep interactive elements within ~78% diameter" guidance implies for a single circular screen.

**V4 (P2 — state inconsistency).** On first visiting Settings, the sensor row reads **"Polar H10 182CCF39"** (name + device ID). After navigating into the strap-pairing screen and back out via its "SETTINGS" link, the same row reads just **"Polar H10"** — the device-ID suffix silently disappears and does not come back on subsequent views this session. Minor but a real inconsistency in a paired-device identity string.

**V5 (P3 — connection-status dot).** The Polar H10 row's status dot renders gray/neutral rather than the mock's "green dot" for a configured device. Given the H10 strap was not physically worn during this audit, this is very plausibly *correct* (paired-but-not-currently-connected state) rather than a bug — flagging only because the dot color semantics weren't otherwise verifiable this session.

---

## Watch — Strap pairing screen

File: `w_strap_pairing.png`.

Reached via the "SETTINGS" link visible at the bottom of the Pairing screen (see Navigation architecture below) and, separately, by tapping the Polar H10 row in Settings. Shows "PAIR YOUR STRAP" title with heartbeat glyph, a full-width brand-gradient "SCAN" button, explanatory copy ("No straps yet — tap Scan and wake the H10 (moisten the pads)."), and a "SETTINGS" text link at the bottom. Clean, legible, no defects found. SCAN was not tapped (avoids initiating a real BLE scan/pairing action beyond what's needed for the audit).

---

## Watch — Resting-HR measurement (inline, in Settings)

Files: `w_resting_hr.png` ("Measuring… (60s)"), `w_resting_hr_progress.png` and `w_resting_hr_progress2.png` (~15s apart, same text), then transitions to "No strap data — wear the H10".

Tapping "Measure resting HR" does not open a dedicated screen (the phone mock's screen 8 has a full dashed-ring/countdown/live-BPM treatment; the watch mock does not spec an equivalent screen, so a simpler watch treatment is reasonable in principle). What was observed:

**V6 (P2 — no live progress feedback / dead-end error copy).** The button's label switches in place to "Measuring… (60s)" and **stays frozen at "(60s)" for the ~15–20 seconds observed** — no visible countdown, ring, or other progress indicator ticks during that window. It then jumps straight to a terminal state, "No strap data — wear the H10," with no retry affordance surfaced in the same view. (This end state is expected given the H10 wasn't actually worn during this audit — the concern is the static countdown during the "measuring" phase, which gives a runner no feedback that anything is happening.)

---

## Watch — Navigation architecture (cross-screen)

**V7 (P1 — Home becomes unreachable after the app is backgrounded/killed).** This was reproduced twice, consistently:

1. Force-stop the app, then relaunch (`am start`) — simulating what happens after the OS reclaims the process, a reboot, or an app update. Result: the app opens directly to the **Pair-your-strap screen**, not Home (`w_home_final2.png`, `w_home_verify2.png` — both 31,339-byte pairing-screen captures).
2. From Pairing, the only exit is the visible "SETTINGS" link → Settings.
3. From Settings, the only exit is the "BACK" button at the bottom of the scroll → returns to **Pairing**, not Home.
4. The Android system back button from Settings does not navigate within the app at all — it exits the app entirely, back to the watch's tile carousel (`w_check2.png`).
5. The *only* path back to the true Home/Ready screen found during this entire session was the invisible bottom hotspot described in V1 — which is itself a Home→Settings link, not a way in from elsewhere. In other words, once the app is cold/warm-relaunched with no strap actively connected, **there was no discovered UI path back to Home** during this audit.

Net effect: a disconnected sensor (a completely ordinary state — first install, watch reboot, app update, or simply the strap not being worn yet) traps navigation in a Pairing⇄Settings loop, contradicting the mock's Home-first navigation model where Home/Ready is the one-swipe-from-watchface front door regardless of sensor state.

---

## Watch — Tile carousel

Files: `w_watchface.png` (watchface), `w_tile1.png`–`w_tile9.png` (Steps → Heart rate → Quick start → Weather → Sleep → Today/Calendar → Gemini → ECG → "Add new" — 9 swipes, full carousel).

**Not reachable / not found.** Swiped through the entire tile carousel from the watchface (confirmed reaching the terminal "Add new" tile) and found only stock Wear OS tiles. No HR Bridge tile was present. This may simply mean the app hasn't registered/pinned a Wear Tile in this build (design mock screen 1, "Tile — quick-launch front door," maps to `androidx.wear.tiles`) — adding one would require changing device tile configuration, which is out of scope for this audit per the no-device-setting-changes constraint.

## Watch — Music / Metrics / Laps / Run Controls

**Not reachable.** Per the design's navigation model, these are pages of the in-run swipe pager (reached by swiping from the **HR hero**, which is itself only shown *during* an active run) — not accessible from the pre-run Home/Ready screen. Swiping up/down/left/right from Home produced no screen change at all (confirmed via before/after screenshot diffing). Starting a run was explicitly out of scope for this audit, so Music, Metrics, Laps, and Run Controls could not be captured.

---

## Screens not reachable and why

| Screen | Reason |
|---|---|
| Phone: Feed, Run Detail, Trends, Watch tab, Profile, Settings, Resting-HR, Record Ready | Phone dropped off wireless ADB before capture and never reconnected despite ~20 attempts over 6+ minutes; no physical/USB access to recover it. |
| Watch: HR Bridge Tile (carousel) | Full 9-tile carousel swept from the watchface; no HR Bridge tile registered/pinned in this build. |
| Watch: Music, Metrics, Laps, Run Controls | These are in-run swipe-pager screens only reachable while a run is active; starting a run was explicitly excluded from this audit's scope. |

---

## 2026-07-11 re-verification (watch, post-Plan-3)

Device: Pixel Watch 3, `192.168.0.120:45471`, wear-debug installed from branch `master` HEAD `6d4ca78`. Watch had no strap remembered for this session (confirms the "unconfigured" paths below; strap-dependent paths — GPS status line with a paired strap, live BPM, connected-state naming — are out of scope per Plan 3 Task 5 brief and deferred to Plan 4).

Screenshots: `/private/tmp/claude-501/-Users-bobbywhiteley-Documents-Claude-Projects-WatchApp/47bed566-0ba0-4220-819b-bb0628f9c2a3/scratchpad/verify3/`.

| # | Check | Verdict | Screenshot(s) |
|---|---|---|---|
| 1 | Cold-launch (force-stop → `am start`) lands on Home | PASS | `v1_coldlaunch2.png` |
| 2 | V1 — SETTINGS control visibly rendered (no longer invisible) | PASS | `v1_coldlaunch2.png` |
| 3 | V2 (adapted) — strap status line reads "TAP TO PAIR STRAP" when unconfigured | PASS | `v1_coldlaunch2.png` |
| 4 | V2 (adapted) — "GPS · ON AT START" line | NOT CHECKABLE (only renders when `hasStrap` is true; no strap remembered this session — confirmed in source, `ReadyScreen.kt:115-145`) | — |
| 5 | SETTINGS pill uiautomator bounds fully inside the 456px circle | PASS (with note) | `home_dump.xml`; visible pixel bbox of the pill fully inside r=228 (max corner dist 211.2px); the invisible clickable touch-target parent node's bottom-right corner sits ~4px past r=228 (dist 232.1px) — not visible/rendered, negligible, not a V1 regression |
| 6 | Settings reachable from Home via SETTINGS pill | PASS | `v_settings1.png` |
| 7 | V4 — strap identity string stable across repeat Settings visits (no flicker) | PASS (as checkable without a strap) | `v_settings1.png`, `v7_settings_before_kill.png`, `v6_settings_hr_section.png` — all read "Polar H10" consistently, no ID-suffix flicker observed |
| 8 | Settings BACK → returns to Home | PASS | `v_settingsback_home.png` |
| 9 | System back from Home exits app (to watchface/tile carousel) | PASS | `v_sysback_home.png`; confirmed via `topResumedActivity=...wearable.sysui...` |
| 10 | Pairing reachable from Home via "TAP TO PAIR STRAP" | PASS | `v_pairing1.png` |
| 11 | V7 — background (`HOME` key) + `am kill` + relaunch from Pairing → lands on Home | PASS | `v7_pairing_kill_relaunch2.png` |
| 12 | V7 — background (`HOME` key) + `am kill` + relaunch from Settings → lands on Home | PASS | `v7_settings_kill_relaunch2.png` |
| 13 | V6 — resting-HR countdown ticks (two captures ~7-8s apart show different remaining seconds) | PASS | `v6_countdown_t0.png` ("Measuring… 59s"), `v6_countdown_t7.png` ("Measuring… 43s") |
| 14 | V6 — terminal state after timeout shows RETRY affordance | PASS | `v6_terminal_retry.png` ("No strap data — wear the H10" / "tap to retry") |
| 15 | V6 — tapping RETRY restarts the countdown clean, no lingering "tap to retry" subtitle under the live countdown | PASS | `v6_retry_tapped.png` ("Measuring… 59s", no leftover subtitle) |

**Result: 14 PASS, 0 FAIL, 1 not checkable this session (GPS status line — requires a paired strap, deferred to Plan 4 per brief).**

All Plan 3 watch fixes (V1, V2 adapted, V4 as checkable, V6, V7) hold up on real hardware. No regressions found. Do NOT start a run / no device-setting changes were honored throughout.

---

## Plan 4 verification (2026-07-11)

Plan: `docs/superpowers/plans/2026-07-11-perfection-plan-4-gaps.md` (Task 4: H10 battery %, HR-based calories, resting-HR auto-update from Health Connect). Device state at verification time: watch reachable (`192.168.0.120:45471`); phone asleep/unreachable (`192.168.0.115:45027` refused) — all phone checks deferred to the morning checklist below, per plan constraint ("hardware notes: ... on-device checks that need them go to the runbook/morning list").

**Watch checks performed:**

- `./gradlew :wear:assembleDebug :mobile:assembleDebug` → BUILD SUCCESSFUL (both APKs up to date from prior Task 1/2/3 work, HEAD `e5940ae`).
- Installed `wear-debug.apk` on the watch (`adb install -r`), force-stopped, relaunched via monkey launcher intent → landed on `MainActivity` (`topResumedActivity=...presentation.MainActivity`).
- Screenshot `verify4_home.png` (`.../scratchpad/verify4/`): Home/Ready screen renders cleanly with no strap paired — "TAP TO PAIR STRAP" line present, no battery `· n%` segment, no null/blank artifacts, START RUN button and SETTINGS pill both intact. Confirms Task 1's battery-segment addition to the Ready line degrades gracefully when `battery == null` (unconfigured/no-strap state), as specified ("battery segment only when non-null").
- No run was started; no device settings were changed.

**Not checked tonight (phone unreachable):** Settings body-fields rows (Task 2), run-detail calorie hint (Task 2), resting-HR toggle subtitle states (Task 3). See morning checklist below.

### MORNING CHECKLIST

Phone (`192.168.0.115:45027` or USB if wireless ADB still flaky):

1. **Install** — `adb install -r --user 0 mobile/build/outputs/apk/debug/mobile-debug.apk`; verify no stale copy under user 10 (`adb shell pm list packages --user 10 | grep runh10` should be empty, or if present confirm it's not shadowing the user-0 install — `pm list packages -u` cross-check).
2. **Settings — body fields** — open Settings, screenshot the new BODY section (weight/birth-year/sex rows) rendering correctly with the existing stepper/picker idiom; confirm blank/unset state has no crash and no garbage default values.
3. **Run detail — calorie hint** — open a run with `kcal == null` and no weight set; screenshot the "—" placeholder with the "set weight to enable" hint text (only when kcal null AND weight null per spec).
4. **Resting-HR toggle subtitle** — Settings, screenshot both subtitle states: "Updated <relative time> from Health Connect" (if HC has resting-HR data) and the honest no-data state "No resting-HR data in Health Connect — enable Fitbit sync" (if not). Confirm toggle off → no auto-check subtitle noise.
5. **Battery % live check** — pair the H10 strap on the watch, confirm the Ready line shows `· <n>%` and the phone sensor card shows the same battery segment; disconnect and confirm it reverts to no-segment (not a stale/frozen number).
6. **Calorie sanity** — after a real run with weight/birth-year/sex all set, confirm the run-detail kcal value is in a plausible range for duration/HR (rough gut check, not exact) and that it persisted (survives app restart, appears in Health Connect as `ActiveCaloriesBurnedRecord`).
7. **Resting-HR auto-update live** — after a night with the watch/Fitbit syncing resting HR to Health Connect, confirm the phone's next app-resume or the 24h WorkManager tick picks it up (subtitle updates to "Updated <relative time>..."), and that it does NOT overwrite a same-day manual entry (manual wins for the day, per `RestingHrPick` — auto only applies if strictly newer).

---

## 2026-07-11 phone audit (post-Plans 1-4)

Device: Pixel 9 Pro, HR Bridge v2.0 @ commit `1a8034e`, package `com.example.runh10`. Wireless-debugging endpoint rotated mid-session (Android drops the listener on network change); resumed on the new port and finished the full capture. Screenshots: `/private/tmp/claude-501/-Users-bobbywhiteley-Documents-Claude-Projects-WatchApp/47bed566-0ba0-4220-819b-bb0628f9c2a3/scratchpad/phone-audit/`.

All 8 target screens captured: `01-feed.png`, `02-run-detail.png`, `04-trends.png`, `05-watch.png`, `06-profile.png`, `07-settings-top.png` / `08-settings-mid.png` / `08b-settings-mid-recheck.png` / `09-settings-bottom.png` (full scroll), `10-resting-hr-measure.png`, `13-record-ready.png`.

### Insets gate (8 screens)

| Screen | Verdict | Screenshot |
|---|---|---|
| Feed | PASS | `01-feed.png` |
| Run Detail | PASS | `02-run-detail.png` (map hero bleeds edge-to-edge behind the status bar by design — matches the mock's edge-to-edge treatment; back button and SHARE pill both sit well clear of the clock, no text/icon collision) |
| Trends | PASS | `04-trends.png` |
| Watch tab | PASS | `05-watch.png` |
| Profile | PASS | `06-profile.png` |
| Settings | **FAIL** | `07-settings-top.png` (unscrolled: clean), `08-settings-mid.png` + `08b-settings-mid-recheck.png` + `09-settings-bottom.png` (scrolled: broken) |
| Resting-HR measure | PASS | `10-resting-hr-measure.png` |
| Record Ready | PASS | `13-record-ready.png` |

**Settings detail:** the screen is clean on first render (`07-settings-top.png` — "Settings" title sits with proper clearance below the clock). But scroll the list at all, and the top "UNITS" row (the orange MILES/KILOMETERS segmented pill) rides up and sits directly behind the status bar — the "MILES" pill's orange background is rendered right under/through the "7:1x" clock digits, and "KILOMETERS" text is overlapped by the wifi/signal/battery icons. Re-captured twice at the same scroll position (`08-settings-mid.png` and `08b-settings-mid-recheck.png`, one minute apart with no further input) — identical, so this is a persistent state, not a mid-animation artifact. Also confirmed at max-scroll (`09-settings-bottom.png`, Auto-pause is the last row — same collision persists at the top). The list has no top-inset spacer/clip preventing content from scrolling into the status-bar safe area. This is the "never-visually-verified fix" screen and the fix does not hold once you scroll — Run Detail, Resting-HR, and Record Ready all verified clean.

### Plan 4 UI

- **Body rows present and styled consistently**: Settings → BODY section has Weight, Birth year, Sex (`07-settings-top.png`, `08-settings-mid.png`). Same card/list-row idiom as the rest of Settings (label + `For calorie estimates` subtitle, right-aligned value/`not set`, hairline dividers between rows); Sex uses a segmented MALE/FEMALE toggle matching the same pattern as the UNITS segmented control at the top of the screen. No visual inconsistency.
- **Run Detail empty-profile hint**: with the profile empty, the Calories row shows "—" and the exact hint text underneath reads: **"set weight, birth year & sex in settings to estimate calories"** (`02-run-detail.png`).
- **Resting-HR auto-update toggle subtitle**: reads **"From watch sleep & HRV data"**, toggle is ON (`07-settings-top.png`, `08-settings-mid.png`). Note: this is the static/always-on-copy variant, not one of the two dynamic Health-Connect-driven subtitle strings ("Updated <relative time>..." / "No resting-HR data in Health Connect...") called out in the prior morning-checklist note — worth reconciling with Task 3's spec if those dynamic strings were meant to replace this line rather than sit alongside the auto-update toggle.

### General audit vs mocks — findings

**P1 — Settings screen: scrolled content collides with the status bar.** Described above. The orange UNITS pill and its MILES/KILOMETERS labels visually merge with the system clock and status icons once the list is scrolled past its initial position, in both the mid-scroll and fully-scrolled states. This directly fails the insets requirement this audit was commissioned to verify. Fix needs a top inset/clip on the scrollable content, not just on the fixed "Settings" header row.

**P2 — Watch tab sync log renders 10 duplicate placeholder rows.** `05-watch.png`: the "SYNC LOG" card shows ten consecutive rows that all read "No unsynced runs" — every other empty state in the app (e.g. Trends' "Not enough runs with HRV yet") is a single line, not a repeated list. Looks like a fixed-size placeholder list (e.g. `List(10) { EmptyRow() }`) rendering instead of a single empty-state message or the actual (currently-empty) log entries.

**P3 — Settings has no Sign-out / account row.** The mock (`HRBridge Phone.dc.html`) shows a "Sign out" affordance at the bottom of Settings; the live Settings screen ends at Auto-pause with nothing below. Likely intentional (no auth system implemented yet) rather than a defect — flagging for awareness only, not a bug.

No other broken/clipped/misaligned layout was observed. Feed, Run Detail, Trends, Profile, and Record Ready all structurally match their mock counterparts (stat rows, zone bar, map hero, HR profile cards, sensor/GPS cards) modulo the sparse real data (one 0.05 mi run) which is a data-state artifact, not a UI bug.

**Total findings: 3 (P1: 1, P2: 1, P3: 1).**

---

## 2026-07-11 fix-verification (P1 + P2)

Device: Pixel 9 Pro, `100.113.206.32:33705`, `mobile-debug.apk` rebuilt from this fix wave (`git` branch `master`, working tree). `./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug` → BUILD SUCCESSFUL. Installed with `adb install -r --user 0`; `dumpsys package com.example.runh10` confirms `User 10: installed=false` (no stray user-10 shadow copy). Screenshots: `.../scratchpad/phone-audit/fix-verify/`.

- **P1 — Settings scroll clips below the status bar: FIXED.** `SettingsScreen.kt`'s root `Column` now splits `bottomPadding` around `verticalScroll` (`padding(top = calculateTopPadding()).verticalScroll(...).padding(bottom = calculateBottomPadding())`), matching FeedScreen's `contentPadding` idiom. On-device: `03-settings-top.png` (unscrolled, clean) → full scroll to the bottom via swipe → `04-settings-scrolled.png` shows the UNITS pill fully scrolled out from under the status bar, clock/wifi/battery icons unobstructed, Auto-pause visible as the last row. No collision.
- **P2 — Watch tab sync log duplicate rows: FIXED.** Root cause: `SyncViewModel.onResume()`/`syncNow()` append a fresh progress line to `state.log` on every sync pass, even when nothing changed, so repeated resumes/manual syncs accumulate runs of identical consecutive `"No unsynced runs"` entries. Fixed in `WatchTabScreen.kt` by collapsing consecutive-duplicate lines before rendering (real distinct entries still render as before). On-device: `08-watchtab.png` shows a single `"No unsynced runs"` line; tapped SYNC NOW five more times and re-screenshotted (`09-watchtab-after-syncs.png`) — still exactly one line, no reaccumulation.
