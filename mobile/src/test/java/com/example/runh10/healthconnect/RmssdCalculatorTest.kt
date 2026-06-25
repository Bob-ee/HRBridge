package com.example.runh10.healthconnect

import com.example.runh10.shared.model.RrRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RmssdCalculatorTest {
    // 20 intervals alternating 800/810 ms, 100 ms apart, all in window [0,30000).
    // successive diffs are all ±10 -> squared 100 -> mean 100 -> rmssd 10.0
    private fun alternating(n: Int, startTs: Long = 0L) = (0 until n).map {
        RrRow(ts = startTs + it * 100L, rr = if (it % 2 == 0) 800 else 810)
    }

    @Test fun fullWindow_computesRmssd_atWindowEnd() {
        val out = RmssdCalculator.compute(alternating(20))
        assertEquals(1, out.size)
        assertEquals(30_000L, out[0].tsMs)          // window end
        assertEquals(10.0, out[0].rmssdMs, 1e-9)
    }

    @Test fun sparseWindow_isSkipped() {
        assertTrue(RmssdCalculator.compute(alternating(10)).isEmpty())
    }

    @Test fun implausibleRr_isFiltered() {
        // 19 good + many garbage (<300 / >2000) -> still < 20 valid -> skipped
        val rows = alternating(19) + List(10) { RrRow(ts = 1900L + it, rr = 5000) }
        assertTrue(RmssdCalculator.compute(rows).isEmpty())
    }

    @Test fun emptyInput_returnsEmpty() {
        assertTrue(RmssdCalculator.compute(emptyList()).isEmpty())
    }
}
