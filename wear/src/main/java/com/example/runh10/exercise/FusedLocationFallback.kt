package com.example.runh10.exercise

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Direct FusedLocationProvider stream used only when Health Services GPS goes silent
 * mid-run (see [GpsReengageDecider][com.example.runh10.workout.GpsReengageDecider]).
 *
 * Runs inside the app's own UID — unlike the normal path where GPS is brokered by
 * Health Services / GMS — which is what makes it a genuine second source. The typed
 * `health|location` foreground service plus the partial wake lock entitle this
 * while-in-use location access to survive the screen turning off during a run.
 *
 * Emits only real fixes; there is no interpolation or synthesis.
 */
class FusedLocationFallback(
    context: Context,
    private val onLocation: (Location) -> Unit,
) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(onLocation)
        }
    }

    /**
     * Requests ~1 Hz high-accuracy updates. Permission (ACCESS_FINE_LOCATION) is
     * required and is already requested/granted for the run; the caller only starts
     * this while a location-typed FGS is foregrounded, so the missing-permission lint
     * is suppressed intentionally.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .build()
        runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.onFailure { Log.e(TAG, "fallback location start failed", it) }
    }

    fun stop() {
        runCatching { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val TAG = "FusedLocationFallback"
        const val INTERVAL_MS = 1_000L
    }
}
