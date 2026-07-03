package com.example.runh10.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.example.runh10.shared.media.MediaCommand
import com.example.runh10.shared.media.MediaProtocol
import com.example.runh10.shared.media.MediaState
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Music control source for the watch: prefers the phone's media session (relayed by
 * the phone app over the Data Layer), falls back to sessions playing ON the watch
 * (requires notification access for [WatchMediaListenerService]).
 */
class WatchMediaClient(private val context: Context) : MessageClient.OnMessageReceivedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()

    /** True when the current state is served by a watch-local session, not the phone. */
    private val _localFallback = MutableStateFlow(false)
    val localFallback: StateFlow<Boolean> = _localFallback.asStateFlow()

    private var phoneStateAtMs = 0L
    private var localController: MediaController? = null
    private var sessionManager: MediaSessionManager? = null
    private var started = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onLocalSessions(list.orEmpty())
    }

    private val localCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishLocalIfActive()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishLocalIfActive()
    }

    fun start() {
        if (started) return
        started = true
        messageClient.addListener(this)
        requestPhoneState()
        startLocalWatcher()
    }

    fun stop() {
        if (!started) return
        started = false
        messageClient.removeListener(this)
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        localController?.unregisterCallback(localCallback)
        localController = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MediaProtocol.PATH_STATE) return
        val s = runCatching { MediaProtocol.decodeState(event.data) }.getOrNull() ?: return
        if (s.sourcePackage != null) {
            phoneStateAtMs = System.currentTimeMillis()
            _state.value = s
            _localFallback.value = false
        } else {
            // Phone reports nothing playing: drop its claim entirely so a watch-local
            // session can take over immediately (a stale phone track must not linger
            // and swallow transport commands for the freshness window).
            phoneStateAtMs = 0L
            if (!_localFallback.value) _state.value = null
            publishLocalIfActive()
        }
    }

    fun play() = command(MediaCommand(MediaCommand.PLAY))
    fun pause() = command(MediaCommand(MediaCommand.PAUSE))
    fun next() = command(MediaCommand(MediaCommand.NEXT))
    fun prev() = command(MediaCommand(MediaCommand.PREV))
    fun setVolume(pct: Int) = command(MediaCommand(MediaCommand.VOLUME, pct.coerceIn(0, 100)))

    private fun command(cmd: MediaCommand) {
        if (_localFallback.value) {
            localController?.let { c ->
                when (cmd.action) {
                    MediaCommand.PLAY -> c.transportControls.play()
                    MediaCommand.PAUSE -> c.transportControls.pause()
                    MediaCommand.NEXT -> c.transportControls.skipToNext()
                    MediaCommand.PREV -> c.transportControls.skipToPrevious()
                    MediaCommand.VOLUME -> cmd.value?.let { v ->
                        val max = c.playbackInfo?.maxVolume ?: 0
                        if (max > 0) c.setVolumeTo((v * max) / 100, 0)
                    }
                }
                publishLocalIfActive()
            }
            return
        }
        scope.launch {
            phoneNode()?.let { node ->
                runCatching {
                    messageClient.sendMessage(node, MediaProtocol.PATH_COMMAND, MediaProtocol.encodeCommand(cmd)).await()
                }.onFailure { Log.w(TAG, "media command failed", it) }
            }
        }
    }

    fun requestPhoneState() {
        scope.launch {
            phoneNode()?.let { node ->
                runCatching {
                    messageClient.sendMessage(node, MediaProtocol.PATH_REQUEST_STATE, ByteArray(0)).await()
                }
            }
        }
    }

    private suspend fun phoneNode(): String? = runCatching {
        val info = capabilityClient
            .getCapability(SyncProtocol.CAP_PHONE, CapabilityClient.FILTER_REACHABLE)
            .await()
        (info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull())?.id
    }.getOrNull()

    // ── Watch-local sessions (fallback) ──────────────────────────────────────

    private fun startLocalWatcher() {
        val component = ComponentName(context, WatchMediaListenerService::class.java)
        try {
            val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            sessionManager = mgr
            mgr.addOnActiveSessionsChangedListener(sessionsListener, component)
            onLocalSessions(mgr.getActiveSessions(component))
        } catch (se: SecurityException) {
            // Notification access not granted on the watch — bridge-only mode.
            Log.i(TAG, "no notification access; watch-local media fallback disabled")
        }
    }

    private fun onLocalSessions(sessions: List<MediaController>) {
        localController?.unregisterCallback(localCallback)
        localController = sessions.firstOrNull()
        localController?.registerCallback(localCallback)
        publishLocalIfActive()
    }

    /** Local sessions only win when the phone hasn't reported anything recently. */
    private fun publishLocalIfActive() {
        val c = localController
        val phoneFresh = System.currentTimeMillis() - phoneStateAtMs < PHONE_FRESH_MS &&
            _state.value?.sourcePackage != null && !_localFallback.value
        if (c == null) {
            if (_localFallback.value) { _localFallback.value = false; _state.value = null; requestPhoneState() }
            return
        }
        if (phoneFresh) return
        val md = c.metadata
        val ps = c.playbackState
        val info = c.playbackInfo
        _state.value = MediaState(
            sourceApp = appLabel(c.packageName),
            sourcePackage = c.packageName,
            track = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position ?: 0,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            volumePct = info?.let { if (it.maxVolume > 0) it.currentVolume * 100 / it.maxVolume else 0 } ?: 0,
            sentAtMs = System.currentTimeMillis(),
        )
        _localFallback.value = true
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))

    companion object {
        private const val TAG = "WatchMediaClient"
        private const val PHONE_FRESH_MS = 30_000L
    }
}
