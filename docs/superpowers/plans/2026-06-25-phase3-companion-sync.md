# Phase 3 — Companion Sync → Health Connect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the companion phone app that auto-pulls finalized runs from the watch over the Wearable Data Layer, parses each NDJSON file, computes RMSSD/HRV, and writes the run idempotently into Health Connect — then ACKs the watch so it purges the file but keeps its session stub.

**Architecture:** `:mobile` becomes a Compose "sync console". The NDJSON schema, models, and a new `SyncProtocol` (paths/capabilities + `SessionMeta` codec) live once in `:shared` and are reused by both sides. Pure phone-side logic (`RmssdCalculator`, `SessionParser`) is JVM-unit-tested. A `PhoneSyncClient` orchestrates the per-session pipeline (request → transfer → parse → RMSSD → Health Connect write → ACK, all-or-nothing); a `SyncViewModel` drives the UI, gates on Health Connect permission state, and auto-syncs on foreground.

**Tech Stack:** Kotlin 2.0.0, AGP 8.7.0, Jetpack Compose (BOM 2024.04.01) + Material3, `androidx.health.connect:connect-client` 1.1.0 (phone only), `play-services-wearable` 19.0.0, kotlinx.serialization 1.6.3, lifecycle-viewmodel-compose. JUnit4 + kotlinx-coroutines-test for JVM unit tests; on-device verification for transport + Health Connect.

## Global Constraints

Copy these verbatim into every task's mental checklist:

- **Repo:** `~/AndroidStudioProjects/RunH10` (NOT the WatchApp working dir). Base package `com.example.runh10`. `:mobile` `applicationId`/`namespace` = `com.example.runh10`. compileSdk/minSdk/targetSdk = **34**. `:mobile` and `:shared` use Java/jvmTarget **17**.
- **Build:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` then `cd ~/AndroidStudioProjects/RunH10 && ./gradlew <task>`. `adb` = `/opt/homebrew/bin/adb`.
- **Health Connect is PHONE-ONLY** (`androidx.health.connect:connect-client` 1.1.0). NEVER add it to `:wear`.
- **Reuse `:shared`** (`SampleRow`, `SessionMeta`, `Split`, `SessionBundle`, `NdjsonSerializer`, new `SyncProtocol`). NEVER redefine the NDJSON schema or the wire protocol in `:mobile` or `:wear`.
- **HC `Metadata`:** never the bare `Metadata(clientRecordId=…)` constructor (internal in 1.1.0 — won't compile). Use `Metadata.activelyRecorded(clientRecordId = sessionId, clientRecordVersion = 1L, device = Device(type = Device.TYPE_WATCH))` on **every** record. Same `clientRecordId` upserts → re-sync never duplicates.
- **Route** is `exerciseRoute` on `ExerciseSessionRecord` — NEVER separate location records.
- **Purge only after ACK:** the watch deletes a `.ndjson` only when the phone confirms the HC write. The watch file is the durable retry queue.
- **All-or-nothing per session:** build all records for one session, insert, and only send `/runh10/ack/<id>` on full success. Any failure → no ACK → run stays on watch → retried next sync. Idempotency makes retries safe.
- **RMSSD windows:** 30 s non-overlapping; filter RR to `300..2000` ms; **skip** any window with `< 20` valid intervals; one `HeartRateVariabilityRmssdRecord` per surviving window, timestamped at window end.
- **Splits deferred to Phase 4:** `SessionParser` sets `SessionBundle.splits = emptyList()`. Splits are not consumed anywhere in Phase 3.
- **Units:** SI stored (m, m/s, kcal, ms); imperial is a display concern (none in Phase 3's console).
- **TDD:** for the pure-logic tasks (`SyncProtocol` codec, `RmssdCalculator`, `SessionParser`) write the failing JVM test first. For Android-framework tasks (Compose, Health Connect, Data Layer, ViewModel) the deliverable is full implementation code plus an explicit on-device verification step — noted per task.
- **Spec:** this plan implements `docs/superpowers/specs/2026-06-25-phase3-companion-sync-design.md` and `~/Documents/Claude/Projects/WatchApp/PLAN.md` §"Phase 3" / §"Phase 0" (API pins). Cite Phase 0 before inventing any API variant.
- **Prerequisite for Tasks 6–8 on-device steps:** a physical Android phone with Health Connect installed, on adb, paired to the Pixel Watch 3. Tasks 1–5 do NOT need it.

**Existing symbols reused (exact, verified):**
- `com.example.runh10.shared.model.SampleRow` (sealed): `HrRow(ts:Long, bpm:Int)`, `RrRow(ts:Long, rr:Int)`, `LocRow(ts:Long, lat:Double, lon:Double, alt:Double?, spd:Double?, dist:Double?)`, `CalRow(ts:Long, kcal:Double)`, `LapRow(ts:Long)`.
- `com.example.runh10.shared.model.SessionMeta(sessionId, startEpochMs, startZoneId, endEpochMs:Long?, exerciseType="RUNNING", appVersion, state:SessionState)`; `enum SessionState { RECORDING, FINALIZED, SYNCING, SYNCED }`.
- `com.example.runh10.shared.model.SessionBundle(meta, samples:List<SampleRow>, splits:List<Split>)`.
- `com.example.runh10.shared.serial.NdjsonSerializer` — `decodeTolerant(lines: Sequence<String>): List<SampleRow>`, `json: Json` (classDiscriminator `"t"`).
- `com.example.runh10.shared.Constants` — `APP_VERSION="1.0"`, `MILE_METERS=1609.344`.
- Watch `com.example.runh10.session.SessionStore` — `fileFor(id):File`, `markSyncing(id)`, `markSynced(id)`, `purgeFile(id)`. Watch `SessionDao` — `byState(state):List<SessionEntity>`, `observeAll()`, `setState`, `finalize`, `upsert`. `SessionEntity(sessionId, startEpochMs, startZoneId, endEpochMs:Long?, exerciseType, appVersion, state:String)`.
- Watch manifest pattern: services under `.service.*`, `android:exported="false"`.

---

## File Structure (decomposition)

**New in `:shared`:**
- `shared/.../sync/SyncProtocol.kt` — Data Layer path + capability constants; encode/decode `List<SessionMeta>`.
- (modify) `shared/.../model/SessionModels.kt` — add `@Serializable` to `SessionMeta` + `SessionState`.

**New in `:mobile`:**
- `mobile/.../MainActivity.kt` (rewrite) — `ComponentActivity` + `setContent`.
- `mobile/.../ui/SyncScreen.kt` — the console Composable.
- `mobile/.../ui/SyncViewModel.kt` — UI state, permission gating, auto-sync trigger.
- `mobile/.../healthconnect/RmssdCalculator.kt` — pure RMSSD windows.
- `mobile/.../parse/SessionParser.kt` — pure NDJSON → `SessionBundle`.
- `mobile/.../healthconnect/HealthConnectWriter.kt` — build + insert HC records; permission set.
- `mobile/.../sync/PhoneSyncClient.kt` — orchestrator (find node → request → per-session pipeline).
- `mobile/.../sync/SyncListenerService.kt` — `WearableListenerService` (receives unsynced reply + channel events).
- `mobile/.../sync/ChannelInbox.kt` — process-scoped coordinator bridging the listener service ↔ `PhoneSyncClient`.
- `mobile/res/values/wear.xml` — capability `runh10_phone`.
- (modify) manifest, `build.gradle.kts`, `themes.xml`, catalog.

**New in `:wear`:**
- `wear/.../sync/WearSyncService.kt` — `WearableListenerService` (lists unsynced, sends files, handles ACK).
- (modify) `wear/.../session/SessionStore.kt` + `SessionDao.kt` — add `getUnsynced()`.
- `wear/res/values/wear.xml` — capability `runh10_watch`.
- (modify) `wear/src/main/AndroidManifest.xml` — register `WearSyncService`.

---

# Task 1: `:mobile` → Compose build setup + sync-console scaffold

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `mobile/build.gradle.kts`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/src/main/res/values/themes.xml`, `mobile/src/main/res/values-night/themes.xml`
- Delete: `mobile/src/main/res/layout/activity_main.xml`
- Rewrite: `mobile/src/main/java/com/example/runh10/MainActivity.kt`

