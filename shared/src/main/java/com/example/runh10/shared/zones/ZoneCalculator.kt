package com.example.runh10.shared.zones

import kotlin.math.roundToInt

class ZoneCalculator(val maxHr: Int, val restingHr: Int) {
    val reserve: Int get() = (maxHr - restingHr).coerceAtLeast(1)
    fun boundary(p: Double): Int = (restingHr + p * reserve).roundToInt()
    val edges: List<Int> = listOf(0.5, 0.6, 0.7, 0.8, 0.9, 1.0).map { boundary(it) }

    /** 1..5 by %HRR: <60->1, <70->2, <80->3, <90->4, else 5. Below 50% still Z1. */
    fun zoneFor(bpm: Int): Int {
        val pct = (bpm - restingHr).toDouble() / reserve
        return when {
            pct < 0.60 -> 1
            pct < 0.70 -> 2
            pct < 0.80 -> 3
            pct < 0.90 -> 4
            else -> 5
        }
    }
}

object MaxHr {
    fun estimate(age: Int): Int = (208 - 0.7 * age).roundToInt()
}
