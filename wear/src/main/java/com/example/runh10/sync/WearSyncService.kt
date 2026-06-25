package com.example.runh10.sync

import com.example.runh10.session.SessionStore
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.cancel
import android.net.Uri
import android.util.Log

class WearSyncService : WearableListenerService() {
    // SupervisorJob + handler: a GMS failure (e.g. BLE drop mid-transfer) logs instead of crashing the process.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w("WearSyncService", "sync op failed", e) }
    )
    private val store by lazy { SessionStore(applicationContext) }
    private val channelClient by lazy { Wearable.getChannelClient(applicationContext) }
    private val messageClient by lazy { Wearable.getMessageClient(applicationContext) }

    override fun onMessageReceived(event: MessageEvent) {
        val node = event.sourceNodeId
        when {
            event.path == SyncProtocol.PATH_REQUEST_UNSYNCED -> scope.launch {
                val metas = store.getUnsynced()
                messageClient.sendMessage(node, SyncProtocol.PATH_UNSYNCED_LIST,
                    SyncProtocol.encodeMetaList(metas)).await()
            }
            SyncProtocol.idFromPath(SyncProtocol.PREFIX_START_TRANSFER, event.path) != null -> scope.launch {
                val id = SyncProtocol.idFromPath(SyncProtocol.PREFIX_START_TRANSFER, event.path)!!
                val file = store.fileFor(id)
                if (!file.exists()) return@launch
                store.markSyncing(id)
                val channel = channelClient.openChannel(node, SyncProtocol.pathSession(id)).await()
                channelClient.sendFile(channel, Uri.fromFile(file)).await()
            }
            SyncProtocol.idFromPath(SyncProtocol.PREFIX_ACK, event.path) != null -> scope.launch {
                val id = SyncProtocol.idFromPath(SyncProtocol.PREFIX_ACK, event.path)!!
                store.markSynced(id)
                store.purgeFile(id)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        store.close()
    }
}
