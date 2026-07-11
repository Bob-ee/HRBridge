package com.example.runh10.shared.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.StringWriter
import java.io.Writer

class SafeLineWriterTest {
    @Test fun writesLinesWithNewlines() {
        val sink = StringWriter()
        val w = SafeLineWriter(sink)
        assertTrue(w.writeLine("""{"t":"hr","bpm":80}"""))
        assertTrue(w.writeLine("""{"t":"hr","bpm":81}"""))
        assertEquals("{\"t\":\"hr\",\"bpm\":80}\n{\"t\":\"hr\",\"bpm\":81}\n", sink.toString())
        assertFalse(w.failed)
    }

    @Test fun failsClosedAfterIoException() {
        var throwAt = 2
        var writes = 0
        val sink = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                writes++
                if (writes >= throwAt) throw IOException("ENOSPC")
            }
            override fun flush() {}
            override fun close() {}
        }
        val w = SafeLineWriter(sink)
        assertTrue(w.writeLine("row1"))          // write #1 ok
        assertFalse(w.writeLine("row2"))         // throws -> false
        assertTrue(w.failed)
        throwAt = Int.MAX_VALUE                   // sink healthy again...
        assertFalse(w.writeLine("row3"))         // ...but writer stays failed (no zombie writes)
    }

    @Test fun closeSwallowsIoException() {
        val sink = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {}
            override fun flush() { throw IOException("boom") }
            override fun close() { throw IOException("boom") }
        }
        SafeLineWriter(sink).close() // must not throw
    }
}
