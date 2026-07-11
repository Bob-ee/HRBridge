package com.example.runh10.workout

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Pure accumulator that turns raw fallback (FusedLocationProvider) fixes into the
 * run's cumulative distance while Health Services is silent.
 *
 * Health Services owns `DISTANCE_TOTAL` on the happy path; during a GPS cutout that
 * total freezes at the cutout instant, so the whole fallback segment would otherwise
 * contribute zero distance to splits, rolling pace and the run summary. This class
 * integrates the fused fixes on top of a snapshot of the frozen HS total:
 *
 *  - the FIRST fused fix only anchors — the gap between the last HS position and the
 *    first fused fix is unknowable, and we never fabricate distance;
 *  - each subsequent fix adds the equirectangular delta from the previous fix, gated
 *    by [MAX_STEP_M] so a GPS teleport can't inflate the total (the anchor still
 *    moves, so tracking resumes honestly from the new position).
 *
 * Owned by ExerciseClientManager; created on fallback start, dropped on teardown.
 * Pure and dependency-free so the arithmetic is unit-testable
 * (see FallbackDistanceTrackerTest).
 */
class FallbackDistanceTracker(private val baseDistanceM: Double) {

    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var accumM = 0.0

    /** Cumulative run distance so far: HS snapshot + accumulated fallback metres. */
    val totalM: Double get() = baseDistanceM + accumM

    /**
     * Feeds one real fused fix and returns the new cumulative distance.
     * First fix anchors only; later fixes accumulate the gated delta.
     */
    fun onFix(lat: Double, lon: Double): Double {
        if (!anchorLat.isNaN() && !anchorLon.isNaN()) {
            val d = roughMeters(anchorLat, anchorLon, lat, lon)
            if (d.isFinite() && d < MAX_STEP_M) accumM += d
        }
        anchorLat = lat
        anchorLon = lon
        return totalM
    }

    companion object {
        /**
         * A single ~1 Hz step at or beyond this is a GPS teleport, not running
         * (100 m/s = 360 km/h) — the delta is discarded, the anchor still advances.
         */
        const val MAX_STEP_M = 100.0

        /**
         * Monotonic floor for the run's cumulative distance, applied at the single
         * point where an HS `DISTANCE_TOTAL` lands in ExerciseMetrics. After a
         * fallback segment, HS's own total does NOT include the fallback metres, so
         * a recovered HS value is LOWER than our accumulated total and would regress
         * `max(dist)` in the summary. Taking the max means distance never jumps
         * backward; the honesty tradeoff is that post-recovery HS deltas only resume
         * counting once HS's total climbs past the floor, so the run may modestly
         * UNDER-count after recovery — never fabricate.
         */
        fun monotonic(incomingM: Double?, currentM: Double?): Double? = when {
            incomingM == null -> currentM
            currentM == null -> incomingM
            else -> maxOf(incomingM, currentM)
        }

        /**
         * Equirectangular distance approximation, plenty at ~1 Hz stride scale.
         * (Wear-side twin of the phone recorder's `roughMeters`; kept local rather
         * than imported across modules.)
         */
        fun roughMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            return sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
        }
    }
}
