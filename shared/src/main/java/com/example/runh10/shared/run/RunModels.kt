package com.example.runh10.shared.run

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
