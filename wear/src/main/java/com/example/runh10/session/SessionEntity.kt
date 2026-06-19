package com.example.runh10.session

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.runh10.shared.model.SessionState

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val startEpochMs: Long,
    val startZoneId: String,
    val endEpochMs: Long?,
    val exerciseType: String,
    val appVersion: String,
    val state: String,   // SessionState.name
)

class SessionStateConverters {
    @TypeConverter fun toState(v: String): SessionState = SessionState.valueOf(v)
    @TypeConverter fun fromState(s: SessionState): String = s.name
}
