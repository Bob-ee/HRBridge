package com.example.runh10.workout

import com.example.runh10.shared.model.Split

/** Latest values pulled from the Health Services exercise stream. */
data class ExerciseMetrics(
    val distanceMeters: Double? = null,
    val speedMps: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitude: Double? = null,
    /** Horizontal accuracy of the latest fix in meters, null when Health Services didn't report one. */
    val accuracyM: Double? = null,
    val cadenceSpm: Double? = null,
    val gps: String = "UNKNOWN",
    val exerciseState: String = "—",
)

/** Everything the live UI renders, merged from the BLE + exercise streams. */
data class UiState(
    val running: Boolean = false,
    val hrState: String = "IDLE",
    val bpm: Int? = null,
    val rrMs: List<Int> = emptyList(),
    val distanceMeters: Double? = null,
    val speedMps: Double? = null,
    val gps: String = "UNKNOWN",
    val lat: Double? = null,
    val lon: Double? = null,
    val elapsedSec: Long = 0,
    val exerciseState: String = "—",
    val movingSec: Long = 0,
    val runState: RunState = RunState.WARMUP,
    val warmupDistanceMeters: Double? = null,
    val rollingPaceMps: Double? = null,
    val avgPaceMps: Double? = null,
    val currentLapPaceMps: Double? = null,
    val kcal: Double? = null,
    val cadenceSpm: Double? = null,
    val hrZone: Int? = null,
    val zoneEdges: List<Int> = emptyList(),
    val gpsLocked: Boolean = false,
    val splits: List<Split> = emptyList(),
    /** In-progress lap snapshot (index/pace/avg HR) for the Laps screen. */
    val currentLap: com.example.runh10.shared.run.LiveLap? = null,
    /** Current-lap pace minus avg closed-lap pace, in sec/mi (negative = faster). */
    val lapDeltaSecPerMi: Double? = null,
    /** Whole-run streaming RMSSD in ms. */
    val hrvMs: Double? = null,
    /** Decimated lat/lon trail for the summary route sketch. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /** True once a reading has arrived AND the strap has gone quiet for 5s+ (F3). */
    val hrStale: Boolean = false,
)
