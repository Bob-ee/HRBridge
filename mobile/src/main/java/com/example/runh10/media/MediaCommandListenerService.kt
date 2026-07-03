package com.example.runh10.media

import com.example.runh10.shared.media.MediaProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives watch → phone media commands and state requests (path prefix /hrbridge/media). */
class MediaCommandListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            MediaProtocol.PATH_COMMAND -> {
                runCatching { MediaProtocol.decodeCommand(event.data) }.getOrNull()?.let { cmd ->
                    MediaRelay.apply(this, cmd)
                    // Reflect the result back quickly (e.g. play→pause icon flip).
                    scope.launch {
                        kotlinx.coroutines.delay(250)
                        MediaRelay.broadcastState(this@MediaCommandListenerService)
                    }
                }
            }
            MediaProtocol.PATH_REQUEST_STATE -> scope.launch {
                MediaRelay.broadcastState(this@MediaCommandListenerService)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