**Interfaces:**
- Produces: catalog aliases `libs.health.connect.client`, `libs.androidx.compose.material3`, `libs.androidx.lifecycle.viewmodel.compose`; a Compose `MainActivity` calling `setContent`. Later tasks add real UI inside `setContent`.

- [ ] **Step 1: Add versions + libraries to `gradle/libs.versions.toml`**

In `[versions]`, change `playServicesWearable = "18.0.0"` to `playServicesWearable = "19.0.0"` and append:
```toml
healthConnect = "1.1.0"
lifecycleViewmodelCompose = "2.8.7"
```
In `[libraries]`, append:
```toml
health-connect-client = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleViewmodelCompose" }
```

- [ ] **Step 2: Rewrite `mobile/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.runh10"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.runh10"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.play.services.wearable)
    implementation(libs.health.connect.client)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    wearApp(project(":wear"))
}
```

- [ ] **Step 3: Replace `:mobile` themes with framework-parented themes** (removes the AppCompat/Material dependency the old Views theme needed).

`mobile/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RunH10" parent="@android:style/Theme.DeviceDefault.Light.NoActionBar" />
</resources>
```
`mobile/src/main/res/values-night/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RunH10" parent="@android:style/Theme.DeviceDefault.NoActionBar" />
</resources>
```

- [ ] **Step 4: Delete the Views layout**

```bash
cd ~/AndroidStudioProjects/RunH10
git rm mobile/src/main/res/layout/activity_main.xml
```

- [ ] **Step 5: Rewrite `MainActivity.kt`**

```kotlin
package com.example.runh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { SyncConsolePlaceholder() }
            }
        }
    }
}

@Composable
private fun SyncConsolePlaceholder() {
    Text("Run H10 — sync console")
}
```

- [ ] **Step 6: Build**

Run: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && cd ~/AndroidStudioProjects/RunH10 && ./gradlew :mobile:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(mobile): convert :mobile to Compose sync-console scaffold"
```

---

# Task 2: `:shared` sync protocol (serializable meta + wire codec)

**Files:**
- Modify: `shared/src/main/java/com/example/runh10/shared/model/SessionModels.kt`
- Create: `shared/src/main/java/com/example/runh10/shared/sync/SyncProtocol.kt`
- Test: `shared/src/test/java/com/example/runh10/shared/sync/SyncProtocolTest.kt`

**Interfaces:**
- Produces: `SyncProtocol` object with `CAP_WATCH="runh10_watch"`, `CAP_PHONE="runh10_phone"`, `PATH_REQUEST_UNSYNCED="/runh10/request_unsynced"`, `PATH_UNSYNCED_LIST="/runh10/unsynced_list"`, `pathStartTransfer(id):String` (`"/runh10/start_transfer/$id"`), `pathSession(id):String` (`"/runh10/session/$id"`), `pathAck(id):String` (`"/runh10/ack/$id"`), `idFromPath(prefix, path):String?`, `encodeMetaList(List<SessionMeta>):ByteArray`, `decodeMetaList(ByteArray):List<SessionMeta>`. `SessionMeta`/`SessionState` are `@Serializable`.

- [ ] **Step 1: Write the failing test**

`shared/src/test/java/com/example/runh10/shared/sync/SyncProtocolTest.kt`:
```kotlin
package com.example.runh10.shared.sync

