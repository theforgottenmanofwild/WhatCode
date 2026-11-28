package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextConvertTest {

    private fun applied(t: String, e: TextEdit?): String = e!!.let { t.substring(0, it.start) + it.insert + t.substring(it.end) }

    @Test
    fun spacesBecomeTabsOnlyAtTheStartOfALine() {
        val t = "a\n    b    c\n        d\n      e"
        assertEquals("a\n\tb    c\n\t\td\n\t  e", applied(t, TextConvert.indentation(t, toTabs = true, width = 4)))
    }

    @Test
    fun tabsBecomeSpacesUpToTheTabStop() {
        val t = "\ta\n  \tb\tc"
        assertEquals("  a\n    b\tc", applied(t, TextConvert.indentation(t, toTabs = false, width = 2)))
        assertEquals("    a\n    b\tc", applied(t, TextConvert.indentation(t, toTabs = false, width = 4)))
    }

    @Test
    fun blankLinesAndLineEndingsAreLeftAlone() {
        val t = "    \r\n    a\r\n\t\r\n"
        assertEquals("    \r\n\ta\r\n\t\r\n", applied(t, TextConvert.indentation(t, toTabs = true, width = 4)))
    }

    @Test
    fun noIndentationToChangeGivesNoEdit() {
        assertNull(TextConvert.indentation("a\n\tb", toTabs = true))
        assertNull(TextConvert.indentation("a\n  b", toTabs = false))
        assertNull(TextConvert.indentation("", toTabs = true))
    }

    @Test
    fun theCursorStaysOnItsCharacterWhenIndentationChanges() {
        val t = "    a\n    b"
        val e = TextConvert.indentation(t, toTabs = true, width = 4, selStart = t.indexOf('b'), selEnd = t.length)!!
        val out = applied(t, e)
        assertEquals(out.indexOf('b'), e.selStart)
        assertEquals(out.length, e.selEnd)
    }

    @Test
    fun everyKindOfBreakBecomesTheOneAskedFor() {
        val t = "a\r\nb\nc\rd"
        assertEquals("a\nb\nc\nd", applied(t, TextConvert.lineEndings(t, "\n")))
        assertEquals("a\r\nb\r\nc\r\nd", applied(t, TextConvert.lineEndings(t, "\r\n")))
    }

    @Test
    fun aTextThatIsAlreadyAsAskedGivesNoEdit() {
        assertNull(TextConvert.lineEndings("a\nb\n", "\n"))
        assertNull(TextConvert.lineEndings("a\r\nb", "\r\n"))
        assertNull(TextConvert.lineEndings("no break", "\r\n"))
    }

    @Test
    fun theCursorStaysOnItsCharacterWhenEndingsChange() {
        val lf = "ab\ncd\nef"
        val toCrlf = TextConvert.lineEndings(lf, "\r\n", lf.indexOf('c'), lf.indexOf('e'))!!
        val crlf = applied(lf, toCrlf)
        assertEquals(crlf.indexOf('c'), toCrlf.selStart)
        assertEquals(crlf.indexOf('e'), toCrlf.selEnd)
        val toLf = TextConvert.lineEndings(crlf, "\n", crlf.indexOf('e'), 3)!!
        assertEquals(lf.indexOf('e'), toLf.selStart)
        assertEquals(2, toLf.selEnd) // from between \r and \n
    }
}
