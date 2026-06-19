package com.example.runh10.session

import android.content.Context
import androidx.room.Room
import com.example.runh10.shared.Constants
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class SessionStore(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext, AppDatabase::class.java, "runh10.db"
    ).build()
    private val dao = db.sessionDao()
    private val dir = File(context.applicationContext.filesDir, "sessions").apply { mkdirs() }

    fun fileFor(id: String): File = File(dir, "$id.ndjson")

    suspend fun createSession(startZoneId: String): SessionMeta {
        val meta = SessionMeta(
            sessionId = UUID.randomUUID().toString(),
            startEpochMs = System.currentTimeMillis(),
            startZoneId = startZoneId,
            appVersion = Constants.APP_VERSION,
            state = SessionState.RECORDING,
        )
        dao.upsert(meta.toEntity())
        fileFor(meta.sessionId).createNewFile()
        return meta
    }

    suspend fun finalize(id: String, endEpochMs: Long) =
        dao.finalize(id, endEpochMs, SessionState.FINALIZED.name)

    suspend fun markSyncing(id: String) = dao.setState(id, SessionState.SYNCING.name)
    suspend fun markSynced(id: String) = dao.setState(id, SessionState.SYNCED.name)
    suspend fun purgeFile(id: String) { fileFor(id).delete() }

    fun observeAll(): Flow<List<SessionMeta>> = dao.observeAll().map { list -> list.map { it.toMeta() } }

    fun close() { db.close() }

    /** On launch: any RECORDING row with a non-empty file → FINALIZED at last sample ts (crash recovery). */
    suspend fun recoverOrphans() {
        dao.byState(SessionState.RECORDING.name).forEach { e ->
            val f = fileFor(e.sessionId)
            if (f.exists() && f.length() > 0) {
                val lastTs = f.useLines { lines -> lines.lastOrNull { it.isNotBlank() } }
                    ?.let { Regex("\"ts\":(\\d+)").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                    ?: e.startEpochMs
                dao.finalize(e.sessionId, lastTs, SessionState.FINALIZED.name)
            }
        }
    }
}

private fun SessionMeta.toEntity() = SessionEntity(
    sessionId, startEpochMs, startZoneId, endEpochMs, exerciseType, appVersion, state.name
)
private fun SessionEntity.toMeta() = SessionMeta(
    sessionId, startEpochMs, startZoneId, endEpochMs, exerciseType, appVersion, SessionState.valueOf(state)
)
