package com.example.runh10.shared.serial

import com.example.runh10.shared.model.EvtRow
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import org.junit.Assert.assertEquals
import org.junit.Test

class NdjsonSerializerTest {
    @Test fun encodes_hr_row_as_tagged_json_line() {
        val line = NdjsonSerializer.encode(HrRow(ts = 1000, bpm = 152))
        assertEquals("""{"t":"hr","ts":1000,"bpm":152}""", line)
    }

    @Test fun encodes_evt_row_with_gps_and_state() {
        val line = NdjsonSerializer.encode(EvtRow(ts = 1000, gps = "ACQUIRED", state = "ACTIVE_ENDED"))
        assertEquals("""{"t":"evt","ts":1000,"gps":"ACQUIRED","state":"ACTIVE_ENDED"}""", line)
    }

    @Test fun round_trips_an_evt_row_with_only_one_field() {
        val row = EvtRow(ts = 2000, gps = "UNAVAILABLE")
        assertEquals(row, NdjsonSerializer.decode(NdjsonSerializer.encode(row)))
    }

    @Test fun round_trips_a_loc_row() {
        val row = LocRow(ts = 2000, lat = 42.1, lon = -71.2, spd = 3.1, dist = 12.0)
        assertEquals(row, NdjsonSerializer.decode(NdjsonSerializer.encode(row)))
    }

    @Test fun tolerant_decode_drops_a_truncated_final_line() {
        val good = NdjsonSerializer.encode(HrRow(ts = 1, bpm = 100))
        val lines = sequenceOf(good, """{"t":"loc","ts":20""", "")  // truncated + blank
        val rows = NdjsonSerializer.decodeTolerant(lines)
        assertEquals(1, rows.size)
        assertEquals(HrRow(ts = 1, bpm = 100), rows.first())
    }
}
