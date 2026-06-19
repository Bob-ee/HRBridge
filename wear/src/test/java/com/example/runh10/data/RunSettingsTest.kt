package com.example.runh10.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunSettingsTest {

    @Test fun effectiveMaxHr_explicit_maxHr_wins_over_age_estimate() {
        val s = RunSettings(age = 30, maxHr = 175)
        assertEquals(175, s.effectiveMaxHr())
    }

    @Test fun effectiveMaxHr_age_only_returns_tanaka_estimate() {
        // MaxHr.estimate(30) = 208 - 0.7*30 = 187
        val s = RunSettings(age = 30, maxHr = null)
        assertEquals(187, s.effectiveMaxHr())
    }

    @Test fun effectiveMaxHr_both_null_returns_null() {
        val s = RunSettings(age = null, maxHr = null)
        assertNull(s.effectiveMaxHr())
    }
}
