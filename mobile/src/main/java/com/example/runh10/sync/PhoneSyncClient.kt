package com.example.runh10.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.parse.SessionParser
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File
import android.net.Uri

data class SyncResult(val synced: Int, val failed: Int, val total: Int)

class PhoneSyncClient(private val context: Context) {
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val channelClient by lazy { Wearable.getChannelClient(context) }
    private val writer by lazy { HealthConnectWriter(context) }

    /** One full sync pass. [progress] receives human-readable status lines. */
    suspend fun sync(progress: (String) -> Unit): SyncResult {
        ChannelInbox.reset()

        val nodeId = findWatchNode()
        if (nodeId == null) {
            progress("Watch not reachable")
            return SyncResult(0, 0, 0)
        }

        messageClient.sendMessage(nodeId, SyncProtocol.PATH_REQUEST_UNSYNCED, ByteArray(0)).await()
        val metas = SyncProtocol.decodeMetaList(ChannelInbox.awaitUnsyncedList())
        if (metas.isEmpty()) { progress("No unsynced runs"); return SyncResult(0, 0, 0) }
        progress("Found ${metas.size} unsynced run(s)")

        var synced = 0
        var failed = 0
        metas.sortedBy { it.startEpochMs }.forEachIndexed { i, meta ->
            progress("Pulling run ${i + 1} of ${metas.size}…")
            val ok = runCatching { syncOne(nodeId, meta) }.getOrElse { e ->
                progress("✗ ${meta.sessionId}: ${e.message}"); false
            }
            if (ok) { synced++; progress("✓ ${meta.sessionId} written to Health Connect") } else failed++
        }
        progress("Done: $synced synced, $failed failed")
        return SyncResult(synced, failed, metas.size)
    }

    private suspend fun syncOne(nodeId: String, meta: SessionMeta): Boolean {
        val id = meta.sessionId
        messageClient.sendMessage(nodeId, SyncProtocol.pathStartTransfer(id), ByteArray(0)).await()

        val channel = ChannelInbox.awaitChannel(SyncProtocol.pathSession(id))
        val dest = File(context.cacheDir, "$id.ndjson")
        channelClient.receiveFile(channel, Uri.fromFile(dest), false).await()
        ChannelInbox.awaitInputClosed(SyncProtocol.pathSession(id))

        val bundle = dest.useLines { SessionParser.parse(meta, it) }
        val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
        writer.write(bundle, rmssd)
        dest.delete()

        messageClient.sendMessage(nodeId, SyncProtocol.pathAck(id), ByteArray(0)).await()
        return true
    }

    private suspend fun findWatchNode(): String? {
        val info = capabilityClient
            .getCapability(SyncProtocol.CAP_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await()
        val nodes = info.nodes
        return (nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull())?.id
    }

    suspend fun isHealthConnectReady(): Boolean = writer.isAvailable()
    suspend fun hasPermissions(): Boolean = writer.hasAllPermissions()
}