import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProtocolTest {
    private fun meta(id: String) = SessionMeta(
        sessionId = id, startEpochMs = 1000L, startZoneId = "America/New_York",
        endEpochMs = 2000L, appVersion = "1.0", state = SessionState.FINALIZED,
    )

    @Test fun metaList_roundTrips() {
        val list = listOf(meta("a"), meta("b"))
        val decoded = SyncProtocol.decodeMetaList(SyncProtocol.encodeMetaList(list))
        assertEquals(list, decoded)
    }

    @Test fun paths_are_built_and_parsed() {
        assertEquals("/runh10/start_transfer/x", SyncProtocol.pathStartTransfer("x"))
        assertEquals("/runh10/session/x", SyncProtocol.pathSession("x"))
        assertEquals("/runh10/ack/x", SyncProtocol.pathAck("x"))
        assertEquals("x", SyncProtocol.idFromPath("/runh10/ack/", "/runh10/ack/x"))
        assertNull(SyncProtocol.idFromPath("/runh10/ack/", "/runh10/session/x"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SyncProtocolTest*"`
Expected: FAIL — `SyncProtocol` unresolved (and `SessionMeta` not serializable).

- [ ] **Step 3: Make `SessionMeta`/`SessionState` serializable**

In `shared/src/main/java/com/example/runh10/shared/model/SessionModels.kt`, add imports and annotations:
```kotlin
package com.example.runh10.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionState { RECORDING, FINALIZED, SYNCING, SYNCED }

@Serializable
data class SessionMeta(
    val sessionId: String,
    val startEpochMs: Long,
    val startZoneId: String,
    val endEpochMs: Long? = null,
    val exerciseType: String = "RUNNING",
    val appVersion: String,
    val state: SessionState,
)
```
(Leave `Split` and `SessionBundle` unchanged.)

- [ ] **Step 4: Create `SyncProtocol`**

`shared/src/main/java/com/example/runh10/shared/sync/SyncProtocol.kt`:
```kotlin
package com.example.runh10.shared.sync

import com.example.runh10.shared.model.SessionMeta
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Single wire definition for the Data Layer sync, shared by watch + phone. */
object SyncProtocol {
    const val CAP_WATCH = "runh10_watch"
    const val CAP_PHONE = "runh10_phone"

    const val PATH_REQUEST_UNSYNCED = "/runh10/request_unsynced"
    const val PATH_UNSYNCED_LIST = "/runh10/unsynced_list"
    const val PREFIX_START_TRANSFER = "/runh10/start_transfer/"
    const val PREFIX_SESSION = "/runh10/session/"
    const val PREFIX_ACK = "/runh10/ack/"

    fun pathStartTransfer(id: String) = "$PREFIX_START_TRANSFER$id"
    fun pathSession(id: String) = "$PREFIX_SESSION$id"
    fun pathAck(id: String) = "$PREFIX_ACK$id"

    /** Returns the id if [path] starts with [prefix], else null. */
    fun idFromPath(prefix: String, path: String): String? =
        if (path.startsWith(prefix)) path.removePrefix(prefix).ifEmpty { null } else null

    private val json = Json { encodeDefaults = true }
    private val metaListSerializer = ListSerializer(SessionMeta.serializer())

    fun encodeMetaList(list: List<SessionMeta>): ByteArray =
        json.encodeToString(metaListSerializer, list).toByteArray(Charsets.UTF_8)

    fun decodeMetaList(bytes: ByteArray): List<SessionMeta> =
        json.decodeFromString(metaListSerializer, String(bytes, Charsets.UTF_8))
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :shared:testDebugUnitTest --tests "*SyncProtocolTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(shared): serializable SessionMeta + SyncProtocol wire codec"
```

---

# Task 3: `RmssdCalculator` (pure, JVM TDD)

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/healthconnect/RmssdCalculator.kt`
- Test: `mobile/src/test/java/com/example/runh10/healthconnect/RmssdCalculatorTest.kt`

**Interfaces:**
- Consumes: `RrRow` from `:shared`.
- Produces: `RmssdCalculator.compute(rr: List<RrRow>): List<RmssdCalculator.RmssdPoint>`; `data class RmssdPoint(val tsMs: Long, val rmssdMs: Double)`. Constants `WINDOW_MS=30_000L`, `MIN_RR=300`, `MAX_RR=2000`, `MIN_SAMPLES=20`.

- [ ] **Step 1: Write the failing test**

`mobile/src/test/java/com/example/runh10/healthconnect/RmssdCalculatorTest.kt`:
```kotlin
package com.example.runh10.healthconnect

import com.example.runh10.shared.model.RrRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmssdCalculatorTest {
    // 20 intervals alternating 800/810 ms, 100 ms apart, all in window [0,30000).
    // successive diffs are all ±10 -> squared 100 -> mean 100 -> rmssd 10.0
    private fun alternating(n: Int, startTs: Long = 0L) = (0 until n).map {
        RrRow(ts = startTs + it * 100L, rr = if (it % 2 == 0) 800 else 810)
    }

    @Test fun fullWindow_computesRmssd_atWindowEnd() {
        val out = RmssdCalculator.compute(alternating(20))
        assertEquals(1, out.size)
        assertEquals(30_000L, out[0].tsMs)          // window end
        assertEquals(10.0, out[0].rmssdMs, 1e-9)
    }

    @Test fun sparseWindow_isSkipped() {
        assertTrue(RmssdCalculator.compute(alternating(10)).isEmpty())
    }

    @Test fun implausibleRr_isFiltered() {
        // 19 good + many garbage (<300 / >2000) -> still < 20 valid -> skipped
        val rows = alternating(19) + List(10) { RrRow(ts = 1900L + it, rr = 5000) }
        assertTrue(RmssdCalculator.compute(rows).isEmpty())
    }

    @Test fun emptyInput_returnsEmpty() {
        assertTrue(RmssdCalculator.compute(emptyList()).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :mobile:testDebugUnitTest --tests "*RmssdCalculatorTest*"`
Expected: FAIL — `RmssdCalculator` unresolved.

- [ ] **Step 3: Implement `RmssdCalculator`**

```kotlin
package com.example.runh10.healthconnect

import com.example.runh10.shared.model.RrRow
import kotlin.math.sqrt

/** RMSSD over 30 s non-overlapping windows of consecutive RR intervals. SI: ms in, ms out. */
object RmssdCalculator {
    const val WINDOW_MS = 30_000L
    const val MIN_RR = 300
    const val MAX_RR = 2000
    const val MIN_SAMPLES = 20

    data class RmssdPoint(val tsMs: Long, val rmssdMs: Double)

    fun compute(rr: List<RrRow>): List<RmssdPoint> {
        val valid = rr.asSequence()
            .filter { it.rr in MIN_RR..MAX_RR }
            .sortedBy { it.ts }
            .toList()
        if (valid.isEmpty()) return emptyList()
        val t0 = valid.first().ts
        return valid.groupBy { (it.ts - t0) / WINDOW_MS }
            .toSortedMap()
            .mapNotNull { (windowIndex, rows) ->
                if (rows.size < MIN_SAMPLES) return@mapNotNull null
                val v = rows.map { it.rr }
                var sumSq = 0.0
                for (i in 1 until v.size) {
                    val d = (v[i] - v[i - 1]).toDouble()
                    sumSq += d * d
                }
                val rmssd = sqrt(sumSq / (v.size - 1))
                RmssdPoint(tsMs = t0 + (windowIndex + 1) * WINDOW_MS, rmssdMs = rmssd)
            }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :mobile:testDebugUnitTest --tests "*RmssdCalculatorTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(mobile): RmssdCalculator with 30s windows + sparse-skip"
```

---

# Task 4: `SessionParser` (pure, JVM TDD)

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/parse/SessionParser.kt`
- Test: `mobile/src/test/java/com/example/runh10/parse/SessionParserTest.kt`

**Interfaces:**
- Consumes: `SessionMeta`, `SampleRow`, `SessionBundle`, `NdjsonSerializer` from `:shared`.
- Produces: `SessionParser.parse(meta: SessionMeta, lines: Sequence<String>): SessionBundle` — samples sorted by `ts`, `splits = emptyList()`, tolerant of truncated/blank/garbage lines.

- [ ] **Step 1: Write the failing test**

`mobile/src/test/java/com/example/runh10/parse/SessionParserTest.kt`:
```kotlin
package com.example.runh10.parse

import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionParserTest {
    private val meta = SessionMeta(
        sessionId = "s1", startEpochMs = 1000L, startZoneId = "America/New_York",
        endEpochMs = 3000L, appVersion = "1.0", state = SessionState.FINALIZED,
    )

    @Test fun parses_sorts_and_tolerates_truncated_tail() {
        val lines = sequenceOf(
            """{"t":"hr","ts":2000,"bpm":150}""",
            """{"t":"loc","ts":1500,"lat":40.0,"lon":-73.0,"dist":100.0}""",
            "",                                   // blank skipped
            """{"t":"hr","ts":2500,"bpm":1""",    // truncated tail skipped
        )
        val bundle = SessionParser.parse(meta, lines)
        assertEquals(meta, bundle.meta)
        assertEquals(listOf(1500L, 2000L), bundle.samples.map { it.ts })  // sorted, garbage dropped
        assertTrue(bundle.samples[0] is LocRow)
        assertTrue(bundle.samples[1] is HrRow)
        assertTrue(bundle.splits.isEmpty())      // splits deferred to Phase 4
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :mobile:testDebugUnitTest --tests "*SessionParserTest*"`
Expected: FAIL — `SessionParser` unresolved.

- [ ] **Step 3: Implement `SessionParser`**

```kotlin
package com.example.runh10.parse

import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.serial.NdjsonSerializer

/** Parse one (possibly crash-truncated) session NDJSON into a SessionBundle. Splits deferred to Phase 4. */
object SessionParser {
    fun parse(meta: SessionMeta, lines: Sequence<String>): SessionBundle {
        val samples = NdjsonSerializer.decodeTolerant(lines).sortedBy { it.ts }
        return SessionBundle(meta = meta, samples = samples, splits = emptyList())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :mobile:testDebugUnitTest --tests "*SessionParserTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(mobile): SessionParser (NDJSON -> SessionBundle, truncation-tolerant)"
```

---

# Task 5: `HealthConnectWriter` + permissions + manifest

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/healthconnect/HealthConnectWriter.kt`
- Create: `mobile/src/main/java/com/example/runh10/PermissionsRationaleActivity.kt`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `mobile/src/main/res/values/strings.xml` (privacy-policy string)

**Interfaces:**
- Consumes: `SessionBundle`, `RmssdCalculator.RmssdPoint`.
- Produces: `HealthConnectWriter(context)` with:
  - `companion object { val PERMISSIONS: Set<String> }` (all WRITE perms + route),
  - `suspend fun isAvailable(): Boolean` (wraps `getSdkStatus`),
  - `suspend fun hasAllPermissions(): Boolean`,
  - `suspend fun write(bundle: SessionBundle, rmssd: List<RmssdCalculator.RmssdPoint>)` — builds all records, `insertRecords` (chunked ≤1000), throws on failure.

- [ ] **Step 1: Implement `HealthConnectWriter`** (Android-framework task — no JVM unit test; verified on-device in Task 8). Cite Phase 0 for every API.

```kotlin
package com.example.runh10.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.example.runh10.shared.model.CalRow
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.SessionBundle
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectWriter(private val context: Context) {

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun write(bundle: SessionBundle, rmssd: List<RmssdCalculator.RmssdPoint>) {
        val meta = bundle.meta
        val zone = runCatching { ZoneId.of(meta.startZoneId) }.getOrDefault(ZoneId.systemDefault())
        val start = Instant.ofEpochMilli(meta.startEpochMs)
        val samples = bundle.samples
        val lastTs = samples.maxOfOrNull { it.ts } ?: meta.startEpochMs
        val end = Instant.ofEpochMilli(meta.endEpochMs ?: lastTs)
        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        fun md() = Metadata.activelyRecorded(
            clientRecordId = meta.sessionId,
            clientRecordVersion = 1L,
            device = Device(type = Device.TYPE_WATCH),
        )

        val locs = samples.filterIsInstance<LocRow>().sortedBy { it.ts }
        val hrs = samples.filterIsInstance<HrRow>().sortedBy { it.ts }
        val cals = samples.filterIsInstance<CalRow>().sortedBy { it.ts }

        val records = mutableListOf<Record>()

        // Exercise session + route (route is a ctor param, NOT separate records)
        val route = locs.map { l ->
            ExerciseRoute.Location(
                time = Instant.ofEpochMilli(l.ts),
                latitude = l.lat,
                longitude = l.lon,
                horizontalAccuracy = null,
                altitude = l.alt?.let { Length.meters(it) },
            )
        }
        records += ExerciseSessionRecord(
            startTime = start, startZoneOffset = startOffset,
            endTime = end, endZoneOffset = endOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Run",
            metadata = md(),
            exerciseRoute = if (route.isNotEmpty()) ExerciseRoute(route) else null,
        )

        // Heart rate series
        if (hrs.isNotEmpty()) {
            records += HeartRateRecord(
                startTime = Instant.ofEpochMilli(hrs.first().ts), startZoneOffset = startOffset,
                endTime = Instant.ofEpochMilli(hrs.last().ts), endZoneOffset = endOffset,
                samples = hrs.map {
                    HeartRateRecord.Sample(time = Instant.ofEpochMilli(it.ts), beatsPerMinute = it.bpm.toLong())
                },
                metadata = md(),
            )
        }

        // Speed series (skip null speeds)
        val spd = locs.filter { it.spd != null }
        if (spd.isNotEmpty()) {
            records += SpeedRecord(
                startTime = Instant.ofEpochMilli(spd.first().ts), startZoneOffset = startOffset,
                endTime = Instant.ofEpochMilli(spd.last().ts), endZoneOffset = endOffset,
                samples = spd.map {
                    SpeedRecord.Sample(time = Instant.ofEpochMilli(it.ts), speed = Velocity.metersPerSecond(it.spd!!))
                },
                metadata = md(),
            )
        }

        // Distance total = max cumulative dist
        val totalDist = locs.mapNotNull { it.dist }.maxOrNull()
        if (totalDist != null && totalDist > 0) {
            records += DistanceRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                distance = Length.meters(totalDist), metadata = md(),
            )
        }

        // Active calories total = max cumulative kcal
        val totalKcal = cals.maxOfOrNull { it.kcal }
        if (totalKcal != null && totalKcal > 0) {
            records += ActiveCaloriesBurnedRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                energy = Energy.kilocalories(totalKcal), metadata = md(),
            )
        }

        // Elevation gain = sum of positive altitude deltas
        val gain = elevationGain(locs.mapNotNull { it.alt })
        if (gain > 0) {
            records += ElevationGainedRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                elevation = Length.meters(gain), metadata = md(),
            )
        }

        // HRV/RMSSD — one instantaneous record per surviving window
        rmssd.forEach { p ->
            val t = Instant.ofEpochMilli(p.tsMs)
            records += HeartRateVariabilityRmssdRecord(
                time = t, zoneOffset = zone.rules.getOffset(t),
                heartRateVariabilityMillis = p.rmssdMs, metadata = md(),
            )
        }

        // Batch insert (≤1000 per call). Upsert on clientRecordId makes retry safe.
        client().let { c ->
            records.chunked(1000).forEach { chunk -> c.insertRecords(chunk) }
        }
    }

    private fun elevationGain(alts: List<Double>): Double {
        var gain = 0.0
        for (i in 1 until alts.size) {
            val d = alts[i] - alts[i - 1]
            if (d > 0) gain += d
        }
        return gain
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(ElevationGainedRecord::class),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        )
    }
}
```

- [ ] **Step 2: Create `PermissionsRationaleActivity`** (privacy-policy rationale screen HC opens for the permission usage explanation).

```kotlin
package com.example.runh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text(
                        "Run H10 writes your recorded runs (route, heart rate, distance, pace, " +
                            "calories, elevation, and HRV/RMSSD) to Health Connect. Data is written " +
                            "only when you sync and is never shared elsewhere."
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Add Health Connect manifest entries** to `mobile/src/main/AndroidManifest.xml`.

Inside `<manifest>` but above `<application>`, add the permissions + queries:
```xml
    <uses-permission android:name="android.permission.health.WRITE_EXERCISE" />
    <uses-permission android:name="android.permission.health.WRITE_EXERCISE_ROUTE" />
    <uses-permission android:name="android.permission.health.WRITE_HEART_RATE" />
    <uses-permission android:name="android.permission.health.WRITE_SPEED" />
    <uses-permission android:name="android.permission.health.WRITE_DISTANCE" />
    <uses-permission android:name="android.permission.health.WRITE_ACTIVE_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.WRITE_ELEVATION_GAINED" />
    <uses-permission android:name="android.permission.health.WRITE_HEART_RATE_VARIABILITY" />

    <queries>
        <package android:name="com.google.android.apps.healthdata" />
    </queries>
```
Inside `<application>`, add the rationale activity + the `ViewPermissionUsageActivity` alias:
```xml
        <activity
            android:name=".PermissionsRationaleActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
            </intent-filter>
        </activity>

        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:targetActivity=".PermissionsRationaleActivity"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>
```

- [ ] **Step 4: Build**

Run: `./gradlew :mobile:assembleDebug`
Expected: `BUILD SUCCESSFUL` (compile-checks the HC API surface).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(mobile): HealthConnectWriter + HC permissions/manifest"
```

---

# Task 6: Watch-side Data Layer transport (`WearSyncService`)

**Files:**
- Modify: `wear/src/main/java/com/example/runh10/session/SessionDao.kt`
- Modify: `wear/src/main/java/com/example/runh10/session/SessionStore.kt`
- Create: `wear/src/main/java/com/example/runh10/sync/WearSyncService.kt`
- Create: `wear/src/main/res/values/wear.xml`
- Modify: `wear/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `SyncProtocol` (`:shared`), `SessionStore`.
- Produces: `SessionStore.getUnsynced(): List<SessionMeta>` (FINALIZED + SYNCING, oldest first). `WearSyncService` answering `request_unsynced`, `start_transfer/<id>`, `ack/<id>`.

- [ ] **Step 1: Add the DAO query**

In `wear/src/main/java/com/example/runh10/session/SessionDao.kt`, add:
```kotlin
    @Query("SELECT * FROM sessions WHERE state IN ('FINALIZED','SYNCING') ORDER BY startEpochMs ASC")
    suspend fun unsynced(): List<SessionEntity>
```

- [ ] **Step 2: Add `getUnsynced()` to `SessionStore`**

In `wear/src/main/java/com/example/runh10/session/SessionStore.kt`, add (the `SessionEntity.toMeta()` mapper already exists at file bottom):
```kotlin
    suspend fun getUnsynced(): List<SessionMeta> = dao.unsynced().map { it.toMeta() }
```

- [ ] **Step 3: Create the capability file** `wear/src/main/res/values/wear.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="android_wear_capabilities">
        <item>runh10_watch</item>
    </string-array>
</resources>
```

- [ ] **Step 4: Create `WearSyncService`**

`wear/src/main/java/com/example/runh10/sync/WearSyncService.kt`:
```kotlin
package com.example.runh10.sync

import com.example.runh10.session.SessionStore
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.net.Uri

class WearSyncService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { SessionStore(applicationContext) }
    private val channelClient by lazy { Wearable.getChannelClient(applicationContext) }
    private val messageClient by lazy { Wearable.getMessageClient(applicationContext) }

    override fun onMessageReceived(event: MessageEvent) {
        val node = event.sourceNodeId
        when {
            event.path == SyncProtocol.PATH_REQUEST_UNSYNCED -> scope.launch {
                val metas = store.getUnsynced()
                messageClient.sendMessage(node, SyncProtocol.PATH_UNSYNCED_LIST,
                    SyncProtocol.encodeMetaList(metas)).await()
            }
            SyncProtocol.idFromPath(SyncProtocol.PREFIX_START_TRANSFER, event.path) != null -> scope.launch {
                val id = SyncProtocol.idFromPath(SyncProtocol.PREFIX_START_TRANSFER, event.path)!!
                val file = store.fileFor(id)
                if (!file.exists()) return@launch
                store.markSyncing(id)
                val channel = channelClient.openChannel(node, SyncProtocol.pathSession(id)).await()
                channelClient.sendFile(channel, Uri.fromFile(file)).await()
            }
            SyncProtocol.idFromPath(SyncProtocol.PREFIX_ACK, event.path) != null -> scope.launch {
                val id = SyncProtocol.idFromPath(SyncProtocol.PREFIX_ACK, event.path)!!
                store.markSynced(id)
                store.purgeFile(id)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        store.close()
    }
}
```
> Note: the GMS `Task`s above are awaited with `kotlinx-coroutines-play-services`'s `.await()` (NOT `kotlinx-coroutines-guava`, which is for Health Services). The catalog entry + dependency are added in the next step.

- [ ] **Step 4a: Add the coroutines-play-services dependency**

In `gradle/libs.versions.toml` `[versions]` add `coroutinesPlayServices = "1.8.1"`; in `[libraries]` add:
```toml
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutinesPlayServices" }
```
In `wear/build.gradle.kts` dependencies add: `implementation(libs.kotlinx.coroutines.play.services)`.

- [ ] **Step 5: Register the service** in `wear/src/main/AndroidManifest.xml`, inside `<application>` after `WorkoutForegroundService`:
```xml
        <service
            android:name=".sync.WearSyncService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <action android:name="com.google.android.gms.wearable.CHANNEL_EVENT" />
                <data android:scheme="wear" android:host="*" android:pathPrefix="/runh10" />
            </intent-filter>
        </service>
```

- [ ] **Step 6: Build the watch app**

Run: `./gradlew :wear:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(wear): WearSyncService + getUnsynced + runh10_watch capability"
```

---

# Task 7: Phone-side transport orchestrator (`PhoneSyncClient`)

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/sync/ChannelInbox.kt`
- Create: `mobile/src/main/java/com/example/runh10/sync/SyncListenerService.kt`
- Create: `mobile/src/main/java/com/example/runh10/sync/PhoneSyncClient.kt`
- Create: `mobile/src/main/res/values/wear.xml`
- Modify: `mobile/src/main/AndroidManifest.xml`
- Modify: `gradle/libs.versions.toml` + `mobile/build.gradle.kts` (coroutines-play-services)

**Interfaces:**
- Consumes: `SyncProtocol`, `SessionParser`, `RmssdCalculator`, `HealthConnectWriter`, `ChannelInbox`.
- Produces: `PhoneSyncClient(context)` with `suspend fun sync(progress: (String) -> Unit): SyncResult` (`data class SyncResult(val synced: Int, val failed: Int, val total: Int)`). `ChannelInbox` object: `offerUnsyncedList(bytes)`, `awaitUnsyncedList(): ByteArray`, `offerChannel(channel)`, `awaitChannel(path): ChannelClient.Channel`, `offerInputClosed(path)`, `awaitInputClosed(path)`, `reset()`.

- [ ] **Step 1: Add coroutines-play-services to `:mobile`**

In `mobile/build.gradle.kts` dependencies add `implementation(libs.kotlinx.coroutines.play.services)` (catalog entry added in Task 6 Step 4a).

- [ ] **Step 2: Create `ChannelInbox`** — bridges the manifest listener service callbacks to the orchestrator's coroutine.

```kotlin
package com.example.runh10.sync

import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.CompletableDeferred

/** Process-scoped rendezvous between SyncListenerService callbacks and PhoneSyncClient. */
object ChannelInbox {
    @Volatile private var unsyncedList = CompletableDeferred<ByteArray>()
    private val channels = HashMap<String, CompletableDeferred<ChannelClient.Channel>>()
    private val inputClosed = HashMap<String, CompletableDeferred<Unit>>()

    @Synchronized fun reset() {
        unsyncedList = CompletableDeferred()
        channels.clear()
        inputClosed.clear()
    }

    fun offerUnsyncedList(bytes: ByteArray) { unsyncedList.complete(bytes) }
    suspend fun awaitUnsyncedList(): ByteArray = unsyncedList.await()

    @Synchronized private fun channelSlot(path: String) =
        channels.getOrPut(path) { CompletableDeferred() }
    @Synchronized private fun inputSlot(path: String) =
        inputClosed.getOrPut(path) { CompletableDeferred() }

    fun offerChannel(channel: ChannelClient.Channel) { channelSlot(channel.path).complete(channel) }
    suspend fun awaitChannel(path: String): ChannelClient.Channel = channelSlot(path).await()

    fun offerInputClosed(path: String) { inputSlot(path).complete(Unit) }
    suspend fun awaitInputClosed(path: String) { inputSlot(path).await() }
}
```

- [ ] **Step 3: Create `SyncListenerService`** — the manifest-registered service that feeds `ChannelInbox`.

```kotlin
package com.example.runh10.sync

import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class SyncListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == SyncProtocol.PATH_UNSYNCED_LIST) {
            ChannelInbox.offerUnsyncedList(event.data)
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        ChannelInbox.offerChannel(channel)
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appErrorCode: Int) {
        ChannelInbox.offerInputClosed(channel.path)
    }
}
```

- [ ] **Step 4: Create `PhoneSyncClient`** — the orchestrator.

```kotlin
package com.example.runh10.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.parse.SessionParser
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File
import android.net.Uri

data class SyncResult(val synced: Int, val failed: Int, val total: Int)

class PhoneSyncClient(private val context: Context) {
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val channelClient by lazy { Wearable.getChannelClient(context) }
    private val writer by lazy { HealthConnectWriter(context) }

    /** One full sync pass. [progress] receives human-readable status lines. */
    suspend fun sync(progress: (String) -> Unit): SyncResult {
        ChannelInbox.reset()

        val nodeId = findWatchNode()
        if (nodeId == null) {
            progress("Watch not reachable")
            return SyncResult(0, 0, 0)
        }

        messageClient.sendMessage(nodeId, SyncProtocol.PATH_REQUEST_UNSYNCED, ByteArray(0)).await()
        val metas = SyncProtocol.decodeMetaList(ChannelInbox.awaitUnsyncedList())
        if (metas.isEmpty()) { progress("No unsynced runs"); return SyncResult(0, 0, 0) }
        progress("Found ${metas.size} unsynced run(s)")

        var synced = 0
        var failed = 0
        metas.sortedBy { it.startEpochMs }.forEachIndexed { i, meta ->
            progress("Pulling run ${i + 1} of ${metas.size}…")
            val ok = runCatching { syncOne(nodeId, meta) }.getOrElse { e ->
                progress("✗ ${meta.sessionId}: ${e.message}"); false
            }
            if (ok) { synced++; progress("✓ ${meta.sessionId} written to Health Connect") } else failed++
        }
        progress("Done: $synced synced, $failed failed")
        return SyncResult(synced, failed, metas.size)
    }

    private suspend fun syncOne(nodeId: String, meta: SessionMeta): Boolean {
        val id = meta.sessionId
        messageClient.sendMessage(nodeId, SyncProtocol.pathStartTransfer(id), ByteArray(0)).await()

        val channel = ChannelInbox.awaitChannel(SyncProtocol.pathSession(id))
        val dest = File(context.cacheDir, "$id.ndjson")
        channelClient.receiveFile(channel, Uri.fromFile(dest), false).await()
        ChannelInbox.awaitInputClosed(SyncProtocol.pathSession(id))

        val bundle = dest.useLines { SessionParser.parse(meta, it) }
        val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
        writer.write(bundle, rmssd)
        dest.delete()

        messageClient.sendMessage(nodeId, SyncProtocol.pathAck(id), ByteArray(0)).await()
        return true
    }

    private suspend fun findWatchNode(): String? {
        val info = capabilityClient
            .getCapability(SyncProtocol.CAP_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await()
        val nodes = info.nodes
        return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
    }

    suspend fun isHealthConnectReady(): Boolean = writer.isAvailable()
    suspend fun hasPermissions(): Boolean = writer.hasAllPermissions()
}
```
> Note: `dest.useLines { ... }` passes a `Sequence<String>` to `SessionParser.parse`; the block returns the bundle so the file handle closes before the file is reused.

- [ ] **Step 5: Create the phone capability file** `mobile/src/main/res/values/wear.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="android_wear_capabilities">
        <item>runh10_phone</item>
    </string-array>
</resources>
```

- [ ] **Step 6: Register `SyncListenerService`** in `mobile/src/main/AndroidManifest.xml`, inside `<application>`:
```xml
        <service
            android:name=".sync.SyncListenerService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <action android:name="com.google.android.gms.wearable.CHANNEL_EVENT" />
                <data android:scheme="wear" android:host="*" android:pathPrefix="/runh10" />
            </intent-filter>
        </service>
```

- [ ] **Step 7: Build**

Run: `./gradlew :mobile:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(mobile): PhoneSyncClient orchestrator + Data Layer listener"
```

---

# Task 8: Sync console UI (`SyncViewModel` + `SyncScreen`) + end-to-end verification

**Files:**
- Create: `mobile/src/main/java/com/example/runh10/ui/SyncViewModel.kt`
- Create: `mobile/src/main/java/com/example/runh10/ui/SyncScreen.kt`
- Modify: `mobile/src/main/java/com/example/runh10/MainActivity.kt`

**Interfaces:**
- Consumes: `PhoneSyncClient`, `HealthConnectWriter.PERMISSIONS`, `PermissionController`.
- Produces: `SyncViewModel` exposing `StateFlow<SyncUiState>` (`data class SyncUiState(val hcAvailable: Boolean, val permissionsGranted: Boolean, val syncing: Boolean, val log: List<String>)`), `fun onResume()`, `fun syncNow()`, `fun onPermissionsResult()`. `SyncScreen(vm)` Composable + the permission request launcher in `MainActivity`.

- [ ] **Step 1: Create `SyncViewModel`**

```kotlin
package com.example.runh10.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runh10.sync.PhoneSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val hcAvailable: Boolean = false,
    val permissionsGranted: Boolean = false,
    val syncing: Boolean = false,
    val log: List<String> = emptyList(),
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val client = PhoneSyncClient(app)
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /** Refresh HC + permission gates; auto-sync if ready and idle. */
    fun onResume() {
        viewModelScope.launch {
            val available = client.isHealthConnectReady()
            val granted = available && client.hasPermissions()
            _state.update { it.copy(hcAvailable = available, permissionsGranted = granted) }
            if (available && granted && !_state.value.syncing) runSync()
        }
    }

    fun syncNow() {
        if (_state.value.hcAvailable && _state.value.permissionsGranted && !_state.value.syncing) {
            viewModelScope.launch { runSync() }
        }
    }

    /** Called by the Activity after the permission request returns. */
    fun onPermissionsResult() = onResume()

    private suspend fun runSync() {
        _state.update { it.copy(syncing = true) }
        try {
            client.sync { line -> _state.update { s -> s.copy(log = s.log + line) } }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }
}
```

- [ ] **Step 2: Create `SyncScreen`**

```kotlin
package com.example.runh10.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SyncScreen(
    vm: SyncViewModel,
    onRequestPermissions: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            !state.hcAvailable ->
                Text("Health Connect not available — install/update it to sync.")
            !state.permissionsGranted ->
                Button(onClick = onRequestPermissions) { Text("Grant Health Connect access") }
            else ->
                Button(onClick = vm::syncNow, enabled = !state.syncing) {
                    Text(if (state.syncing) "Syncing…" else "Sync now")
                }
        }
        LazyColumn(Modifier.padding(top = 16.dp)) {
            items(state.log) { line -> Text(line) }
        }
    }
}
```

- [ ] **Step 3: Wire `MainActivity`** — permission launcher + lifecycle.

```kotlin
package com.example.runh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.ui.SyncScreen
import com.example.runh10.ui.SyncViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()

    private val requestPerms =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            vm.onPermissionsResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    SyncScreen(vm = vm, onRequestPermissions = { requestPerms.launch(HealthConnectWriter.PERMISSIONS) })
                }
            }
        }
        // Auto-sync trigger on each foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.onResume() }
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :mobile:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install both apps** (phone on adb + watch paired).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/AndroidStudioProjects/RunH10
./gradlew :wear:installDebug :mobile:installDebug
/opt/homebrew/bin/adb devices -l   # confirm BOTH watch + phone listed
```

- [ ] **Step 6: On-device end-to-end verification** (record results in the ledger). Ensure the watch has ≥1 FINALIZED session (do a short recorded run if needed).

  - Launch the phone app. On first run, tap **Grant Health Connect access**, approve all permissions in the single Health Connect sheet. Expected: button flips to **Sync now** and an auto-sync starts.
  - Expected log: `Found N unsynced run(s)` → `Pulling run 1 of N…` → `✓ <id> written to Health Connect` → `Done: …`.
  - Open **Health Connect / Google Health** → confirm an Exercise session (RUNNING) with **route, heart rate series, distance, pace/speed, calories, elevation, and an HRV/RMSSD series**.
  - **Re-sync the same session is impossible from the watch** (file purged after ACK). To prove idempotency, before ACK is not testable in UI; instead verify the watch session row now shows state **SYNCED** and the `.ndjson` is gone: `adb -s <watch> shell run-as com.example.runh10 ls files/sessions` (the synced id's file absent).
  - Confirm the watch history still shows the run (stub retained).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(mobile): sync console UI + ViewModel; Phase 3 end-to-end verified"
```

