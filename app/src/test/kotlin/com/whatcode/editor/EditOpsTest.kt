package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The line tools work on plain text and numbers, so they can be checked without a device. */
class EditOpsTest {

    private val kotlin = Highlighter.languageFor("a.kt")

    /** Applies [e] to [src] and shows the selection: | for a cursor, « and » around a selection. */
    private fun apply(src: String, e: TextEdit): String {
        val out = src.substring(0, e.start) + e.insert + src.substring(e.end)
        return if (e.selStart == e.selEnd) {
            out.substring(0, e.selStart) + "|" + out.substring(e.selStart)
        } else {
            out.substring(0, e.selStart) + "«" + out.substring(e.selStart, e.selEnd) + "»" + out.substring(e.selEnd)
        }
    }

    private val four = "    "

    @Test
    fun tabAtTheCursorInsertsOneLevel() {
        assertEquals("a  |b", apply("ab", EditOps.indentEdit("ab", 1, 1, false, "  ")))
        assertEquals("\t|", apply("", EditOps.indentEdit("", 0, 0, false, "\t")))
    }

    @Test
    fun tabOnSelectedLinesIndentsEachNonEmptyLine() {
        val src = "a\nb\n\nc"
        assertEquals("  «a\n  b\n\n  c»", apply(src, EditOps.indentEdit(src, 0, src.length, false, "  ")))
    }

    @Test
    fun outdentRemovesOneLevelFromEachLine() {
        val src = "    a\n\t b\n  c"
        assertEquals("«a\n b\nc»", apply(src, EditOps.indentEdit(src, 0, src.length, true, four)))
        // with two-space levels only two spaces go
        assertEquals("|  a", apply("    a", EditOps.indentEdit("    a", 0, 0, true, "  ")))
    }

    @Test
    fun outdentNeverGoesBeforeTheStartOfTheLine() {
        assertEquals("a|bc", apply("  abc", EditOps.indentEdit("  abc", 3, 3, true, four)))
    }

    @Test
    fun moveLinesSwapsWithTheNeighbourAndKeepsTheCursor() {
        assertEquals("|b\na\nc", apply("a\nb\nc", EditOps.moveLinesEdit("a\nb\nc", 2, 2, up = true)!!))
        assertEquals("b\n|a\nc", apply("a\nb\nc", EditOps.moveLinesEdit("a\nb\nc", 0, 0, up = false)!!))
        assertNull(EditOps.moveLinesEdit("a\nb", 0, 0, up = true))
        assertNull(EditOps.moveLinesEdit("a\nb", 2, 2, up = false))
    }

    @Test
    fun moveLinesMovesEverySelectedLine() {
        val src = "a\nb\nc\nd"
        val e = EditOps.moveLinesEdit(src, 2, 5, up = true)!! // b and c go above a
        assertEquals("b\nc\na", e.insert)
        assertEquals(0, e.start)
        assertEquals(5, e.end)
        assertEquals(0, e.selStart)
        assertEquals(3, e.selEnd)
    }

    @Test
    fun duplicateCopiesTheLineBelowAndMovesTheCursorDown() {
        assertEquals("a\n|a\nb", apply("a\nb", EditOps.duplicateLinesEdit("a\nb", 0, 0)))
    }

    @Test
    fun deleteRemovesTheLineWithItsBreak() {
        assertEquals("a\n|c", apply("a\nb\nc", EditOps.deleteLinesEdit("a\nb\nc", 2, 2)))
        assertEquals("a\nb|", apply("a\nb\nc", EditOps.deleteLinesEdit("a\nb\nc", 4, 4)))
        assertEquals("|", apply("abc", EditOps.deleteLinesEdit("abc", 1, 1)))
    }

    @Test
    fun toggleCommentCommentsThenUncomments() {
        val src = "a\n  b"
        val on = EditOps.toggleCommentEdit(src, 0, src.length, "//", "")!!
        val commented = src.substring(0, on.start) + on.insert + src.substring(on.end)
        assertEquals("// a\n//   b", commented)
        val off = EditOps.toggleCommentEdit(commented, 0, commented.length, "//", "")!!
        assertEquals("a\n  b", commented.substring(0, off.start) + off.insert + commented.substring(off.end))
        assertNull(EditOps.toggleCommentEdit("  \n", 0, 0, "//", ""))
    }

    @Test
    fun toggleCommentCanWrapLikeHtml() {
        val on = EditOps.toggleCommentEdit("<p>", 0, 0, "<!--", "-->")!!
        assertEquals("<!-- <p> -->", on.insert)
    }

