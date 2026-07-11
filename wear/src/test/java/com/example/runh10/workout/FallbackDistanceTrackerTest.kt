package com.example.runh10.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit spec for the pure fallback-distance accumulator. A 0.0001° latitude step at
 * R = 6 371 000 m is ~11.1195 m — used as the canonical "one running stride burst"
 * throughout; a 0.01° step (~1 112 m) is the canonical teleport.
 */
class FallbackDistanceTrackerTest {

    /** Metres per 0.0001° of latitude on the tracker's spherical model. */
    private val STEP_M = Math.toRadians(0.0001) * 6_371_000.0   // ≈ 11.1195

    // --- anchoring -------------------------------------------------------------

    @Test fun first_fix_anchors_only_no_delta() {
        val t = FallbackDistanceTracker(baseDistanceM = 1_000.0)
        assertEquals(1_000.0, t.onFix(40.0, -75.0), 1e-9)
        assertEquals(1_000.0, t.totalM, 1e-9)
    }

    @Test fun zero_base_first_fix_is_zero() {
        val t = FallbackDistanceTracker(baseDistanceM = 0.0)
        assertEquals(0.0, t.onFix(40.0, -75.0), 1e-9)
    }

    // --- accumulation ----------------------------------------------------------

    @Test fun consecutive_fixes_accumulate_delta() {
        val t = FallbackDistanceTracker(baseDistanceM = 500.0)
        t.onFix(40.0, -75.0)
        assertEquals(500.0 + STEP_M, t.onFix(40.0001, -75.0), 0.05)
    }

    @Test fun three_fixes_accumulate_both_deltas() {
        val t = FallbackDistanceTracker(baseDistanceM = 500.0)
        t.onFix(40.0, -75.0)
        t.onFix(40.0001, -75.0)
        assertEquals(500.0 + 2 * STEP_M, t.onFix(40.0002, -75.0), 0.1)
    }

    @Test fun step_just_under_gate_is_accepted() {
        // 0.0008° ≈ 89 m — a fast sprint second, but under the 100 m gate.
        val t = FallbackDistanceTracker(baseDistanceM = 0.0)
        t.onFix(40.0, -75.0)
        assertEquals(8 * STEP_M, t.onFix(40.0008, -75.0), 0.1)
    }

    // --- teleport gate ---------------------------------------------------------

    @Test fun teleport_over_100m_is_ignored() {
        val t = FallbackDistanceTracker(baseDistanceM = 250.0)
        t.onFix(40.0, -75.0)
        // 0.01° ≈ 1 112 m in one second: not running, a GPS jump. No credit.
        assertEquals(250.0, t.onFix(40.01, -75.0), 1e-9)
    }

    @Test fun teleport_reanchors_so_next_delta_is_from_new_position() {
        val t = FallbackDistanceTracker(baseDistanceM = 250.0)
        t.onFix(40.0, -75.0)
        t.onFix(40.01, -75.0)                       // teleport: ignored, re-anchored
        assertEquals(250.0 + STEP_M, t.onFix(40.0101, -75.0), 0.05)
    }

    @Test fun non_finite_delta_is_ignored() {
        val t = FallbackDistanceTracker(baseDistanceM = 100.0)
        t.onFix(40.0, -75.0)
        assertEquals(100.0, t.onFix(Double.NaN, Double.NaN), 1e-9)
        // Recovers cleanly: the NaN anchor yields another ignored delta, then re-anchors.
        assertEquals(100.0, t.onFix(40.0001, -75.0), 1e-9)
        assertEquals(100.0 + STEP_M, t.onFix(40.0002, -75.0), 0.05)
    }

    // --- monotonic floor (applied where HS distance lands in ExerciseMetrics) ---

    @Test fun monotonic_floor_never_decreases() {
        // Post-recovery HS total doesn't know about the fallback segment: lower
        // incoming must NOT regress the run's cumulative distance.
        assertEquals(1_500.0, FallbackDistanceTracker.monotonic(1_200.0, 1_500.0)!!, 1e-9)
    }

    @Test fun monotonic_advances_when_incoming_exceeds_floor() {
        assertEquals(1_600.0, FallbackDistanceTracker.monotonic(1_600.0, 1_500.0)!!, 1e-9)
    }

    @Test fun monotonic_null_handling() {
        assertEquals(1_500.0, FallbackDistanceTracker.monotonic(null, 1_500.0)!!, 1e-9)
        assertEquals(1_200.0, FallbackDistanceTracker.monotonic(1_200.0, null)!!, 1e-9)
        assertNull(FallbackDistanceTracker.monotonic(null, null))
    }
}
