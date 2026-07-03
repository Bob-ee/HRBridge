package com.example.runh10.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Notification listener that (a) unlocks MediaSessionManager access and (b) watches
 * the active session, pushing state changes to the watch via [MediaRelay].
 * The user grants this under Settings → Notifications → Notification access.
 */
class MediaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watched: MediaController? = null
    private var debounce: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.orEmpty().firstOrNull())
        pushSoon()
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushSoon()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushSoon()
        override fun onSessionDestroyed() { watch(null); pushSoon() }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(this, MediaNotificationListener::class.java)
            mgr.addOnActiveSessionsChangedListener(sessionsListener, component)
            watch(MediaRelay.activeController(this))
            pushSoon()
        } catch (se: SecurityException) {
            Log.w(TAG, "listener connected but session access denied", se)
        }
    }

    override fun onListenerDisconnected() {
        runCatching {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mgr.removeOnActiveSessionsChangedListener(sessionsListener)
        }
        watch(null)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun watch(controller: MediaController?) {
        watched?.unregisterCallback(controllerCallback)
        watched = controller
        controller?.registerCallback(controllerCallback)
    }

    /** Debounced broadcast — metadata + playback-state changes tend to arrive in bursts. */
    private fun pushSoon() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(400)
            MediaRelay.broadcastState(this@MediaNotificationListener)
        }
    }

    companion object {
        private const val TAG = "MediaNotifListener"
    }
}
