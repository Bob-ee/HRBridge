package com.example.runh10.exercise

import android.content.Context
import android.util.Log
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.LocationAvailability
import com.example.runh10.workout.ExerciseMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await

/**
 * Wraps the Health Services ExerciseClient for a RUNNING session, sourcing
 * GPS location / speed / distance from the watch. Heart rate is deliberately
 * NOT requested here — it comes from the H10 over BLE instead.
 */
class ExerciseClientManager(context: Context) {

    private val exerciseClient: ExerciseClient =
        HealthServices.getClient(context).exerciseClient

    private val _metrics = MutableStateFlow(ExerciseMetrics())
    val metrics: StateFlow<ExerciseMetrics> = _metrics.asStateFlow()

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latest = update.latestMetrics
            val distance = (latest.getData(DataType.DISTANCE_TOTAL)?.total as? Number)?.toDouble()
            val speed = latest.getData(DataType.SPEED).lastOrNull()?.value
            val location = latest.getData(DataType.LOCATION).lastOrNull()?.value
            val cadence = latest.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.value
            _metrics.update {
                it.copy(
                    distanceMeters = distance ?: it.distanceMeters,
                    speedMps = speed ?: it.speedMps,
                    lat = location?.latitude ?: it.lat,
                    lon = location?.longitude ?: it.lon,
                    // Health Services reports unavailable altitude as the sentinel
                    // Double.MAX_VALUE (NOT null), so the ?: guard alone would record
                    // that garbage. Treat the sentinel as absent → honest null.
                    altitude = location?.altitude?.takeUnless { it == Double.MAX_VALUE } ?: it.altitude,
                    cadenceSpm = (cadence as? Number)?.toDouble() ?: it.cadenceSpm,
                    exerciseState = update.exerciseStateInfo.state.toString(),
                )
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}

        override fun onRegistered() {}

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "exercise registration failed", throwable)
        }

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            if (availability is LocationAvailability) {
                // Log the exact string so the gpsLocked predicate can be confirmed/tuned
                // against real Health Services values during on-device acceptance.
                Log.d(TAG, "LocationAvailability=$availability")
                _metrics.update { it.copy(gps = availability.toString()) }
            }
        }
    }

    suspend fun start() {
        val config = ExerciseConfig.builder(ExerciseType.RUNNING)
            .setDataTypes(setOf(DataType.LOCATION, DataType.SPEED, DataType.DISTANCE_TOTAL, DataType.STEPS_PER_MINUTE))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(true)
            .build()
        exerciseClient.setUpdateCallback(callback)
        exerciseClient.startExerciseAsync(config).await()
    }

    suspend fun stop() {
        runCatching { exerciseClient.endExerciseAsync().await() }
        runCatching { exerciseClient.clearUpdateCallbackAsync(callback).await() }
    }

    companion object {
        private const val TAG = "ExerciseClientManager"
    }
}
