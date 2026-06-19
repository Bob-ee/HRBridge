package com.example.runh10.presentation

import com.example.runh10.shared.model.Split
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightedAvgHrTest {
    @Test
    fun emptyList_returnsEmDash() {
        val result = weightedAvgHr(emptyList())
        assertEquals("—", result)
    }

    @Test
    fun allNullAvgBpm_returnsEmDash() {
        val splits = listOf(
            Split(index = 1, distanceMeters = 1600.0, movingDurationMs = 600000, avgPaceMps = 2.5, avgBpm = null, elevationGainM = 10.0),
            Split(index = 2, distanceMeters = 1600.0, movingDurationMs = 600000, avgPaceMps = 2.5, avgBpm = null, elevationGainM = 10.0),
        )
        val result = weightedAvgHr(splits)
        assertEquals("—", result)
    }

    @Test
    fun allZeroDuration_returnsEmDash() {
        val splits = listOf(
            Split(index = 1, distanceMeters = 1600.0, movingDurationMs = 0, avgPaceMps = 2.5, avgBpm = 150, elevationGainM = 10.0),
            Split(index = 2, distanceMeters = 1600.0, movingDurationMs = 0, avgPaceMps = 2.5, avgBpm = 160, elevationGainM = 10.0),
        )
        val result = weightedAvgHr(splits)
        assertEquals("—", result)
    }

    @Test
    fun mixedSplits_calculatesWeightedAverage() {
        // Split A: avgBpm=150, movingDurationMs=600000 (60%)
        // Split B: avgBpm=160, movingDurationMs=300000 (40%)
        // Weighted mean = (150*600000 + 160*300000) / 900000 = 138000000 / 900000 = 153.333... -> 153
        val splits = listOf(
            Split(index = 1, distanceMeters = 1600.0, movingDurationMs = 600000, avgPaceMps = 2.5, avgBpm = 150, elevationGainM = 10.0),
            Split(index = 2, distanceMeters = 1600.0, movingDurationMs = 300000, avgPaceMps = 2.5, avgBpm = 160, elevationGainM = 10.0),
        )
        val result = weightedAvgHr(splits)
        assertEquals("153 bpm", result)
    }

    @Test
    fun nullAvgBpmSkipped_returnsValidBpm() {
        // One split with null avgBpm should be skipped, leaving only the valid one
        val splits = listOf(
            Split(index = 1, distanceMeters = 1600.0, movingDurationMs = 600000, avgPaceMps = 2.5, avgBpm = null, elevationGainM = 10.0),
            Split(index = 2, distanceMeters = 1600.0, movingDurationMs = 300000, avgPaceMps = 2.5, avgBpm = 145, elevationGainM = 10.0),
        )
        val result = weightedAvgHr(splits)
        assertEquals("145 bpm", result)
    }
}
