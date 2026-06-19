package com.example.runh10.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionClassifierTest {
    private val c = MotionClassifier(runSustainMs = 10_000, idleSustainMs = 4_000)

    @Test fun running_only_after_cadence_sustained() {
        assertEquals(Motion.WALKING, c.feed(cadenceSpm = 160.0, speedMps = 2.2, nowMs = 0))     // just crossed
        assertEquals(Motion.WALKING, c.feed(160.0, 2.2, 5_000))                                  // not yet 10s
        assertEquals(Motion.RUNNING, c.feed(160.0, 2.2, 11_000))                                 // sustained
    }

    @Test fun speed_walking_does_not_trigger_running() {
        // fast walk: high-ish speed but low cadence
        assertEquals(Motion.WALKING, c.feed(120.0, 2.0, 0))
        assertEquals(Motion.WALKING, c.feed(120.0, 2.0, 20_000))
    }

    @Test fun idle_after_stopped_sustained() {
        c.feed(160.0, 2.2, 0); c.feed(160.0, 2.2, 11_000)   // RUNNING
        assertEquals(Motion.RUNNING, c.feed(0.0, 0.0, 12_000))     // just stopped
        assertEquals(Motion.IDLE, c.feed(0.0, 0.0, 16_500))        // stopped > 4s
    }
}
