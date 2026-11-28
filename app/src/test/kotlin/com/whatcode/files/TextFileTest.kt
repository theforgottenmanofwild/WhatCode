package com.whatcode.files

import com.whatcode.editor.LineEndings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TextFileTest {

    private fun read(bytes: ByteArray) = TextFile.read(ByteArrayInputStream(bytes))

    private fun write(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        TextFile.write(out, text)
        return out.toByteArray()
    }

    /** Opening and saving again must give back the very same bytes. */
    private fun assertSavesUnchanged(name: String, content: String) {
        val original = content.toByteArray(Charsets.UTF_8)
        assertArrayEquals("$name: bytes changed by open and save", original, write(read(original)))
    }

    @Test
    fun aFileThatIsNotTextIsRefusedSoItIsNeverSavedOverWithGarbage() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
        try {
            read(png)
            fail("a file with zero bytes is not text")
        } catch (_: NotTextException) {
        }
    }

    @Test
    fun aZeroByteFarIntoALongTextFileDoesNotStopItOpening() {
        val text = "a".repeat(20_000) + "\u0000" + "b"
        assertEquals(text, read(text.toByteArray()))
    }

    @Test
    fun savingKeepsWindowsLineEndings() = assertSavesUnchanged("crlf", "one\r\ntwo\r\nthree\r\n")

    @Test
    fun savingKeepsUnixLineEndings() = assertSavesUnchanged("lf", "one\ntwo\nthree\n")

    @Test
    fun savingKeepsOldMacLineEndings() = assertSavesUnchanged("cr", "one\rtwo\rthree\r")

    @Test
    fun savingKeepsMixedLineEndingsExactlyAsTheyWere() =
        assertSavesUnchanged("mixed", "one\r\ntwo\nthree\rfour\r\n\n\r\r\nfive")

    @Test
    fun savingKeepsTheAbsenceOfALastLineBreak() {
        assertSavesUnchanged("crlf no final break", "a\r\nb")
        assertSavesUnchanged("lf no final break", "a\nb")
    }

    @Test
    fun savingKeepsBlankLinesAndAFileOfOnlyLineBreaks() {
        assertSavesUnchanged("blank crlf lines", "\r\n\r\n\r\n")
        assertSavesUnchanged("blank lf lines", "\n\n\n")
        assertSavesUnchanged("empty", "")
    }

    @Test
    fun savingKeepsNonAsciiTextWithWindowsLineEndings() =
        assertSavesUnchanged("unicode", "caf\u00E9 \u00FC\u00F1\u00EE \u4E2D\u6587 \uD83D\uDE00\r\nsecond line\r\n")

    @Test
    fun savingKeepsALongFileWithWindowsLineEndings() {
        val sb = StringBuilder()
        for (i in 0 until 20_000) sb.append("line number ").append(i).append("\r\n")
        assertSavesUnchanged("long crlf", sb.toString())
    }

    @Test
    fun readingKeepsTheCarriageReturnsInTheText() {
        assertEquals("a\r\nb\r\n", read("a\r\nb\r\n".toByteArray()))
        assertEquals("a\rb", read("a\rb".toByteArray()))
    }

    @Test
    fun writingAddsNothingAndStripsNothing() {
        assertArrayEquals("a\r\nb".toByteArray(), write("a\r\nb"))
        assertArrayEquals("a\nb\n".toByteArray(), write("a\nb\n"))
    }

    @Test
    fun theEndingShownIsTheEndingOfTheFileThatWasOpened() {
        assertEquals(LineEndings.Kind.CRLF, LineEndings.detect(read("a\r\nb\r\n".toByteArray())))
        assertEquals(LineEndings.Kind.LF, LineEndings.detect(read("a\nb\n".toByteArray())))
        assertEquals(LineEndings.Kind.MIXED, LineEndings.detect(read("a\r\nb\n".toByteArray())))
    }

    @Test
    fun anEditedFileKeepsTheEndingsOfTheLinesThatWereNotTouched() {
        // The editor changes only the text that was edited; every other character is written back as it was.
        val opened = read("keep\r\nchange me\r\nkeep too\r\n".toByteArray())
        val edited = opened.replace("change me", "changed")
        assertArrayEquals("keep\r\nchanged\r\nkeep too\r\n".toByteArray(), write(edited))
    }
}
