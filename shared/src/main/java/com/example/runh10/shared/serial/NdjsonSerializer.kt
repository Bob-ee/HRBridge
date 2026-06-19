package com.example.runh10.shared.serial

import com.example.runh10.shared.model.SampleRow
import kotlinx.serialization.json.Json

object NdjsonSerializer {
    val json = Json {
        classDiscriminator = "t"
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun encode(row: SampleRow): String = json.encodeToString(SampleRow.serializer(), row)

    fun decode(line: String): SampleRow = json.decodeFromString(SampleRow.serializer(), line)

    /** Parse a (possibly crash-truncated) file: skip blank lines and any line that fails to parse. */
    fun decodeTolerant(lines: Sequence<String>): List<SampleRow> =
        lines.mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty()) null else runCatching { decode(line) }.getOrNull()
        }.toList()
}
