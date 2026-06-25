# Phase 3 design — Companion sync → Health Connect

**Date:** 2026-06-25
**Repo:** `~/AndroidStudioProjects/RunH10` (branch `master`, local-only) — baseline HEAD `82cc9b0`
**Status:** approved design, ready for implementation planning
**Source of truth:** `~/Documents/Claude/Projects/WatchApp/PLAN.md` §"Phase 3" + §"Phase 0" (API pins). This
document refines that plan with the open design decisions resolved; it does not override the API pins or
anti-pattern guards there.

---

## Goal

Open the phone app → it auto-pulls finalized runs from the watch over the Wearable Data Layer → parses
each NDJSON file → writes it idempotently into Health Connect (route, HR, speed, distance, calories,
elevation, RMSSD/HRV) → ACKs the watch → the watch purges the `.ndjson` but keeps its `SessionMeta` stub
(state `SYNCED`, still visible in watch history).

## Scope boundary

- **In scope:** the phone↔watch transport, the NDJSON parser, RMSSD computation, the idempotent Health
  Connect writer, a minimal "sync console" phone UI, and the Health Connect permission flow.
- **Out of scope (Phase 4):** session list/detail screens, route maps, HR graphs, GPX/TCX/CSV export, a
  persistent retry queue with per-session failed-state affordances, and background/in-range sync.

## Resolved design decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Phone UI scope | **Bare-bones sync console** — HC readiness/permission state, a "Sync now" button, a live status log. Phase 4 replaces it. |
| 2 | Sync trigger | **Auto-sync on app foreground** (guarded against double-run; only when HC ready + permitted) **plus** a manual "Sync now" retry button. |
| 3 | RMSSD windowing | **30s non-overlapping windows**; one instantaneous HRV record per surviving window, timestamped at window end. **Skip any window with <20 valid RR intervals.** |
| 4 | Partial-failure | **All-or-nothing per session**; ACK only on full HC write success. The watch `.ndjson` is the durable retry queue (safe because writes are idempotent on `clientRecordId`). |
| 5 | Permissions UX | **Gate sync on permission state**; **one-tap grant** of all WRITE permissions via a single `PermissionController` request contract. |

---

## Architecture

Three units, matching PLAN.md Phase 3 Tasks 1–3.

### Task 1 — `:mobile` converted to Compose (the sync console)

- Replace the generated Views stub (`activity_main.xml` + layout-based `MainActivity`) with Compose:
  add Compose deps + `kotlin.plugin.compose` to `:mobile`, delete `activity_main.xml`, rewrite
  `MainActivity` as `ComponentActivity` + `setContent {}`. Wire `implementation(project(":shared"))`.
- **UI: a single sync-console screen** driven by a `SyncViewModel` exposing one `StateFlow<SyncUiState>`:
  - **HC readiness banner** — reflects `getSdkStatus`: available / not-installed (with install link) /
    update-required.
  - **Permission state** — if not all granted, a single **"Grant Health Connect access"** button that
    fires the one-shot all-permissions request contract.
  - **"Sync now" button** — enabled only when SDK-available **and** all permissions granted.
  - **Status log** — an append-only list of human-readable lines, e.g. `Found 4 unsynced runs`,
    `Pulling run 2 of 4…`, `✓ run_794351a1 written to Health Connect`, `✗ run_… failed: <reason>`.
- **Auto-sync:** on `ON_RESUME`, if SDK-available + all-permissions-granted + no sync currently running,
  kick off a sync. A `syncInProgress` flag (in the ViewModel) prevents a re-foreground from double-firing.

### Task 2 — Data Layer transport (both sides)

Reuses the file-transfer pattern pinned in PLAN.md §Phase 0 and the existing `SessionStore` on the watch.

**Watch — `wear/.../sync/WearSyncService : WearableListenerService`:**
- Manifest intent-filter: actions `MESSAGE_RECEIVED` + `CHANNEL_EVENT`,
  `<data scheme="wear" host="*" pathPrefix="/runh10">`. Capability `runh10_watch` in
  `wear/res/values/wear.xml`.
- `onMessageReceived` `/runh10/request_unsynced` → reply via `sendMessage` to the source node with the
  list of FINALIZED session ids + sizes (from `SessionStore.getUnsynced()`).
