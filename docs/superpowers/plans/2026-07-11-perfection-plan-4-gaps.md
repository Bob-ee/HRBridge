# Perfection Pass — Plan 4: Deliberate Gaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill v2's three deliberate gaps: H10 strap battery %, real (estimated) calories, and the overnight resting-HR auto-update — per the approved spec (`docs/superpowers/specs/2026-07-10-v2-perfection-pass-design.md`, Phase 2 section).

**Architecture:** Battery: standard BLE Battery Service read chained after the HR CCCD write in the shared client, exposed as a StateFlow, surfaced in both apps' sensor lines. Calories: new nullable profile fields (weight/birth-year/sex) + a pure Keytel-formula `CalorieEstimator` + computation in `RunAnalyzer` + one-shot backfill + an `ActiveCaloriesBurnedRecord` from the total in the HC writer. Resting-HR: a phone-side daily check (WorkManager + on-resume) reading Health Connect's `RestingHeartRateRecord` (Pixel Watch/Fitbit writes it nightly), gated by the existing `autoUpdateResting` toggle with an honest no-data state.

**Tech Stack:** Kotlin, BLE GATT, Room/DataStore, Health Connect client, WorkManager, JUnit4.

## Global Constraints

- Repo `/Users/bobbywhiteley/AndroidStudioProjects/RunH10`, branch `master` (starts at c16447f). `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Full suite green + both apps assemble before every commit: `./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug`.
- Commits via `git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "..."` — no AI attribution ever. Never stage `.idea/` files.
- NEVER fake data: absent battery service → no battery display; missing profile fields → calories stay "—" with a set-weight affordance; no HC resting-HR data → toggle shows an honest "requires Fitbit sync in Health Connect" state. Estimates are labeled by nature (calories are an estimate everywhere they're written — this is industry-standard and acceptable; do not interpolate HR).
- GATT ops are SERIAL: the battery read must not race the HR CCCD descriptor write (chain via `onDescriptorWrite`).
- Room changes: additive migrations only, never destructive.
- Hardware notes: no strap is paired tonight and the phone may be asleep — on-device checks that need them go to the runbook/morning list, not into task acceptance.

---

### Task 1: H10 battery % (shared client + both sensor lines)

**Files:**
- Modify: `shared/src/main/java/com/example/runh10/shared/ble/HeartRateBleClient.kt`
- Test: `shared/src/test/java/com/example/runh10/shared/ble/BatteryLevelParserTest.kt` (new)
- Modify: `wear/.../presentation/ReadyScreen.kt` (strap status line gains `· <n>%` when known)
- Modify: `mobile/.../ui/record/RecordScreens.kt` (Ready sensor card battery), `mobile/.../record/PhoneRecordController.kt` (expose through PhoneRunUi if that's the screen's data source — read it first)

**Interfaces:**
- Produces in the shared client: `val battery: StateFlow<Int?>` (null until read / when service absent); companion UUIDs `BATTERY_SERVICE = 0000180f-...`, `BATTERY_LEVEL = 00002a19-...`; a pure `object BatteryLevelParser { fun parse(value: ByteArray): Int? }` returning null for empty/out-of-range (>100, <0) payloads — TDD this (uint8 at [0]; 0..100 valid; empty/garbage → null).
- Read chain: `onServicesDiscovered` unchanged (HR subscribe first) → in `onDescriptorWrite` (new override; verify it's the HR CCCD that completed) issue `readCharacteristic(batteryLevel)` if the service exists → `onCharacteristicRead` parses via BatteryLevelParser → `_battery.value`. On disconnect → `_battery.value = null`. Reconnect re-reads (services rediscovered). Absent service → stays null, no error.
- Watch Ready line becomes `"<NAME> · CONNECTED · 87%"` (battery segment only when non-null); phone sensor card same pattern. Match existing style idioms.

**Steps:** TDD parser (RED→GREEN) → client wiring → UI surfaces → full suite + assemble → commit `feat(shared,wear,mobile): H10 battery level in sensor status`.

### Task 2: Calories — profile fields + Keytel estimator + analyzer + backfill + HC

**Files:**
- Modify: `mobile/.../data/AthleteStore.kt` (fields: `weightKg: Double?`, `birthYear: Int?`, `sexMale: Boolean?` + setters)
- Modify: `mobile/.../ui/settings/SettingsScreen.kt` (three new rows under HEART RATE or a new BODY section; steppers/pickers per existing idiom)
- Create: `mobile/.../data/CalorieEstimator.kt` + Test: `mobile/src/test/java/com/example/runh10/data/CalorieEstimatorTest.kt`
- Modify: `mobile/.../data/RunAnalyzer.kt` (compute kcal when param null), `mobile/.../data/RunRepository.kt` (pass profile-derived inputs; one-shot backfill), `mobile/.../healthconnect/HealthConnectWriter.kt` (total-kcal record when no CalRows)
- Modify: `mobile/.../ui/detail/RunDetailScreen.kt` (the "—" gains a "set weight to enable" hint when profile incomplete — small text, only when kcal null AND weight null)

**Interfaces:**
- `object CalorieEstimator { fun kcal(hrs: List<HrRow>, weightKg: Double, age: Int, male: Boolean, maxGapMs: Long = 10_000): Double? }` — Keytel per-minute rate integrated over consecutive-sample intervals (gap-capped like the zone math): male `(-55.0969 + 0.6309*hr + 0.1988*w + 0.2017*age)/4.184` kcal/min, female `(-20.4022 + 0.4472*hr - 0.1263*w + 0.074*age)/4.184`; clamp negative per-interval rates to 0; null when hrs.size < 2. TDD with hand-computed cases (both sexes, a gap-capped case, negative-rate clamp, empty/one-sample → null).
- `RunAnalyzer.analyze` computes `kcal ?: estimator(...)` when profile inputs are passed (new optional params or a small data class — keep the existing signature working for callers that don't care).
- Backfill: in `RunRepository`, `suspend fun backfillCalories(): Int` — for each summary row where `kcal == null` and profile complete: parseStored → estimate → dao update (add a narrow `@Query` update, no schema change — `kcal` column exists). Invoke once per process from SyncViewModel next to the recovery hook (guard so it doesn't loop; a simple "ran this process" flag is fine — it's cheap and idempotent anyway).
- HC: `HealthConnectWriter.write` gains `totalKcal: Double? = null`; when CalRows absent and totalKcal != null, insert `ActiveCaloriesBurnedRecord` spanning the session, `clientRecordId = "${sessionId}_kcal"` (idempotent upsert like the others). Callers (saveRun, sync path) pass the analyzed value.
- Age from `birthYear`: compute at estimation time (current year − birthYear); do NOT store an age that goes stale.

**Steps:** TDD estimator → fields+UI → analyzer/repo wiring + backfill → HC record → full suite → commit `feat(mobile): HR-based calorie estimates — profile body fields, analyzer, backfill, Health Connect`.

### Task 3: Resting-HR auto-update from Health Connect

**Files:**
- Modify: `mobile/AndroidManifest.xml` + wherever HC permissions are declared/requested (find the existing `health_permissions` declaration / permission set — add `android.permission.health.READ_RESTING_HEART_RATE`)
- Create: `mobile/.../healthconnect/RestingHrUpdater.kt` (+ WorkManager worker)
- Modify: `mobile/.../data/AthleteStore.kt` (a `restingSource: String?` — "manual"/"auto" — set by both paths), `mobile/.../ui/settings/SettingsScreen.kt` (toggle subtitle shows last auto-update or the honest no-data state)
- Test: `mobile/src/test/java/com/example/runh10/healthconnect/RestingHrPickTest.kt` — pure selection logic

**Interfaces:**
- Pure decision: `object RestingHrPick { fun pick(records: List<Pair<Long,Int>>, currentMeasuredAtMs: Long?, nowMs: Long): Int?` — newest record within the last 48h, only if newer than `currentMeasuredAtMs`, bpm sanity 25..100; else null. TDD.
- `RestingHrUpdater.checkOnce(context)`: if toggle off → no-op; read `RestingHeartRateRecord` last 48h (permission-gated, `runCatching`); pick; on hit → `athleteStore.setRestingHrAuto(bpm)` (new setter stamping time + source="auto"; manual setter stamps "manual"). Wire: a `PeriodicWorkRequest` (24h, unique, KEEP) registered at app start + a lightweight call from SyncViewModel.onResume (same guarded pattern as recovery/repush).
- Settings honesty: when toggle is on but the last check found no records at all, subtitle = "No resting-HR data in Health Connect — enable Fitbit sync"; when it worked, "Updated <relative time> from Health Connect". Persist the last-check outcome in AthleteStore (two small fields).
- Manual measurement continues to win for the day it's taken (pick() already guarantees: auto only applies if strictly newer than the manual timestamp).

**Steps:** TDD pick → permission + updater + worker → settings UI → full suite → commit `feat(mobile): overnight resting-HR auto-update from Health Connect`.

### Task 4: Verification + docs

- Build + install BOTH apps if devices reachable (phone: `--user 0`, verify user 10 clean); screenshot the new Settings rows + the detail-screen calorie hint (phone) if awake; watch Ready line unchanged-when-no-strap check. Append results to the audit doc; anything needing the strap/phone goes to the runbook list in the ledger instead. Commit docs.

## Task order
1 → 2 → 3 → 4 strictly serial (2 and 3 share AthleteStore/SettingsScreen; 1 touches the shared client alone but keep the single-tree discipline).

## Deferred
- Live battery % validation, calorie sanity vs a real run, actual overnight auto-update observation: runbook (need strap/night).
- CalRow live emission during recording (per-sample calorie rows): YAGNI — the total-record approach covers HC; revisit only if a consumer needs the curve.
