package com.example.runh10.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.runHistoryDataStore by preferencesDataStore(name = "run_history")

/** Aggregates of the most recent finished run — feeds the quick-launch tile and readiness card. */
data class LastRun(
    val name: String,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val elapsedSec: Long,
    val avgBpm: Int?,
    val hrvMs: Double?,
    /** HRV of the run before this one, for the tile's "↑ 3" delta. */
    val prevHrvMs: Double?,
)

class RunHistoryPrefs(private val context: Context) {
    private object K {
        val NAME = stringPreferencesKey("last_name")
        val ENDED = longPreferencesKey("last_ended_ms")
        val DIST = doublePreferencesKey("last_dist_m")
        val ELAPSED = longPreferencesKey("last_elapsed_sec")
        val AVG_BPM = intPreferencesKey("last_avg_bpm")
        val HRV = doublePreferencesKey("last_hrv_ms")
        val PREV_HRV = doublePreferencesKey("prev_hrv_ms")
    }

    val lastRun: Flow<LastRun?> = context.runHistoryDataStore.data.map { p ->
        val ended = p[K.ENDED] ?: return@map null
        LastRun(
            name = p[K.NAME] ?: "Run",
            endedAtMs = ended,
            distanceMeters = p[K.DIST] ?: 0.0,
            elapsedSec = p[K.ELAPSED] ?: 0L,
            avgBpm = p[K.AVG_BPM],
            hrvMs = p[K.HRV],
            prevHrvMs = p[K.PREV_HRV],
        )
    }

    suspend fun record(name: String, endedAtMs: Long, distanceMeters: Double, elapsedSec: Long, avgBpm: Int?, hrvMs: Double?) {
        val previousHrv = context.runHistoryDataStore.data.first()[K.HRV]
        context.runHistoryDataStore.edit { p ->
            p[K.NAME] = name
            p[K.ENDED] = endedAtMs
            p[K.DIST] = distanceMeters
            p[K.ELAPSED] = elapsedSec
            if (avgBpm != null) p[K.AVG_BPM] = avgBpm else p.remove(K.AVG_BPM)
            previousHrv?.let { p[K.PREV_HRV] = it }
            if (hrvMs != null) p[K.HRV] = hrvMs else p.remove(K.HRV)
        }
    }
}

/** "Morning Run" / "Afternoon Tempo"-free default naming by local hour. */
fun defaultRunName(hourOfDay: Int): String = when (hourOfDay) {
    in 4..11 -> "Morning Run"
    in 12..16 -> "Afternoon Run"
    in 17..20 -> "Evening Run"
    else -> "Night Run"
}
