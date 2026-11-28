package com.whatcode.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchDataTest {

    @Test
    fun everyKindReachesTheSizeAskedFor() {
        for (kind in BenchData.Kind.values()) {
            val size = 200_000L
            val bytes = BenchData.bytes(kind, size)
            assertTrue("${kind.label} is too short: ${bytes.size}", bytes.size >= size - 2_000)
            assertTrue("${kind.label} is too long: ${bytes.size}", bytes.size < size + 200_000)
        }
    }

    @Test
    fun theSameKindAndSizeGivesTheSameText() {
        for (kind in BenchData.Kind.values()) {
            assertTrue(BenchData.bytes(kind, 50_000).contentEquals(BenchData.bytes(kind, 50_000)))
        }
    }

    @Test
    fun theJsonIsOneLineAndTheOthersHaveManyLines() {
        val json = String(BenchData.bytes(BenchData.Kind.JSON_LINE, 100_000), Charsets.UTF_8)
        assertEquals(0, json.count { it == '\n' })
        for (kind in listOf(BenchData.Kind.LOG, BenchData.Kind.SOURCE, BenchData.Kind.CSV)) {
            val text = String(BenchData.bytes(kind, 100_000), Charsets.UTF_8)
            assertTrue(kind.label, text.count { it == '\n' } > 500)
        }
    }

    @Test
    fun theTextIsValidUtf8WithSomeNonAsciiLetters() {
        val text = BenchData.bytes(BenchData.Kind.LOG, 100_000)
        val decoded = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(text)).toString()
        assertTrue(decoded.any { it.code > 127 })
    }
}
