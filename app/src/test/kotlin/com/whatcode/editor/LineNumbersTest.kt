package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The gutter's width and which rows it numbers are worked out on plain text, so no device is needed. */
class LineNumbersTest {

    @Test
    fun theGutterHasAsManyDigitsAsTheLastLineNumber() {
        assertEquals(1, LineNumbers.digits(1))
        assertEquals(1, LineNumbers.digits(9))
        assertEquals(2, LineNumbers.digits(10))
        assertEquals(2, LineNumbers.digits(99))
        assertEquals(3, LineNumbers.digits(100))
        assertEquals(4, LineNumbers.digits(1000))
        assertEquals(7, LineNumbers.digits(1_234_567))
    }

    @Test
    fun anOddLineCountStillGivesOneDigit() {
        assertEquals(1, LineNumbers.digits(0))
        assertEquals(1, LineNumbers.digits(-5))
    }

    @Test
    fun breaksAreCountedInARange() {
        val t = "a\nb\nc\n"
        assertEquals(3, LineNumbers.breaksIn(t, 0, t.length))
        assertEquals(1, LineNumbers.breaksIn(t, 0, 2))
        assertEquals(0, LineNumbers.breaksIn(t, 0, 1))
        assertEquals(3, LineNumbers.breaksIn(t, -4, 100))
        assertEquals(0, LineNumbers.breaksIn(t, 4, 2))
    }

    @Test
    fun carriageReturnsAreNotLineBreaks() {
        assertEquals(1, LineNumbers.breaksIn("a\r\nb\rc", 0, 6))
    }

    /** Rows of [text] as the layout would split it at [rowStarts]; returns "row:number" for each numbered row. */
    private fun label(text: String, rowStarts: IntArray, first: Int, last: Int, firstNumber: Int): List<String> {
        val out = ArrayList<String>()
        LineNumbers.label(first, last, { rowStarts[it] }, text, firstNumber) { row, n -> out.add("$row:$n") }
        return out
    }

    @Test
    fun everyRowOfUnwrappedLinesGetsANumber() {
        val text = "one\ntwo\nthree"
        assertEquals(listOf("0:1", "1:2", "2:3"), label(text, intArrayOf(0, 4, 8), 0, 2, 1))
    }

    @Test
    fun aWrappedLineIsNumberedOnItsFirstRowOnly() {
        // "aaaa bbbb cccc\nd" wrapped into "aaaa ", "bbbb ", "cccc\n", "d"
        val text = "aaaa bbbb cccc\nd"
        val starts = intArrayOf(0, 5, 10, 15)
        assertEquals(listOf("0:1", "3:2"), label(text, starts, 0, 3, 1))
    }

    @Test
    fun scrollingIntoTheMiddleOfAWrappedLineKeepsTheNumbersRight() {
        val text = "aaaa bbbb cccc\nd\ne"
        val starts = intArrayOf(0, 5, 10, 15, 17)
        // The first row on screen is the second row of line 1: it has no number, the next lines are 2 and 3.
        assertEquals(listOf("3:2", "4:3"), label(text, starts, 1, 4, 1))
    }

    @Test
    fun theEmptyLastLineAfterAFinalBreakGetsANumber() {
        val text = "a\n"
        assertEquals(listOf("0:1", "1:2"), label(text, intArrayOf(0, 2), 0, 1, 1))
    }

    @Test
    fun emptyTextHasLineOne() {
        assertEquals(listOf("0:1"), label("", intArrayOf(0), 0, 0, 1))
    }

    @Test
    fun blankLinesInTheMiddleAreNumbered() {
        val text = "a\n\n\nb"
        assertEquals(listOf("0:1", "1:2", "2:3", "3:4"), label(text, intArrayOf(0, 2, 3, 4), 0, 3, 1))
    }

    @Test
    fun theTotalFollowsTheEditsWithoutCountingTheWholeText() {
        val total = LineTotal()
        assertEquals(1, total.lines)
        // typing "a\nb\nc" into an empty text
        total.added("a\nb\nc", 0, 5)
        assertEquals(3, total.lines)
        // deleting "\nb" (positions 1..3 of "a\nb\nc")
        total.removed("a\nb\nc", 1, 2)
        total.added("a\nc", 1, 0)
        assertEquals(2, total.lines)
        // replacing the whole text with one that has four lines
        total.removed("a\nc", 0, 3)
        total.added("1\n2\n3\n4", 0, 7)
        assertEquals(4, total.lines)
    }

    @Test
    fun theTotalNeverGoesBelowOneLine() {
        val total = LineTotal()
        total.removed("\n\n\n", 0, 3)
        assertEquals(1, total.lines)
    }
}
