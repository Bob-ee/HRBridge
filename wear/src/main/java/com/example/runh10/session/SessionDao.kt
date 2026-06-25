package com.example.runh10.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: SessionEntity)
    @Query("UPDATE sessions SET state = :state WHERE sessionId = :id") suspend fun setState(id: String, state: String)
    @Query("UPDATE sessions SET state = :state, endEpochMs = :end WHERE sessionId = :id") suspend fun finalize(id: String, end: Long, state: String)
    @Query("SELECT * FROM sessions ORDER BY startEpochMs DESC") fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE state = :state") suspend fun byState(state: String): List<SessionEntity>
    @Query("SELECT * FROM sessions WHERE state IN ('FINALIZED','SYNCING') ORDER BY startEpochMs ASC")
    suspend fun unsynced(): List<SessionEntity>
}
