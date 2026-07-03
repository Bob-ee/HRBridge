package com.example.runh10.shared.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire protocol for the music bridge: the phone relays its active media session
 * to the watch over the Data Layer; the watch sends transport/volume commands back.
 */
object MediaProtocol {
    /** Phone → watch: JSON [MediaState] snapshot (MessageClient broadcast). */
    const val PATH_STATE = "/runh10/media/state"

    /** Watch → phone: JSON [MediaCommand]. */
    const val PATH_COMMAND = "/runh10/media/cmd"

    /** Watch → phone: ask for an immediate state re-broadcast (empty payload). */
    const val PATH_REQUEST_STATE = "/runh10/media/request_state"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeState(s: MediaState): ByteArray = json.encodeToString(MediaState.serializer(), s).toByteArray(Charsets.UTF_8)
    fun decodeState(b: ByteArray): MediaState = json.decodeFromString(MediaState.serializer(), String(b, Charsets.UTF_8))
    fun encodeCommand(c: MediaCommand): ByteArray = json.encodeToString(MediaCommand.serializer(), c).toByteArray(Charsets.UTF_8)
    fun decodeCommand(b: ByteArray): MediaCommand = json.decodeFromString(MediaCommand.serializer(), String(b, Charsets.UTF_8))
}

@Serializable
data class MediaState(
    /** Human app label, e.g. "Spotify". Null when nothing is playing/paused anywhere. */
    val sourceApp: String? = null,
    val sourcePackage: String? = null,
    val track: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** 0..100 of the phone's media stream. */
    val volumePct: Int = 0,
    /** Epoch ms when this snapshot was taken — the watch extrapolates position while playing. */
    val sentAtMs: Long = 0,
)

@Serializable
data class MediaCommand(
    /** One of: play, pause, next, prev, volume. */
    val action: String,
    /** For action=volume: absolute 0..100. */
    val value: Int? = null,
) {
    companion object {
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val NEXT = "next"
        const val PREV = "prev"
        const val VOLUME = "volume"
    }
}
