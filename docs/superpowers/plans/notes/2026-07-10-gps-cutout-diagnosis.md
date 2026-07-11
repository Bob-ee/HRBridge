# GPS cutout diagnosis — Run H10 / HR Bridge

**Date:** 2026-07-10 · **Author:** diagnosis pass over the June 30 session + live device inspection
**Symptom:** on outdoor watch runs, GPS location stops ~45 s after run start and never recovers, while HR/RR recording continues fine for the full run.

**Evidence classes are tagged inline:** `[MEASURED]` = from the recorded session file, `[CODE]` = from the repo source, `[DEVICE]` = read live off the watch over adb, `[PLATFORM]` = Android/Wear OS behaviour from knowledge (verify on next run), `[SPECULATION]` = inference not yet backed by a breadcrumb.

---

## 1. Data forensics

Source: `phone-sessions/fca56d91.ndjson` — the only recorded session. June 30, 06:51:06–07:37:56 local. 8,134 rows: 2,807 `hr`, 5,294 `rr`, **33 `loc`**. This build **predates** the `EvtRow` instrumentation, so there are zero `evt` rows and no direct GPS-state breadcrumbs — everything below is reconstructed from `loc` timing/fields.

### 1a. The 33 loc rows — per-fix timeline

`loc` row `ts` is the **record wall-clock time** (`System.currentTimeMillis()` at write, `SessionRecorder.kt:65`), not the GPS fix time. Fields present across **all** 33 rows: `lat, lon, spd, dist` only. `[MEASURED]`

| # | local time | T+ (from HR start) | gap | note |
|---|-----------|--------------------|-----|------|
| 1 | 06:51:13.235 | +7.04s | — | first fix, **lat/lon only** (no spd/dist yet) |
| 2 | 06:51:16.787 | +10.6s | +3.55s | first fix with speed+distance |
| 3–29 | 06:51:17…06:51:50 | … | ~1.0–1.7s (one 2.17s at +30.4s) | steady ~1 Hz, monotonic distance |
| 30 | 06:51:50.726 | +44.5s | +1.15s | lat 42.3873877 |
| 31 | 06:51:51.668 | +45.5s | +0.94s | **identical lat/lon/spd/dist to #30** |
| 32 | 06:51:51.805 | +45.6s | +0.14s | last new fix (lat 42.3873922) |
| 33 | 06:51:51.811 | +45.6s | +0.006s | **identical to #32**, 6 ms later |

- **Exact death:** last location update at **06:51:51.8**, i.e. **45.6 s after the first HR sample** (HR started 06:51:06.199). Matches the reported "~45–46 s." `[MEASURED]`
- **Time to first fix:** ~7.0 s from HR start to first lat/lon — normal cold TTFF. `[MEASURED]`
- **Cadence of fixes:** steady ~1 Hz the entire 38 s of live GPS, no thinning, no growing gaps before death. `[MEASURED]`
- **Death shape: sudden, not degrading.** Speed sat at 1.53–1.82 m/s (a warm-up walk/jog) the whole time; the last three speeds are 1.79, 1.76, 1.76 — no stall, no teleport, no accuracy blow-out visible. GPS was healthy right up to the instant it stopped. `[MEASURED]`

### 1b. The death signature hiding in the pre-instrumentation data

The recorder's location job re-emits on **every** `ExerciseMetrics` change and writes a `loc` row whenever `lat/lon` are non-null (`SessionRecorder.kt:51–66`). `[CODE]` Because `ExerciseMetrics` is a data class, a re-emit with **identical** `lat/lon/spd/dist` can only happen when some **other** field changed (`gps` availability string, `exerciseState`, or `cadenceSpm`). `[CODE]`

