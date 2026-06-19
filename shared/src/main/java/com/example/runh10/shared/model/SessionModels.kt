package com.example.runh10.shared.model

enum class SessionState { RECORDING, FINALIZED, SYNCING, SYNCED }

data class SessionMeta(
    val sessionId: String,
    val startEpochMs: Long,
    val startZoneId: String,
    val endEpochMs: Long? = null,
    val exerciseType: String = "RUNNING",
    val appVersion: String,
    val state: SessionState,
)

/** A completed mile (or manual lap) segment. Computed live on the watch; re-derived on the phone. */
data class Split(
    val index: Int,
    val distanceMeters: Double,
    val movingDurationMs: Long,
    val avgPaceMps: Double,
    val avgBpm: Int?,
    val elevationGainM: Double,
)

/** Parsed result of one NDJSON file (phone-side, Phase 3 — defined here so the schema lives in one place). */
data class SessionBundle(
    val meta: SessionMeta,
    val samples: List<SampleRow>,
    val splits: List<Split>,
)
