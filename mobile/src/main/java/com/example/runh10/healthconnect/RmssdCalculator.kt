package com.example.runh10.healthconnect

import com.example.runh10.shared.model.RrRow
import kotlin.math.sqrt

/** RMSSD over 30 s non-overlapping windows of consecutive RR intervals. SI: ms in, ms out. */
object RmssdCalculator {
    const val WINDOW_MS = 30_000L
    const val MIN_RR = 300
    const val MAX_RR = 2000
    const val MIN_SAMPLES = 20

    data class RmssdPoint(val tsMs: Long, val rmssdMs: Double)

    fun compute(rr: List<RrRow>): List<RmssdPoint> {
        val valid = rr.asSequence()
            .filter { it.rr in MIN_RR..MAX_RR }
            .sortedBy { it.ts }
            .toList()
        if (valid.isEmpty()) return emptyList()
        val t0 = valid.first().ts
        return valid.groupBy { (it.ts - t0) / WINDOW_MS }
            .toSortedMap()
            .mapNotNull { (windowIndex, rows) ->
                if (rows.size < MIN_SAMPLES) return@mapNotNull null
                val v = rows.map { it.rr }
                var sumSq = 0.0
                for (i in 1 until v.size) {
                    val d = (v[i] - v[i - 1]).toDouble()
                    sumSq += d * d
                }
                val rmssd = sqrt(sumSq / (v.size - 1))
                RmssdPoint(tsMs = t0 + (windowIndex + 1) * WINDOW_MS, rmssdMs = rmssd)
            }
    }
}
