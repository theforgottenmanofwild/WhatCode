package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trimming trailing whitespace and picking the characters that get a whitespace mark. */
class WhitespaceTest {

    private fun apply(text: String, e: TextEdit) = text.substring(0, e.start) + e.insert + text.substring(e.end)

    @Test
    fun everyLineKeepsItsOwnEnding() {
        assertEquals("a\nb\r\nc\rd\r\n\r\n", TrimWhitespace.trim("a  \nb\t\r\nc \rd\t \r\n  \r\n"))
    }

    @Test
    fun theLastLineAndBlankLinesAreTrimmed() {
        assertEquals("a\n\nb", TrimWhitespace.trim("a\n   \nb  "))
        assertEquals("", TrimWhitespace.trim(" \t "))
    }

    @Test
    fun whitespaceInsideALineStays() {
        val text = "  a  b\tc\n\td"
        assertEquals(text, TrimWhitespace.trim(text))
        assertNull(TrimWhitespace.edit(text, 0, 0))
        assertNull(TrimWhitespace.edit("", 0, 0))
    }

    @Test
    fun theEditGivesTheSameTextAsTrimming() {
        val text = "one  \r\ntwo\nthree\t\nfour"
        val e = TrimWhitespace.edit(text, 0, 0)!!
        assertEquals(TrimWhitespace.trim(text), apply(text, e))
        assertEquals(3, e.start)
    }

    @Test
    fun theCursorStaysOnTheSameCharacter() {
        val text = "ab  \ncd  \nef"
        val e = TrimWhitespace.edit(text, 11, 11)!!
        assertEquals("ab\ncd\nef", apply(text, e))
        assertEquals(7, e.selStart)
        assertEquals(7, e.selEnd)
    }

    @Test
    fun aCursorInTheRemovedSpacesGoesToTheEndOfItsLine() {
        val text = "ab    \ncd"
        assertEquals(2, TrimWhitespace.edit(text, 4, 4)!!.selStart)
        assertEquals(2, TrimWhitespace.edit(text, 6, 6)!!.selStart)
        assertEquals(3, TrimWhitespace.edit(text, 7, 7)!!.selStart)
        assertEquals(1, TrimWhitespace.edit(text, 1, 1)!!.selStart)
    }

    @Test
    fun aSelectionKeepsItsDirection() {
        val text = "a  \nbc  "
        val e = TrimWhitespace.edit(text, 6, 0)!!
        assertEquals(4, e.selStart)
        assertEquals(0, e.selEnd)
    }

    @Test
    fun markdownIsLeftAlone() {
        assertFalse(TrimWhitespace.appliesTo(Highlighter.languageFor("README.md")))
        assertFalse(TrimWhitespace.appliesTo(Highlighter.languageFor("notes.markdown")))
        assertTrue(TrimWhitespace.appliesTo(Highlighter.languageFor("Main.kt")))
        assertTrue(TrimWhitespace.appliesTo(Highlighter.languageFor("notes.txt")))
    }

    @Test
    fun marksAreFoundOnlyInTheRangeAsked() {
        val found = ArrayList<String>()
        WhitespaceMarks.find(" a\tb c ", 1, 6) { at, tab -> found.add("$at:$tab") }
        assertEquals(listOf("2:true", "4:false"), found)
        found.clear()
        WhitespaceMarks.find("\r\n", -3, 40) { at, tab -> found.add("$at:$tab") }
        assertEquals(emptyList<String>(), found)
    }
}
