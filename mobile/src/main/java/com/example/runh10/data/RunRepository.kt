package com.example.runh10.data

import android.content.Context
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.parse.SessionParser
import com.example.runh10.shared.Constants
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import com.example.runh10.shared.zones.ZoneCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId
import java.util.Calendar

/**
 * Phone-side run store: raw NDJSON session files in filesDir/sessions plus the Room
 * summary cache that powers the feed/detail/trends screens.
 */
class RunRepository(private val context: Context) {

    private val dao = RunDatabase.get(context).runDao()
    private val athlete = AthleteStore(context)
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    fun fileFor(id: String): File = File(dir, "$id.ndjson")
    fun metaFileFor(id: String): File = File(dir, "$id.meta.json")

    fun observeAll(): Flow<List<RunSummaryEntity>> = dao.observeAll()
    fun observeById(id: String): Flow<RunSummaryEntity?> = dao.observeById(id)
    suspend fun byId(id: String): RunSummaryEntity? = dao.byId(id)
    fun observeThisWeek(): Flow<List<RunSummaryEntity>> = dao.observeSince(startOfWeekMs())

    suspend fun updateNameFeel(id: String, name: String, feel: String?) = dao.updateNameFeel(id, name, feel)
    suspend fun markHcPending(id: String, pending: Boolean) = dao.markHcPending(id, pending)

    suspend fun delete(id: String) {
        dao.delete(id)
        fileFor(id).delete()
        metaFileFor(id).delete()
    }

    /** Current zone calculator from the athlete profile, or null until both HRs are set. */
    suspend fun zoneCalculator(): ZoneCalculator? {
        val p = athlete.current()
        val max = p.maxHr ?: return null
        val rest = p.restingHr ?: return null
        return ZoneCalculator(max, rest)
    }

