package com.example.runh10.data

import com.example.runh10.shared.model.HrRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Expected values are the Keytel formula evaluated by hand for each case (see the
 * numbers inline) — not a re-run of the production code — so these actually verify
 * the arithmetic in [CalorieEstimator], not just that it agrees with itself.
 */
class CalorieEstimatorTest {

    // maxGapMs is overridden to something well above the interval in these two cases —
    // they're testing the rate formula on a full, uncapped interval; the default 10s
    // cap (meant for live-recording sample gaps) would otherwise truncate a 1-2 minute
    // synthetic interval and these would silently be testing the cap instead.

    @Test fun male_singleOneMinuteInterval_matchesHandComputedRate() {
        // rate = (-55.0969 + 0.6309*150 + 0.1988*70 + 0.2017*30) / 4.184 = 14.222060229445509
        val hrs = listOf(HrRow(ts = 0, bpm = 150), HrRow(ts = 60_000, bpm = 150))
        val kcal = CalorieEstimator.kcal(hrs, weightKg = 70.0, age = 30, male = true, maxGapMs = 300_000L)
        assertEquals(14.222060229445509, kcal!!, 1e-6)
    }

    @Test fun female_singleTwoMinuteInterval_matchesHandComputedRate() {
        // rate = (-20.4022 + 0.4472*140 - 0.1263*60 + 0.074*25) / 4.184 = 8.718403441682598
        // over 2 minutes = 17.436806883365197
        val hrs = listOf(HrRow(ts = 0, bpm = 140), HrRow(ts = 120_000, bpm = 140))
        val kcal = CalorieEstimator.kcal(hrs, weightKg = 60.0, age = 25, male = false, maxGapMs = 300_000L)
        assertEquals(17.436806883365197, kcal!!, 1e-6)
    }

    @Test fun gapExceedingMax_isCappedRatherThanIntegratedInFull() {
        // rate = (-55.0969 + 0.6309*160 + 0.1988*80 + 0.2017*40) / 4.184 = 16.68716539196941
        // Real gap is 15s but maxGapMs defaults to 10s, so only 10s of it counts:
        // 16.68716539196941 * (10_000/60_000) = 2.7811942319949012
        val hrs = listOf(HrRow(ts = 0, bpm = 160), HrRow(ts = 15_000, bpm = 160))
        val kcal = CalorieEstimator.kcal(hrs, weightKg = 80.0, age = 40, male = true)
        assertEquals(2.7811942319949012, kcal!!, 1e-6)
        // Sanity: the uncapped value would have been meaningfully larger — proves the
        // cap is actually doing something rather than being a no-op in this test.
        assertEquals(true, kcal < 16.68716539196941 * (15_000 / 60_000.0))
    }

    @Test fun negativeRatePerInterval_isClampedToZero_notNegative() {
        // rate = (-20.4022 + 0.4472*60 - 0.1263*90 + 0.074*20) / 4.184 = -0.8262906309751431
        // A resting HR well below what the regression's intercept assumes goes negative;
        // that must clamp to 0 kcal for the interval, never subtract from the total.
        val hrs = listOf(HrRow(ts = 0, bpm = 60), HrRow(ts = 60_000, bpm = 60))
        val kcal = CalorieEstimator.kcal(hrs, weightKg = 90.0, age = 20, male = false)
        assertEquals(0.0, kcal!!, 1e-9)
    }

    @Test fun multipleIntervals_sumEachIntervalsRateSeparately() {
        // interval 1 (0->60s, hr=140): rate 13.192782026768645, 1 min -> 13.192782026768645
        // interval 2 (60s->180s, hr=150): rate 14.700669216061188, 2 min -> 29.401338432122376
        // total = 42.59412045889102
        val hrs = listOf(
            HrRow(ts = 0, bpm = 140),
            HrRow(ts = 60_000, bpm = 150),
            HrRow(ts = 180_000, bpm = 130), // last sample only bounds the final interval
        )
        val kcal = CalorieEstimator.kcal(hrs, weightKg = 75.0, age = 35, male = true, maxGapMs = 300_000L)
        assertEquals(42.59412045889102, kcal!!, 1e-6)
    }

    @Test fun emptyHrList_returnsNull() {
        assertNull(CalorieEstimator.kcal(emptyList(), weightKg = 70.0, age = 30, male = true))
    }

    @Test fun singleSample_returnsNull() {
        val hrs = listOf(HrRow(ts = 0, bpm = 140))
        assertNull(CalorieEstimator.kcal(hrs, weightKg = 70.0, age = 30, male = true))
    }
}
