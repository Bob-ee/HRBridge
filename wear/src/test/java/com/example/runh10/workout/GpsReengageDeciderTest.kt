package com.example.runh10.workout

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit spec for the pure GPS re-engagement decision unit. All timing is fed in as
 * plain millis so the logic is deterministic and free of any Health Services /
 * Android dependency.
 */
class GpsReengageDeciderTest {

    private val NOFIX = GpsReengageDecider.NO_FIX_THRESHOLD_MS      // 60_000
    private val COOL = GpsReengageDecider.REENGAGE_COOLDOWN_MS      // 120_000

    private fun decide(
        gps: String = "UNAVAILABLE",
        state: RunState = RunState.RUNNING,
        msSinceLastLoc: Long = NOFIX + 1,
        msSinceLastReengageAttempt: Long? = null,
    ) = GpsReengageDecider.decide(gps, state, msSinceLastLoc, msSinceLastReengageAttempt)

    // --- healthy stream → NONE -------------------------------------------------

    @Test fun healthy_fresh_fix_is_none() {
        assertEquals(GpsReengageAction.NONE, decide(msSinceLastLoc = 5_000))
    }

    @Test fun acquired_tethered_is_none_even_after_long_gap() {
        // A live availability of ACQUIRED means GPS is working; the loc gap is moot.
        assertEquals(
            GpsReengageAction.NONE,
            decide(gps = "ACQUIRED_TETHERED", msSinceLastLoc = 5 * NOFIX),
        )
    }

    @Test fun acquired_untethered_is_none() {
        assertEquals(
            GpsReengageAction.NONE,
            decide(gps = "ACQUIRED_UNTETHERED", msSinceLastLoc = 5 * NOFIX),
        )
    }

    @Test fun exactly_at_threshold_is_still_healthy() {
        assertEquals(GpsReengageAction.NONE, decide(msSinceLastLoc = NOFIX))
    }

    // --- warmup / paused → NONE ------------------------------------------------

    @Test fun warmup_never_reengages() {
        assertEquals(GpsReengageAction.NONE, decide(state = RunState.WARMUP))
    }

    @Test fun auto_paused_never_reengages() {
        assertEquals(GpsReengageAction.NONE, decide(state = RunState.AUTO_PAUSED))
    }

    @Test fun manual_paused_never_reengages() {
        assertEquals(GpsReengageAction.NONE, decide(state = RunState.MANUAL_PAUSED))
    }

    // --- first breach → REENGAGE ----------------------------------------------

    @Test fun silent_over_threshold_while_running_reengages() {
        assertEquals(
            GpsReengageAction.REENGAGE,
            decide(gps = "UNAVAILABLE", msSinceLastLoc = NOFIX + 1, msSinceLastReengageAttempt = null),
        )
    }

    @Test fun no_gnss_string_counts_as_silent() {
        assertEquals(
            GpsReengageAction.REENGAGE,
            decide(gps = "NO_GNSS", msSinceLastReengageAttempt = null),
        )
    }

    @Test fun unknown_string_counts_as_silent() {
        assertEquals(
            GpsReengageAction.REENGAGE,
            decide(gps = "UNKNOWN", msSinceLastReengageAttempt = null),
        )
    }

    // --- throttle: max one REENGAGE per cooldown -------------------------------

    @Test fun does_not_reengage_again_within_cooldown() {
        assertEquals(
            GpsReengageAction.NONE,
            decide(msSinceLastLoc = 5 * NOFIX, msSinceLastReengageAttempt = COOL - 1),
        )
    }

    // --- escalation: still silent a cooldown after the attempt → FALLBACK ------

    @Test fun escalates_to_fallback_after_cooldown_with_no_fix() {
        assertEquals(
            GpsReengageAction.FALLBACK,
            decide(msSinceLastLoc = 5 * NOFIX, msSinceLastReengageAttempt = COOL),
        )
    }

    @Test fun escalates_to_fallback_well_after_cooldown() {
        assertEquals(
            GpsReengageAction.FALLBACK,
            decide(msSinceLastLoc = 10 * NOFIX, msSinceLastReengageAttempt = 3 * COOL),
        )
    }

    @Test fun recovery_during_cooldown_returns_none() {
        // A fix arrived after the re-engage attempt: the loc gap is fresh again, so
        // even though an attempt was logged, we are healthy → NONE (caller clears state).
        assertEquals(
            GpsReengageAction.NONE,
            decide(msSinceLastLoc = 2_000, msSinceLastReengageAttempt = COOL + 5_000),
        )
    }

    @Test fun paused_after_attempt_does_not_fall_back() {
        // If the user paused, we stand down regardless of the silent GPS + prior attempt.
        assertEquals(
            GpsReengageAction.NONE,
            decide(state = RunState.MANUAL_PAUSED, msSinceLastReengageAttempt = 3 * COOL),
        )
    }
}
