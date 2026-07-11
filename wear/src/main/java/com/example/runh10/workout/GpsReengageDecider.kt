package com.example.runh10.workout

/** What the pipeline should do about a (possibly) silent Health Services GPS stream. */
enum class GpsReengageAction { NONE, REENGAGE, FALLBACK }

/**
 * Pure, dependency-free decision unit for the GPS-cutout defence.
 *
 * The June 30 forensics (docs/superpowers/plans/notes/2026-07-10-gps-cutout-diagnosis.md)
 * show Health Services location dies ~45 s into a run and never recovers while the
 * exercise stays ACTIVE and HR keeps flowing. The addendum records that waking the
 * watch did NOT restore GPS — so passively waiting is not enough; we must actively
 * re-assert the stream and, failing that, fall back to a direct location source.
 *
 * The escalation is deliberately conservative:
 *  - no fix for > [NO_FIX_THRESHOLD_MS] while RUNNING and not ACQUIRED → [REENGAGE]
 *    (re-assert the Health Services update stream), at most once per [REENGAGE_COOLDOWN_MS];
 *  - if a further [REENGAGE_COOLDOWN_MS] elapses after that attempt with still no fix →
 *    [FALLBACK] (start a direct FusedLocationProvider stream);
 *  - WARMUP / paused, or a healthy stream → [NONE].
 *
 * Everything is fed in as plain values so this is trivially testable and the caller
 * owns all the state (last-fix time, last-attempt time, run state).
 */
object GpsReengageDecider {

    /** A location gap longer than this (while not ACQUIRED) is treated as "silent". */
    const val NO_FIX_THRESHOLD_MS = 60_000L

    /**
     * Minimum spacing between re-engage attempts AND the grace window after an attempt
     * before we give up on Health Services and escalate to the fallback.
     */
    const val REENGAGE_COOLDOWN_MS = 120_000L

    /**
     * @param gpsAvailability the latest LocationAvailability string (e.g. "ACQUIRED_TETHERED",
     *        "UNAVAILABLE", "NO_GNSS", "UNKNOWN"). "ACQUIRED*" means GPS is live.
     * @param runState the run state machine's current state.
     * @param msSinceLastLoc millis since the last *Health Services* location fix.
     * @param msSinceLastReengageAttempt millis since the last REENGAGE attempt this run,
     *        or null if none has been attempted (or it was cleared on recovery).
     */
    fun decide(
        gpsAvailability: String,
        runState: RunState,
        msSinceLastLoc: Long,
        msSinceLastReengageAttempt: Long?,
    ): GpsReengageAction {
        // Only intervene during an active run. WARMUP (GPS still acquiring) and either
        // paused state must stand down — a paused runner isn't moving and shouldn't
        // trigger location churn.
        if (runState != RunState.RUNNING) return GpsReengageAction.NONE

        // Healthy: GPS is acquired, or we have a fix within the no-fix window.
        if (isAcquired(gpsAvailability) || msSinceLastLoc <= NO_FIX_THRESHOLD_MS) {
            return GpsReengageAction.NONE
        }

        // GPS is silent (no fix past the threshold, availability not acquired) mid-run.
        val sinceAttempt = msSinceLastReengageAttempt
            ?: return GpsReengageAction.REENGAGE   // never tried this run → re-assert now

        // Already re-engaged once. Wait out the cooldown; if the grace window has fully
        // elapsed and GPS is still silent, Health Services isn't coming back → fall back.
        return if (sinceAttempt >= REENGAGE_COOLDOWN_MS) GpsReengageAction.FALLBACK
        else GpsReengageAction.NONE
    }

    private fun isAcquired(availability: String): Boolean =
        availability.contains("ACQUIRED", ignoreCase = true)
}
