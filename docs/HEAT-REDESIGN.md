# HEAT Redesign Sprint (branch `redesign-heat`)

Started 2026-07-02 (overnight autonomous sprint). Source of truth for visuals:
`design_handoff_fable5/` (Claude Design "Fable 5" handoff — README.md + two `.dc.html`
mocks, kept outside the repo in the WatchApp project folder). This doc is the
implementation plan and decision record.

## Scope

1. **Watch UI total redesign** — swipe-nav model v2: RUN/HR hero home; swipe **up**
   pages run data (HR → Metrics → Laps), swipe **right** = run controls
   (PAUSE/FINISH/LAP/LOCK), swipe **left** = music. Time-of-day pinned to top of
   run-data screens only. Plus: Ready, Summary, Settings screens in HEAT, and a
   quick-launch **Tile** (readiness card + last run + START RUN).
2. **Phone companion app (new)** — Feed, Run Detail, Record loop (Ready/Live/Save),
   Profile, Settings, Resting-HR measurement, and Trends + Watch tabs (repo features
   not in mocks, done in HEAT style).
3. **Branding** — display name **"HR Bridge"** everywhere; new logo = pulse-waveform
   mark (**no bridge imagery — hard user requirement**). Package stays
   `com.example.runh10`.

## Locked decisions (user, 2026-07-02)

- **Routes = custom canvas.** GPS polyline drawn with the cool→hot HR-heatmap gradient
  (`#2E9BE6 → #22C55E → #F59E0B → #EF4444`) over a faint grid on dark. NO map SDK,
  no API keys.
- **Music = "both, phone preferred".** Phone runs a NotificationListenerService +
  MediaSessionManager relay over the Data Layer; watch falls back to its local
  MediaSessionManager when the phone is unreachable.
- **Work continues until hardware is genuinely required**, then stop + hand off.

## Architecture decisions (this sprint)

- **Shared design tokens**: `shared/.../design/HeatTokens.kt` — plain ARGB `Long`
  constants (shared has no Compose dep); both apps wrap in `Color(...)`.
- **Zone math**: `ZoneCalculator` + `MaxHr` move to `shared/.../zones/`; wear keeps
  source compatibility via `typealias` in the old package.
- **Run logic units** (`RunClock`, `RollingPace`, `MotionClassifier`,
  `RunStateMachine`, `SplitTracker`) move to `shared/.../run/` with typealiases in
  `wear/.../workout/` so existing wear code + tests compile untouched. Phone record
  flow reuses them.
- **Phone run store**: sync no longer deletes the pulled NDJSON — it lands in
  `filesDir/sessions/` and a Room `run_summary` table caches computed aggregates
  (distance, elapsed/moving ms, avg/max HR, HRV avg, kcal, zone millis Z1–Z5,
  downsampled route + HR series as JSON, splits JSON, name, feel). `RunAnalyzer`
  computes summaries from `SessionBundle`. Phone-recorded runs write into the same
  store, then to Health Connect via the existing `HealthConnectWriter`.
- **Phone recording**: H10 BLE client generalized (same GATT code as watch),
  FusedLocationProvider for GPS (`play-services-location`), step-counter cadence,
  typed FGS `location|connectedDevice`.
- **Watch pagers**: `androidx.compose.foundation` pagers (BOM already provides).
  Horizontal pages `[Controls, RunData, Music]` start on RunData; vertical pages
  `[HR, Metrics, Laps]`. Page dots per mock. Known tradeoff: Wear system
  swipe-to-dismiss on left edge may fight the controls swipe — verify on hardware.
- **Tile**: `androidx.wear.tiles` + protolayout. Readiness = resting HR (settings) +
  HRV (last run RMSSD avg, synced store) + last-run line; START RUN deep-links into
  MainActivity with auto-start extra.
- **Media protocol**: `shared/.../media/MediaProtocol.kt` — `/runh10/media/state`
  (phone→watch, JSON MediaState), `/runh10/media/cmd` (watch→phone, JSON command).
- **Fonts**: Saira (400–700) + Saira Condensed (500–800) static TTFs bundled in both
  apps' `res/font` (downloaded from google/fonts GitHub).

## Phase order (task list mirrors this)

1. ✅ Plan doc (this file)
2. Foundations: fonts, HeatTokens, shared moves (zones/run units), MediaProtocol
3. Wear theme + components (ring, chips, dots, pinned clock)
4. Wear swipe-nav run experience wired to WorkoutController
5. Wear music screen + MediaClient (bridge + local fallback)
6. Wear Ready/Summary/Settings (+ restyled pairing)
7. Wear Tile
8. Phone media relay service
9. Phone theme/nav/RunRepository (+ sync persists sessions)
10. Phone Feed/Detail/Trends/Watch tabs
11. Phone record flow + Save
12. Phone Profile/Settings/Resting-HR measure
13. Branding: HR Bridge + pulse logo
14. Build + tests green, commits, hardware handoff notes

## Existing behavior kept (restyled, not removed)

- Strap scan/pair/remember/forget flow; auto-connect on launch
- Warmup / auto-pause / manual pause state machine + voice announcements
  (voice coach, mile announcements, split/pace/zone toggles)
- Ambient mode active screen (burn-in-safe)
- Watch→phone session sync + Health Connect write (idempotent, validated Phase 3)
- EvtRow GPS diagnostics (open GPS-cutout investigation — do not remove)
- Settings: age/maxHR/resting, announce toggles, auto-pause

## Hardware-required (deferred to handoff)

- Install on watch + phone; grant notification access (media relay, both devices)
- Tile add + render check; swipe-nav vs system swipe-to-dismiss on device
- H10 pairing on phone; live record loop; resting-HR measure with strap
- GPS-cutout evt-row data collection (pre-existing open bug)
