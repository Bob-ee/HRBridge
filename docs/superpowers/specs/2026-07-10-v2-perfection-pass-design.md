# HR Bridge v2 Perfection Pass — Design

**Date:** 2026-07-10
**Branch baseline:** `redesign-heat` (code-complete HEAT redesign), to be merged to `master` in Phase 0.
**Goal:** Take v2 from "code-complete" to "perfect": fix every known defect, fill every deliberate gap, ship Phase 4 export + Strava, deepen Trends, upgrade audio, and leave the codebase clean, tested, and adversarially reviewed. Maximize autonomous work; nothing blocks on a human run.

## Context / findings driving this design

- The v2 **watch** build was never installed — the watch still runs the June 30 v1 + EvtRow-instrumentation build (default launcher icon confirms it). The v2 watch UI is entirely unvalidated on hardware.
- The **phone** runs v2 (installed 2026-07-03) and has a confirmed insets defect: `AppRoot.kt`'s scaffold hands status-bar padding to the four tab screens only; **Settings** (explicitly given `PaddingValues(0.dp)`, `AppRoot.kt:88`), **Resting-HR**, and the **Record loop (Ready/Live/Save)** get no top inset and collide with the clock. No screen in `:mobile` uses `statusBarsPadding()`.
- The **GPS cutout bug** (dies ~46 s into a run, never recovers) is still open, but every run since June 30 recorded EvtRow GPS breadcrumbs — the diagnostic data already exists on the watch.
- Deliberate v2 gaps: strap battery % (BLE Battery Service never read), calories (kcal is a never-populated passthrough; HC write path ready), overnight resting-HR auto-update (`autoUpdateResting` is a dead preference with no consumer).
- Phone voice cues duplicate logic: `PhoneRecordController` hard-codes its own announcement string instead of reusing `MileAnnouncement`, and lacks the watch's split/pace/zone sub-toggles.
- Oversized files: `RecordScreens.kt` (664 lines, three screens), `RunExperience.kt` (575), `PhoneRecordController.kt` (445), `WorkoutController.kt` (424).

## Locked decisions

- Foundation-first phased campaign (approved over parallel blitz / spec-per-bucket).
- Full scope approved: reliability + gaps + polish + export + Strava upload + deeper trends + audio/music upgrades.
- Maximize autonomous work; live-run validations compiled into a runbook for Bob, not blockers.
- Phone installs use `adb install --user 0` **only** — the app must never land in the Private space profile (user 10).
- Commits authored as Bob-ee, no AI attribution, per standing rule.
- Package stays `com.example.runh10`; branding stays "HR Bridge", no bridge imagery.

## Phase 0 — Truth & baseline

1. Re-pair watch over wireless ADB (needs Bob once: Settings → Developer options → Wireless debugging → Pair new device).
2. Pull all session NDJSON from the watch (`run-as com.example.runh10` copy). Sessions since June 30 carry EvtRow breadcrumbs.
3. Diagnose the GPS cutout from the EvtRow + loc-row data **before touching the location code**. Current hypothesis (unconfirmed): missing `ACCESS_BACKGROUND_LOCATION`; the breadcrumbs confirm or kill it.
4. Merge `redesign-heat` → `master`, push. All subsequent work branches from master.
5. Install v2 on the watch; reinstall phone build with `--user 0`. Verify launcher icons, version stamps on both devices.

**Exit criteria:** GPS root cause identified with evidence (or explicitly documented as needing one instrumented run); v2 visibly running on both devices; single master baseline.

## Phase 1 — Correctness

- **GPS fix** per the Phase 0 diagnosis. Acceptance: explanation matches every breadcrumb trace pulled from the watch; fix ships with a logcat-verifiable signal for the next real run.
- **Insets:** route the scaffold's real `innerPadding` into Settings, Resting-HR, and the Record routes (replace the `PaddingValues(0.dp)` hand-off); intentionally full-bleed screens (Run Detail map hero) keep edge-to-edge but their top controls get `statusBarsPadding()`. Verify by ADB screenshot on-device for every screen.
- **Visual audit:** screen-by-screen comparison of both apps against the design mocks (`design/` + "HRBridge companion app design.zip" in the WatchApp project dir). Deviations fixed or explicitly recorded as adaptations (protolayout tile limits stay accepted).
- **Reliability hardening**, adversarial pass over: BLE drop/reconnect during pause; process death mid-run (FGS restart, session file integrity); location/BT permission revoked mid-run; storage-full writes; Data Layer sync interruption; phone reboot with queued sessions. Each hole fixed gets a unit test where the logic is testable off-device.

## Phase 2 — Fill the gaps

