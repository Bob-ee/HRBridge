package com.example.runh10.workout

import java.util.ArrayDeque

class RollingPace(private val windowMs: Long = 30_000) {
    private data class P(val ts: Long, val dist: Double)
    private val buf = ArrayDeque<P>()

    fun add(ts: Long, cumulativeDistanceM: Double) {
        buf.addLast(P(ts, cumulativeDistanceM))
        while (buf.size > 1 && ts - buf.peekFirst().ts > windowMs) buf.pollFirst()
    }

    fun paceMps(): Double? {
        if (buf.size < 2) return null
        val a = buf.peekFirst(); val b = buf.peekLast()
        val dt = (b.ts - a.ts) / 1000.0
        if (dt <= 0) return null
        return ((b.dist - a.dist) / dt).takeIf { it.isFinite() && it >= 0 }
    }
}