- `onMessageReceived` `/runh10/start_transfer/<id>` → `openChannel(phoneNodeId, "/runh10/session/<id>")`
  then `sendFile(channel, Uri.fromFile(SessionStore.fileFor(id)))`; mark session `SYNCING`.
- `onMessageReceived` `/runh10/ack/<id>` → `SessionStore.markSynced(id)` + `purgeFile(id)` (the
  `SessionMeta` row remains; only the `.ndjson` is deleted).

**Phone — `mobile/.../sync/PhoneSyncClient.kt` + `mobile/.../sync/SyncListenerService :
WearableListenerService`:**
- Capability `runh10_phone` in `mobile/res/values/wear.xml`.
- Find the watch node via `CapabilityClient.getCapability("runh10_watch", FILTER_REACHABLE)` (prefer
  `isNearby`). Send `/runh10/request_unsynced`; receive the list.
- For each session **oldest first**: send `/runh10/start_transfer/<id>`; the watch opens the channel →
  phone's `onChannelOpened(channel)` → `receiveFile(channel, destFileUri, append=false)`; wait for
  `onInputClosed`. Then parse + write to HC (Task 3). On full success, send `/runh10/ack/<id>`. On any
  failure, do **not** ACK (the run stays on the watch, retried next sync), log the reason, and continue to
  the next session — one bad run does not block the others.

### Task 3 — Parser + RMSSD + HealthConnectWriter

**`mobile/.../parse/SessionParser.kt`** — reuse `:shared` `NdjsonSerializer`; produce a `SessionBundle`
(ordered samples + start/end/zone, from the `SessionMeta` the watch sends alongside the file). Tolerate gaps
and a **truncated final line** (files are crash-resilient and may end mid-write).

> **Splits deferred to Phase 4 (refinement, 2026-06-25):** splits are computed in-memory on the watch
> (`SplitTracker`) and are **not** written to the NDJSON, and moving-time is not recorded in the file.
> Nothing in Phase 3 consumes splits — the Health Connect write uses route/HR/distance/speed directly, and
> the bare sync console shows no splits. So the Phase 3 parser sets `SessionBundle.splits = emptyList()`;
> re-deriving splits from samples is implemented in Phase 4 where the session-detail UI displays them.

