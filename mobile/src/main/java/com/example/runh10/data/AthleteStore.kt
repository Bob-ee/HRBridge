package com.example.runh10.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.athleteDataStore by preferencesDataStore(name = "athlete")

/**
 * Athlete profile + phone-app settings. Max/resting HR drive every zone in the app
 * (%HRR / Karvonen via the shared ZoneCalculator).
 */
data class AthleteProfile(
    val name: String = "Runner",
    val subtitle: String = "",             // e.g. "Runner since 2021 · Portland, OR"
    val maxHr: Int? = null,
    val restingHr: Int? = null,
    val restingMeasuredAtMs: Long? = null,
    val autoUpdateResting: Boolean = true,
    val unitsMiles: Boolean = true,
    val voiceCoach: Boolean = true,
    val mileAnnouncements: Boolean = true,
    val autoPause: Boolean = false,
    val measureDurationSec: Int = 60,      // 30/60/90/120
)

class AthleteStore(private val context: Context) {
    private object K {
        val NAME = stringPreferencesKey("name")
        val SUBTITLE = stringPreferencesKey("subtitle")
        val MAX = intPreferencesKey("max_hr")
        val REST = intPreferencesKey("resting_hr")
        val REST_AT = longPreferencesKey("resting_measured_at")
        val AUTO_REST = booleanPreferencesKey("auto_update_resting")
        val MILES = booleanPreferencesKey("units_miles")
        val VOICE = booleanPreferencesKey("voice_coach")
        val MILE_ANN = booleanPreferencesKey("mile_announcements")
        val AUTOPAUSE = booleanPreferencesKey("auto_pause")
        val MEASURE_DUR = intPreferencesKey("measure_duration_sec")
    }

    val profile: Flow<AthleteProfile> = context.athleteDataStore.data.map { p ->
        AthleteProfile(
            name = p[K.NAME] ?: "Runner",
            subtitle = p[K.SUBTITLE] ?: "",
            maxHr = p[K.MAX],
            restingHr = p[K.REST],
            restingMeasuredAtMs = p[K.REST_AT],
            autoUpdateResting = p[K.AUTO_REST] ?: true,
            unitsMiles = p[K.MILES] ?: true,
            voiceCoach = p[K.VOICE] ?: true,
            mileAnnouncements = p[K.MILE_ANN] ?: true,
            autoPause = p[K.AUTOPAUSE] ?: false,
            measureDurationSec = p[K.MEASURE_DUR] ?: 60,
        )
    }

    suspend fun current(): AthleteProfile = profile.first()

    suspend fun setName(v: String) = context.athleteDataStore.edit { it[K.NAME] = v }
    suspend fun setSubtitle(v: String) = context.athleteDataStore.edit { it[K.SUBTITLE] = v }
    suspend fun setMaxHr(v: Int) = context.athleteDataStore.edit { it[K.MAX] = v }
    suspend fun setRestingHr(v: Int) = context.athleteDataStore.edit {
        it[K.REST] = v
        it[K.REST_AT] = System.currentTimeMillis()
    }
    suspend fun setAutoUpdateResting(v: Boolean) = context.athleteDataStore.edit { it[K.AUTO_REST] = v }
    suspend fun setUnitsMiles(v: Boolean) = context.athleteDataStore.edit { it[K.MILES] = v }
    suspend fun setVoiceCoach(v: Boolean) = context.athleteDataStore.edit { it[K.VOICE] = v }
    suspend fun setMileAnnouncements(v: Boolean) = context.athleteDataStore.edit { it[K.MILE_ANN] = v }
    suspend fun setAutoPause(v: Boolean) = context.athleteDataStore.edit { it[K.AUTOPAUSE] = v }
    suspend fun setMeasureDuration(v: Int) = context.athleteDataStore.edit { it[K.MEASURE_DUR] = v }
}
