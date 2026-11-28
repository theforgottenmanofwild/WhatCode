package com.whatcode.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream

class TextFileStreamTest {

    private val mixed = "plain, é, 漢字, 😀 emoji, ünï\r\nsecond line\rthird\n" + "x".repeat(50) + "😀"

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)

    /** Gives out [per] bytes at most each time, as a slow provider does. */
    private class Slow(input: InputStream, private val per: Int) : FilterInputStream(input) {
        var closed = false
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, per))
        override fun close() { closed = true; super.close() }
    }

    @Test
    fun everyChunkSizeGivesTheSameTextWhateverTheHint() {
        for (chunk in 1..11) for (hint in listOf(-1L, 0L, 1L, 5L, 1000L, bytes(mixed).size.toLong())) {
            val out = TextFile.read(ByteArrayInputStream(bytes(mixed)), hint, chunk = chunk)
            assertEquals("chunk $chunk hint $hint", mixed, out)
        }
    }

    @Test
    fun aSlowStreamThatSplitsEveryCharacterGivesTheSameText() {
        for (per in 1..5) assertEquals(mixed, TextFile.read(Slow(ByteArrayInputStream(bytes(mixed)), per), -1, chunk = 3))
    }

    @Test
    fun lineEndingsAreKeptAsTheyWere() {
        val text = "a\r\nb\rc\nd\r\n\r\n"
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), bytes(text).size.toLong()))
    }

    @Test
    fun anEmptyFileAndOneWithoutALineBreakAreRead() {
        assertEquals("", TextFile.read(ByteArrayInputStream(ByteArray(0)), 0))
        assertEquals("", TextFile.read(ByteArrayInputStream(ByteArray(0)), -1))
        val one = "x".repeat(300_000)
        assertEquals(one, TextFile.read(ByteArrayInputStream(bytes(one)), -1))
    }

    @Test
    fun aFileOfExactlyTheProbeSizeIsRead() {
        val text = "y".repeat(8000)
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), 8000))
        val longer = "y".repeat(8001)
        assertEquals(longer, TextFile.read(ByteArrayInputStream(bytes(longer)), 8001))
    }

    @Test
    fun aHintThatIsTooSmallOrTooBigStillReadsEverything() {
        val text = "é".repeat(5000) + "z".repeat(5000)
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), 10))
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), 10_000_000))
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), Long.MAX_VALUE))
        assertEquals(text, TextFile.read(ByteArrayInputStream(bytes(text)), Long.MIN_VALUE))
    }

    @Test
    fun invalidUtf8BecomesReplacementCharactersAndNothingElseChanges() {
        val raw = byteArrayOf('a'.code.toByte(), 0xC3.toByte(), 'b'.code.toByte(), 0xFF.toByte(), 'c'.code.toByte())
        assertEquals("a\uFFFDb\uFFFDc", TextFile.read(ByteArrayInputStream(raw)))
    }

    @Test
    fun aFileWithAZeroByteIsNotTextAndIsClosed() {
        val raw = "abc".toByteArray() + byteArrayOf(0) + "def".toByteArray()
        val s = Slow(ByteArrayInputStream(raw), 100)
        try {
            TextFile.read(s)
            fail("a file with a zero byte was read as text")
        } catch (_: NotTextException) {
        }
        assertTrue(s.closed)
    }

    @Test
    fun progressGrowsAndEndsAtTheSizeOfTheFile() {
        val data = bytes("line of text\n".repeat(5000))
        val seen = ArrayList<Long>()
        TextFile.read(ByteArrayInputStream(data), data.size.toLong(), { seen.add(it) }, chunk = 1000)
        assertTrue(seen.size > 10)
        for (i in 1 until seen.size) assertTrue(seen[i] >= seen[i - 1])
        assertEquals(data.size.toLong(), seen.last())
    }

    @Test
    fun cancellingStopsTheReadAndClosesTheFile() {
        val data = bytes("line of text\n".repeat(5000))
        val s = Slow(ByteArrayInputStream(data), 10_000)
        var pieces = 0
        try {
            TextFile.read(s, data.size.toLong(), { pieces++ }, { pieces >= 3 }, chunk = 1000)
            fail("the read was not stopped")
        } catch (_: ReadCancelled) {
        }
        assertEquals(3, pieces)
        assertTrue(s.closed)
    }

    @Test
    fun cancellingBeforeTheFirstPieceReadsNoText() {
        var pieces = 0
        try {
            TextFile.read(ByteArrayInputStream(bytes("abc")), 3, { pieces++ }, { true })
            fail("the read was not stopped")
        } catch (_: ReadCancelled) {
        }
        assertEquals(0, pieces)
    }

    @Test
    fun everyChunkSizeWritesTheSameBytesAndNeverCutsAnEmoji() {
        for (chunk in 1..9) {
            val out = ByteArrayOutputStream()
            TextFile.write(out, mixed, chunk = chunk)
            assertArrayEquals("chunk $chunk", bytes(mixed), out.toByteArray())
        }
    }

    @Test
    fun writingReportsProgressUpToTheLengthAndWritesNothingForEmptyText() {
        val seen = ArrayList<Int>()
        TextFile.write(ByteArrayOutputStream(), mixed, { seen.add(it) }, chunk = 10)
        assertEquals(mixed.length, seen.last())
        for (i in 1 until seen.size) assertTrue(seen[i] > seen[i - 1])
        val none = ArrayList<Int>()
        val out = ByteArrayOutputStream()
        TextFile.write(out, "", { none.add(it) })
        assertEquals(0, out.size())
        assertTrue(none.isEmpty())
    }

    @Test
    fun aTextWrittenAndReadBackIsUnchanged() {
        val out = ByteArrayOutputStream()
        TextFile.write(out, mixed, chunk = 4)
        assertEquals(mixed, TextFile.read(ByteArrayInputStream(out.toByteArray()), out.size().toLong(), chunk = 5))
    }
}
