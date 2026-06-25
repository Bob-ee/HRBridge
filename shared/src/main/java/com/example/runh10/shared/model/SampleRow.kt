package com.example.runh10.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SampleRow {
    abstract val ts: Long
}

@Serializable @SerialName("loc")
data class LocRow(
    override val ts: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val spd: Double? = null,
    val dist: Double? = null,
) : SampleRow()

@Serializable @SerialName("hr")
data class HrRow(override val ts: Long, val bpm: Int) : SampleRow()

@Serializable @SerialName("rr")
data class RrRow(override val ts: Long, val rr: Int) : SampleRow()

@Serializable @SerialName("cal")
data class CalRow(override val ts: Long, val kcal: Double) : SampleRow()

// Retained for backward-compatible decoding of older sessions; no longer written
// (the manual-lap feature was removed — splits are auto-generated per mile).
@Serializable @SerialName("lap")
data class LapRow(override val ts: Long) : SampleRow()
