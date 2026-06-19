package com.example.runh10.workout
import com.example.runh10.shared.Constants
import org.junit.Assert.*
import org.junit.Test
class SplitTrackerTest {
    @Test fun emits_a_split_when_a_mile_completes() {
        val t = SplitTracker(splitMeters = Constants.MILE_METERS)
        assertNull(t.onSample(800.0, 240_000, 150, 0.0))
        val s = t.onSample(1610.0, 480_000, 156, 5.0)   // crosses 1 mile
        assertNotNull(s)
        assertEquals(1, s!!.index)
        assertEquals(480_000, s.movingDurationMs)
        assertTrue(s.avgPaceMps > 0)
    }
}