- **Strap battery %:** in `HeartRateBleClient.onServicesDiscovered`, after HR subscribe, read Battery Service `0x180F` char `0x2A19` (subscribe to notifications when supported; re-read on reconnect). Expose as `StateFlow<Int?>` alongside HR; surface in the watch Ready sensor card and phone Record sensor card ("87%" style per mocks). Absent service → hide, never fake.
- **Calories (HR-based estimate, Keytel formula):** add **weight, birth year, sex** to the phone `AthleteProfile` + Settings UI (watch keeps its own `age` setting; no cross-device profile sync is added — calories are computed on the phone, where all runs land). Compute kcal in `RunAnalyzer` from stored HR samples + profile — this retroactively fills already-synced runs. Live record loop shows a running estimate; totals flow to Run Detail, Save screen, and Health Connect's `ActiveCaloriesBurnedRecord` (write path already supports it). Missing profile fields → "—" stays, with a "set weight to enable calories" affordance.
- **Overnight resting-HR auto-update:** a daily `WorkManager` job on the phone reads the latest `RestingHeartRateRecord` from Health Connect (written nightly by the Pixel Watch's Fitbit integration); when `autoUpdateResting` is on, it updates `AthleteProfile.restingHr` (+ timestamp, source="auto") and zones recompute. Manual measurement unchanged and always wins for the day it's taken. **Assumption to verify on-device first:** Fitbit resting-HR records exist in HC on Bob's phone; if not, the toggle gets an honest disabled state ("requires Fitbit sync") instead of silently doing nothing.

## Phase 3 — Data out

- **Exporters** (per PLAN.md Phase 4): `GpxExporter` / `TcxExporter` / `CsvExporter` in `:mobile`, fed from the stored session bundle (NDJSON + summary). GPX: trackpoints with time/lat/lon/ele + `<gpxtpx:hr>`; TCX: `<HeartRateBpm>`; CSV: one row per merged sample. Share via `FileProvider` + `ACTION_SEND` from Run Detail. Acceptance: exported GPX imports into Strava with HR intact.
- **Route-card share:** stylized canvas route card → PNG via `rememberGraphicsLayer().toImageBitmap()` → share sheet.
- **Sync queue status:** per-run status chips (SYNCING / FINALIZED / SYNCED / FAILED-retry) surfaced on run cards; retries safe via the existing idempotent HC writes.
- **Strava auto-upload:** OAuth 2.0 (authorization-code with PKCE-style redirect via custom tab) + token refresh in encrypted storage; upload the GPX through Strava's upload API with dedup by external id; per-run "Upload to Strava" + optional auto-upload-on-save toggle. **Ships dark until Bob creates the Strava API app** (strava.com/settings/api) and provides client ID/secret; everything else is built and testable behind a debug stub.

## Phase 4 — Delight

- **Trends:** add weekly training load (zone-weighted TRIMP-style score), pace-at-HR fitness trajectory (median pace at fixed %HRR band over time), PR history (fastest mile/5K/10K, longest run — computed once per new run, cached in Room), resting-HR trend line. All from existing stored data; each computation unit-tested.
- **Audio unification + upgrades:** phone adopts the shared `MileAnnouncement` builder; phone Settings gains the same split/pace/zone sub-toggles as the watch. New (both devices): zone-change alerts ("entering zone four", toggleable), announcement frequency setting (every mile / half mile / off). TTS continues to duck media.
- **Music/watch UX polish:** pass over MusicScreen + watch screens with the visual audit findings; no functional redesign, keep phone-preferred relay architecture.

## Phase 5 — Quality gate

- **Refactor for isolation:** split `RecordScreens.kt` into Ready/Live/Save files; extract the pager host pieces of `RunExperience.kt`; carve the inline TTS + ingest out of `PhoneRecordController.kt`; slim `WorkoutController.kt` where announcement/lap logic can move to tested units. No behavior change; tests green before/after.
- **Tests:** unit tests for every new computation (calories, training load, PRs, pace-at-HR, exporters — exporter output schema-validated). Full `:shared` + `:wear` + `:mobile` test suites green.
- **Final adversarial review:** independent reviewer over the entire campaign diff; all confirmed findings fixed.
- **Runbook for Bob:** short checklist of live-only validations — GPS fix confirmation on a real outdoor run, voice cues + music ducking by ear, auto-pause/lock feel, tile readiness card after first run, notification-access grant for the music relay, deferred Phase 1+2 live tests that remain relevant on v2.

## Error handling principles (unchanged from v1/v2, enforced everywhere new)

Never fake data: missing sensor → honest null/"—"; gaps stay gaps. All long-running work in typed FGS; all writes crash-safe append NDJSON; HC writes idempotent; BLE reconnect with capped backoff.

## Dependencies on Bob (all non-blocking, slot in whenever)

1. Watch wireless-debugging pairing (30 s, needed for Phase 0 data pull + installs).
2. Strava API app client ID/secret (2 min, only gates Strava upload going live).
3. Eventually: one outdoor run with the runbook to confirm the GPS fix + live-only checks.

## Out of scope

Map SDKs (canvas routes stay), watch-side overnight recording service, calorie sources beyond HR estimate, any package/branding change, Wear tile Saira/gradient workarounds (protolayout limits accepted).
