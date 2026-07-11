package com.example.runh10.data

import com.example.runh10.shared.model.HrRow

/**
 * Estimates total calories burned from a heart-rate stream using the Keytel et al.
 * (2005) regression equations — kcal/min as a function of HR, body weight, age, and
 * sex. Consecutive HR samples each contribute rate × duration; duration is gap-capped
 * the same way RunAnalyzer caps zone time, so a long silent gap (sensor dropout, HR
 * strap disconnect) can't manufacture calories out of nothing. A per-interval rate
 * that goes negative — the regression's intercept assumes an HR floor the sample can
 * fall under — is clamped to zero rather than allowed to subtract from the total.
 */
object CalorieEstimator {

    fun kcal(
        hrs: List<HrRow>,
        weightKg: Double,
        age: Int,
        male: Boolean,
        maxGapMs: Long = 10_000L,
    ): Double? {
        if (hrs.size < 2) return null
        var total = 0.0
        for (i in 0 until hrs.size - 1) {
            val durMs = (hrs[i + 1].ts - hrs[i].ts).coerceIn(0L, maxGapMs)
            val bpm = hrs[i].bpm
            val ratePerMin = if (male) {
                (-55.0969 + 0.6309 * bpm + 0.1988 * weightKg + 0.2017 * age) / 4.184
            } else {
                (-20.4022 + 0.4472 * bpm - 0.1263 * weightKg + 0.074 * age) / 4.184
            }
            total += ratePerMin.coerceAtLeast(0.0) * (durMs / 60_000.0)
        }
        return total
    }
}
