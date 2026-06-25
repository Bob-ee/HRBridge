package com.example.runh10.shared.sync

import com.example.runh10.shared.model.SessionMeta
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Single wire definition for the Data Layer sync, shared by watch + phone. */
object SyncProtocol {
    const val CAP_WATCH = "runh10_watch"
    const val CAP_PHONE = "runh10_phone"

    const val PATH_REQUEST_UNSYNCED = "/runh10/request_unsynced"
    const val PATH_UNSYNCED_LIST = "/runh10/unsynced_list"
    const val PREFIX_START_TRANSFER = "/runh10/start_transfer/"
    const val PREFIX_SESSION = "/runh10/session/"
    const val PREFIX_ACK = "/runh10/ack/"

    fun pathStartTransfer(id: String) = "$PREFIX_START_TRANSFER$id"
    fun pathSession(id: String) = "$PREFIX_SESSION$id"
    fun pathAck(id: String) = "$PREFIX_ACK$id"

    /** Returns the id if [path] starts with [prefix], else null. */
    fun idFromPath(prefix: String, path: String): String? =
        if (path.startsWith(prefix)) path.removePrefix(prefix).ifEmpty { null } else null

    private val json = Json { encodeDefaults = true }
    private val metaListSerializer = ListSerializer(SessionMeta.serializer())

    fun encodeMetaList(list: List<SessionMeta>): ByteArray =
        json.encodeToString(metaListSerializer, list).toByteArray(Charsets.UTF_8)

    fun decodeMetaList(bytes: ByteArray): List<SessionMeta> =
        json.decodeFromString(metaListSerializer, String(bytes, Charsets.UTF_8))
}