    /**
     * Ingest a parsed session (from watch sync or the phone record loop): computes the
     * summary against the current heart profile and caches it. The NDJSON is expected
     * to already sit at [fileFor] (sessionId).
     */
    suspend fun ingest(
        bundle: SessionBundle,
        source: String,
        name: String? = null,
        workoutType: String = "RUN",
        precomputedHrvMs: Double? = null,
        kcal: Double? = null,
        movingMsOverride: Long? = null,
    ): RunSummaryEntity = withContext(Dispatchers.Default) {
        // A re-sync of an already-ingested session must not clobber a user-edited
        // name or feel — keep them if the row exists.
        val existing = dao.byId(bundle.meta.sessionId)
        val p = athlete.current()
        val summary = RunAnalyzer.analyze(
            bundle = bundle,
            zoneCalc = zoneCalculator(),
            name = existing?.name ?: name ?: defaultName(bundle.meta.startEpochMs),
            source = source,
            workoutType = existing?.workoutType ?: workoutType,
            precomputedHrvMs = precomputedHrvMs,
            kcal = kcal,
            movingMsOverride = movingMsOverride,
            weightKg = p.weightKg,
            age = p.birthYear?.let { currentYear() - it },
            male = p.sexMale,
        ).copy(feel = existing?.feel, hcPending = existing?.hcPending ?: false)
        dao.upsert(summary)
        summary
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    /**
     * One-shot fill-in for rows ingested before the athlete's body profile (weight,
     * birth year, sex) was set, or before this feature existed — kcal for them is
     * null forever otherwise. Age is derived at estimation time (current year minus
     * birth year), never stored, so it can't go stale. Returns the count updated.
     */
    suspend fun backfillCalories(): Int = withContext(Dispatchers.IO) {
        val p = athlete.current()
        val weightKg = p.weightKg
        val birthYear = p.birthYear
        val male = p.sexMale
        if (weightKg == null || birthYear == null || male == null) return@withContext 0
        val age = currentYear() - birthYear
        var updated = 0
        for (row in dao.missingKcal()) {
            runCatching {
                val meta = SessionMeta(
                    sessionId = row.sessionId,
                    startEpochMs = row.startMs,
                    startZoneId = ZoneId.systemDefault().id,
                    endEpochMs = row.endMs,
                    appVersion = Constants.APP_VERSION,
                    state = SessionState.FINALIZED,
                )
                val bundle = parseStored(meta) ?: return@runCatching
                val hrs = bundle.samples.filterIsInstance<HrRow>()
                val kcal = CalorieEstimator.kcal(hrs, weightKg, age, male) ?: return@runCatching
                dao.updateKcal(row.sessionId, kcal)
                updated++
            }
        }
        updated
    }

    /** Re-parse a stored session file (detail screens needing full-resolution data). */
    suspend fun parseStored(meta: SessionMeta): SessionBundle? = withContext(Dispatchers.IO) {
        val f = fileFor(meta.sessionId)
        if (!f.exists()) return@withContext null
        f.useLines { SessionParser.parse(meta, it) }
    }

    /**
     * Ingest sessions stranded by a mid-run process death (meta sidecar present, no Room
     * row). The recovered run is honest: end = last sample's timestamp, moving time unknown
     * (elapsed is used), and the name marks it recovered. Returns the count recovered.
     *
     * [excludeSessionId] must be the id of any session PhoneRecordController is currently
     * recording: this is per-Activity and re-runs on every MainActivity creation, while a
     * live phone run (singleton controller + FGS) survives Activity death, so without the
     * exclusion recovery can delete or prematurely ingest a run that's still in progress.
     */
    suspend fun recoverOrphans(excludeSessionId: String? = null): Int = withContext(Dispatchers.IO) {
        val known = dao.allIds().toSet()
        val orphans = OrphanScanner.findOrphans(dir.listFiles()?.toList() ?: emptyList(), known)
        var recovered = 0
        for (metaFile in orphans) {
            runCatching {
                val meta = Json.decodeFromString(SessionMeta.serializer(), metaFile.readText())
                if (meta.sessionId == excludeSessionId) return@runCatching // live run — never touch it
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
                // Route the recovered run through the existing hcPending retry path so
                // repushHealthConnect (joined after recovery in SyncViewModel.onResume) picks
                // it up — the write is idempotent, so this is harmless if it already landed.
                dao.markHcPending(meta.sessionId, true)
                metaFile.delete()
                recovered++
            }
        }
        recovered
    }

    /**
     * Retry Health Connect writes for phone-recorded runs whose original write failed (F5).
     * Watch-synced runs already retry via the normal sync path — this only covers rows
     * PhoneRecordController.saveRun flagged with hcPending. The write is idempotent
     * (clientRecordId upsert), so re-pushing an already-written session is harmless.
     *
     * The meta sidecar is gone by the time a row reaches here (deleted on save), so the
     * SessionMeta is reconstructed from the Room row rather than replaying the original file.
     * startZoneId isn't persisted on the row; the current system zone is used as a stand-in —
     * it only affects the display-offset on HC records, not the recorded instants.
     */
    suspend fun repushHealthConnect(context: Context): Int = withContext(Dispatchers.IO) {
        val pending = dao.pendingHc()
        if (pending.isEmpty()) return@withContext 0
        val writer = HealthConnectWriter(context)
        if (!writer.isAvailable() || !writer.hasAllPermissions()) return@withContext 0
        var pushed = 0
        for (row in pending) {
            runCatching {
                val meta = SessionMeta(
                    sessionId = row.sessionId,
                    startEpochMs = row.startMs,
                    startZoneId = ZoneId.systemDefault().id,
                    endEpochMs = row.endMs,
                    appVersion = Constants.APP_VERSION,
                    state = SessionState.FINALIZED,
                )
                val bundle = parseStored(meta) ?: return@runCatching
                val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
                // row.kcal was computed at ingest time from the profile that existed then
                // (or by backfillCalories() since) — reuse it rather than re-estimating,
                // so this retry can't disagree with what's already shown in the app.
                writer.write(bundle, rmssd, row.kcal)
                dao.markHcPending(row.sessionId, false)
                pushed++
            }
        }
        pushed
    }

    private fun defaultName(startMs: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = startMs }
        return when (c.get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Morning Run"
            in 12..16 -> "Afternoon Run"
            in 17..20 -> "Evening Run"
            else -> "Night Run"
        }
    }

    private fun startOfWeekMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }.timeInMillis

    companion object {
        @Volatile private var instance: RunRepository? = null
        fun get(context: Context): RunRepository = instance ?: synchronized(this) {
            instance ?: RunRepository(context.applicationContext).also { instance = it }
        }
    }
}
