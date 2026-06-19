package com.example.runh10.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class RunClockTest {
    private var t = 0L
    private val clock = RunClock { t }

    @Test fun moving_excludes_paused_interval_elapsed_does_not() {
        t = 1000; clock.start()
        t = 4000                    // +3s running
        clock.pause()
        t = 9000                    // +5s paused
        clock.resume()
        t = 11000                   // +2s running
        assertEquals(10000, clock.elapsedMs())   // 11000-1000
        assertEquals(5000, clock.movingMs())     // 3000 + 2000
    }
}
