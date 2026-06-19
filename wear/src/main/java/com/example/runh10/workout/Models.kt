package com.example.runh10.workout

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
)
