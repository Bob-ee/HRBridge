package com.example.runh10.shared.serial

import java.io.IOException
import java.io.Writer

/**
 * Guards session-file writes: one IOException (disk full, revoked storage) permanently
 * fails the writer instead of crashing the process. The recording degrades to an honest
 * gap — a recording failure must never kill a live run. Flushes per line so a process
 * kill loses at most the torn tail line.
 */
class SafeLineWriter(private val out: Writer) {
    @Volatile var failed: Boolean = false
        private set

    @Synchronized
    fun writeLine(line: String): Boolean {
        if (failed) return false
        return try {
            out.write(line + "\n"); out.flush()
            true
        } catch (e: IOException) {
            failed = true
            runCatching { out.close() }
            false
        }
    }

    @Synchronized
    fun close() {
        runCatching { out.flush() }
        runCatching { out.close() }
    }
}
