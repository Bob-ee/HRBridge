package com.example.runh10.shared.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryLevelParserTest {
    @Test fun parses_a_typical_mid_range_level() {
        assertEquals(87, BatteryLevelParser.parse(byteArrayOf(87)))
    }

    @Test fun parses_the_minimum_valid_level() {
        assertEquals(0, BatteryLevelParser.parse(byteArrayOf(0)))
    }

    @Test fun parses_the_maximum_valid_level() {
        assertEquals(100, BatteryLevelParser.parse(byteArrayOf(100)))
    }

    @Test fun returns_null_for_an_empty_payload() {
        assertNull(BatteryLevelParser.parse(byteArrayOf()))
    }

    @Test fun returns_null_for_a_value_above_100() {
        assertNull(BatteryLevelParser.parse(byteArrayOf(101)))
    }

    @Test fun returns_null_for_a_raw_byte_that_reads_negative_but_is_out_of_range_unsigned() {
        // 0xFF as a signed Kotlin Byte is -1; as an unsigned uint8 it's 255 — out of range.
        assertNull(BatteryLevelParser.parse(byteArrayOf(0xFF.toByte())))
    }

    @Test fun ignores_trailing_bytes_and_reads_only_the_first() {
        assertEquals(42, BatteryLevelParser.parse(byteArrayOf(42, 99, 7)))
    }
}
