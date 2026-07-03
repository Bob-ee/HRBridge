package com.example.runh10.shared.run

import com.example.runh10.shared.Constants
import com.example.runh10.shared.model.Split

/** Live (not yet closed) lap snapshot for the in-run Laps screen. */
data class LiveLap(
    val index: Int,
    val distanceM: Double,
    val paceMps: Double?,
    val avgBpm: Int?,
)

class SplitTracker(private val splitMeters: Double = Constants.MILE_METERS) {
    private var index = 0
    private var segStartDist = 0.0
    private var segStartMovingMs = 0L
    private var bpmSum = 0L; private var bpmCount = 0
    private var minAlt = Double.NaN; private var maxAlt = Double.NaN
    private var nextBoundary = splitMeters

    fun onSample(cumulativeDistanceM: Double, movingMs: Long, bpm: Int?, altitudeM: Double?): Split? {
        accumulate(bpm, altitudeM)
        return if (cumulativeDistanceM >= nextBoundary) {
            val s = close(cumulativeDistanceM, movingMs)
            nextBoundary += splitMeters
            s
        } else null
    }

    /** Snapshot of the lap currently in progress (index is 1-based, so lap 1 while none closed). */
    fun currentLap(cumulativeDistanceM: Double, movingMs: Long): LiveLap {
        val dist = (cumulativeDistanceM - segStartDist).coerceAtLeast(0.0)
        val dur = movingMs - segStartMovingMs
        val pace = if (dur > 1000 && dist > 1.0) dist / (dur / 1000.0) else null
        val avgBpm = if (bpmCount > 0) (bpmSum / bpmCount).toInt() else null
        return LiveLap(index + 1, dist, pace, avgBpm)
    }

    /** Manual lap: close the in-progress segment now; the next auto boundary is one full split away. */
    fun closeNow(cumulativeDistanceM: Double, movingMs: Long): Split {
        val s = close(cumulativeDistanceM, movingMs)
        nextBoundary = cumulativeDistanceM + splitMeters
        return s
    }

    private fun accumulate(bpm: Int?, alt: Double?) {
        if (bpm != null) { bpmSum += bpm; bpmCount++ }
        if (alt != null) {
            if (minAlt.isNaN() || alt < minAlt) minAlt = alt
            if (maxAlt.isNaN() || alt > maxAlt) maxAlt = alt
        }
    }

    private fun close(cumulativeDistanceM: Double, movingMs: Long): Split {
        index += 1
        val dist = cumulativeDistanceM - segStartDist
        val dur = movingMs - segStartMovingMs
        val pace = if (dur > 0) dist / (dur / 1000.0) else 0.0
        val gain = if (minAlt.isNaN() || maxAlt.isNaN()) 0.0 else (maxAlt - minAlt).coerceAtLeast(0.0)
        val avgBpm = if (bpmCount > 0) (bpmSum / bpmCount).toInt() else null
        val s = Split(index, dist, dur, pace, avgBpm, gain)
        segStartDist = cumulativeDistanceM; segStartMovingMs = movingMs
        bpmSum = 0; bpmCount = 0; minAlt = Double.NaN; maxAlt = Double.NaN
        return s
    }
}
