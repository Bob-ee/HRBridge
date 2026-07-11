package com.example.runh10.healthconnect

/**
 * Pure selection logic for the overnight resting-HR auto-update: given the
 * `RestingHeartRateRecord`s Health Connect returned for the last 48h (as ts-ms/bpm pairs),
 * picks the newest one worth adopting — or null if none qualify.
 *
 * Manual measurements win for the day they're taken: a candidate only qualifies if it's
 * strictly newer than [currentMeasuredAtMs] (which the manual setter stamps just like auto).
 */
object RestingHrPick {
    private const val WINDOW_MS = 48L * 60 * 60 * 1000
    private val SANE_BPM = 25..100

    fun pick(records: List<Pair<Long, Int>>, currentMeasuredAtMs: Long?, nowMs: Long): Int? {
        val windowStart = nowMs - WINDOW_MS
        return records
            .asSequence()
            .filter { (ts, _) -> ts in windowStart..nowMs }
            .filter { (_, bpm) -> bpm in SANE_BPM }
            .filter { (ts, _) -> currentMeasuredAtMs == null || ts > currentMeasuredAtMs }
            .maxByOrNull { (ts, _) -> ts }
            ?.second
    }
}
