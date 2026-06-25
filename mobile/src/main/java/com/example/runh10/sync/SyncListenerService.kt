package com.example.runh10.sync

import com.example.runh10.shared.sync.SyncProtocol
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class SyncListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == SyncProtocol.PATH_UNSYNCED_LIST) {
            ChannelInbox.offerUnsyncedList(event.data)
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        ChannelInbox.offerChannel(channel)
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appErrorCode: Int) {
        ChannelInbox.offerInputClosed(channel.path)
    }
}
