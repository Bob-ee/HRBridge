# Perfection Pass — Plan 2: Reliability Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the verified reliability findings F1–F6 from `docs/superpowers/plans/notes/2026-07-10-reliability-findings.md` so neither app can crash mid-run on I/O failure, and a phone run survives process death.

**Architecture:** A new shared `SafeLineWriter` guards all session-file I/O (both apps). The phone gains what the watch already has: a persisted session meta (JSON sidecar) plus an orphan-recovery scan at startup, built around a pure, unit-testable `OrphanScanner`. The watch service gets honest post-kill semantics. Phone-recorded runs get a Health Connect re-push flag in Room.

**Tech Stack:** Kotlin, kotlinx.serialization (SessionMeta is already `@Serializable`), Room (schema bump), JUnit4 plain unit tests.

## Global Constraints

- Repo `/Users/bobbywhiteley/AndroidStudioProjects/RunH10`, branch `master`. `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before any `./gradlew`.
- Test commands: `./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest` (focused: add `--tests <Class>`).
- Commits authored via `git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "..."` — no AI attribution of any kind. Never stage the dirty `.idea/` files.
- Core product invariant: NEVER fake data. Failure → honest gap/null, never synthesized samples. A recording failure must not kill the run.
- Decision on finding F8 (HR rows written while paused): KEEP — pause HR is real measured data, consistent on both apps. Do not "fix" F8. F7 (media-unavailable UX) is deferred to a later plan.
- Findings doc with all file:line evidence: `docs/superpowers/plans/notes/2026-07-10-reliability-findings.md` — implementers should read their finding's section.

---

### Task 1: SafeLineWriter — guarded session-file I/O in both recorders (F2)

**Files:**
- Create: `shared/src/main/java/com/example/runh10/shared/serial/SafeLineWriter.kt`
- Test: `shared/src/test/java/com/example/runh10/shared/serial/SafeLineWriterTest.kt`
- Modify: `wear/src/main/java/com/example/runh10/session/SessionRecorder.kt` (writeLine, ~72-75)
- Modify: `mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt` (writer field ~121, beginRun ~266, finishRun ~378, saveRun ~385, discardRun ~412, writeRow ~422-427)

**Interfaces:**
- Produces: `class SafeLineWriter(private val out: java.io.Writer)` with:
  - `@Synchronized fun writeLine(line: String): Boolean` — writes line + newline + flush; returns false (and sets `failed`) on IOException; once failed, all subsequent calls no-op returning false.
  - `val failed: Boolean` (@Volatile, public read)
  - `@Synchronized fun close()` — flush+close, swallowing IOException.
- Task 2 consumes: `PhoneRecordController.writer` becomes `SafeLineWriter?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.example.runh10.shared.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.StringWriter
import java.io.Writer

class SafeLineWriterTest {
    @Test fun writesLinesWithNewlines() {
        val sink = StringWriter()
        val w = SafeLineWriter(sink)
        assertTrue(w.writeLine("""{"t":"hr","bpm":80}"""))
        assertTrue(w.writeLine("""{"t":"hr","bpm":81}"""))
        assertEquals("{\"t\":\"hr\",\"bpm\":80}\n{\"t\":\"hr\",\"bpm\":81}\n", sink.toString())
        assertFalse(w.failed)
    }

    @Test fun failsClosedAfterIoException() {
        var throwAt = 2
        var writes = 0
        val sink = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                writes++
                if (writes >= throwAt) throw IOException("ENOSPC")
            }
            override fun flush() {}
            override fun close() {}
        }
        val w = SafeLineWriter(sink)
        assertTrue(w.writeLine("row1"))          // write #1 ok
        assertFalse(w.writeLine("row2"))         // throws -> false
        assertTrue(w.failed)
        throwAt = Int.MAX_VALUE                   // sink healthy again...
        assertFalse(w.writeLine("row3"))         // ...but writer stays failed (no zombie writes)
    }

    @Test fun closeSwallowsIoException() {
        val sink = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {}
            override fun flush() { throw IOException("boom") }
            override fun close() { throw IOException("boom") }
        }
        SafeLineWriter(sink).close() // must not throw
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :shared:test --tests SafeLineWriterTest`
Expected: compilation FAILURE (SafeLineWriter not defined).

- [ ] **Step 3: Implement**

```kotlin
package com.example.runh10.shared.serial

