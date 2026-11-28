package com.whatcode.editor.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetBodyTest {

    private fun stops(p: SnippetBody.Parsed) = p.stops.map { Triple(it.index, it.start, it.end) }

    @Test
    fun plainTextHasNoStops() {
        val p = SnippetBody.parse("hello world")
        assertEquals("hello world", p.text)
        assertTrue(p.stops.isEmpty())
        assertEquals(11, p.finalOffset)
    }

    @Test
    fun anEmptyBodyIsEmpty() {
        val p = SnippetBody.parse("")
        assertEquals("", p.text)
        assertEquals(0, p.finalOffset)
    }

    @Test
    fun stopsAreFoundWithTheirText() {
        val p = SnippetBody.parse("f(\${1:a}, \$2) \$0;")
        assertEquals("f(a, ) ;", p.text)
        assertEquals(listOf(Triple(1, 2, 3), Triple(2, 5, 5)), stops(p))
        assertEquals(7, p.finalOffset)
    }

    @Test
    fun stopsAreVisitedInTheOrderOfTheirNumbers() {
        val p = SnippetBody.parse("\$3 \$1 \$2")
        assertEquals(listOf(1, 2, 3), p.stops.map { it.index })
        assertEquals(listOf(1, 2, 0), p.stops.map { it.start })
    }

    @Test
    fun bracesWithoutTextAreAStopToo() {
        val p = SnippetBody.parse("a\${1}b\${0}c")
        assertEquals("abc", p.text)
        assertEquals(listOf(Triple(1, 1, 1)), stops(p))
        assertEquals(2, p.finalOffset)
    }

    @Test
    fun aNumberUsedTwiceShowsTheSameTextAndTheFirstIsTheStop() {
        val p = SnippetBody.parse("\$1 and \${1:x} and \$1")
        assertEquals("x and x and x", p.text)
        assertEquals(listOf(Triple(1, 0, 1)), stops(p))
    }

    @Test
    fun theFirstDefaultGivenIsTheOneUsed() {
        assertEquals("a-a", SnippetBody.parse("\${1:a}-\${1:b}").text)
    }

    @Test
    fun withoutAZeroTheCursorGoesToTheEnd() {
        val p = SnippetBody.parse("x\${1:y}z")
        assertEquals(3, p.finalOffset)
    }

    @Test
    fun escapesGiveTheirCharacter() {
        assertEquals("\$1 \\ }", SnippetBody.parse("\\\$1 \\\\ \\}").text)
        assertTrue(SnippetBody.parse("\\\$1").stops.isEmpty())
    }

    @Test
    fun aBackslashBeforeAnythingElseStays() {
        assertEquals("a\\nb \\t", SnippetBody.parse("a\\nb \\t").text)
    }

    @Test
    fun aDefaultCanHoldEscapedBracesAndDollars() {
        val p = SnippetBody.parse("\${1:a\\}b\\\$c}")
        assertEquals("a}b\$c", p.text)
        assertEquals(listOf(Triple(1, 0, 5)), stops(p))
    }

    @Test
    fun aDollarSignThatStartsNothingIsKept() {
        assertEquals("cost: \$ and \$x and \${y} and \${", SnippetBody.parse("cost: \$ and \$x and \${y} and \${").text)
    }

    @Test
    fun anUnclosedBraceIsPlainText() {
        val p = SnippetBody.parse("a \${1:b")
        assertEquals("a \${1:b", p.text)
        assertTrue(p.stops.isEmpty())
    }

    @Test
    fun anythingButDigitsOrAColonInTheBracesIsPlainText() {
        assertEquals("\${1+2}", SnippetBody.parse("\${1+2}").text)
        assertEquals("\${a:b}", SnippetBody.parse("\${a:b}").text)
    }

    @Test
    fun aStopCanHaveSeveralDigitsButNotMoreThanThree() {
        assertEquals(listOf(12), SnippetBody.parse("\$12").stops.map { it.index })
        assertEquals(listOf(123), SnippetBody.parse("\$123").stops.map { it.index })
        val p = SnippetBody.parse("\$1234")
        assertEquals("4", p.text)
        assertEquals(listOf(123), p.stops.map { it.index })
    }

    @Test
    fun theLinesAfterTheFirstStartWithTheIndentAndATabIsOneLevel() {
        val p = SnippetBody.parse("if (\${1:c}) {\n\t\$0\n}", indent = "    ", unit = "  ")
        assertEquals("if (c) {\n      \n    }", p.text)
        assertEquals(listOf(Triple(1, 4, 5)), stops(p))
        assertEquals(p.text.indexOf("\n    }") - 0, p.finalOffset)
    }

    @Test
    fun aDefaultWithLineBreaksIsIndentedToo() {
        val p = SnippetBody.parse("\${1:a\nb}", indent = "\t", unit = "\t")
        assertEquals("a\n\tb", p.text)
        assertEquals(listOf(Triple(1, 0, 4)), stops(p))
    }

    @Test
    fun carriageReturnsAreDropped() {
        assertEquals("a\nb", SnippetBody.parse("a\r\nb").text)
    }

    @Test
    fun emojiAreCopied() {
        val p = SnippetBody.parse("\uD83D\uDE00\${1:\uD83D\uDE00}")
        assertEquals("\uD83D\uDE00\uD83D\uDE00", p.text)
        assertEquals(listOf(Triple(1, 2, 4)), stops(p))
    }
}
