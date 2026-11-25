package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class LineCounterTest {

    /** The slow way, from the start of the text. */
    private fun slow(t: String, pos: Int): Pair<Int, Int> {
        var line = 1
        var lastNl = -1
        for (i in 0 until pos) if (t[i] == '\n') { line++; lastNl = i }
        return line to (pos - lastNl)
    }

    private fun check(c: LineCounter, t: String, pos: Int) {
        val p = c.locate(t, pos)
        assertEquals("line at $pos in ${t.length}", slow(t, pos).first, p.line)
        assertEquals("column at $pos in ${t.length}", slow(t, pos).second, p.column)
    }

    @Test
    fun matchesCountingFromTheStartForEveryPositionInAnyOrder() {
        val t = "one\ntwo\n\nthree and more\nx\n"
        val c = LineCounter()
        for (pos in 0..t.length) check(c, t, pos)
        for (pos in t.length downTo 0) check(c, t, pos)
        val order = listOf(5, 20, 2, 9, 9, 0, 26, 13, 1, 25)
        for (pos in order) check(c, t, pos)
    }

    @Test
    fun staysRightWhileTheTextIsEdited() {
        var t = "alpha\nbeta\ngamma\ndelta\n"
        val c = LineCounter()
        check(c, t, 14)
        // type in the line with the cursor
        t = t.substring(0, 14) + "XY" + t.substring(14); c.edited(14)
        check(c, t, 16)
        // backspace
        t = t.substring(0, 15) + t.substring(16); c.edited(15)
        check(c, t, 15)
        // add a line break before the remembered position, far above it
        t = t.substring(0, 2) + "\n" + t.substring(2); c.edited(2)
        check(c, t, 16)
        // delete a lot of text before the cursor
        t = t.substring(10); c.edited(0)
        check(c, t, 4)
        // the text is replaced by a shorter one
        t = "q"; c.edited(0)
        check(c, t, 1)
    }

    @Test
    fun anEmptyTextIsLineOneColumnOne() {
        val p = LineCounter().locate("", 0)
        assertEquals(1, p.line)
        assertEquals(1, p.column)
    }

    @Test
    fun aPositionPastTheEndIsClamped() {
        val p = LineCounter().locate("ab\ncd", 99)
        assertEquals(2, p.line)
        assertEquals(3, p.column)
    }
}
