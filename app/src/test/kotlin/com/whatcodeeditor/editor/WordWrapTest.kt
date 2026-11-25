package com.whatcodeeditor.editor

import com.whatcodeeditor.settings.EditorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Word wrap itself is a setting of the editor view, which needs a device. What can be checked here is that it starts
 * on (as the editor always wrapped), and that the status bar and the line keys count lines of the text, never lines
 * of the screen, so they give the same answer with wrapping on or off.
 */
class WordWrapTest {

    private val longLine = "x".repeat(20_000)
    private val text = "first\n    $longLine\nlast"

    @Test
    fun wrapsByDefaultLikeTheEditorAlwaysDid() {
        assertTrue(EditorSettings.DEFAULT_WORD_WRAP)
    }

    @Test
    fun statusBarCountsTextLinesNotScreenLines() {
        val c = LineCounter()
        val secondLine = text.indexOf('\n') + 1
        val inLong = c.locate(text, secondLine + 15_000)
        assertEquals(2, inLong.line)
        assertEquals(15_001, inLong.column)
        val last = c.locate(text, text.length)
        assertEquals(3, last.line)
        assertEquals(5, last.column)
        // and back into the long line from the end
        val again = c.locate(text, secondLine + 4)
        assertEquals(2, again.line)
        assertEquals(5, again.column)
    }

    @Test
    fun lineKeysUseTheWholeTextLine() {
        val ls = text.indexOf('\n') + 1
        val le = ls + 4 + longLine.length
        val middle = ls + 10_000
        assertEquals(ls, EditOps.lineStart(text, middle))
        assertEquals(le, EditOps.lineEnd(text, middle))
        // the start key goes to the first character, then to the true start of the line
        assertEquals(ls + 4, EditOps.smartHome(text, middle))
        assertEquals(ls, EditOps.smartHome(text, ls + 4))
    }

    @Test
    fun lineToolsTakeTheWholeLongLine() {
        val ls = text.indexOf('\n') + 1
        val (s, e) = EditOps.lineWithBreak(text, ls + 12_345)
        assertEquals(ls, s)
        assertEquals(ls + 4 + longLine.length + 1, e)
    }
}
