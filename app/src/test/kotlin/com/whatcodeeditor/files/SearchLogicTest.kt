package com.whatcodeeditor.files

import java.util.concurrent.CancellationException
import java.util.regex.PatternSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The rules of Search in files work on plain text and numbers, so they can be checked without a device. */
class SearchLogicTest {

    private fun pattern(q: String, case: Boolean = false, word: Boolean = false, regex: Boolean = false) =
        SearchLogic.compile(q, case, word, regex)

    @Test
    fun generatedAndToolFoldersAreSkipped() {
        for (n in listOf(".git", "node_modules", "build", "dist", "out", "target", ".gradle", ".idea", "__pycache__", ".venv")) {
            assertTrue(n, SearchLogic.isSkippedFolder(n))
        }
        for (n in listOf("src", "docs", "app", "builder", "Build", "buildSrc", "gradle", ".github")) {
            assertFalse(n, SearchLogic.isSkippedFolder(n))
        }
    }

    @Test
    fun filesThatAreNeverTextAreNotRead() {
        for (n in listOf("logo.png", "LOGO.PNG", "a.tar.gz", "lib.so", "app.apk", "font.woff2", "song.mp3", "x.jar")) {
            assertTrue(n, SearchLogic.isBinaryName(n))
        }
        for (n in listOf("main.kt", "Makefile", "notes.txt", "archive.", "png", "image.png.md", ".gitignore")) {
            assertFalse(n, SearchLogic.isBinaryName(n))
        }
    }

    @Test
    fun aZeroByteAtTheStartMeansNotText() {
        assertTrue(SearchLogic.looksBinary(byteArrayOf(1, 2, 0, 4)))
        assertFalse(SearchLogic.looksBinary("hello\nworld".toByteArray()))
        assertFalse(SearchLogic.looksBinary(ByteArray(0)))
        val late = ByteArray(10_000) { 'a'.code.toByte() }
        late[9000] = 0
        assertFalse(SearchLogic.looksBinary(late)) // only the start is looked at
    }

    @Test
    fun veryLargeFilesAreTooBig() {
        assertFalse(SearchLogic.isTooBig(SearchLogic.MAX_BYTES.toLong()))
        assertTrue(SearchLogic.isTooBig(SearchLogic.MAX_BYTES + 1L))
        assertFalse(SearchLogic.isTooBig(-1L)) // size unknown: checked while reading
        assertFalse(SearchLogic.isTooBig(0))
    }

    @Test
    fun plainTextIsNotARegularExpression() {
        assertTrue(pattern("a.b").matcher("xa.by").find())
        assertFalse(pattern("a.b").matcher("axb").find())
        assertTrue(pattern("f(x)").matcher("call f(x) now").find())
    }

    @Test
    fun caseWholeWordAndRegexOptions() {
        assertTrue(pattern("Cat").matcher("a cat").find())
        assertFalse(pattern("Cat", case = true).matcher("a cat").find())
        assertTrue(pattern("cat", word = true).matcher("a cat.").find())
        assertFalse(pattern("cat", word = true).matcher("concat").find())
        assertTrue(pattern("c.t", regex = true).matcher("cot").find())
        assertTrue(pattern("cat|dog", word = true, regex = true).matcher("hot dog").find())
        assertFalse(pattern("cat|dog", word = true, regex = true).matcher("hotdog").find())
    }

    @Test
    fun aBrokenRegularExpressionIsReported() {
        try {
            pattern("(", regex = true)
            fail("expected a PatternSyntaxException")
        } catch (_: PatternSyntaxException) {
        }
    }

    @Test
    fun fileNamesAreMatchedByName() {
        assertTrue(SearchLogic.nameMatches("MainActivity.kt", pattern("activity")))
        assertFalse(SearchLogic.nameMatches("MainActivity.kt", pattern("activity", case = true)))
        assertFalse(SearchLogic.nameMatches("util.kt", pattern("main")))
        assertTrue(SearchLogic.nameMatches("logo.png", pattern("\\.png$", regex = true)))
        assertFalse(SearchLogic.nameMatches("a.txt", pattern("z*", regex = true))) // an empty match is no match
    }

