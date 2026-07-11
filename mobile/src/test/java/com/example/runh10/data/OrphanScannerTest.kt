package com.example.runh10.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OrphanScannerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun touch(name: String) = tmp.newFile(name)

    @Test fun findsMetaWithSiblingNdjsonNotInRoom() {
        touch("aaa.meta.json"); touch("aaa.ndjson")
        touch("bbb.meta.json"); touch("bbb.ndjson")
        val orphans = OrphanScanner.findOrphans(tmp.root.listFiles()!!.toList(), knownIds = setOf("bbb"))
        assertEquals(listOf("aaa.meta.json"), orphans.map { it.name })
    }

    @Test fun ignoresMetaWithoutNdjsonAndNdjsonWithoutMeta() {
        touch("lonely.meta.json")            // no data file: nothing to recover
        touch("synced.ndjson")               // no sidecar: a normal synced session
        val orphans = OrphanScanner.findOrphans(tmp.root.listFiles()!!.toList(), knownIds = emptySet())
        assertEquals(emptyList<String>(), orphans.map { it.name })
    }

    @Test fun emptyDirYieldsNothing() {
        assertEquals(0, OrphanScanner.findOrphans(emptyList(), emptySet()).size)
    }
}
