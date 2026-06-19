package com.example.runh10.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.runh10.shared.model.SessionState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = SessionStore(context)

    @After fun tearDown() {
        store.close()
        val dbFile = context.getDatabasePath("runh10.db")
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        File(context.filesDir, "sessions").deleteRecursively()
    }

    @Test fun create_finalize_purge_lifecycle() = runTest {
        val meta = store.createSession("America/New_York")
        store.fileFor(meta.sessionId).appendText("""{"t":"hr","ts":123,"bpm":150}""" + "\n")
        store.finalize(meta.sessionId, 456)
        val finalized = store.observeAll().first().first { it.sessionId == meta.sessionId }
        assertEquals(SessionState.FINALIZED, finalized.state)
        store.markSyncing(meta.sessionId)
        val syncing = store.observeAll().first().first { it.sessionId == meta.sessionId }
        assertEquals(SessionState.SYNCING, syncing.state)
        store.markSynced(meta.sessionId)
        store.purgeFile(meta.sessionId)
        assertFalse(store.fileFor(meta.sessionId).exists())
    }
}
