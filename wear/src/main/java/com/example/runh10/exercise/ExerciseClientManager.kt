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
import androidx.health.services.client.data.LocationAccuracy
import androidx.health.services.client.data.LocationAvailability
import com.example.runh10.workout.ExerciseMetrics
import com.example.runh10.workout.FallbackDistanceTracker
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

    /**
     * Wall-clock time of the last *Health Services* location fix (0 = none yet this
     * session). Only genuine HS fixes bump this — fallback fixes deliberately do NOT,
     * so the re-engage decider keeps measuring the HS silence, not our own patched-in
     * stream. Read by [WorkoutController][com.example.runh10.workout.WorkoutController].
     */
    @Volatile var lastHsLocationAtMs: Long = 0L
        private set

    /** Last real HS availability string, tracked even while the fallback masks [metrics].gps. */
    @Volatile private var lastHsAvailability: String = "UNKNOWN"

    /** True while the direct-location fallback owns the gps field (see [beginFallback]). */
    @Volatile private var fallbackActive: Boolean = false

    /**
     * Distance accumulator for the current fallback segment (non-null only while
     * [fallbackActive]). Snapshots the frozen HS total on begin and integrates fused
     * fixes on top, so splits/pace/summary keep advancing through the cutout.
     */
    private var fallbackDistance: FallbackDistanceTracker? = null

    /**
     * Invoked (main looper) when a genuine HS location fix arrives while the fallback
     * is active — WorkoutController hooks its idempotent stopFallback() here so the
     * fallback is torn down immediately on recovery instead of waiting up to ~1 s for
     * the next tick (which would interleave dual-source LocRows). Both the HS callback
     * (registered via the executor-less setUpdateCallback) and the controller's
     * Main.immediate scope run on the main looper, so no lock is needed.
     */
    var onHsLocationDuringFallback: (() -> Unit)? = null

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latest = update.latestMetrics
            val distance = (latest.getData(DataType.DISTANCE_TOTAL)?.total as? Number)?.toDouble()
            val speed = latest.getData(DataType.SPEED).lastOrNull()?.value
            val locationPoint = latest.getData(DataType.LOCATION).lastOrNull()
            val location = locationPoint?.value
            // Stamp the arrival of a genuine HS fix so the decider can tell live GPS
            // from a stale copy (data class re-emits carry the old lat/lon forward).
            if (location != null) lastHsLocationAtMs = System.currentTimeMillis()
            // Per-fix horizontal accuracy rides on the DataPoint (not LocationData);
            // guard against sentinel/garbage values the same way altitude is guarded.
            val accuracy = (locationPoint?.accuracy as? LocationAccuracy)
                ?.horizontalPositionErrorMeters
                ?.takeUnless { it == Double.MAX_VALUE || !it.isFinite() || it < 0.0 }
            val cadence = latest.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.value
            _metrics.update {
                it.copy(
                    // Monotonic floor (single point where HS distance lands in the
                    // metrics): after a fallback segment HS's own total does NOT
                    // include the fallback metres, so it comes back LOWER than our
                    // accumulated total. See FallbackDistanceTracker.monotonic for
                    // the honesty tradeoff (modest post-recovery under-count, never
                    // a backward jump, never fabricated).
                    distanceMeters = FallbackDistanceTracker.monotonic(distance, it.distanceMeters),
                    speedMps = speed ?: it.speedMps,
                    lat = location?.latitude ?: it.lat,
                    lon = location?.longitude ?: it.lon,
                    // Health Services reports unavailable altitude as the sentinel
                    // Double.MAX_VALUE (NOT null), so the ?: guard alone would record
                    // that garbage. Treat the sentinel as absent → honest null.
                    altitude = location?.altitude?.takeUnless { it == Double.MAX_VALUE } ?: it.altitude,
                    accuracyM = accuracy ?: it.accuracyM,
                    cadenceSpm = (cadence as? Number)?.toDouble() ?: it.cadenceSpm,
                    exerciseState = update.exerciseStateInfo.state.toString(),
                )
            }
            // HS delivered a real fix while the fallback was streaming → recovery.
            // Tear the fallback down NOW rather than on the next 1 Hz tick, so no
            // further dual-source LocRows interleave. Idempotent; main looper.
            if (location != null && fallbackActive) onHsLocationDuringFallback?.invoke()
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
                val real = availability.toString()
                lastHsAvailability = real
                // The intra-fallback HS availability history is the diagnostic
                // deliverable — let it flow into the EvtRow trail even while the
                // fallback runs. FALLBACK_ACTIVE already logged its own transition
                // when the fallback began, so provenance stays readable; recovery
                // detection is unaffected (it keys off lastHsLocationAtMs, not gps).
                _metrics.update { it.copy(gps = real) }
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

    /**
     * Least-destructive re-assertion of the location stream when Health Services goes
     * silent mid-run. The pinned androidx.health.services 1.0.0 ExerciseClient exposes
     * no GPS-specific "restart location" call (updateExerciseTypeConfigAsync only takes
     * a golf config), so we re-register the update callback — the documented way to
     * reconnect to an *ongoing* exercise — and flush any buffered metrics. This does
     * NOT pause/end/restart the exercise, so run state and totals are untouched.
     *
     * Also drops a FALLBACK/REENGAGE breadcrumb through the gps field so the recorder's
     * EvtRow mechanism captures the attempt in the session file.
     */
    suspend fun reengage() {
        _metrics.update { it.copy(gps = GPS_REENGAGE_ATTEMPT) }
        runCatching { exerciseClient.setUpdateCallback(callback) }
            .onFailure { Log.e(TAG, "re-register update callback failed", it) }
        runCatching { exerciseClient.flushAsync().await() }
            .onFailure { Log.e(TAG, "flush failed", it) }
    }

    /** Marks the direct-location fallback as active and announces it via the gps field. */
    fun beginFallback() {
        fallbackActive = true
        // Snapshot the run's cumulative distance at the cutout instant; fused deltas
        // accumulate on top. Fresh tracker = cleared anchor, so the first fused fix
        // anchors only (no fabricated jump from the last HS position).
        fallbackDistance = FallbackDistanceTracker(_metrics.value.distanceMeters ?: 0.0)
        _metrics.update { it.copy(gps = GPS_FALLBACK_ACTIVE) }
    }

    /**
     * Pushes a real fallback fix into the SAME metrics stream the recorder writes from,
     * so it lands as an ordinary LocRow (with accuracy) on the existing write path.
     * No interpolation — only fixes the FusedLocationProvider actually delivered.
     * The gps field is NOT re-stamped here: FALLBACK_ACTIVE was announced once by
     * [beginFallback], and HS availability changes are allowed through mid-fallback
     * (their history is the diagnostic trail) — re-stamping every fix would ping-pong
     * EvtRows against them.
     */
    fun pushFallbackLocation(lat: Double, lon: Double, alt: Double?, acc: Double?, speed: Double?) {
        val cumulativeM = fallbackDistance?.onFix(lat, lon)
        _metrics.update {
            it.copy(
                lat = lat,
                lon = lon,
                altitude = alt ?: it.altitude,
                accuracyM = acc ?: it.accuracyM,
                speedMps = speed ?: it.speedMps,
                distanceMeters = cumulativeM ?: it.distanceMeters,
            )
        }
    }

    /**
     * Ends the fallback and restores the real HS availability — an EvtRow transition.
     * The accumulated fallback distance deliberately REMAINS in the metrics: the
     * monotonic guard in [callback]'s update handler keeps HS's (lower, fallback-blind)
     * post-recovery totals from regressing it.
     */
    fun endFallback() {
        fallbackActive = false
        fallbackDistance = null
        _metrics.update { it.copy(gps = lastHsAvailability) }
    }

    companion object {
        private const val TAG = "ExerciseClientManager"

        /** gps-field markers routed through the recorder's EvtRow breadcrumb channel. */
        const val GPS_REENGAGE_ATTEMPT = "REENGAGE_ATTEMPT"
        const val GPS_FALLBACK_ACTIVE = "FALLBACK_ACTIVE"
    }
}
