# Reliability adversarial review — code-level findings

Date: 2026-07-10
Baseline: `master` @ 245d764
Scope: `shared/`, `wear/src/main`, `mobile/src/main`
Method: each scenario below was traced against the actual code path. A finding is only
recorded where a concrete failure sequence is reachable from real code (file:line cited).
GPS cutout (~46s) is explicitly out of scope — handled separately with on-device data.

Severity key: **P1** = data loss / crash / lying data. **P2** = degraded-but-honest.
**P3** = hardening nice-to-have.

## Summary

| # | Scenario | Verdict | Severity |
|---|----------|---------|----------|
| 1 | BLE drop during pause/lock | CLEARED (1 caveat → F3) | — |
| 2 | Process death mid-run + FGS restart (both apps) | FINDINGS | F1 **P1**, F4 **P2** |
| 3 | Partial NDJSON line on kill | CLEARED | — |
| 4 | Location / BT permission revoked mid-run | FINDING (reduces to F1) | **P1** |
| 5 | Storage-full write path | FINDING | F2 **P1** |
| 6 | Data Layer sync interruption + resume idempotency | CLEARED | — |
| 7 | Phone reboot with unsynced sessions | CLEARED (1 caveat → F1) | — |
| 8 | Media relay when notification access revoked mid-run | CLEARED (1 caveat → F7) | — |
| — | Cross-cutting | FINDINGS | F5 **P2**, F6 **P3**, F7 **P3**, F8 **P3** |

Finding tally: **2 P1** (F1, F2), **3 P2** (F3, F4, F5), **3 P3** (F6, F7, F8), **3 scenarios fully cleared** (3, 6, and — modulo the in-progress-run caveat — 7; scenario 1 and 8 clear except for the cross-cutting caveats F3/F7).

---

## Scenario 1 — BLE drop during pause/lock

**Verdict: CLEARED** (data path robust; one live-display caveat, F3).

Trace: On a GATT drop the callback lands `STATE_DISCONNECTED`, closes the gatt, and calls
`scheduleReconnect()` (`shared/.../ble/HeartRateBleClient.kt:188-193`). `scheduleReconnect`
only aborts when `wantConnected` is false (`:159`), and `wantConnected` is cleared **only** in
`disconnect()` (`:134-142`). Neither pause path calls `disconnect()`:
`PhoneRecordController.manualPause()`/auto-pause only touch the state machine and clock
(`mobile/.../record/PhoneRecordController.kt:355`, `:322-323`); watch pause is the same
(`wear/.../workout/WorkoutController.kt:355-362`, `:173-176`). So the capped backoff
(1→2→4→8→10 s, `autoConnect=true`, `:162-166`) keeps retrying through a pause and through a
screen lock — the FGS wake lock keeps the process alive so GATT callbacks continue
(`wear/.../service/WorkoutForegroundService.kt:169-174`, `mobile/.../record/RecordForegroundService.kt:65-70`).

During the outage no HR rows are written (writers are driven by `ble.hr` emissions only), so the
saved file honestly shows a gap rather than fabricated samples. The `_hr` SharedFlow does not
synthesize values on reconnect (`HeartRateBleClient.kt:66-71`). **Robust.**

Caveat carried to **F3**: the *live UI* keeps displaying the last bpm during the outage.

---

## Scenario 2 — Process death mid-run + FGS restart (both apps)

### F1 — Phone: process death mid-run loses the entire in-progress run — **P1**

Concrete sequence:
1. Phone run in progress. Samples are written via `PhoneRecordController.writeRow()`, which does
   `w.write(...); w.newLine()` with **no `flush()`** (`mobile/.../record/PhoneRecordController.kt:422-427`).
   The `BufferedWriter` (`:266`) holds up to ~8 KB of un-flushed rows.
2. Process is killed (low memory / permission revoke / reboot). Everything in the
   `PhoneRecordController` singleton is lost, including `meta` (the only copy of the session's
   `SessionMeta` — it lives in memory at `:120/:265`, never persisted).
3. On restart, `RecordForegroundService` is `START_STICKY` (`RecordForegroundService.kt:40`) and is
   re-created with a **null** intent, so `when (intent?.action)` matches no branch (`:28-39`) —
   `startAsForeground()` is never re-called and no recording is re-established.
