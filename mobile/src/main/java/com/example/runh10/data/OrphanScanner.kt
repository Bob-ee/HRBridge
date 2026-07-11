package com.example.runh10.data

import java.io.File

/**
 * Pure orphan detection: a session is orphaned when its meta sidecar exists (written at
 * record start, deleted on save/discard) but Room never got a summary row — i.e. the
 * process died mid-run. Sidecar-less .ndjson files are normal synced sessions.
 */
object OrphanScanner {
    private const val META_SUFFIX = ".meta.json"

    fun findOrphans(sessionFiles: List<File>, knownIds: Set<String>): List<File> {
        val byName = sessionFiles.associateBy { it.name }
        return sessionFiles
            .filter { it.name.endsWith(META_SUFFIX) }
            .filter { meta ->
                val id = meta.name.removeSuffix(META_SUFFIX)
                id !in knownIds && byName.containsKey("$id.ndjson")
            }
            .sortedBy { it.name }
    }
}
