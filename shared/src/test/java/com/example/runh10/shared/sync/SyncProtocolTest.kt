package com.example.runh10.shared.sync

import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProtocolTest {
    private fun meta(id: String) = SessionMeta(
        sessionId = id, startEpochMs = 1000L, startZoneId = "America/New_York",
        endEpochMs = 2000L, appVersion = "1.0", state = SessionState.FINALIZED,
    )

    @Test fun metaList_roundTrips() {
        val list = listOf(meta("a"), meta("b"))
        val decoded = SyncProtocol.decodeMetaList(SyncProtocol.encodeMetaList(list))
        assertEquals(list, decoded)
    }

    @Test fun paths_are_built_and_parsed() {
        assertEquals("/runh10/start_transfer/x", SyncProtocol.pathStartTransfer("x"))
        assertEquals("/runh10/session/x", SyncProtocol.pathSession("x"))
        assertEquals("/runh10/ack/x", SyncProtocol.pathAck("x"))
        assertEquals("x", SyncProtocol.idFromPath("/runh10/ack/", "/runh10/ack/x"))
        assertNull(SyncProtocol.idFromPath("/runh10/ack/", "/runh10/session/x"))
    }
}
