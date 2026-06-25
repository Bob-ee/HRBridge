package com.example.runh10.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.example.runh10.shared.model.CalRow
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.SessionBundle
import java.time.Instant
import java.time.ZoneId

class HealthConnectWriter(private val context: Context) {

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun write(bundle: SessionBundle, rmssd: List<RmssdCalculator.RmssdPoint>) {
        val meta = bundle.meta
        val zone = runCatching { ZoneId.of(meta.startZoneId) }.getOrDefault(ZoneId.systemDefault())
        val samples = bundle.samples
        val firstTs = samples.minOfOrNull { it.ts } ?: meta.startEpochMs
        val lastTs = samples.maxOfOrNull { it.ts } ?: meta.startEpochMs
        // Widen [start,end] to CONTAIN every sample: HC rejects the whole insert if any exerciseRoute /
        // sample time falls outside the session window — which would make this session fail on every retry.
        val start = Instant.ofEpochMilli(minOf(meta.startEpochMs, firstTs))
        val end = Instant.ofEpochMilli(maxOf(meta.endEpochMs ?: lastTs, lastTs))
        val startOffset = zone.rules.getOffset(start)
        val endOffset = zone.rules.getOffset(end)
        fun md(clientRecordId: String = meta.sessionId) = Metadata.activelyRecorded(
            clientRecordId = clientRecordId,
            clientRecordVersion = 1L,
            device = Device(type = Device.TYPE_WATCH),
        )

        val locs = samples.filterIsInstance<LocRow>().sortedBy { it.ts }
        // HC rejects the whole HeartRateRecord if any sample's bpm is outside 1..300 (recordings can
        // contain bpm=0 sensor dropouts) — drop implausible samples so one bad reading can't fail the run.
        val hrs = samples.filterIsInstance<HrRow>().filter { it.bpm in 1..300 }.sortedBy { it.ts }
        val cals = samples.filterIsInstance<CalRow>().sortedBy { it.ts }

        val records = mutableListOf<Record>()

        // Exercise session + route (route is a ctor param, NOT separate records)
        val route = locs.map { l ->
            ExerciseRoute.Location(
                time = Instant.ofEpochMilli(l.ts),
                latitude = l.lat,
                longitude = l.lon,
                horizontalAccuracy = null,
                altitude = l.alt?.let { Length.meters(it) },
            )
        }
        records += ExerciseSessionRecord(
            startTime = start, startZoneOffset = startOffset,
            endTime = end, endZoneOffset = endOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Run",
            metadata = md(),
            exerciseRoute = if (route.isNotEmpty()) ExerciseRoute(route) else null,
        )

        // Heart rate series
        if (hrs.isNotEmpty()) {
            records += HeartRateRecord(
                startTime = Instant.ofEpochMilli(hrs.first().ts), startZoneOffset = startOffset,
                endTime = Instant.ofEpochMilli(hrs.last().ts), endZoneOffset = endOffset,
                samples = hrs.map {
                    HeartRateRecord.Sample(time = Instant.ofEpochMilli(it.ts), beatsPerMinute = it.bpm.toLong())
                },
                metadata = md(),
            )
        }

        // Speed series (skip null speeds)
        val spd = locs.filter { it.spd != null }
        if (spd.isNotEmpty()) {
            records += SpeedRecord(
                startTime = Instant.ofEpochMilli(spd.first().ts), startZoneOffset = startOffset,
                endTime = Instant.ofEpochMilli(spd.last().ts), endZoneOffset = endOffset,
                samples = spd.map {
                    SpeedRecord.Sample(time = Instant.ofEpochMilli(it.ts), speed = Velocity.metersPerSecond(it.spd!!))
                },
                metadata = md(),
            )
        }

        // Distance total = max cumulative dist
        val totalDist = locs.mapNotNull { it.dist }.maxOrNull()
        if (totalDist != null && totalDist > 0) {
            records += DistanceRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                distance = Length.meters(totalDist), metadata = md(),
            )
        }

        // Active calories total = max cumulative kcal
        val totalKcal = cals.maxOfOrNull { it.kcal }
        if (totalKcal != null && totalKcal > 0) {
            records += ActiveCaloriesBurnedRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                energy = Energy.kilocalories(totalKcal), metadata = md(),
            )
        }

        // Elevation gain = sum of positive altitude deltas
        val gain = elevationGain(locs.mapNotNull { it.alt })
        if (gain > 0) {
            records += ElevationGainedRecord(
                startTime = start, startZoneOffset = startOffset,
                endTime = end, endZoneOffset = endOffset,
                elevation = Length.meters(gain), metadata = md(),
            )
        }

        // HRV/RMSSD — one instantaneous record per surviving window
        rmssd.forEachIndexed { index, p ->
            val t = Instant.ofEpochMilli(p.tsMs)
            records += HeartRateVariabilityRmssdRecord(
                time = t, zoneOffset = zone.rules.getOffset(t),
                heartRateVariabilityMillis = p.rmssdMs, metadata = md("${meta.sessionId}_hrv_$index"),
            )
        }

        // Batch insert (≤1000 per call). Upsert on clientRecordId makes retry safe.
        client().let { c ->
            records.chunked(1000).forEach { chunk -> c.insertRecords(chunk) }
        }
    }

    private fun elevationGain(alts: List<Double>): Double {
        var gain = 0.0
        for (i in 1 until alts.size) {
            val d = alts[i] - alts[i - 1]
            if (d > 0) gain += d
        }
        return gain
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(ElevationGainedRecord::class),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
        )
    }
}
