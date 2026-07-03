package com.example.runh10.data

import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.shared.Constants
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.zones.ZoneCalculator

/**
 * Computes the cached [RunSummaryEntity] from a parsed session. Zone times use the
 * athlete's current %HRR bands; series are downsampled for card/detail rendering.
 */
object RunAnalyzer {

    private const val ROUTE_MAX_POINTS = 220
    private const val HR_MAX_POINTS = 160
    private const val MAX_SAMPLE_GAP_MS = 10_000L

    fun analyze(
        bundle: SessionBundle,
        zoneCalc: ZoneCalculator?,
        name: String,
        source: String,
        workoutType: String = "RUN",
        precomputedHrvMs: Double? = null,
        kcal: Double? = null,
        movingMsOverride: Long? = null,
    ): RunSummaryEntity {
        val samples = bundle.samples
        val startMs = bundle.meta.startEpochMs
        val endMs = bundle.meta.endEpochMs ?: samples.lastOrNull()?.ts ?: startMs
        val elapsedMs = (endMs - startMs).coerceAtLeast(0)

        val locs = samples.filterIsInstance<LocRow>()
        val hrs = samples.filterIsInstance<HrRow>()
        val rrs = samples.filterIsInstance<RrRow>()

        // Distance: prefer the cumulative dist field; else integrate lat/lon.
        val distanceM = locs.mapNotNull { it.dist }.maxOrNull()
            ?: integrateDistance(locs)

        val avgBpm = hrs.takeIf { it.isNotEmpty() }?.map { it.bpm }?.average()?.toInt()
        val maxBpm = hrs.maxOfOrNull { it.bpm }

        val hrvMs = precomputedHrvMs ?: RmssdCalculator.compute(rrs)
            .takeIf { it.isNotEmpty() }?.map { it.rmssdMs }?.average()

        // Elevation gain: sum of positive altitude deltas ≥ 0.5 m (noise gate).
        var elevGain = 0.0
        var lastAlt: Double? = null
        locs.forEach { l ->
            val alt = l.alt ?: return@forEach
            lastAlt?.let { prev -> val d = alt - prev; if (d >= 0.5) elevGain += d }
            lastAlt = alt
        }

        // Time in zones from consecutive HR samples (gap-capped).
        val zoneMillis = LongArray(5)
        if (zoneCalc != null) {
            for (i in 0 until hrs.size - 1) {
                val dur = (hrs[i + 1].ts - hrs[i].ts).coerceIn(0, MAX_SAMPLE_GAP_MS)
                val z = zoneCalc.zoneFor(hrs[i].bpm)
                zoneMillis[(z - 1).coerceIn(0, 4)] += dur
            }
        }

        // Splits: prefer the watch's live splits; else re-derive per mile from the trail.
        val splits = bundle.splits.takeIf { it.isNotEmpty() }?.map {
            SplitJson(it.index, it.distanceMeters, it.movingDurationMs, it.avgPaceMps, it.avgBpm, it.elevationGainM)
        } ?: deriveSplits(locs, hrs, startMs)

        // Route + HR series, downsampled.
        val route = downsample(locs.map { listOf(it.lat, it.lon) }, ROUTE_MAX_POINTS)
        val hrSeries = downsample(
            hrs.map { listOf(((it.ts - startMs) / 1000).toDouble(), it.bpm.toDouble()) },
            HR_MAX_POINTS,
        )

        return RunSummaryEntity(
            sessionId = bundle.meta.sessionId,
            name = name,
            workoutType = workoutType,
            source = source,
            startMs = startMs,
            endMs = endMs,
            distanceM = distanceM,
            elapsedMs = elapsedMs,
            // Splits only cover whole miles — never use their sum as moving time (it
            // would drop the final partial mile and wildly overstate pace). True
            // moving time comes from the recorder when available; else elapsed.
            movingMs = movingMsOverride ?: elapsedMs,
            avgBpm = avgBpm,
            maxBpm = maxBpm,
            hrvMs = hrvMs,
            kcal = kcal,
            elevGainM = elevGain,
            zoneMillisJson = RunJson.encodeLongs(zoneMillis.toList()),
            splitsJson = RunJson.encodeSplits(splits),
            routeJson = RunJson.encodePairs(route),
            hrSeriesJson = RunJson.encodePairs(hrSeries),
            feel = null,
        )
    }

    private fun integrateDistance(locs: List<LocRow>): Double {
        var sum = 0.0
        for (i in 1 until locs.size) {
            sum += roughMeters(locs[i - 1].lat, locs[i - 1].lon, locs[i].lat, locs[i].lon)
        }
        return sum
    }

    private fun roughMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1) * kotlin.math.cos(Math.toRadians((lat1 + lat2) / 2))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
    }

    /** Mile splits re-derived from the trail; movingMs approximated by wall time between boundaries. */
    private fun deriveSplits(locs: List<LocRow>, hrs: List<HrRow>, startMs: Long): List<SplitJson> {
        if (locs.isEmpty()) return emptyList()
        val out = mutableListOf<SplitJson>()
        var index = 0
        var boundary = Constants.MILE_METERS
        var segStartDist = 0.0
        var segStartTs = startMs
        var hrIdx = 0
        var bpmSum = 0L
        var bpmCount = 0
        locs.forEach { l ->
            val dist = l.dist ?: return@forEach
            while (hrIdx < hrs.size && hrs[hrIdx].ts <= l.ts) {
                bpmSum += hrs[hrIdx].bpm; bpmCount++; hrIdx++
            }
            if (dist >= boundary) {
                index += 1
                val segDist = dist - segStartDist
                val segMs = (l.ts - segStartTs).coerceAtLeast(1)
                out += SplitJson(
                    index = index,
                    distanceM = segDist,
                    movingMs = segMs,
                    avgPaceMps = segDist / (segMs / 1000.0),
                    avgBpm = if (bpmCount > 0) (bpmSum / bpmCount).toInt() else null,
                )
                segStartDist = dist
                segStartTs = l.ts
                bpmSum = 0; bpmCount = 0
                boundary += Constants.MILE_METERS
            }
        }
        return out
    }

    private fun downsample(points: List<List<Double>>, max: Int): List<List<Double>> {
        if (points.size <= max) return points
        val step = points.size.toDouble() / max
        return (0 until max).map { i -> points[(i * step).toInt().coerceAtMost(points.size - 1)] }
    }
}