---

## Final verification checklist (whole Phase 3)

- [ ] `./gradlew :shared:testDebugUnitTest :mobile:testDebugUnitTest` — all JVM unit tests pass (SyncProtocol, Rmssd, SessionParser).
- [ ] `./gradlew :mobile:assembleDebug :wear:assembleDebug` — both build.
- [ ] Phone app launches in Compose; permission gating + one-tap grant work.
- [ ] With watch + phone connected, phone lists FINALIZED sessions and transfers oldest-first.
- [ ] Run appears in Health Connect with route, HR, distance, pace, calories, elevation, and RMSSD/HRV.
- [ ] Each ACK purges the watch `.ndjson` but the `SessionMeta` row remains (state SYNCED) and shows in watch history.
- [ ] Failure path: with HC permission revoked or watch out of range, sync reports the failure and the run stays on the watch (no data loss).

## Self-review notes (addressed)

- **Spec coverage:** Task 1 (Compose) ↔ spec Task 1; Tasks 6–7 (transport) ↔ spec Task 2; Tasks 3–5 (RMSSD/parser/writer) ↔ spec Task 3; Task 8 (UI gating + auto-sync) ↔ spec decisions 1/2/5. Splits deferral + meta-on-wire refinements are in the spec amendments.
- **Type consistency:** `SessionMeta.sessionId` is the `clientRecordId` throughout; `RmssdCalculator.RmssdPoint(tsMs,rmssdMs)` consumed unchanged by `HealthConnectWriter.write`; `SyncProtocol` path/prefix names identical across `WearSyncService`, `SyncListenerService`, `PhoneSyncClient`.
- **No placeholders:** every step ships real code/commands. The one runtime dep nuance (`kotlinx-coroutines-play-services` for `.await()`) is added explicitly in Task 6 Step 4a / Task 7 Step 1.