4. There is **no orphan recovery on the phone**: `grep` for `recoverOrphans`/`SessionState.RECORDING`
   in `mobile/src/main` finds only the write site — nothing scans `filesDir/sessions` for stranded
   files. `RunRepository` has no recovery entry point (`mobile/.../data/RunRepository.kt` whole file).

Result: the orphan `<id>.ndjson` sits on disk forever, un-ingested and unreferenced, and the
un-flushed tail is gone outright. The whole in-progress run is unrecoverable — a data-loss and a
silent one.

Contrast (why this is phone-specific): the **watch** flushes every line
(`wear/.../session/SessionRecorder.kt:72-75`) **and** finalizes stranded `RECORDING` rows on next
launch (`wear/.../session/SessionStore.kt:49-59`, invoked at
`wear/.../workout/WorkoutController.kt:117`). The watch persists `SessionMeta` to Room at session
create (`SessionStore.kt:22-33`), so recovery has something to finalize. The phone has neither the
per-line flush nor a persisted meta nor a recovery scan.

### F4 — Watch: process death silently ends the run; the "defensive re-assert" is dead code — **P2**

`WorkoutForegroundService.onStartCommand` comments that `START_STICKY` redelivery "can bring
ACTION_START without a preceding ACTION_CONNECT" and re-asserts foreground + `beginRun()` on that
path (`wear/.../service/WorkoutForegroundService.kt:70-84`). This is factually wrong: `START_STICKY`
redelivers a **null** intent, not the original action. So after a kill the branch never runs, the
service comes back with no notification and no exercise session, and the run does not resume.

Severity is P2 (not P1) because the watch's per-line flush + `recoverOrphans()` (see F1 contrast)
preserve and finalize all data written up to the kill — the run ends early and honestly rather than
losing data. Fix candidates: use `START_REDELIVER_INTENT`, or persist a "run active" flag and
resume from it, and correct the comment.

Note (benign): under the normal flow the service calls `startAsForeground()` twice per run
(ACTION_CONNECT `:64`, then ACTION_START `:77`) — see **F6**.

---

## Scenario 3 — Partial NDJSON line on kill

**Verdict: CLEARED.**

