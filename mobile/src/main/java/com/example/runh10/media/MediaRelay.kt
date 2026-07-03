package com.example.runh10.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
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
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Phone-side music bridge: snapshots the active media session and pushes it to the
 * watch; applies transport/volume commands from the watch. Session access is gated
 * on notification access being granted to [MediaNotificationListener].
 */
object MediaRelay {

    private const val TAG = "MediaRelay"

    /** The phone's current best media session, or null (no session / no permission). */
    fun activeController(context: Context): MediaController? = try {
        val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = mgr.getActiveSessions(ComponentName(context, MediaNotificationListener::class.java))
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    } catch (se: SecurityException) {
        null
    }

    fun snapshot(context: Context): MediaState {
        val c = activeController(context) ?: return MediaState(sentAtMs = System.currentTimeMillis())
        val md = c.metadata
        val ps = c.playbackState
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0
        return MediaState(
            sourceApp = appLabel(context, c.packageName),
            sourcePackage = c.packageName,
            track = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position ?: 0,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            volumePct = vol,
            sentAtMs = System.currentTimeMillis(),
        )
    }

    /** Push the current snapshot to every reachable watch node. */
    suspend fun broadcastState(context: Context) {
        val state = snapshot(context)
        val bytes = MediaProtocol.encodeState(state)
        val messageClient = Wearable.getMessageClient(context)
        runCatching {
            val info = Wearable.getCapabilityClient(context)
                .getCapability(SyncProtocol.CAP_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await()
            info.nodes.forEach { node ->
                runCatching { messageClient.sendMessage(node.id, MediaProtocol.PATH_STATE, bytes).await() }
            }
        }.onFailure { Log.w(TAG, "media state broadcast failed", it) }
    }

    /** Apply a watch command to the active session / media volume. */
    fun apply(context: Context, cmd: MediaCommand) {
        when (cmd.action) {
            MediaCommand.VOLUME -> cmd.value?.let { v ->
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0, 100) * max) / 100, 0)
            }
            else -> activeController(context)?.transportControls?.let { t ->
                when (cmd.action) {
                    MediaCommand.PLAY -> t.play()
                    MediaCommand.PAUSE -> t.pause()
                    MediaCommand.NEXT -> t.skipToNext()
                    MediaCommand.PREV -> t.skipToPrevious()
                    else -> Unit
                }
            }
        }
    }

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
