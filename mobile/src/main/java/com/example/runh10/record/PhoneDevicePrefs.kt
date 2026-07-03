package com.example.runh10.record

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.phoneBleDataStore by preferencesDataStore(name = "phone_ble_prefs")

/** Remembered H10 strap for the phone record loop (mirrors the watch's DevicePrefs). */
class PhoneDevicePrefs(private val context: Context) {
    private val keyMac = stringPreferencesKey("last_device_mac")
    private val keyName = stringPreferencesKey("last_device_name")

    val lastDevice: Flow<Pair<String, String>?> = context.phoneBleDataStore.data.map { prefs ->
        val mac = prefs[keyMac]
        val name = prefs[keyName]
        if (mac != null && name != null) mac to name else null
    }

    suspend fun saveLastDevice(mac: String, name: String) {
        context.phoneBleDataStore.edit { it[keyMac] = mac; it[keyName] = name }
    }

    suspend fun clear() {
        context.phoneBleDataStore.edit { it.remove(keyMac); it.remove(keyName) }
    }
}