> **Wire transport of `SessionMeta` (refinement, 2026-06-25):** the `.ndjson` file holds only sample rows,
> not the `SessionMeta` (which lives in the watch's Room store). The watch therefore sends the serialized
> `SessionMeta` per session in its `request_unsynced` reply, so the phone has the sessionId, zone, and
> start/end needed for Health Connect's zone offsets. `SessionMeta`/`SessionState` become `@Serializable`
> in `:shared`, and a shared `SyncProtocol` holds the path/capability constants + the meta-list codec so
> both sides share one wire definition.

**`mobile/.../healthconnect/RmssdCalculator.kt`:**
- Input: the ordered `RrRow` list from the bundle.
- Filter implausible RR before computing: drop intervals `<300 ms` or `>2000 ms`.
- Partition the run timeline into **30s non-overlapping windows**.
- For each window with **≥20 valid RR intervals**, compute `rmssd = sqrt(mean(diff(rr)^2))` over the
  successive differences; emit one value timestamped at the **window end**. Windows with fewer than 20
  valid intervals are **skipped** (no record emitted) rather than producing a garbage value across a gap.
- **Unit-tested** against a hand-computed fixture (known RR sequence → known RMSSD, plus a sparse-window
  case that asserts the window is skipped).

**`mobile/.../healthconnect/HealthConnectWriter.kt`:**
- Gate on `HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE`; obtain
  `HealthConnectClient.getOrCreate(context)`.
- **Every** record carries
  `metadata = Metadata.activelyRecorded(clientRecordId = sessionId, clientRecordVersion = 1L,
  device = Device(type = Device.TYPE_WATCH))`. (Do NOT use the bare `Metadata(clientRecordId=…)`
  constructor — internal in 1.1.0.)
- Records written per session: `ExerciseSessionRecord` (RUNNING, start/end + zone offsets, title,
  `exerciseRoute` built from `loc` samples), `HeartRateRecord`, `SpeedRecord`, `DistanceRecord` (total),
  `ActiveCaloriesBurnedRecord`, `ElevationGainedRecord`, and one `HeartRateVariabilityRmssdRecord` per
  surviving RMSSD window. The route is `exerciseRoute` on the session — **not** separate location records.
- `insertRecords(allRecords)` as one logical unit (chunk ≤1000 if needed). **All-or-nothing:** any
  exception aborts the session's write and skips the ACK. Re-sync upserts on the same `clientRecordId`, so
  retrying after a partially-applied chunk is safe.

**Permissions (`mobile/AndroidManifest.xml` + supporting classes):**
- One `<uses-permission android:name="android.permission.health.WRITE_*">` per record type, plus the route
  permission constant `HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE`.
- `ViewPermissionUsageActivity` activity-alias (privacy-policy rationale, API 34+) +
  `mobile/.../PermissionsRationaleActivity.kt`.
- `<queries><package android:name="com.google.android.apps.healthdata"/></queries>`.
- **One-tap grant:** request the full permission set in a single
  `PermissionController.createRequestPermissionResultContract()` launch.

---

## Data flow (one sync pass)

```
phone foreground / "Sync now"
  └─ check SDK available + all permissions granted    (else show banner / grant button; stop)
  └─ PhoneSyncClient: find runh10_watch node (isNearby)
  └─ send /runh10/request_unsynced ──────────────▶ watch replies: [(id,size), …] FINALIZED
  └─ for each session, OLDEST FIRST:
       └─ send /runh10/start_transfer/<id> ───────▶ watch openChannel + sendFile; marks SYNCING
       └─ onChannelOpened → receiveFile(append=false) → onInputClosed
       └─ SessionParser → SessionBundle
       └─ RmssdCalculator → HRV windows
       └─ HealthConnectWriter.insertRecords(allRecords)   (all-or-nothing)
       └─ on full success: send /runh10/ack/<id> ──▶ watch markSynced + purgeFile (keeps stub)
       └─ on failure: log reason, no ACK, continue to next session
```

## Error handling summary

- **HC not available / not permitted:** sync is gated off; the console shows the reason and the remedy
  (install link / grant button). Auto-sync does not fire into a permission wall.
- **No watch reachable:** log "watch not reachable"; nothing transferred; user can retry.
- **Transfer fails mid-file:** no parse, no write, no ACK; file stays on watch; retried next sync.
- **Parse/RMSSD/HC write fails:** no ACK; file stays on watch; reason logged; other sessions still
  process. Idempotency makes the eventual retry safe.
- **Re-sync of an already-written run:** upserts on `clientRecordId` — no duplicates in Health Connect.

## Testing

- **Unit:** `RmssdCalculator` against a hand-computed fixture (known RMSSD; sparse-window skip).
- **Unit:** `SessionParser` tolerates a truncated final line and out-of-order/gappy samples (reuse real
  captured sessions, e.g. `run_794351a1` / `64c36afa`, as fixtures where practical).
- **On-device (per PLAN.md Phase 3 checklist):**
  - Both modules build; phone app launches in Compose.
  - With watch + phone connected, phone lists FINALIZED sessions and transfers oldest-first.
  - Run appears in Google Health / Health Connect with route, HR series, distance, pace, calories, and an
    RMSSD/HRV series.
  - **Re-sync the same session does NOT duplicate** (upsert verified).
  - Multiple queued runs all sync; each ACK purges the `.ndjson` on the watch but the `SessionMeta` row
    remains (state `SYNCED`) and shows in watch history.

## Reuse / anti-pattern guards (from PLAN.md)

- Reuse `:shared` (`SampleRow`, `SessionBundle`, `Split`, `SessionMeta`, `NdjsonSerializer`) on the phone
  — **never redefine the NDJSON schema in `:mobile`**.
- Health Connect is **phone-only** (`androidx.health.connect:connect-client` 1.1.0); never add it to
  `:wear`.
- Do NOT use the `Metadata(clientRecordId=…)` constructor (internal in 1.1.0).
- Do NOT attach the route as separate location records — it is `exerciseRoute` on the session.
- Do NOT purge a session before the phone ACK (purge only after a confirmed HC write).
- Do NOT assume the file is complete — the parser must tolerate a truncated last line.

## Hard prerequisite

A physical Android phone (available) with **Health Connect installed**, connected to adb, and **paired to
the Pixel Watch 3** for the Data Layer transport. Must be set up before Task 2 can be tested.
