package com.whatcodeeditor.editor

import com.whatcodeeditor.editor.LineEndings.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineEndingsTest {

    private fun kind(t: String) = LineEndings.detect(t)

    @Test
    fun recognisesEachKindOfLineEnding() {
        assertEquals(Kind.LF, kind("a\nb\nc\n"))
        assertEquals(Kind.CRLF, kind("a\r\nb\r\nc\r\n"))
        assertEquals(Kind.CR, kind("a\rb\rc\r"))
    }

    @Test
    fun aTextWithNoLineBreakShowsLfBecauseThatIsWhatANewLineGets() {
        assertEquals(Kind.LF, kind(""))
        assertEquals(Kind.LF, kind("one line only"))
    }

    @Test
    fun aMissingLastLineBreakDoesNotMatter() {
        assertEquals(Kind.CRLF, kind("a\r\nb"))
        assertEquals(Kind.LF, kind("a\nb"))
    }

    @Test
    fun aCrLfPairIsOneBreakNotACrPlusAnLf() {
        assertEquals(Kind.CRLF, kind("\r\n"))
        assertEquals(Kind.CRLF, kind("\r\n\r\n\r\n"))
    }

    @Test
    fun mixedEndingsAreReportedAsMixedInAnyOrder() {
        assertEquals(Kind.MIXED, kind("a\r\nb\nc"))
        assertEquals(Kind.MIXED, kind("a\nb\r\nc"))
        assertEquals(Kind.MIXED, kind("a\rb\nc"))
        assertEquals(Kind.MIXED, kind("a\r\nb\rc"))
        assertEquals(Kind.MIXED, kind("a\rb\r\nc"))
    }

    @Test
    fun aLoneCrBeforeACrLfIsStillMixed() {
        assertEquals(Kind.MIXED, kind("a\r\r\nb"))
    }

    @Test
    fun oneStrayLfInAWindowsFileMakesItMixed() {
        val t = "line\r\n".repeat(1000) + "odd\n" + "line\r\n".repeat(1000)
        assertEquals(Kind.MIXED, kind(t))
    }

    @Test
    fun aTextEndingInALoneCrIsCr() {
        assertEquals(Kind.CR, kind("abc\r"))
    }

    @Test
    fun theKindsHaveTheLabelsTheStatusBarShows() {
        assertEquals("LF", Kind.LF.label)
        assertEquals("CRLF", Kind.CRLF.label)
        assertEquals("CR", Kind.CR.label)
        assertEquals("Mixed", Kind.MIXED.label)
    }

    @Test
    fun worksOnAnyCharSequenceNotOnlyAString() {
        assertEquals(Kind.CRLF, LineEndings.detect(StringBuilder("a\r\nb\r\n")))
    }

    @Test
    fun aScanThatIsCancelledGivesNothing() {
        val big = "x".repeat(300_000) + "\r\n"
        assertNull(LineEndings.detect(big) { true })
        assertEquals(Kind.CRLF, LineEndings.detect(big) { false })
    }

    @Test
    fun aBigFileOfOneKindIsScannedToTheEnd() {
        val n = LineEndings.SYNC_LIMIT * 5
        val t = StringBuilder()
        while (t.length < n) t.append("some code here();\r\n")
        assertEquals(Kind.CRLF, kind(t.toString()))
    }

    @Test
    fun aBigFileStopsAtTheFirstSignOfAnotherKind() {
        // The other kind is at the very start, so a cancel that would fire later is never reached.
        val t = "a\nb\r\n" + "x".repeat(500_000)
        assertEquals(Kind.MIXED, LineEndings.detect(t) { true })
    }
}