    @Test
    fun findsLinesWithLineAndColumn() {
        val text = "alpha\n  beta gamma\ngamma\n"
        val m = SearchLogic.findLines(text, pattern("gamma"), 100)
        assertEquals(2, m.size)
        assertEquals(2, m[0].line)
        assertEquals(7, m[0].col)
        assertEquals(5, m[0].length)
        assertEquals("beta gamma", m[0].text)
        assertEquals(3, m[1].line)
        assertEquals(0, m[1].col)
    }

    @Test
    fun oneEntryPerLineEvenWithSeveralMatches() {
        val m = SearchLogic.findLines("a a a\nb\na", pattern("a"), 100)
        assertEquals(listOf(1, 3), m.map { it.line })
        assertEquals(0, m[0].col)
    }

    @Test
    fun windowsLineEndingsDoNotChangeLinesOrSnippets() {
        val m = SearchLogic.findLines("one\r\ntwo\r\nthree\r\n", pattern("t"), 100)
        assertEquals(listOf(2, 3), m.map { it.line })
        assertEquals("two", m[0].text)
        assertEquals("three", m[1].text)
    }

    @Test
    fun theLastLineNeedsNoLineBreak() {
        assertEquals(listOf(3), SearchLogic.findLines("a\nb\nneedle", pattern("needle"), 100).map { it.line })
        assertTrue(SearchLogic.findLines("", pattern("x"), 100).isEmpty())
    }

    @Test
    fun emptyMatchesDoNotCount() {
        assertTrue(SearchLogic.findLines("abc\ndef", pattern("x*", regex = true), 100).isEmpty())
        assertEquals(listOf(1), SearchLogic.findLines("xx\ndef", pattern("x*", regex = true), 100).map { it.line })
    }

    @Test
    fun theLimitStopsTheListOfLines() {
        val text = "hit\n".repeat(500)
        assertEquals(SearchLogic.MAX_PER_FILE, SearchLogic.findLines(text, pattern("hit"), SearchLogic.MAX_PER_FILE).size)
        assertEquals(3, SearchLogic.findLines(text, pattern("hit"), 3).size)
        assertTrue(SearchLogic.findLines(text, pattern("hit"), 0).isEmpty())
    }

    @Test
    fun aLongLineIsCutToAShortSnippetAroundTheMatch() {
        val line = "x".repeat(100) + "NEEDLE" + "y".repeat(300)
        val m = SearchLogic.findLines(line, pattern("needle"), 10).single()
        assertEquals(100, m.col)
        assertTrue(m.text.length < 200)
        assertTrue(m.text.startsWith("…") && m.text.endsWith("…"))
        assertEquals("NEEDLE", m.text.substring(m.textCol, m.textCol + m.textLen))
    }

    @Test
    fun theSnippetDropsLeadingSpacesAndShowsTheMatch() {
        val m = SearchLogic.snippet(7, "    foo bar", 8, 3)
        assertEquals("foo bar", m.text)
        assertEquals("bar", m.text.substring(m.textCol, m.textCol + m.textLen))
        assertEquals(7, m.line)
        assertEquals(8, m.col)
    }

    @Test
    fun cancellingStopsBeforeAnotherLineIsRead() {
        try {
            SearchLogic.findLines("a\nb\n", pattern("a"), 10) { true }
            fail("expected a CancellationException")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun cancellingStopsARegularExpressionInTheMiddleOfALine() {
        // (a+)+b on a run of a's takes practically forever; the search must still be stoppable.
        val line = "a".repeat(60)
        var asked = 0
        val started = System.nanoTime()
        try {
            SearchLogic.findLines(line, pattern("(a+)+b", regex = true), 10) { asked++ > 0 }
            fail("expected a CancellationException")
        } catch (_: CancellationException) {
        }
        assertTrue("took too long to stop", (System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun aFileCountsItsLinesAndItsName() {
        val lines = SearchLogic.findLines("a\na", pattern("a"), 10)
        assertEquals(2, FileHits("1", "x/a.txt", lines).count)
        assertEquals(3, FileHits("1", "x/a.txt", lines, nameMatch = true).count)
        assertEquals(1, FileHits("1", "x/a.png", emptyList(), nameMatch = true).count)
    }
}
