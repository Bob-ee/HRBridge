package com.example.runh10.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePrefsTest {
    private val prefs = DevicePrefs(ApplicationProvider.getApplicationContext())

    @Test fun saves_and_clears_last_device() = runTest {
        prefs.clear()
        assertNull(prefs.lastDevice.first())
        prefs.saveLastDevice("AA:BB:CC", "Polar H10")
        assertEquals("AA:BB:CC" to "Polar H10", prefs.lastDevice.first())
        prefs.clear()
        assertNull(prefs.lastDevice.first())
    }
}
