package com.example.runh10.sync

import android.content.Context
import com.example.runh10.data.RunRepository
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.parse.SessionParser
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import android.net.Uri

private const val LIST_TIMEOUT_MS = 30_000L      // wait for the watch's unsynced-list reply
private const val SESSION_TIMEOUT_MS = 120_000L  // per-session transfer + parse + HC write

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
        val metas = try {
            SyncProtocol.decodeMetaList(withTimeout(LIST_TIMEOUT_MS) { ChannelInbox.awaitUnsyncedList() })
        } catch (e: TimeoutCancellationException) {
            progress("Watch did not respond"); return SyncResult(0, 0, 0)
        }
        if (metas.isEmpty()) { progress("No unsynced runs"); return SyncResult(0, 0, 0) }
        progress("Found ${metas.size} unsynced run(s)")

        var synced = 0
        var failed = 0
        metas.sortedBy { it.startEpochMs }.forEachIndexed { i, meta ->
            progress("Pulling run ${i + 1} of ${metas.size}…")
            // TimeoutCancellationException is a CancellationException — catch it FIRST so a genuine
            // outer cancellation still propagates instead of being swallowed as a per-session failure.
            val ok = try {
                withTimeout(SESSION_TIMEOUT_MS) { syncOne(nodeId, meta) }
            } catch (e: TimeoutCancellationException) {
                progress("✗ ${meta.sessionId}: timed out"); false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress("✗ ${meta.sessionId}: ${e.message}"); false
            }
            if (ok) { synced++; progress("✓ ${meta.sessionId} written to Health Connect") } else failed++
        }
        progress("Done: $synced synced, $failed failed")
        return SyncResult(synced, failed, metas.size)
    }

    private suspend fun syncOne(nodeId: String, meta: SessionMeta): Boolean {
        val id = meta.sessionId
        // HEAT redesign: sessions now land in the phone's permanent run store so the
        // feed/detail screens can render them — no longer a throwaway cache file.
        val repo = RunRepository.get(context)
        val dest = repo.fileFor(id)
        messageClient.sendMessage(nodeId, SyncProtocol.pathStartTransfer(id), ByteArray(0)).await()
        val channel = ChannelInbox.awaitChannel(SyncProtocol.pathSession(id))
        try {
            channelClient.receiveFile(channel, Uri.fromFile(dest), false).await()
            ChannelInbox.awaitInputClosed(SyncProtocol.pathSession(id))
            val bundle = dest.useLines { SessionParser.parse(meta, it) }
            val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
            writer.write(bundle, rmssd)   // throws on failure → ACK skipped (all-or-nothing)
            repo.ingest(
                bundle = bundle,
                source = "watch",
                precomputedHrvMs = rmssd.takeIf { it.isNotEmpty() }?.map { it.rmssdMs }?.average(),
            )
        } catch (t: Throwable) {
            dest.delete()   // failed transfer/write → clean slate so the retry re-pulls
            throw t
        } finally {
            runCatching { channelClient.close(channel) }   // release GMS channel on success + failure
        }
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
