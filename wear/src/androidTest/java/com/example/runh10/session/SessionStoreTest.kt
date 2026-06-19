package com.example.runh10.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runh10.shared.model.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val store = SessionStore(ApplicationProvider.getApplicationContext())

    @Test fun create_finalize_purge_lifecycle() = runTest {
        val meta = store.createSession("America/New_York")
        store.fileFor(meta.sessionId).appendText("""{"t":"hr","ts":123,"bpm":150}""" + "\n")
        store.finalize(meta.sessionId, 456)
        val finalized = store.observeAll().first().first { it.sessionId == meta.sessionId }
        assertEquals(SessionState.FINALIZED, finalized.state)
        store.markSynced(meta.sessionId)
        store.purgeFile(meta.sessionId)
        assertFalse(store.fileFor(meta.sessionId).exists())
    }
}
