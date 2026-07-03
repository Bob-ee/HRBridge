package com.example.runh10.shared.run

import kotlin.math.sqrt

/**
 * Streaming whole-run RMSSD over consecutive RR intervals. Same artifact gate as the
 * phone-side batch calculator (300–2000 ms); a rejected interval breaks the
 * consecutive chain so we never diff across an artifact.
 */
class RollingRmssd(
    private val minRr: Int = 300,
    private val maxRr: Int = 2000,
    private val minDiffs: Int = 10,
) {
    private var prev: Int? = null
    private var sumSq = 0.0
    private var count = 0

    fun feed(rrMs: Int) {
        if (rrMs !in minRr..maxRr) { prev = null; return }
        prev?.let { p ->
            val d = (rrMs - p).toDouble()
            sumSq += d * d
            count++
        }
        prev = rrMs
    }

    fun reset() { prev = null; sumSq = 0.0; count = 0 }

    /** RMSSD in ms, or null until enough clean successive diffs have accumulated. */
    fun value(): Double? = if (count >= minDiffs) sqrt(sumSq / count) else null
}