Every reader goes through `NdjsonSerializer.decodeTolerant`, which trims blanks and
`runCatching`-drops any line that fails to parse (`shared/.../serial/NdjsonSerializer.kt:17-22`).
`SessionParser.parse` uses it (`mobile/.../parse/SessionParser.kt:9-12`), as does the watch orphan
scan when it reads the last timestamp (`SessionStore.kt:53-55` uses a hand-rolled regex to extract
`"ts"` and falls back to `startEpochMs`). A crash-truncated final line is therefore skipped, not fatal.
The watch flushes per line so at most the tail line is torn (`SessionRecorder.kt:74`). **Robust.**
(The phone's *whole* un-flushed tail loss is a different defect — F1 — not a partial-line-parsing problem.)

---

## Scenario 4 — Location / BT permission revoked mid-run

**Verdict: FINDING — reduces to F1 (P1) on the phone; safe on the watch.**

Revoking a held runtime permission (`ACCESS_FINE_LOCATION`, `BLUETOOTH_CONNECT`) via Settings forces
an Android process kill; a `location`-typed FGS additionally becomes non-startable. Both manifests
declare and rely on these (`mobile/.../AndroidManifest.xml:15-24`, `.../record/RecordForegroundService.kt:57-62`
type LOCATION|CONNECTED_DEVICE; `wear/.../AndroidManifest.xml:11-21`, service type `health|location`).
So this scenario collapses to a mid-run process death:

- **Phone:** unrecoverable in-progress run — exactly **F1 (P1)**.
- **Watch:** data written so far is flushed + finalized on next launch (F1 contrast), so no data loss;
  the run ends (F4).

No code path fabricates data after a revoke: writers are emission-driven and simply stop.

---

## Scenario 5 — Storage-full write path

### F2 — Disk-full during a write crashes the app mid-run on both sides — **P1**

Neither writer guards the I/O:

- Watch: `SessionRecorder.writeLine` does `w.write(...); w.newLine(); w.flush()` with no try/catch
  (`wear/.../session/SessionRecorder.kt:72-75`). It runs inside a `scope.launch` collector
  (`:37-42`) on `WorkoutController.scope`, which is a bare `SupervisorJob + Dispatchers.Main.immediate`
  with **no `CoroutineExceptionHandler`** (`WorkoutController.kt:48`). An `IOException` (ENOSPC) from
  `flush()` propagates to the thread's default handler → process crash mid-run.
- Phone: `PhoneRecordController.writeRow` is `@Synchronized w.write(...); w.newLine()` with no
  try/catch (`mobile/.../record/PhoneRecordController.kt:422-427`). It is called both from the HR
  collector coroutine (`:284-294`) **and directly from `locationCallback.onLocationResult` on the
  main looper** (`:223-232`). A disk-full throw on the location path is an uncaught exception on the
  main thread → immediate crash. And per F1, the crash then loses the in-progress run.

This violates "never dies mid-run." A one-line `runCatching` around each write (degrading to an
honest gap) is the obvious fix; it is a good Plan-2 candidate but is not currently test-covered, so
it should not be treated as a trivial in-line fix.

---

## Scenario 6 — Data Layer sync interruption + resume idempotency

**Verdict: CLEARED — genuinely idempotent across interruption.**

Pull protocol: phone requests list → watch replies → per session: START_TRANSFER → watch
`markSyncing` + `sendFile` → phone `receiveFile` → parse → HC `write` → `ingest` → ACK → watch
`markSynced` + `purgeFile` (`mobile/.../sync/PhoneSyncClient.kt:71-98`,
`wear/.../sync/WearSyncService.kt:28-50`).

Interruption analysis:
- Interrupted after `markSyncing` but before ACK → the session is left in `SYNCING` on the watch, and
  `getUnsynced()` **includes** `SYNCING` (`wear/.../session/SessionDao.kt:16-17`
  `state IN ('FINALIZED','SYNCING')`), so it is re-offered on the next pass rather than stranded.
- Failed transfer/parse/HC-write → `syncOne` deletes the partial dest file and rethrows
  (`PhoneSyncClient.kt:90-92`), and per-session failures are isolated (`:56-66`) — one bad run does
  not abort the batch, and the outer `CancellationException` is rethrown first so a real cancel is not
  swallowed.
- ACK send failing after a successful HC write → counted as failed, but the watch keeps the session
  and re-offers it; the re-pull is safe because HC writes upsert on `clientRecordId = sessionId`
  (and `..._hrv_$index`, with skipped windows keeping `index` stable — `HealthConnectWriter.kt:49-53,
  138-146, 149-152`), and `RunRepository.ingest` preserves user-edited name/feel on re-ingest
  (`RunRepository.kt:62-73`).

HC-window hardening is already present (widen `[start,end]` to contain all samples `:44-46`; drop
bpm∉1..300 `:57-58`; drop RMSSD∉1..200 `:139-146`) so a single bad sample cannot fail — and thus
cannot infinitely re-fail — a session. **Robust.**

---

## Scenario 7 — Phone reboot with unsynced sessions

**Verdict: CLEARED** (caveat: an in-progress *phone* run at reboot = F1).

Unsynced *watch* sessions live on the watch's Room + files until ACKed
(`wear/.../session/SessionStore.kt`, purge only on ACK `WearSyncService.kt:44-48`); a phone reboot
does not touch them. Sync is triggered from `SyncViewModel.onResume()` (auto-syncs when Health Connect is
ready and idle, `mobile/.../ui/SyncViewModel.kt`) as well as manually, and `findWatchNode` re-resolves
the node each pass (`PhoneSyncClient.kt:100-106`), so after reboot the next sync attempt simply re-pulls.
Already-ingested phone runs persist in Room + `filesDir/sessions` (`RunRepository`), surviving reboot.

The only reboot-time loss is an **in-progress phone-recorded run** (in-memory singleton, un-flushed
buffer, no recovery) — already captured as **F1 (P1)**.

---

## Scenario 8 — Media relay when notification access revoked mid-run

**Verdict: CLEARED for run integrity** (caveat F7 on the media UX).

The media bridge is fully decoupled from recording — separate listener/command services
(`mobile/.../media/*`), no shared state with `PhoneRecordController`. Revoking notification access
does not touch the BLE/GPS record loop, so the run continues.

Revocation is handled without crashing: `MediaNotificationListener.onListenerDisconnected()` removes
the session-changed listener and clears the watched controller
(`mobile/.../media/MediaNotificationListener.kt:55-62`), and every `MediaSessionManager` access is
wrapped so a post-revoke `SecurityException` yields `null` rather than a throw
(`mobile/.../media/MediaRelay.kt:29-35`, listener path `MediaNotificationListener.kt:44-52`). A
post-revoke `broadcastState` sends an empty `MediaState` (`MediaRelay.kt:37-38`) — honest "nothing
playing". **No crash, no fake media data.** See F7 for the UX gap.

---

## Cross-cutting findings

### F3 — Live UI shows stale HR during a BLE outage with no staleness indicator — **P2**

Failure sequence: strap drops → reconnect backoff runs for tens of seconds
(`HeartRateBleClient.kt:162-166`, capped 1→2→4→8→10 s) → `_hr` SharedFlow keeps replaying the last
`HrSample` via `replay=1` (`shared/.../ble/HeartRateBleClient.kt:66-71`) → UI renders `ui.bpm`
unconditionally without a staleness indicator (`wear/.../presentation/RunExperience.kt:149`), displaying
a frozen bpm as if still live. The phone live UI via `PhoneRecordController`'s hr collector has the same
exposure.

The saved session file is unaffected — it honestly shows a gap rather than stale samples — so this is
display-only, not data loss.

Fix shape: emit a staleness marker (e.g., a timestamp on the UI's bpm source) and dim/dash the display
after N seconds without a fresh sample, or emit a sentinel in the `_hr` flow (e.g., `null` or a marker)
and render an explicit "stale" indicator when the live value ages past reconnect-backoff timeouts.

### F5 — Phone-recorded HC write is best-effort with no retry; a mid-batch failure silently leaves partial data — **P2**

`saveRun` wraps the Health Connect write in `runCatching { ... }` and discards any failure
(`mobile/.../record/PhoneRecordController.kt:393-396`), while `HealthConnectWriter.write` inserts in
chunks (`HealthConnectWriter.kt:149-152`). If chunk 1 inserts and chunk 2 throws (or permissions are
absent), the run is still ingested to Room (`:397-404`) but Health Connect ends up with partial or no
records and there is **no retry trigger anywhere** — unlike the watch-sync path (Scenario 6), a
phone-recorded run is never re-offered to HC. Data is not lost (Room has it) so this is degraded, not
P1, but it is silent. Because `clientRecordId` upsert already makes a re-write safe, a "re-push to HC"
action or a pending-HC queue would close it.

### F6 — Watch FGS calls `startAsForeground()` twice per run — **P3**

Normal flow runs ACTION_CONNECT then ACTION_START, each calling `startAsForeground()`
(`WorkoutForegroundService.kt:64` and `:77`). Each call rebuilds the notification, re-creates the
`OngoingActivity`, and restarts the status-update collector (`startStatusUpdates()` cancels the prior
job `:143-145`). Behaviorally benign (`startForeground` is idempotent; the old job is cancelled) but
wasteful and a source of confusion. Collapsing to a single foreground assertion would be cleaner.

### F7 — Media control silently no-ops after notification access revoked; the watch gets no explicit "unavailable" signal — **P3**

After revocation, watch transport/volume commands reach `MediaRelay.apply`, whose
`activeController(context)` returns `null` (SecurityException caught, `MediaRelay.kt:29-35, 73-90`), so
the command does nothing and the echo broadcast (`MediaCommandListenerService.kt:23-27`) sends an
empty state. The watch never receives an explicit "media control unavailable — re-grant notification
access" signal, only a blank now-playing card. Honest but opaque; a typed status would improve the UX.

### F8 — HR rows are still written while paused on both apps — **P3 (design note, likely acceptable)**

During pause the location paths correctly freeze distance/route (phone `PhoneRecordController.kt:210-213`;
watch gates run-logic on `running`), but the HR collectors write `HrRow`/`RrRow` unconditionally
(phone `:284-294`, watch `SessionRecorder.kt:37-42`). Heart rate during a pause is arguably real data
(not fabricated), so this is probably fine and is consistent across both apps — flagged only so the
"a paused run must not grow" invariant is reviewed deliberately rather than by accident. If pause HR
should be excluded from the HC HeartRateRecord, that decision needs to be explicit.
