package com.example.runh10.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.runh10.zones.MaxHr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class RunSettings(
    val age: Int? = null,
    val maxHr: Int? = null,
    val restingHr: Int? = null,
    val announce: Boolean = true,
    val announceSplitTime: Boolean = true,
    val announcePace: Boolean = true,
    val announceHrZone: Boolean = true,
    val autoPause: Boolean = true,
)

fun RunSettings.effectiveMaxHr(): Int? = maxHr ?: age?.let { MaxHr.estimate(it) }

class SettingsStore(private val context: Context) {
    private object K {
        val AGE = intPreferencesKey("age")
        val MAX = intPreferencesKey("max_hr")
        val REST = intPreferencesKey("resting_hr")
        val ANN = booleanPreferencesKey("announce")
        val ANN_SPLIT = booleanPreferencesKey("ann_split")
        val ANN_PACE = booleanPreferencesKey("ann_pace")
        val ANN_ZONE = booleanPreferencesKey("ann_zone")
        val AUTOPAUSE = booleanPreferencesKey("autopause")
    }

    val settings: Flow<RunSettings> = context.settingsDataStore.data.map { p ->
        RunSettings(
            age = p[K.AGE], maxHr = p[K.MAX], restingHr = p[K.REST],
            announce = p[K.ANN] ?: true,
            announceSplitTime = p[K.ANN_SPLIT] ?: true,
            announcePace = p[K.ANN_PACE] ?: true,
            announceHrZone = p[K.ANN_ZONE] ?: true,
            autoPause = p[K.AUTOPAUSE] ?: true,
        )
    }

    suspend fun setAge(v: Int) = context.settingsDataStore.edit { it[K.AGE] = v }
    suspend fun setMaxHr(v: Int) = context.settingsDataStore.edit { it[K.MAX] = v }
    suspend fun setRestingHr(v: Int) = context.settingsDataStore.edit { it[K.REST] = v }
    suspend fun setAnnounce(v: Boolean) = context.settingsDataStore.edit { it[K.ANN] = v }
    suspend fun setAnnounceSplitTime(v: Boolean) = context.settingsDataStore.edit { it[K.ANN_SPLIT] = v }
    suspend fun setAnnouncePace(v: Boolean) = context.settingsDataStore.edit { it[K.ANN_PACE] = v }
    suspend fun setAnnounceHrZone(v: Boolean) = context.settingsDataStore.edit { it[K.ANN_ZONE] = v }
    suspend fun setAutoPause(v: Boolean) = context.settingsDataStore.edit { it[K.AUTOPAUSE] = v }
    suspend fun clear() = context.settingsDataStore.edit { it.clear() }
}
