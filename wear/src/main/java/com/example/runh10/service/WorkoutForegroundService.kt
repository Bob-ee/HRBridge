package com.example.runh10.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.runh10.R
import com.example.runh10.presentation.MainActivity
import com.example.runh10.presentation.formatElapsed
import com.example.runh10.presentation.formatMiles
import com.example.runh10.workout.WorkoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Typed (health|location) foreground service that keeps the run recording while the
 * UI is backgrounded or swept away. It holds a wake lock and shows an ongoing
 * notification; the actual data lives in [WorkoutController]. Stopping requires an
 * explicit ACTION_STOP — the in-app "End & Exit" button — since a foreground service
 * will not stop itself.
 */
class WorkoutForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    /** Service-scoped coroutine scope — cancelled in onDestroy. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Job that drives OngoingActivity status updates; replaced on each startAsForeground. */
    private var statusUpdateJob: Job? = null

    /** The OngoingActivity instance — held so we can update its status while running. */
    private var ongoingActivity: OngoingActivity? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWorkout()
                return START_NOT_STICKY
            }

            ACTION_CONNECT -> {
                // Connect-only: start foreground (wake-lock + notification) and
                // establish the BLE link, but do NOT start the exercise session.
                // The Prep screen stays visible until the user taps Start.
                val address = intent.getStringExtra(EXTRA_DEVICE) ?: return START_NOT_STICKY
                val autoConnect = intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)
                startAsForeground()
                acquireWakeLock()
                WorkoutController.init(applicationContext)
                WorkoutController.connectStrap(address, autoConnect = autoConnect)
            }

            ACTION_START -> {
                // Begin-run: defensively (re)assert foreground + wake-lock before
                // starting the exercise session. Under normal flow ACTION_CONNECT has
                // already called both; but START_STICKY redelivery after a process kill
                // can bring ACTION_START without a preceding ACTION_CONNECT in this
                // process-incarnation. startForeground() is idempotent when already
                // foreground; acquireWakeLock() is safe to call again.
                startAsForeground()
                acquireWakeLock()
                WorkoutController.init(applicationContext)
                WorkoutController.beginRun()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val (notification, ongoing) = buildNotificationWithOngoing()
        ongoingActivity = ongoing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        startStatusUpdates()
    }

    /**
     * Builds the foreground notification and attaches an [OngoingActivity] to it.
     * Returns both so [startAsForeground] can pass the notification to startForeground
     * and retain the OngoingActivity reference for later status updates.
     */
    private fun buildNotificationWithOngoing(): Pair<Notification, OngoingActivity> {
        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Run H10")
            .setContentText("Recording run…")
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .setContentIntent(touchIntent)

        val initialStatus = Status.Builder()
            .addTemplate("#dist# • #time#")
            .addPart("dist", Status.TextPart("0.00 mi"))
            .addPart("time", Status.TextPart("0:00"))
            .build()

        val ongoing = OngoingActivity.Builder(this, NOTIF_ID, builder)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(touchIntent)
            .setStatus(initialStatus)
            .build()
        ongoing.apply(this)

        return Pair(builder.build(), ongoing)
    }

    /**
     * Collects [WorkoutController.uiState] and pushes updated status text to the
     * OngoingActivity on every emission. Cancelled via [statusUpdateJob] in
     * [stopWorkout] and [onDestroy].
     */
    private fun startStatusUpdates() {
        statusUpdateJob?.cancel()
        statusUpdateJob = serviceScope.launch {
            WorkoutController.uiState.collect { ui ->
                val activity = ongoingActivity ?: return@collect
                val distText = formatMiles(ui.distanceMeters)
                val timeText = formatElapsed(ui.movingSec)
                val status = Status.Builder()
                    .addTemplate("#dist# • #time#")
                    .addPart("dist", Status.TextPart(distText))
                    .addPart("time", Status.TextPart(timeText))
                    .build()
                activity.update(this@WorkoutForegroundService, status)
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunH10::workout").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopWorkout() {
        statusUpdateJob?.cancel()
        statusUpdateJob = null
        WorkoutController.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        statusUpdateJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.runh10.action.CONNECT"
        const val ACTION_START = "com.example.runh10.action.START"
        const val ACTION_STOP = "com.example.runh10.action.STOP"
        const val EXTRA_DEVICE = "device_address"
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        private const val CHANNEL_ID = "workout"
        private const val NOTIF_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000 // 4h safety cap
    }
}
