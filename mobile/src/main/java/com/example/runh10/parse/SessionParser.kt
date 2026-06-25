package com.example.runh10.parse

import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.serial.NdjsonSerializer

/** Parse one (possibly crash-truncated) session NDJSON into a SessionBundle. Splits deferred to Phase 4. */
object SessionParser {
    fun parse(meta: SessionMeta, lines: Sequence<String>): SessionBundle {
        val samples = NdjsonSerializer.decodeTolerant(lines).sortedBy { it.ts }
        return SessionBundle(meta = meta, samples = samples, splits = emptyList())
    }
}
