package com.example.runh10.parse

import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionParserTest {
    private val meta = SessionMeta(
        sessionId = "s1", startEpochMs = 1000L, startZoneId = "America/New_York",
        endEpochMs = 3000L, appVersion = "1.0", state = SessionState.FINALIZED,
    )

    @Test fun parses_sorts_and_tolerates_truncated_tail() {
        val lines = sequenceOf(
            """{"t":"hr","ts":2000,"bpm":150}""",
            """{"t":"loc","ts":1500,"lat":40.0,"lon":-73.0,"dist":100.0}""",
            "",                                   // blank skipped
            """{"t":"hr","ts":2500,"bpm":1""",    // truncated tail skipped
        )
        val bundle = SessionParser.parse(meta, lines)
        assertEquals(meta, bundle.meta)
        assertEquals(listOf(1500L, 2000L), bundle.samples.map { it.ts })  // sorted, garbage dropped
        assertTrue(bundle.samples[0] is LocRow)
        assertTrue(bundle.samples[1] is HrRow)
        assertTrue(bundle.splits.isEmpty())      // splits deferred to Phase 4
    }
}
