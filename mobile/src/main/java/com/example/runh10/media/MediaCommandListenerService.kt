package com.example.runh10.media

import com.example.runh10.shared.media.MediaProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives watch → phone media commands and state requests (path prefix /hrbridge/media). */
class MediaCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val appContext = applicationContext
        when (event.path) {
            MediaProtocol.PATH_COMMAND -> {
                runCatching { MediaProtocol.decodeCommand(event.data) }.getOrNull()?.let { cmd ->
                    MediaRelay.apply(appContext, cmd)
                    // Reflect the result back quickly (play→pause icon flip). Runs on a
                    // process-scoped scope: GMS may destroy this service right after
                    // onMessageReceived returns, which would cancel a service scope.
                    echoScope.launch {
                        kotlinx.coroutines.delay(250)
                        MediaRelay.broadcastState(appContext)
                    }
                }
            }
            MediaProtocol.PATH_REQUEST_STATE -> echoScope.launch {
                MediaRelay.broadcastState(appContext)
            }
        }
    }

    companion object {
        private val echoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
