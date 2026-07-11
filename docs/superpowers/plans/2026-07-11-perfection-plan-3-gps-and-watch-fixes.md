# Perfection Pass — Plan 3: GPS Fix Package + Watch Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the GPS-cutout fix package (per the committed diagnosis) and fix the watch visual-audit findings V1, V2, V4, V6, V7.

**Architecture:** GPS: manifest + runtime-request of ACCESS_BACKGROUND_LOCATION, an `acc` (accuracy) field on LocRow, and a defensive location re-engagement path in the watch exercise pipeline. Watch UX: Home-first navigation with an explicit, visible route map (Home ⇄ Settings ⇄ Pairing), sensor/GPS status on Home per the mock, and two small state/feedback fixes.

**Tech Stack:** Kotlin, Wear Compose, Health Services, kotlinx.serialization.

## Global Constraints

- Repo `/Users/bobbywhiteley/AndroidStudioProjects/RunH10`, branch `master` (starts at 03260d1). `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Tests: `./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest`; assemble both apps before each commit.
- Commits via `git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "..."` — no AI attribution ever. Never stage `.idea/` files.
- Never fake data. GPS loss stays an honest gap; the fix changes delivery/recovery, not honesty.
- Evidence docs (the real specs — implementers MUST read the section named in their task):
  - GPS: `docs/superpowers/plans/notes/2026-07-10-gps-cutout-diagnosis.md` (incl. the wake-attempt addendum).
  - UX: `docs/superpowers/plans/notes/2026-07-10-visual-audit.md` (findings V1–V7).
- NDJSON back-compat is mandatory: old session files (no `acc` field) must keep parsing; the phone's `decodeTolerant` and all models treat new fields as optional-with-default.
- The EvtRow breadcrumb instrumentation must remain untouched — it validates this fix on Bob's next outdoor run.

---

### Task 1: LocRow `acc` field + ACCESS_BACKGROUND_LOCATION (manifest + runtime request)

**Files:**
- Modify: `shared/src/main/java/com/example/runh10/shared/model/SampleRow.kt` (LocRow)
- Modify: `shared/src/test/java/com/example/runh10/shared/serial/NdjsonSerializerTest.kt`
- Modify: `wear/src/main/AndroidManifest.xml`; `wear/src/main/java/com/example/runh10/session/SessionRecorder.kt` (LocRow write site); `wear/.../exercise/ExerciseClientManager.kt` ONLY if accuracy is surfaced there; the watch permission-request flow (find where ACCESS_FINE_LOCATION is requested — likely MainActivity or ReadyScreen)
- Modify: `mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt` (locationCallback LocRow write gains `acc = loc.accuracy.toDouble()` when `loc.hasAccuracy()`)

**Interfaces:**
- Produces: `LocRow.acc: Double? = null` (kotlinx `@Serializable` optional — old files parse unchanged; write TDD serializer tests BOTH ways: new row with acc round-trips; legacy line without acc decodes with acc==null).
- Produces: watch requests `ACCESS_BACKGROUND_LOCATION` AFTER fine location is granted (Android requires the two-step: background must be requested separately, and on Wear OS it surfaces as "All the time" in the system dialog/settings). Gate: if the API level's request flow requires a settings redirect, use the standard `ACTION_APPLICATION_DETAILS_SETTINGS` fallback with a one-line explanation row in watch Settings ("Location: While app is open → tap to allow all the time") rather than a nag loop. Denial is acceptable: app keeps working exactly as today.

**Steps:** (1) failing serializer tests → (2) verify RED → (3) add field + writer sites → (4) GREEN → (5) manifest line `<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />` + request flow per above → (6) full suite + assemble → (7) commit `feat(wear,shared): background-location permission + GPS accuracy in session data`.

### Task 2: Defensive GPS re-engagement in the watch exercise pipeline

**Files:**
- Modify: `wear/src/main/java/com/example/runh10/exercise/ExerciseClientManager.kt` and/or `wear/.../workout/WorkoutController.kt` (read both first; the diagnosis doc §static-analysis traces the pipeline)
- Test: a pure decision unit + test (see contract) — `wear/src/test/java/com/example/runh10/workout/GpsReengageDeciderTest.kt`

**Interfaces:**
- Produces: a pure `GpsReengageDecider` (input: gps availability string + run state + millis since last loc + millis since last re-engage attempt; output: NONE | REENGAGE | FALLBACK) with unit tests covering: no fix for >60s while RUNNING and availability not ACQUIRED → REENGAGE (max once per 120s); REENGAGE already attempted and another 120s with no fix → FALLBACK; paused/warmup → NONE; healthy stream → NONE.
- Produces: wiring — on REENGAGE, re-assert the location stream via the least-destructive Health Services call available (read the ExerciseClient API surface in the repo's pinned androidx.health.services version; candidates in preference order: update the exercise config / re-register the update callback; do NOT end the exercise). On FALLBACK, start a plain FusedLocationProvider stream inside the FGS (the typed location FGS + wake lock entitle it) feeding the same LocRow write path, tagged by an EvtRow `gps="FALLBACK_ACTIVE"` so the data provenance is visible post-hoc. Stop the fallback if HS location resumes.
- Every transition writes an EvtRow (the existing breadcrumb mechanism) — that is how the next run proves/disproves the whole theory.

**Steps:** TDD the decider first (RED→GREEN), then wiring, then full suite + assemble, commit `fix(wear): re-engage or fall back when Health Services GPS goes silent mid-run`.
**Escalation rule:** if the pinned Health Services version exposes no sane re-engagement call, implement FALLBACK-only (decider returns FALLBACK directly after the 60s threshold) and say so in the report — do not invent exercise-restart logic.

### Task 3: Watch navigation — Home-first + visible Settings + sensor/GPS status (V7, V1, V2)

**Files:**
- Modify: `wear/src/main/java/com/example/runh10/presentation/MainActivity.kt` (start-destination logic + back stack)
- Modify: `wear/.../presentation/ReadyScreen.kt` (Home: visible SETTINGS affordance + status lines)
- Read first: the audit doc's V1/V2/V7 sections; the mock README §7 (Ready) / §9 (Settings) at the mocks path in the audit doc header.

**Contract (acceptance criteria the reviewer will check):**
1. Cold/warm launch ALWAYS lands on Home/Ready, regardless of strap state (V7). Pairing is reached from Home (e.g., tapping the sensor status line when no strap is configured) and from Settings' strap row — never as a start destination.
2. Settings BACK returns to where the user came from (real back stack; system swipe-back behaves identically). No Pairing⇄Settings loop.
3. Home shows, per the mock: a visible "SETTINGS" text button (fully inside the round-safe area — the V1 element sat at bounds reaching y=456 on a 456px display, i.e. clipped by the bezel; keep interactive elements within ~78% diameter guidance) and the sensor/GPS status lines ("POLAR H10 · <batt>%" once Task-4-of-Plan-4 adds battery — until then "POLAR H10" + connection state; "GPS locked"/"GPS searching" from existing state). No strap configured → status line reads "TAP TO PAIR STRAP" and opens Pairing (V2).
4. No regression to the run flow: START still begins the connect+run sequence unchanged.

**Steps:** read → implement → full wear suite + assemble → commit `fix(wear): home-first navigation, visible settings, sensor/GPS status on ready screen`.

### Task 4: Small watch fixes — device-ID consistency (V4) + live measuring countdown (V6)

**Files:**
- V4: find where the Settings sensor row text comes from (two sources disagree after a Pairing round-trip — likely one path drops the saved device-name suffix; make both read the same persisted name).
- V6: the watch resting-HR measure flow — make the "Measuring… (60s)" label tick down each second ("Measuring… 47s") and give the terminal no-data state a RETRY affordance in place.

**Steps:** read audit V4/V6 → implement → wear suite + assemble → commit `fix(wear): stable strap identity in settings; live countdown + retry in resting-HR measure`.

### Task 5: On-device verification sweep (watch)

**Files:** none (device work + note appended to the visual-audit doc, committed).

**Steps:** build + `adb install -r` to the watch (serial via `adb mdns services`, historically 192.168.0.120) → screenshot-verify each fix: cold-launch (force-stop, `am start`) lands on Home; SETTINGS visibly rendered within the round area (screenshot + crop); status lines present; Settings BACK → Home; Pairing reachable from both entry points; resting-HR countdown ticks (two screenshots ~5s apart differ); uiautomator-dump the Home screen and confirm the SETTINGS node's bounds sit fully inside the visible circle. Append a "2026-07-11 re-verification (watch)" section with pass/fail per item to the audit doc; commit `docs: watch audit re-verification after Plan 3 fixes`.
**Do NOT start a run; do NOT change device settings.** If the watch is unreachable, report BLOCKED — the code tasks stand on their own.

---

## Task order

1 → 2 (GPS package, serial — Task 2 reads Task 1's manifest/permission state). 3 → 4 (UX, serial — same screens). The GPS pair and the UX pair are independent of each other BUT run them serially anyway (single working tree, no worktrees tonight). Task 5 last.

## Explicitly deferred

- Phone insets verification: Plan 1 Task 5 leftover, needs the phone awake (morning).
- Tile registration check: runbook (Bob adds the HR Bridge tile; if absent from the "Add new" tile list, THAT is a bug to file).
- V3 (settings scope beyond mock) and V5 (dot color): design-sync notes, no action.
- Strap-dependent checks (battery %, live BPM on Home): Plan 4.