import java.io.IOException
import java.io.Writer

/**
 * Guards session-file writes: one IOException (disk full, revoked storage) permanently
 * fails the writer instead of crashing the process. The recording degrades to an honest
 * gap — a recording failure must never kill a live run. Flushes per line so a process
 * kill loses at most the torn tail line.
 */
class SafeLineWriter(private val out: Writer) {
    @Volatile var failed: Boolean = false
        private set

    @Synchronized
    fun writeLine(line: String): Boolean {
        if (failed) return false
        return try {
            out.write(line); out.write("\n"); out.flush()
            true
        } catch (e: IOException) {
            failed = true
            runCatching { out.close() }
            false
        }
    }

    @Synchronized
    fun close() {
        runCatching { out.flush() }
        runCatching { out.close() }
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :shared:test --tests SafeLineWriterTest`
Expected: PASS, 3/3.

- [ ] **Step 5: Adopt in the watch recorder**

In `SessionRecorder.kt`: change `private var writer: BufferedWriter?` to `private var writer: SafeLineWriter?`; in `start()` change `writer = store.fileFor(m.sessionId).bufferedWriter()` to `writer = SafeLineWriter(store.fileFor(m.sessionId).bufferedWriter())`; replace `writeLine`:

```kotlin
private fun writeLine(row: SampleRow) {
    writer?.writeLine(NdjsonSerializer.encode(row))
}
```

(drop the `@Synchronized` here — SafeLineWriter synchronizes internally) and in `stop()` replace `writer?.flush(); writer?.close(); writer = null` with `writer?.close(); writer = null`. Remove the now-unused `java.io.BufferedWriter` import.

- [ ] **Step 6: Adopt in the phone controller**

In `PhoneRecordController.kt`: field `private var writer: SafeLineWriter?`; `beginRun` sets `writer = SafeLineWriter(repo.fileFor(m.sessionId).bufferedWriter())`; `writeRow` becomes (keep building `pendingBundleRows` FIRST — the in-memory bundle must stay complete even when the file writer has failed):

```kotlin
private fun writeRow(row: com.example.runh10.shared.model.SampleRow) {
    pendingBundleRows += row
    writer?.writeLine(NdjsonSerializer.encode(row))
}
```

`finishRun`: delete the `writer?.flush()` line (SafeLineWriter flushes per line). `saveRun`: replace `writer?.flush(); writer?.close(); writer = null` with `writer?.close(); writer = null`. `discardRun`: replace `writer?.close()` with `writer?.close()` unchanged semantics (it is now SafeLineWriter.close). Remove the unused `java.io.BufferedWriter` import. Note `@Synchronized` on writeRow must be KEPT (it also guards `pendingBundleRows`, which is appended from two coroutine contexts).

- [ ] **Step 7: Full test + build**

Run: `./gradlew :shared:test :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 8: Commit**

```bash
git add shared/src/main/java/com/example/runh10/shared/serial/SafeLineWriter.kt shared/src/test/java/com/example/runh10/shared/serial/SafeLineWriterTest.kt wear/src/main/java/com/example/runh10/session/SessionRecorder.kt mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "fix: guard session-file writes so disk-full degrades to an honest gap instead of crashing mid-run"
```

### Task 2: Phone run durability — meta sidecar + orphan recovery (F1)

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/data/OrphanScanner.kt`
- Test: `mobile/src/test/java/com/example/runh10/data/OrphanScannerTest.kt`
- Modify: `mobile/src/main/java/com/example/runh10/data/RunRepository.kt` (add metaFileFor, recoverOrphans, delete cleanup)
- Modify: `mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt` (beginRun writes sidecar; saveRun/discardRun delete it)
- Modify: `mobile/src/main/java/com/example/runh10/ui/SyncViewModel.kt` (invoke recovery once at startup — read the file; it has an onResume/init entry point)

**Interfaces:**
- Consumes: Task 1's SafeLineWriter (already in PhoneRecordController).
- Produces:
  - `RunRepository.metaFileFor(id: String): File` → `File(dir, "$id.meta.json")`
  - `suspend fun RunRepository.recoverOrphans(): Int` — returns number recovered.
  - `object OrphanScanner { fun findOrphans(sessionFiles: List<File>, knownIds: Set<String>): List<File> }` — pure: returns `.meta.json` files whose sessionId (basename minus `.meta.json`) is not in knownIds AND whose sibling `.ndjson` exists.

- [ ] **Step 1: Write the failing OrphanScanner tests**

```kotlin
package com.example.runh10.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OrphanScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(name: String) = tmp.newFile(name)

    @Test fun findsMetaWithSiblingNdjsonNotInRoom() {
        touch("aaa.meta.json"); touch("aaa.ndjson")
        touch("bbb.meta.json"); touch("bbb.ndjson")
        val orphans = OrphanScanner.findOrphans(tmp.root.listFiles()!!.toList(), knownIds = setOf("bbb"))
        assertEquals(listOf("aaa.meta.json"), orphans.map { it.name })
    }

    @Test fun ignoresMetaWithoutNdjsonAndNdjsonWithoutMeta() {
        touch("lonely.meta.json")            // no data file: nothing to recover
        touch("synced.ndjson")               // no sidecar: a normal synced session
        val orphans = OrphanScanner.findOrphans(tmp.root.listFiles()!!.toList(), knownIds = emptySet())
        assertEquals(emptyList<String>(), orphans.map { it.name })
    }

    @Test fun emptyDirYieldsNothing() {
        assertEquals(0, OrphanScanner.findOrphans(emptyList(), emptySet()).size)
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :mobile:testDebugUnitTest --tests OrphanScannerTest` → compilation FAILURE.

- [ ] **Step 3: Implement OrphanScanner**

```kotlin
package com.example.runh10.data

import java.io.File

/**
 * Pure orphan detection: a session is orphaned when its meta sidecar exists (written at
 * record start, deleted on save/discard) but Room never got a summary row — i.e. the
 * process died mid-run. Sidecar-less .ndjson files are normal synced sessions.
 */
object OrphanScanner {
    private const val META_SUFFIX = ".meta.json"

    fun findOrphans(sessionFiles: List<File>, knownIds: Set<String>): List<File> {
        val byName = sessionFiles.associateBy { it.name }
        return sessionFiles
            .filter { it.name.endsWith(META_SUFFIX) }
            .filter { meta ->
                val id = meta.name.removeSuffix(META_SUFFIX)
                id !in knownIds && byName.containsKey("$id.ndjson")
            }
            .sortedBy { it.name }
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command → PASS 3/3.

- [ ] **Step 5: Sidecar lifecycle in PhoneRecordController**

`beginRun`, right after `writer = SafeLineWriter(...)`:

```kotlin
// Persist meta NOW so a mid-run process death leaves a recoverable session
// (the watch has had this from day one; see reliability finding F1).
runCatching {
    repo.metaFileFor(m.sessionId)
        .writeText(Json.encodeToString(SessionMeta.serializer(), m))
}
```

with imports `kotlinx.serialization.json.Json` and the existing `SessionMeta`. In `saveRun` (after `repo.ingest(...)` succeeds) and in `discardRun` (next to the `.ndjson` delete): `repo.metaFileFor(m.sessionId).delete()` / `meta?.let { repo.metaFileFor(it.sessionId).delete() }`.

- [ ] **Step 6: Recovery in RunRepository**

```kotlin
fun metaFileFor(id: String): File = File(dir, "$id.meta.json")

/**
 * Ingest sessions stranded by a mid-run process death (meta sidecar present, no Room
 * row). The recovered run is honest: end = last sample's timestamp, moving time unknown
 * (elapsed is used), and the name marks it recovered. Returns the count recovered.
 */
suspend fun recoverOrphans(): Int = withContext(Dispatchers.IO) {
    val known = dao.allIds().toSet()
    val orphans = OrphanScanner.findOrphans(dir.listFiles()?.toList() ?: emptyList(), known)
    var recovered = 0
    for (metaFile in orphans) {
        runCatching {
            val meta = Json.decodeFromString(SessionMeta.serializer(), metaFile.readText())
            val bundle = fileFor(meta.sessionId).useLines { SessionParser.parse(meta, it) }
            if (bundle.samples.isEmpty()) { // nothing usable — clean up the stale files
                fileFor(meta.sessionId).delete(); metaFile.delete(); return@runCatching
            }
            val endMs = bundle.samples.last().ts
            ingest(
                bundle = bundle.copy(meta = meta.copy(endEpochMs = endMs, state = SessionState.FINALIZED)),
                source = "phone",
                name = "Recovered run",
            )
            metaFile.delete()
            recovered++
        }
    }
    recovered
}
```

Imports: `kotlinx.serialization.json.Json`, `com.example.runh10.shared.model.SessionState`. This needs `dao.allIds()` — add to `RunDao`: `@Query("SELECT sessionId FROM run_summary") suspend fun allIds(): List<String>` (read `RunDatabase.kt`/the DAO file to place it; no schema change, no version bump). Also extend `delete(id)` to `metaFileFor(id).delete()` alongside the ndjson delete.

- [ ] **Step 7: Startup hook**

Read `mobile/src/main/java/com/example/runh10/ui/SyncViewModel.kt`. In its init (or the earliest once-per-process entry — if init is unsuitable, use a `@Volatile var recovered` guard), launch `viewModelScope.launch { runCatching { repo.recoverOrphans() } }` BEFORE the first sync so a recovered run is already in Room when sync updates the feed. If SyncViewModel doesn't own a RunRepository reference, obtain via `RunRepository.get(application)`.

- [ ] **Step 8: Tests + build** — `./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug` → green. (recoverOrphans itself is glue over the tested scanner + existing tested parser/ingest — no Robolectric test required.)

- [ ] **Step 9: Commit**

```bash
git add mobile/src/main/java/com/example/runh10/data/OrphanScanner.kt mobile/src/test/java/com/example/runh10/data/OrphanScannerTest.kt mobile/src/main/java/com/example/runh10/data/RunRepository.kt mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt mobile/src/main/java/com/example/runh10/ui/SyncViewModel.kt
# plus the DAO file you edited
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "fix(mobile): persist session meta at run start and recover orphaned runs after process death"
```

### Task 3: Watch service post-kill honesty + single foreground assert (F4, F6)

**Files:**
- Modify: `wear/src/main/java/com/example/runh10/service/WorkoutForegroundService.kt` (onStartCommand ~51-84)

**Interfaces:**
- Consumes: nothing new. Produces: no API change; null-intent restart now stops the service cleanly instead of idling as a ghost.

- [ ] **Step 1: Rewrite onStartCommand's dispatch**

Replace the `when (intent?.action)` block so that: (a) a **null intent** (START_STICKY redelivery after process death) is handled explicitly — the prior run's data is already safe (per-line flush + `recoverOrphans()` on next launch), so the honest behavior is to stop: `stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY`; (b) the factually wrong comment in ACTION_START (claiming sticky redelivery re-brings ACTION_START) is deleted; (c) F6: ACTION_START no longer re-calls `startAsForeground()`/`acquireWakeLock()` when ACTION_CONNECT already did — guard with a service field `private var foregrounded = false` set true in `startAsForeground()`, and call `if (!foregrounded) { startAsForeground(); acquireWakeLock() }` in ACTION_START. ACTION_CONNECT keeps calling both unconditionally (it is the first entry point). Keep ACTION_STOP as is.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_STOP -> {
            stopWorkout()
            return START_NOT_STICKY
        }

        ACTION_CONNECT -> {
            val address = intent.getStringExtra(EXTRA_DEVICE) ?: return START_NOT_STICKY
            val autoConnect = intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)
            startAsForeground()
            acquireWakeLock()
            WorkoutController.init(applicationContext)
            WorkoutController.connectStrap(address, autoConnect = autoConnect)
        }

        ACTION_START -> {
            // Normal flow: ACTION_CONNECT already put us in the foreground with a
            // wake lock; don't rebuild the notification/OngoingActivity a second time.
            if (!foregrounded) {
                startAsForeground()
                acquireWakeLock()
            }
            WorkoutController.init(applicationContext)
            WorkoutController.beginRun()
        }

        // START_STICKY restart after a process kill: the intent is null (sticky
        // restarts never redeliver the original action). The dead run's samples are
        // already safe on disk (per-line flush) and will be finalized by the orphan
        // scan on next app launch — so stop honestly instead of idling as a ghost
        // service with no notification and no exercise.
        null -> {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
    }
    return START_STICKY
}
```

Add the field `private var foregrounded = false` next to `wakeLock`, and set `foregrounded = true` as the first line of `startAsForeground()`.

- [ ] **Step 2: Build + full wear tests** — `./gradlew :wear:testDebugUnitTest :wear:assembleDebug` → green. (Service lifecycle isn't unit-testable without Robolectric; the change is compile-verified + covered by the final review. State so in the report.)

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/example/runh10/service/WorkoutForegroundService.kt
git -c user.name="Bob-ee" -c user.email="robertjwhiteley@gmail.com" commit -m "fix(wear): stop cleanly on sticky restart after process death; assert foreground once per run"
```

### Task 4: Health Connect re-push for phone-recorded runs (F5)

**Files:**
- Modify: `mobile/src/main/java/com/example/runh10/data/RunDatabase.kt` + the entity/DAO files (locate `RunSummaryEntity` — it may live in its own file; read `mobile/.../data/` first)
- Modify: `mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt` (saveRun ~383-408)
- Modify: `mobile/src/main/java/com/example/runh10/ui/SyncViewModel.kt` (retry alongside the Task-2 recovery hook)
- Test: `mobile/src/test/java/com/example/runh10/data/HcPushStateTest.kt` (only if you extract decision logic; see Step 3)

**Interfaces:**
- Consumes: Task 2's SyncViewModel startup hook (retry runs in the same place).
- Produces: `RunSummaryEntity.hcPending: Boolean` (default false); `RunDao.pendingHc(): List<RunSummaryEntity>` and `markHcPending(id, pending)`; `RunRepository.repushHealthConnect(): Int`.

- [ ] **Step 1: Schema** — add `val hcPending: Boolean = false` to `RunSummaryEntity`; bump the Room `@Database` version by 1 and add a `Migration` adding the column with default 0 (`ALTER TABLE run_summary ADD COLUMN hcPending INTEGER NOT NULL DEFAULT 0`). Register it in `RunDatabase.get`. DO NOT use destructive migration — the DB on Bob's phone must survive.
- [ ] **Step 2: saveRun marks the outcome** — in `PhoneRecordController.saveRun`, replace the fire-and-forget `runCatching { ... hcWriter.write(bundle, rmssd) }` with a captured result; after `repo.ingest(...)` set `hcPending` true when the write failed or permissions/availability were missing:

```kotlin
val hcOk = runCatching {
    val hcWriter = HealthConnectWriter(appContext)
    hcWriter.isAvailable() && hcWriter.hasAllPermissions() && run { hcWriter.write(bundle, rmssd); true }
}.getOrDefault(false)
// ... after ingest:
if (!hcOk) repo.markHcPending(m.sessionId, true)
```

- [ ] **Step 3: Re-push in RunRepository**

```kotlin
/** Retry Health Connect writes for phone runs whose original write failed. Idempotent (clientRecordId upsert). */
suspend fun repushHealthConnect(context: Context): Int = withContext(Dispatchers.IO) {
    val pending = dao.pendingHc()
    if (pending.isEmpty()) return@withContext 0
    val writer = HealthConnectWriter(context)
    if (!writer.isAvailable() || !writer.hasAllPermissions()) return@withContext 0
    var pushed = 0
    for (row in pending) {
        runCatching {
            val meta = SessionMeta(sessionId = row.sessionId, startEpochMs = row.startMs, startZoneId = java.time.ZoneId.systemDefault().id, appVersion = com.example.runh10.shared.Constants.APP_VERSION, state = SessionState.FINALIZED, endEpochMs = row.endMs)
            val bundle = parseStored(meta) ?: return@runCatching
            val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
            writer.write(bundle, rmssd)
            dao.markHcPending(row.sessionId, false)
            pushed++
        }
    }
    pushed
}
```

Adjust `SessionMeta` construction to its actual constructor (read `shared/.../model/` — parameter names may differ; if a stored meta sidecar or a cleaner reconstruction exists, prefer it). Wire the call next to `recoverOrphans()` in SyncViewModel. If you find yourself unable to reconstruct an adequate meta, report BLOCKED rather than writing partial HC records.

- [ ] **Step 4: Tests + build** — `./gradlew :mobile:testDebugUnitTest :mobile:assembleDebug` green; if you extracted any pure decision logic, its test goes in HcPushStateTest.
- [ ] **Step 5: Commit** — message: `fix(mobile): flag failed Health Connect writes and re-push idempotently on resume`

### Task 5: Stale-HR indicator on live displays (F3) + one import cleanup

**Files:**
- Modify: `mobile/src/main/java/com/example/runh10/record/PhoneRecordController.kt` (PhoneRunUi + hr collector)
- Modify: `mobile/src/main/java/com/example/runh10/ui/record/RecordScreens.kt` (LiveContent bpm display)
- Modify: `wear/src/main/java/com/example/runh10/workout/WorkoutController.kt` (uiState bpm staleness)
- Modify: `wear/src/main/java/com/example/runh10/presentation/RunExperience.kt` (~149, bpm hero display)
- Modify: `mobile/src/main/java/com/example/runh10/ui/AppRoot.kt` (remove unused `PaddingValues` import — leftover flagged in Task 2's review of Plan 1)

**Interfaces:**
- Produces: `PhoneRunUi.hrStale: Boolean = false`; wear's UI state gains the equivalent. UI renders the bpm dimmed/`--` when stale.

- [ ] **Step 1: Track freshness** — both controllers already tick at 1 Hz. Record `lastHrAtMs` when an HrSample arrives; in the ticker set `hrStale = lastHrAtMs > 0 && now - lastHrAtMs > 5_000`. Read each controller's tick/update function and thread the flag into its UI state.
- [ ] **Step 2: Render honestly** — where the live bpm renders (RecordScreens LiveContent hero; RunExperience ~149): when `hrStale`, show the bpm at 40% alpha (or the theme's `textFaint` color) — the number stays visible (it is the last real reading) but visibly not-live. Do NOT clear it to "--" while a reconnect is in flight; "--" is only for never-had-a-reading. Match each screen's existing style idiom.
- [ ] **Step 3: Import cleanup** — delete `import androidx.compose.foundation.layout.PaddingValues` from AppRoot.kt (verify zero remaining usages first with grep; if a usage appeared since, leave it and say so).
- [ ] **Step 4: Tests + build** — `./gradlew :wear:testDebugUnitTest :mobile:testDebugUnitTest :wear:assembleDebug :mobile:assembleDebug` green.
- [ ] **Step 5: Commit** — message: `fix: dim live HR when the strap has been silent 5s — no lying displays`

---

## Task order

Strictly serial 1 → 2 → 3 → 4 → 5 (1 and 2 share PhoneRecordController; 4 depends on 2's SyncViewModel hook; 5 touches both controllers last).

## After this plan

Plan 3 = GPS fix (from the Task-3 diagnosis doc) + visual-audit fixes. Plan 4 = spec Phase 2 gaps (battery %, calories, resting-HR auto-update). Plan 5 = export + Strava. Plan 6 = trends/audio/refactors + final whole-branch review.
