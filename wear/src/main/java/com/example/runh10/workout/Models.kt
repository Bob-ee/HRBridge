package com.example.runh10.workout

import com.example.runh10.shared.model.Split

/** A single Heart Rate Measurement notification parsed from the H10 (0x2A37). */
data class HrSample(
    val bpm: Int,
    val rrMs: List<Int>,
    val timestamp: Long,
)

/** A BLE device discovered while scanning for the Heart Rate service. */
data class ScanDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

/** Latest values pulled from the Health Services exercise stream. */
data class ExerciseMetrics(
    val distanceMeters: Double? = null,
    val speedMps: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitude: Double? = null,
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
)
