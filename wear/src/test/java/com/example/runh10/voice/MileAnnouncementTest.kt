package com.example.runh10.voice

import com.example.runh10.data.RunSettings
import com.example.runh10.shared.model.Split
import org.junit.Assert.assertEquals
import org.junit.Test

class MileAnnouncementTest {
    private val split = Split(index = 3, distanceMeters = 1609.344, movingDurationMs = 522_000, avgPaceMps = 1609.344 / 510.0, avgBpm = 152, elevationGainM = 4.0)

    @Test fun builds_full_sentence() {
        val s = RunSettings(announce = true, announceSplitTime = true, announcePace = true, announceHrZone = true)
        assertEquals(
            "Mile 3. Split 8 minutes 42 seconds. Pace 8 minutes 30 per mile. Average heart rate 152, zone 4.",
            MileAnnouncement.build(split, zone = 4, s = s)
        )
    }

    @Test fun honors_toggles() {
        val s = RunSettings(announce = true, announceSplitTime = true, announcePace = false, announceHrZone = false)
        assertEquals("Mile 3. Split 8 minutes 42 seconds.", MileAnnouncement.build(split, zone = 4, s = s))
    }
}
