package com.example.runh10.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.runh10.data.AthleteStore
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Overnight resting-HR auto-update: the watch's Fitbit integration writes
 * `RestingHeartRateRecord`s to Health Connect nightly — this reads the last 48h of them and,
 * via [RestingHrPick], adopts the newest one that's fresher than the current measurement
 * (manual entries win for the day they're taken). Runs from a daily WorkManager job plus a
 * cheap on-resume check so the phone doesn't have to be asleep at exactly the right moment.
 */
object RestingHrUpdater {
    private const val UNIQUE_WORK_NAME = "resting_hr_auto_update"
    private val WINDOW = Duration.ofHours(48)

    val READ_RESTING_HR_PERMISSION: String = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

    /** Register the 24h background check. KEEP means repeat calls (every app start) are a no-op
     * once scheduled — WorkManager doesn't reset the cycle. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * One check. No-op (and leaves the last-check outcome untouched) when the toggle is off.
     * Otherwise permission-gated + runCatching: Health Connect being unavailable, the read
     * permission not being granted, or any HC error just means this pass quietly does nothing.
     */
    suspend fun checkOnce(context: Context) {
        val athleteStore = AthleteStore(context)
        val profile = athleteStore.current()
        if (!profile.autoUpdateResting) return

        runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (READ_RESTING_HR_PERMISSION !in granted) return@runCatching

            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(WINDOW), now),
                ),
            )
            val records = response.records.map { it.time.toEpochMilli() to it.beatsPerMinute.toInt() }
            athleteStore.setRestingAutoCheckResult(atMs = now.toEpochMilli(), hadData = records.isNotEmpty())

            val picked = RestingHrPick.pick(records, profile.restingMeasuredAtMs, now.toEpochMilli())
            if (picked != null) athleteStore.setRestingHrAuto(picked)
        }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            checkOnce(applicationContext)
            return Result.success()
        }
    }
}
