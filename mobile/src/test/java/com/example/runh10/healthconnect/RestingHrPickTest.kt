package com.example.runh10.healthconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestingHrPickTest {
    private val now = 1_700_000_000_000L // arbitrary fixed "now"
    private val hour = 3_600_000L

    @Test fun emptyRecords_returnsNull() {
        assertNull(RestingHrPick.pick(emptyList(), currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun singleRecordWithinWindow_newerThanCurrent_isPicked() {
        val records = listOf((now - hour) to 52)
        assertEquals(52, RestingHrPick.pick(records, currentMeasuredAtMs = now - 2 * hour, nowMs = now))
    }

    @Test fun multipleRecords_picksNewestByTimestamp() {
        val records = listOf(
            (now - 10 * hour) to 58,
            (now - 2 * hour) to 51,
            (now - 20 * hour) to 60,
        )
        assertEquals(51, RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun recordOlderThan48h_isExcluded() {
        val records = listOf((now - 49 * hour) to 50)
        assertNull(RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun recordExactlyAt48hBoundary_isIncluded() {
        val records = listOf((now - 48 * hour) to 50)
        assertEquals(50, RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun recordNotNewerThanCurrentMeasuredAt_isExcluded() {
        // manual measurement wins for its day: an HC record at or before the current
        // measurement timestamp must not override it.
        val records = listOf((now - hour) to 55)
        assertNull(RestingHrPick.pick(records, currentMeasuredAtMs = now - hour, nowMs = now))
    }

    @Test fun recordNewerThanCurrentMeasuredAt_isPicked() {
        val records = listOf((now - hour) to 55)
        assertEquals(55, RestingHrPick.pick(records, currentMeasuredAtMs = now - 2 * hour, nowMs = now))
    }

    @Test fun bpmBelowSanityFloor_isExcluded() {
        val records = listOf((now - hour) to 24)
        assertNull(RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun bpmAboveSanityCeiling_isExcluded() {
        val records = listOf((now - hour) to 101)
        assertNull(RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun bpmAtSanityBounds_isIncluded() {
        val records = listOf((now - hour) to 25, (now - 2 * hour) to 100)
        // newest of the two valid boundary records
        assertEquals(25, RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }

    @Test fun onlyInvalidRecordsPresent_returnsNull() {
        val records = listOf((now - hour) to 10, (now - 2 * hour) to 200, (now - 100 * hour) to 55)
        assertNull(RestingHrPick.pick(records, currentMeasuredAtMs = null, nowMs = now))
    }
}
