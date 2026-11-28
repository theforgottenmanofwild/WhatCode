package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksTest {

    private val text = "one\ntwo\nthree\nfour\n"

    @Test
    fun toggleMarksTheLineThenTakesTheMarkAway() {
        val b = Bookmarks()
        assertTrue(b.toggle(text, 5))
        assertEquals(listOf(4), b.lineStarts(text))
        assertTrue(b.has(text, 6))
        assertFalse(b.toggle(text, 7))
        assertEquals(0, b.count)
    }

    @Test
    fun nextAndPreviousGoRoundAndNeedAMark() {
        val b = Bookmarks()
        assertNull(b.next(text, 0))
        assertNull(b.previous(text, 0))
        b.toggle(text, 4)
        b.toggle(text, 14)
        assertEquals(4, b.next(text, 0))
        assertEquals(14, b.next(text, 5))
        assertEquals(4, b.next(text, 14))
        assertEquals(14, b.previous(text, 4))
        assertEquals(4, b.previous(text, 14))
        assertEquals(14, b.previous(text, 20))
        // With one mark, the next one is the mark itself, wherever the cursor is.
        val one = Bookmarks().also { it.toggle(text, 4) }
        assertEquals(4, one.next(text, 4))
        assertEquals(4, one.previous(text, 5))
    }

    @Test
    fun marksFollowTheirLineWhenTextIsAddedOrRemovedAbove() {
        val b = Bookmarks()
        b.toggle(text, 8)
        // A line is added at the top: "new\n" is 4 characters.
        b.edited(0, 0, 4)
        assertEquals(listOf(12), b.lineStarts("new\n$text"))
        // And removed again.
        b.edited(0, 4, 0)
        assertEquals(listOf(8), b.lineStarts(text))
    }

    @Test
    fun typingAtTheStartOfAMarkedLineKeepsTheMarkOnThatLine() {
        val b = Bookmarks()
        b.toggle(text, 4)
        b.edited(4, 0, 2)
        assertEquals(listOf(4), b.lineStarts("one\nxxtwo\nthree\nfour\n"))
    }

    @Test
    fun enterAtTheStartOfAMarkedLinePushesTheMarkDownWithItsText() {
        val b = Bookmarks()
        b.toggle(text, 4)
        b.edited(4, 0, 1)
        assertEquals(listOf(5), b.lineStarts("one\n\ntwo\nthree\nfour\n"))
    }

    @Test
    fun aRemovedRangeThatHoldsMarksLeavesOneAtItsStart() {
        val b = Bookmarks()
        b.toggle(text, 4)
        b.toggle(text, 8)
        b.edited(2, 10, 0)
        assertEquals(1, b.count)
        assertEquals(2, b.offsets()[0])
    }

    @Test
    fun twoMarksOnOneLineAfterAJoinCountOnce() {
        val b = Bookmarks()
        b.toggle(text, 0)
        b.toggle(text, 4)
        // The break between line 1 and 2 is removed.
        b.edited(3, 1, 0)
        val joined = "onetwo\nthree\nfour\n"
        assertEquals(listOf(0), b.lineStarts(joined))
    }

    @Test
    fun emptyTextAndOutOfRangeOffsetsAreSafe() {
        val b = Bookmarks()
        assertTrue(b.toggle("", 0))
        assertEquals(listOf(0), b.lineStarts(""))
        assertEquals(0, b.next("", 5))
        b.load(intArrayOf(3, 99, -1, 3), 10)
        assertEquals(listOf(3), b.offsets().toList())
    }

    @Test
    fun aCopyIsIndependent() {
        val b = Bookmarks().also { it.toggle(text, 4) }
        val c = b.copy()
        c.clear()
        assertEquals(1, b.count)
    }

    @Test
    fun crlfLinesAreMarkedByTheirStart() {
        val t = "a\r\nb\r\nc"
        val b = Bookmarks()
        b.toggle(t, 4)
        assertEquals(listOf(3), b.lineStarts(t))
    }
}
