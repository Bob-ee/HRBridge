package com.example.runh10.data

import android.content.Context
import com.example.runh10.parse.SessionParser
import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.zones.ZoneCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
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

    fun observeAll(): Flow<List<RunSummaryEntity>> = dao.observeAll()
    fun observeById(id: String): Flow<RunSummaryEntity?> = dao.observeById(id)
    suspend fun byId(id: String): RunSummaryEntity? = dao.byId(id)
    fun observeThisWeek(): Flow<List<RunSummaryEntity>> = dao.observeSince(startOfWeekMs())

    suspend fun updateNameFeel(id: String, name: String, feel: String?) = dao.updateNameFeel(id, name, feel)

    suspend fun delete(id: String) {
        dao.delete(id)
        fileFor(id).delete()
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
        val summary = RunAnalyzer.analyze(
            bundle = bundle,
            zoneCalc = zoneCalculator(),
            name = existing?.name ?: name ?: defaultName(bundle.meta.startEpochMs),
            source = source,
            workoutType = existing?.workoutType ?: workoutType,
            precomputedHrvMs = precomputedHrvMs,
            kcal = kcal,
            movingMsOverride = movingMsOverride,
        ).copy(feel = existing?.feel)
        dao.upsert(summary)
        summary
    }

    /** Re-parse a stored session file (detail screens needing full-resolution data). */
    suspend fun parseStored(meta: SessionMeta): SessionBundle? = withContext(Dispatchers.IO) {
        val f = fileFor(meta.sessionId)
        if (!f.exists()) return@withContext null
        f.useLines { SessionParser.parse(meta, it) }
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