Across the whole run there are **exactly two** identical-value consecutive `loc` rows — and **both sit at the death instant** (#30→#31 at T+44.5→45.5 s, and #32→#33 6 ms apart at T+45.6 s). Nowhere earlier in the run does a duplicate appear. `[MEASURED]`

Interpretation: right at the cutoff, non-location fields on `ExerciseMetrics` were churning while `lat/lon` went stale — the classic footprint of a **`LocationAvailability` (or `exerciseState`) transition firing at ~06:51:51.8**. In the current instrumented build these would have been `EvtRow`s. This is the strongest single clue in the data and it points at a state/availability change at death, not at the app or GPS chip silently drifting off. `[MEASURED]` + `[SPECULATION]` on which field.

### 1c. Fields that never appeared — quality notes

- **Altitude: never once recorded** (0/33 rows carry `alt`). The recorder writes `alt = mx.altitude`, and `ExerciseClientManager.kt:51` maps the Health Services `Double.MAX_VALUE` sentinel to null. So Health Services delivered **2D fixes only** (no valid vertical) for the entire live window. Consistent with early-acquisition fixes; not itself the bug, but worth knowing the vertical channel was never healthy. `[MEASURED]` + `[CODE]`
- **No accuracy field is recorded at all** (`LocRow` has no accuracy member, `SessionRecorder.kt:65`). So "did horizontal accuracy degrade before death" is **unanswerable from this file** — the recorder never captured it. If we want that signal, add it to `LocRow`. `[CODE]`

### 1d. Proof the app stayed alive (it's a GPS-only failure)

- **HR:** 2,807 rows spanning 06:51:06 → 07:37:56 = **46.8 min continuous**, ending 46 min *after* GPS died. Max inter-HR gap in the whole run is **2.41 s**. `[MEASURED]`
- 2,761 HR rows were written **after** the last `loc` row. `[MEASURED]`

So the process, the foreground service, the BLE link, and the recorder were all healthy the whole time. Only the Health Services location stream died. This rules out process death, FGS teardown, wake-lock expiry, and app crash.

---

## 2. Static root-cause analysis

### Pipeline trace `[CODE]`

1. `ExerciseClientManager.start()` builds `ExerciseConfig(RUNNING)` with `setDataTypes(LOCATION, SPEED, DISTANCE_TOTAL, STEPS_PER_MINUTE)`, `setIsAutoPauseAndResumeEnabled(false)`, `setIsGpsEnabled(true)`. No batching-mode override. `ExerciseClientManager.kt:76–84`.
2. `onExerciseUpdateReceived` pulls `latest.getData(DataType.LOCATION).lastOrNull()` each update and copies lat/lon/alt into `_metrics`. `onAvailabilityChanged` records the `LocationAvailability` string into `metrics.gps`. `ExerciseClientManager.kt:36–73`.
3. `WorkoutController.beginRun()` launches `exercise.start()` and `recorder.start(...)`. The FGS (`WorkoutForegroundService`, type `health|location`, `PARTIAL_WAKE_LOCK` 4 h cap) keeps the process alive. `WorkoutController.kt:299–316`, `WorkoutForegroundService.kt:86–101,169–174`.
4. `SessionRecorder` collects `metrics` and writes `LocRow` per emission with non-null lat/lon, plus (in the current build) an `EvtRow` on any `gps`/`exerciseState` change. `SessionRecorder.kt:43–67`.

### Live device state (read-only, watch on charger, no run active) `[DEVICE]`

- Permissions **granted**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_HEALTH`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK`. **`ACCESS_BACKGROUND_LOCATION` is absent — not in the manifest, not requested, not granted.**
- FGS live and typed: `isForeground=true … types=0x00000108` = `FOREGROUND_SERVICE_TYPE_HEALTH (0x100) | FOREGROUND_SERVICE_TYPE_LOCATION (0x08)`. Confirms the location-typed FGS runs.
- `location_mode = 3` (HIGH_ACCURACY). `Battery Saver: DISABLED`. `mDeviceIdleMode = false`.
- **`screen_off_timeout = 15000` (15 s).**
- **`ambient_enabled = 0`** — always-on/ambient display is **off**, so the screen goes **fully off** at 15 s idle (no AOD dimmed state).
- `dumpsys location`: the only GPS/fused clients are `com.google.android.gms` (fused_location_provider). **No `com.example.runh10` client appears** — confirming the app never talks to LocationManager/FLP directly; **GPS is brokered through Health Services / GMS**, not the app's own UID.

### Candidate evaluation

**(a) Missing `ACCESS_BACKGROUND_LOCATION`.**
- Is it requested/granted? **No — absent entirely.** `[DEVICE]`
- Does a `health|location` FGS with a wake lock even need it here? **Platform semantics:** `[PLATFORM]` "While-in-use" (foreground) location access is granted whenever the app has a visible activity **or** a running foreground service of type `location`. `ACCESS_BACKGROUND_LOCATION` is required only for location with **neither** a visible activity **nor** a location-typed FGS — true background. This app keeps a `location`-typed FGS running for the entire run (`types=0x108`, confirmed live), so on a stock API-34 model it **should** hold while-in-use location for the whole run **without** background permission. A partial wake lock is irrelevant to location policy (CPU only).
- Verdict vs evidence: **mostly INCONSISTENT as a *sole* cause.** If background permission were strictly required, GPS would fail at the moment the activity went invisible (screen-off) or never start — not run cleanly for 45 s. The steady 45 s of good fixes argues the app *did* have location rights initially. **BUT** there is a real residual risk (see (c)): if this OEM/Wear build does **not** treat Health-Services-brokered GPS as covered by the FGS while-in-use exemption once the foreground *activity* stops, then only `ACCESS_BACKGROUND_LOCATION` would restore delivery. So (a) is demoted from "the bug" to "the likely *fix* for (c)."
- **What settles it:** on the next run, an `EvtRow` showing `LocationAvailability` going UNAVAILABLE/NO_GPS at ~45 s **while `exerciseState` stays ACTIVE** → the exercise is alive but location was cut = a permission/visibility gate, which `ACCESS_BACKGROUND_LOCATION` addresses.

**(b) Health Services batching / exercise-config issue.**
- Config requests default delivery, no `BatchingMode` override; live cadence was a clean ~1 Hz. `[CODE]` `[MEASURED]`
- **Platform:** `[PLATFORM]` in ambient/screen-off Health Services can buffer samples and flush them in a burst on wake or exercise-end. If that were happening here, the buffered fixes would have appeared later as a burst, or at latest been flushed at exercise end — but only 33 rows exist for a 47-min run and nothing arrived after 06:51:51. Data was **truly not delivered**, not merely batched.
- Verdict: **INCONSISTENT** as a benign explanation. Batching does not explain permanent, unrecovered loss. (A pathological "buffer that never flushes because the exercise never returned to a delivering state" reduces to (c).)
- **What settles it:** absence of any late location burst on the next run + no location delivery resuming when the user next wakes the screen.

**(c) Screen-off / ambient transition at ~45 s killing GPS delivery. — PRIMARY.**
- Timing: `screen_off_timeout = 15 s`, ambient/AOD **off**. `[DEVICE]` A plausible sequence: user taps Start, watches the screen while it acquires and for the first stretch (~30 s of interaction/wrist-up), lowers the wrist, screen goes fully off ~15 s later → ~45 s. The exact screen-off moment isn't in this pre-instrumentation file, so the 45 s ≈ interaction-window + 15 s link is `[SPECULATION]`, but the **order of magnitude and the abruptness fit a display-sleep trigger far better than a chip/almanac failure.**
- Mechanism: `[PLATFORM]` when the display fully sleeps and the foreground **activity** stops, the app's location access should fall back to the FGS-`location` while-in-use exemption. On Wear OS, GPS is brokered by Health Services in a **separate process** (confirmed: only GMS holds the GPS client, no runh10 UID). `[DEVICE]` If Health Services stops streaming location to the client once the client app is no longer "visible"/interactive — because the client lacks `ACCESS_BACKGROUND_LOCATION` and the platform does **not** credit the location-typed FGS for the brokered stream — delivery cuts exactly at display sleep, `exerciseState` stays ACTIVE, HR (BLE, unrelated to location policy) keeps flowing. **This matches every measured fact:** sudden death, app alive, HR uninterrupted, non-location-field churn (an availability transition) at the death instant (§1b).
- Verdict: **CONSISTENT with all evidence** and the best fit for the timing + abruptness + the §1b availability-transition signature.
- **What settles it:** on the next run, correlate the `EvtRow` death time against a `logcat` screen/interactivity transition. If GPS dies within a second or two of the screen going off → confirmed.

**(d) Power / location throttling (Doze, Battery Saver, location power-save).**
- `Battery Saver: DISABLED`, `mDeviceIdleMode = false`, `location_mode = 3`. `[DEVICE]` A running exercise + FGS + wake lock normally exempts the app from Doze, and 45 s is far too soon for Doze anyway.
- Verdict: **INCONSISTENT.** Note the device was read on-charger/idle, but nothing suggests an aggressive saver profile.
- **What settles it:** `dumpsys deviceidle` / `dumpsys power` snapshot *during* the next run if the `EvtRow` timing is ambiguous.

**(e) Exercise auto-pause / state change.**
- `setIsAutoPauseAndResumeEnabled(false)` in the HS config `[CODE]`, so HS auto-pause is off; the app's own auto-pause never touches the exercise session. Unlikely to have flipped `exerciseState`.
- Verdict: **unlikely but not excluded** from this file (we never recorded `exerciseState`).
- **What settles it:** an `EvtRow` with a `state` change at ~45 s would prove it; the fix expectation is that `state` stays ACTIVE.

---

## 3. Verdict

**Most probable root cause (confidence ~60%):** GPS delivery is gated on the app being screen-on/visible. When the display fully sleeps ~45 s into the run, Health Services stops streaming brokered location to the client, and — because the app has **no `ACCESS_BACKGROUND_LOCATION`** and (on this device) the `location`-typed FGS is apparently **not credited** for the brokered stream once the activity is invisible — it never resumes. HR is untouched because it rides BLE, which is outside location policy. Candidates (c) [trigger] and (a) [missing permission = the fix] are two halves of one story; I treat them as the leading hypothesis jointly.

**Second (confidence ~20%):** a Health-Services-side location stall on ambient entry that is independent of the client's permission (an HS/OEM delivery bug), which `ACCESS_BACKGROUND_LOCATION` would **not** fix. Distinguished from the primary by whether the fix works.

**Single most important on-device fact:** `ACCESS_BACKGROUND_LOCATION` is entirely absent (not requested/granted) **and** the screen fully sleeps at 15 s with ambient off — the permission gap plus a hard display-sleep is exactly the setup for a visibility-gated location cut.

**What the next instrumented run's `EvtRow`s will show:**
- **If the primary hypothesis is right:** at ~45 s, an `EvtRow` with `gps` transitioning to an UNAVAILABLE/NO_GPS/UNKNOWN string while `state` remains `ACTIVE`, coincident (in logcat) with the screen turning off; no further `loc` rows; no recovery.
- **If it's an exercise-state problem instead:** the `EvtRow` carries a `state` change (e.g. `USER_PAUSED`/`AUTO_PAUSED`/`ENDED`), not just a `gps` change.
- **If Health Services tore the whole exercise down:** `onExerciseUpdateReceived` stops entirely — no `EvtRow`, no `loc`, and the ongoing-notification status text also freezes.
- **If GPS merely degraded (chip/sky):** `loc` rows keep coming at ~1 Hz with worsening accuracy (once accuracy is recorded — see fix), no availability transition. Nothing in the June data supports this.

---

## 4. Recommended fix shape

Order the fixes by the hypothesis they test; ship the diagnostic first so the next run is decisive.

1. **Instrumentation already in place** — the `EvtRow` breadcrumbs (`SessionRecorder.kt:43–67`) will capture the `gps`/`state` transition. **Also add horizontal accuracy to `LocRow`** (and map the HS accuracy field in `ExerciseClientManager`) so "degrading vs cliff-edge" is answerable next time. Low-risk, high-value.
2. **For (a)/(c) — the leading fix:** add `ACCESS_BACKGROUND_LOCATION` to the wear manifest and request it at runtime (on Wear/API 34 it must be granted separately, after fine location). This is the standard requirement for Health Services location that must survive the screen turning off during a run. Cheap, reversible, and directly falsifiable by the next run.
3. **Defensive, complements (c):** on `onAvailabilityChanged` reporting location lost, surface it (already stored in `metrics.gps`) and consider re-asserting the exercise / re-registering the callback if availability stays lost for N seconds — but only if step 2 alone doesn't fix it. Do **not** add this speculatively.
4. **Not indicated:** batching overrides (b), Doze/whitelist changes (d), or auto-pause changes (e) — evidence is against all three; revisit only if the `EvtRow` shows a `state` change or a delayed burst.

### logcat to run during the next outdoor run

```sh
ADB=~/Library/Android/sdk/platform-tools/adb
SERIAL=192.168.0.120:45471   # confirm the serial is still valid before the run

# Clear, then stream the location/exercise/screen signals with timestamps.
$ADB -s $SERIAL logcat -c
$ADB -s $SERIAL logcat -v time \
  ExerciseClientManager:D \
  '*:S' HealthServices:V ExerciseClient:V \
  | grep --line-buffered -iE 'LocationAvailability|exerciseState|location|gps|exercise'
```

To pin the screen-off correlation, in a second shell during the run:
```sh
$ADB -s $SERIAL logcat -v time | grep --line-buffered -iE 'DisplayPowerController|screen (on|off)|Interactive|mWakefulness|Doze|deviceidle'
```

After the run, diff the `EvtRow` timestamp in the session file against the screen-off line: if GPS availability drops within a second or two of the display sleeping, the screen-off/background-location hypothesis is confirmed and fix step 2 is the change to ship.
