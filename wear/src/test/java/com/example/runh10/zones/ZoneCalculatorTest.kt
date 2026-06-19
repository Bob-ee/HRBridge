package com.example.runh10.zones

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneCalculatorTest {
    // maxHr 185, resting 50 -> reserve 135. Boundaries: 50%,60%,70%,80%,90% HRR.
    private val z = ZoneCalculator(maxHr = 185, restingHr = 50)

    @Test fun boundary_uses_hr_reserve() {
        assertEquals(50 + (0.8 * 135).toInt(), z.boundary(0.8))   // 158
    }

    @Test fun classifies_into_five_zones() {
        assertEquals(1, z.zoneFor(100))   // ~37% HRR -> Z1
        assertEquals(4, z.zoneFor(158))   // 80% HRR -> Z4
        assertEquals(5, z.zoneFor(180))   // ~96% HRR -> Z5
    }

    @Test fun estimates_max_from_age_tanaka() {
        assertEquals(187, MaxHr.estimate(30))   // 208 - 0.7*30 = 187
    }
}
