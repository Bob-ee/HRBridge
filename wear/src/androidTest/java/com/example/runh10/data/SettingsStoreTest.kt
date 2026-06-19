package com.example.runh10.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @Before fun clearStore() = runTest {
        // Clear any persisted state so tests are repeatable
        store.clear()
    }

    @Test fun age_drives_effective_max_when_no_explicit_max() = runTest {
        store.setAge(30)
        val s = store.settings.first()
        assertEquals(187, s.effectiveMaxHr())
    }
}