    @Test
    fun newLineBelowKeepsTheIndentAndOpensBlocksAfterABracket() {
        assertEquals("fun a() {\n    |", apply("fun a() {", EditOps.insertLineEdit("fun a() {", 3, false, kotlin, true)))
        assertEquals("  x\n  |", apply("  x", EditOps.insertLineEdit("  x", 1, false, kotlin, false)))
        assertEquals("a\n|\nb", apply("a\nb", EditOps.insertLineEdit("a\nb", 0, false, kotlin, true)))
    }

    @Test
    fun newLineAboveKeepsTheIndent() {
        assertEquals("a\n  |\n  b", apply("a\n  b", EditOps.insertLineEdit("a\n  b", 3, true, kotlin, true)))
    }

    @Test
    fun joinLinesMakesOneSpaceOfTheBreakAndTheIndent() {
        assertEquals("a| b\nc", apply("a\n   b\nc", EditOps.joinLinesEdit("a\n   b\nc", 0, 0)!!))
    }

    @Test
    fun joinLinesOverSeveralLinesPutsTheCursorAtTheLastJoin() {
        val e = EditOps.joinLinesEdit("a\nb\nc\nd", 0, 5)!!
        assertEquals("a b c", e.insert)
        assertEquals(3, e.selStart)
        assertEquals(5, e.end) // the d line stays
    }

    @Test
    fun joinLinesSkipsEmptyLinesAndStopsAtTheEnd() {
        assertEquals("a|\nb", apply("a\n\nb", EditOps.joinLinesEdit("a\n\nb", 0, 0)!!))
        assertNull(EditOps.joinLinesEdit("a\nb", 2, 2))
    }

    @Test
    fun smartHomeStopsAtTheFirstCharacterFirst() {
        assertEquals(2, EditOps.smartHome("  foo", 5))
        assertEquals(0, EditOps.smartHome("  foo", 2))
        assertEquals(2, EditOps.smartHome("  foo", 0))
        assertEquals(0, EditOps.smartHome("foo", 2))
        assertEquals(0, EditOps.smartHome("foo", 0))
        assertEquals(0, EditOps.smartHome("   ", 3))
    }

    @Test
    fun arrowsStepOverAWholeEmoji() {
        val s = "a\uD83D\uDE00b"
        assertEquals(3, EditOps.nextIndex(s, 1))
        assertEquals(1, EditOps.prevIndex(s, 3))
        assertEquals(3, EditOps.prevIndex(s, 4))
        assertEquals(1, EditOps.nextIndex(s, 0))
        assertEquals(4, EditOps.nextIndex(s, 4))
        assertEquals(0, EditOps.prevIndex(s, 0))
        assertEquals(3, EditOps.nextIndex(s, 2)) // already inside the pair: leave it
    }

    @Test
    fun wholeLineRangesIncludeTheBreak() {
        assertEquals(0 to 3, EditOps.lineWithBreak("ab\ncd", 1))
        assertEquals(3 to 5, EditOps.lineWithBreak("ab\ncd", 4))
        assertEquals(0 to 6, EditOps.selectLinesRange("ab\ncd\nef", 1, 4))
        assertEquals(3 to 5, EditOps.selectLinesRange("ab\ncd", 4, 4))
    }

    @Test
    fun editsStayInsideTheText() {
        for (src in listOf("", "a", "\n", "a\n", "\n\na", "  a\n\tb\n")) {
            for (a in 0..src.length) for (b in a..src.length) {
                val edits = listOf(
                    EditOps.moveLinesEdit(src, a, b, true), EditOps.moveLinesEdit(src, a, b, false),
                    EditOps.duplicateLinesEdit(src, a, b), EditOps.deleteLinesEdit(src, a, b),
                    EditOps.indentEdit(src, a, b, false, four), EditOps.indentEdit(src, a, b, true, four),
                    EditOps.toggleCommentEdit(src, a, b, "//", ""), EditOps.joinLinesEdit(src, a, b),
                    EditOps.insertLineEdit(src, a, false, kotlin, true), EditOps.insertLineEdit(src, a, true, kotlin, true),
                )
                for (e in edits) {
                    if (e == null) continue
                    val len = src.length - (e.end - e.start) + e.insert.length
                    assertTrue("range ${e.start}..${e.end} in ${src.length}", e.start in 0..e.end && e.end <= src.length)
                    assertTrue("selection ${e.selStart}..${e.selEnd} in $len", e.selStart in 0..len && e.selEnd in 0..len)
                }
            }
        }
    }
}
