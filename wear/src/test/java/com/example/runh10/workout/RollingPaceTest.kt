package com.example.runh10.workout
import org.junit.Assert.assertEquals
import org.junit.Test
class RollingPaceTest {
    @Test fun pace_is_window_distance_over_window_time() {
        val p = RollingPace(windowMs = 30_000)
        p.add(0, 0.0); p.add(10_000, 30.0); p.add(20_000, 60.0)
        // 60m over 20s = 3.0 m/s
        assertEquals(3.0, p.paceMps()!!, 0.05)
    }
}
