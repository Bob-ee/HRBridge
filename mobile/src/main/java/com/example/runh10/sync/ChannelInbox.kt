package com.example.runh10.sync

import com.google.android.gms.wearable.ChannelClient
import kotlinx.coroutines.CompletableDeferred

/** Process-scoped rendezvous between SyncListenerService callbacks and PhoneSyncClient. */
object ChannelInbox {
    @Volatile private var unsyncedList = CompletableDeferred<ByteArray>()
    private val channels = HashMap<String, CompletableDeferred<ChannelClient.Channel>>()
    private val inputClosed = HashMap<String, CompletableDeferred<Unit>>()

    @Synchronized fun reset() {
        unsyncedList = CompletableDeferred()
        channels.clear()
        inputClosed.clear()
    }

    fun offerUnsyncedList(bytes: ByteArray) { unsyncedList.complete(bytes) }
    suspend fun awaitUnsyncedList(): ByteArray = unsyncedList.await()

    @Synchronized private fun channelSlot(path: String) =
        channels.getOrPut(path) { CompletableDeferred() }
    @Synchronized private fun inputSlot(path: String) =
        inputClosed.getOrPut(path) { CompletableDeferred() }

    fun offerChannel(channel: ChannelClient.Channel) { channelSlot(channel.path).complete(channel) }
    suspend fun awaitChannel(path: String): ChannelClient.Channel = channelSlot(path).await()

    fun offerInputClosed(path: String) { inputSlot(path).complete(Unit) }
    suspend fun awaitInputClosed(path: String) { inputSlot(path).await() }
}
