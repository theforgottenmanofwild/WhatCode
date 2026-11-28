package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineToolsTest {

    private val plain = LineTools.Options()

    private fun applied(t: String, e: TextEdit?): String = e!!.let { t.substring(0, it.start) + it.insert + t.substring(it.end) }

    private fun sorted(t: String, o: LineTools.Options = plain) = applied(t, LineTools.sortLines(t, 0, 0, false, o))

    @Test
    fun sortsTheWholeTextAndKeepsTheFinalBreakAsItWas() {
        assertEquals("a\nb\nc\n", sorted("c\na\nb\n"))
        assertEquals("a\nb\nc", sorted("c\na\nb"))
    }

    @Test
    fun windowsAndMixedLineEndingsStayWhereTheyWere() {
        assertEquals("a\r\nb\r\n", sorted("b\r\na\r\n"))
        assertEquals("a\r\nb\nc", sorted("c\r\nb\na"))
        assertEquals("a\rb", sorted("b\ra"))
    }

    @Test
    fun numbersAreReadAsValuesUnlessThatIsOff() {
        assertEquals("a2\na10", sorted("a10\na2"))
        assertEquals("a10\na2", sorted("a2\na10", LineTools.Options(natural = false)))
    }

    @Test
    fun capitalsCanBeIgnoredOrComeFirst() {
        assertEquals("a\nB", sorted("B\na"))
        assertEquals("B\na", sorted("a\nB", LineTools.Options(ignoreCase = false, natural = false)))
    }

    @Test
    fun sortsDownwards() {
        assertEquals("c\nb\na", applied("a\nc\nb", LineTools.sortLines("a\nc\nb", 0, 0, true, plain)))
    }

    @Test
    fun onlyTheSelectedLinesAreSortedAndTheyStaySelected() {
        val t = "z\nc\nb\na"
        val e = LineTools.sortLines(t, 2, 5, false, plain)!!
        assertEquals("z\nb\nc\na", applied(t, e))
        assertEquals(2, e.selStart)
        assertEquals(5, e.selEnd)
    }

    @Test
    fun aSelectionInsideOneLineWorksOnTheWholeTextWithoutSelectingIt() {
        val e = LineTools.sortLines("b\na", 0, 1, false, plain)!!
        assertEquals("a\nb", e.insert)
        assertEquals(0, e.selStart)
        assertEquals(1, e.selEnd)
    }

    @Test
    fun nothingToDoGivesNoEdit() {
        assertNull(LineTools.sortLines("a\nb", 0, 0, false, plain))
        assertNull(LineTools.sortLines("", 0, 0, false, plain))
        assertNull(LineTools.sortLines("only", 0, 0, false, plain))
        assertNull(LineTools.reverseLines("a\na", 0, 0))
        assertNull(LineTools.removeDuplicateLines("a\nb", 0, 0, false))
    }

    @Test
    fun reverses() {
        assertEquals("c\r\nb\r\na\r\n", applied("a\r\nb\r\nc\r\n", LineTools.reverseLines("a\r\nb\r\nc\r\n", 0, 0)))
    }

    @Test
    fun duplicatesKeepTheFirstAndEachLineItsOwnEnding() {
        val t = "a\r\nb\na\nc\r\n"
        assertEquals("a\r\nb\nc\r\n", applied(t, LineTools.removeDuplicateLines(t, 0, 0, false)))
        assertEquals("a\nb", applied("a\nb\na", LineTools.removeDuplicateLines("a\nb\na", 0, 0, false)))
    }

    @Test
    fun duplicatesCanIgnoreCapitals() {
        assertNull(LineTools.removeDuplicateLines("A\na", 0, 0, false))
        assertEquals("A", applied("A\na", LineTools.removeDuplicateLines("A\na", 0, 0, true)))
    }

    @Test
    fun changesTheCaseOfTheSelectionAndKeepsItSelected() {
        val e = LineTools.changeCase("one two", 4, 7, LineTools.Case.UPPER)!!
        assertEquals("one TWO", applied("one two", e))
        assertEquals(4, e.selStart)
        assertEquals(7, e.selEnd)
    }

    @Test
    fun withNoSelectionOnlyTheWordAtTheCursorChanges() {
        for (cursor in 4..7) {
            val e = LineTools.changeCase("one two six", cursor, cursor, LineTools.Case.UPPER)!!
            assertEquals("one TWO six", applied("one two six", e))
            assertEquals(cursor, e.selStart)
        }
        assertNull(LineTools.changeCase("a  b", 2, 2, LineTools.Case.UPPER))
        assertNull(LineTools.changeCase("", 0, 0, LineTools.Case.UPPER))
    }

    @Test
    fun theCursorStaysInsideAWordThatChangesLength() {
        val e = LineTools.changeCase("stra\u00DFe", 6, 6, LineTools.Case.UPPER)!!
        assertEquals("STRASSE", e.insert)
        assertEquals(6, e.selStart)
    }

    @Test
    fun titleCaseKeepsApostrophesAccentsAndEmojiTogether() {
        assertEquals("Don't Stop", LineTools.convertCase("DON'T stop", LineTools.Case.TITLE))
        assertEquals("E\u0301te", LineTools.convertCase("e\u0301TE", LineTools.Case.TITLE))
        assertEquals("\uD83D\uDE00 Ok", LineTools.convertCase("\uD83D\uDE00 ok", LineTools.Case.TITLE))
    }

    private fun swapped(t: String, at: Int) = applied(t, LineTools.transpose(t, at, at))

    @Test
    fun swapsTheCharactersRoundTheCursor() {
        assertEquals("ba", swapped("ab", 1))
        assertEquals("acb", swapped("abc", 3))
        assertEquals("ab\ndc", swapped("ab\ncd", 5))
        assertEquals("ba\r\n", swapped("ab\r\n", 2))
        assertEquals("ba\r\n", swapped("ab\r\n", 3))
        assertEquals("a\rcb", swapped("a\rbc", 3))
    }

    @Test
    fun anEmojiMovesAsOne() {
        assertEquals("\uD83D\uDE00a", swapped("a\uD83D\uDE00", 1))
        assertEquals("a\uD83D\uDE00", swapped("\uD83D\uDE00a", 2))
    }

    @Test
    fun nothingIsSwappedWithASelectionAtALineStartOrOnAShortLine() {
        assertNull(LineTools.transpose("ab", 0, 2))
        assertNull(LineTools.transpose("ab", 0, 0))
        assertNull(LineTools.transpose("a", 1, 1))
        assertNull(LineTools.transpose("ab\ncd", 3, 3))
        assertNull(LineTools.transpose("", 0, 0))
    }
}
