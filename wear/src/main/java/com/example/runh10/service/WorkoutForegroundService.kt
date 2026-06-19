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
import com.example.runh10.R
import com.example.runh10.presentation.MainActivity
import com.example.runh10.workout.WorkoutController

/**
 * Typed (health|location) foreground service that keeps the run recording while the
 * UI is backgrounded or swept away. It holds a wake lock and shows an ongoing
 * notification; the actual data lives in [WorkoutController]. Stopping requires an
 * explicit ACTION_STOP — the in-app "End & Exit" button — since a foreground service
 * will not stop itself.
 */
class WorkoutForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

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
                // Begin-run: strap is already connected (or connecting). Just start
                // the exercise session / recorder. FGS + wake-lock already held from
                // the prior ACTION_CONNECT; calling startAsForeground() again is safe
                // (idempotent on Android) but guard to avoid duplicate notifications.
                WorkoutController.init(applicationContext)
                WorkoutController.beginRun()
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification()
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
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Run H10")
            .setContentText("Recording run…")
            .setSmallIcon(R.drawable.splash_icon)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
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
        WorkoutController.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
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
