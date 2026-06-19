package com.example.runh10.session

import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.serial.NdjsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderParseTest {
    @Test fun partial_file_parses_every_complete_line() {
        val body = buildString {
            append(NdjsonSerializer.encode(HrRow(ts = 1, bpm = 150))).append("\n")
            append(NdjsonSerializer.encode(HrRow(ts = 2, bpm = 151))).append("\n")
            append("""{"t":"loc","ts":3,"lat":42.0""")   // crash-truncated, no newline
        }
        val rows = NdjsonSerializer.decodeTolerant(body.lineSequence())
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is HrRow })
    }
}
