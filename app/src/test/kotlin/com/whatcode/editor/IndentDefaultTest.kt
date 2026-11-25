package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The indentation chosen in Settings is only the answer for a file that shows no pattern of its own. These checks go
 * through the same functions the editor uses for Tab, Shift+Tab, Enter, Backspace and the indent keys.
 */
class IndentDefaultTest {

    private val kotlin = Highlighter.languageFor("a.kt")
    private val go = Highlighter.languageFor("main.go")
    private val makefile = Highlighter.languageFor("Makefile")
    private val python = Highlighter.languageFor("a.py")
    private val html = Highlighter.languageFor("a.html")

    private val tab = IndentDefault.TAB.unit
    private val two = IndentDefault.SPACES_2.unit
    private val four = IndentDefault.SPACES_4.unit
    private val eight = IndentDefault.SPACES_8.unit

    /** Applies [e] to [src] and shows where the cursor ends up with |. */
    private fun apply(src: String, e: TextEdit): String {
        val out = src.substring(0, e.start) + e.insert + src.substring(e.end)
        return out.substring(0, e.selStart) + "|" + out.substring(e.selStart)
    }

    @Test
    fun theChoicesAreWhatTheirLabelsSay() {
        assertNull(IndentDefault.AUTOMATIC.unit)
        assertEquals("\t", tab)
        assertEquals("  ", two)
        assertEquals("    ", four)
        assertEquals("        ", eight)
    }

    @Test
    fun savedKeysComeBackAndAnythingElseIsAutomatic() {
        for (d in IndentDefault.values()) assertEquals(d, IndentDefault.fromKey(d.key))
        assertEquals(IndentDefault.values().size, IndentDefault.values().map { it.key }.toSet().size)
        assertEquals(IndentDefault.AUTOMATIC, IndentDefault.fromKey(null))
        assertEquals(IndentDefault.AUTOMATIC, IndentDefault.fromKey(""))
        assertEquals(IndentDefault.AUTOMATIC, IndentDefault.fromKey("16"))
    }

    @Test
    fun automaticKeepsTodaysBehaviour() {
        val none = IndentDefault.AUTOMATIC.unit
        assertEquals("    ", TypingLogic.indentUnit("a\nb\n", kotlin, none))
        assertEquals("    ", TypingLogic.indentUnit("", python, none))
        assertEquals("\t", TypingLogic.indentUnit("a\nb\n", go, none))
        assertEquals("\t", TypingLogic.indentUnit("", makefile, none))
        // and the same as asking without a choice at all
        assertEquals(TypingLogic.indentUnit("a\nb\n", go), TypingLogic.indentUnit("a\nb\n", go, none))
    }

    @Test
    fun theChoiceIsUsedWhenTheFileShowsNoPattern() {
        // empty, not indented, indented only by blank lines, and indented deeper than a pattern can be read from
        val texts = listOf("", "a\nb\nc\n", "a\n        \nb\n", "a\n        b\n")
        for (t in texts) {
            for (d in listOf(tab, two, four, eight)) {
                assertEquals(d, TypingLogic.indentUnit(t, kotlin, d))
                assertEquals(d, TypingLogic.indentUnit(t, go, d)) // also in Go, where the choice replaces the tab
            }
        }
        assertEquals("\t", TypingLogic.indentUnit("a\nb\n", kotlin, tab))
    }

    @Test
    fun theFilesOwnIndentationStillWins() {
        val tabbed = "a {\n\tb\n}\n"
        val twos = "a:\n  b: 1\n  c:\n    d: 2\n"
        val fours = "a {\n    b\n}\n"
        for (d in listOf(tab, two, four, eight)) {
            assertEquals("\t", TypingLogic.indentUnit(tabbed, kotlin, d))
            assertEquals("  ", TypingLogic.indentUnit(twos, kotlin, d))
            assertEquals("    ", TypingLogic.indentUnit(fours, kotlin, d))
        }
        // a Go file indented with spaces keeps spaces, as it did before
        assertEquals("    ", TypingLogic.indentUnit(fours, go, tab))
    }

    @Test
    fun tabInsertsAndIndentsWithTheChoice() {
        val e = EditOps.indentEdit("", 0, 0, false, TypingLogic.indentUnit("", kotlin, eight))
        assertEquals("        |", apply("", e))
        val t = TypingLogic.indentUnit("", kotlin, tab)
        assertEquals("\t|", apply("", EditOps.indentEdit("", 0, 0, false, t)))
        // selected lines: every line that has text gets one level, blank lines are left alone
        val src = "a\nb\n\nc"
        val u = TypingLogic.indentUnit(src, kotlin, two)
        assertEquals("  a\n  b\n\n  c", EditOps.indentEdit(src, 0, src.length, false, u).insert)
    }

    @Test
    fun shiftTabRemovesOneLevelOfTheChoice() {
        // twelve spaces show no pattern, so the choice (eight) is one level
        val deep = "            x"
        val u8 = TypingLogic.indentUnit(deep, kotlin, eight)
        assertEquals("    x", EditOps.indentEdit(deep, 12, 12, true, u8).insert)
        // six spaces show no pattern either; the choice (two) is one level
        val six = "      x"
        val u2 = TypingLogic.indentUnit(six, kotlin, two)
        assertEquals("    x", EditOps.indentEdit(six, 6, 6, true, u2).insert)
        // a line with a tab loses the tab
        val tabbed = "\tx"
        assertEquals("x", EditOps.indentEdit(tabbed, 1, 1, true, TypingLogic.indentUnit(tabbed, kotlin, eight)).insert)
    }

    @Test
    fun enterAfterAnOpeningBracketIndentsByTheChoice() {
        val src = "fun f() {"
        for ((d, expected) in listOf(tab to "\t", two to "  ", four to "    ", eight to "        ")) {
            assertEquals("$src\n$expected|", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true, d)))
        }
        // an empty pair opens an indented line between the brackets
        val pair = "f() {}"
        assertEquals("f() {\n\t|\n}", apply(pair, TypingLogic.newline(pair, 5, 5, kotlin, true, tab)))
        // a colon in Python, a tag in HTML
        val py = "if x:"
        assertEquals("if x:\n        |", apply(py, TypingLogic.newline(py, py.length, py.length, python, true, eight)))
        val tags = "<div></div>"
        assertEquals("<div>\n  |\n</div>", apply(tags, TypingLogic.newline(tags, 5, 5, html, true, two)))
    }

    @Test
    fun enterWithoutAnOpenerOnlyCopiesTheIndentation() {
        val src = "    a"
        assertEquals("    a\n    |", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true, tab)))
    }

    @Test
    fun enterKeepsTheFilesOwnIndentationOverTheChoice() {
        val src = "a {\n\tb {"
        assertEquals("$src\n\t\t|", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true, eight)))
    }

    @Test
    fun backspaceInLeadingSpacesRemovesOneLevelOfTheChoice() {
        val eightSpaces = "        "
        val e8 = TypingLogic.backspace(eightSpaces, 8, 8, kotlin, true, true, eight)
        assertNotNull(e8)
        assertEquals("|", apply(eightSpaces, e8!!))
        val fourSpaces = "    "
        assertEquals("  |", apply(fourSpaces, TypingLogic.backspace(fourSpaces, 4, 4, kotlin, true, true, two)!!))
        // part of a level goes first
        val ten = "          "
        assertEquals("        |", apply(ten, TypingLogic.backspace(ten, 10, 10, kotlin, true, true, eight)!!))
        // with a tab level, or one space into a level, it is a plain one-character backspace
        assertNull(TypingLogic.backspace(fourSpaces, 4, 4, kotlin, true, true, tab))
        assertNull(TypingLogic.backspace("   ", 3, 3, kotlin, true, true, two))
    }

    @Test
    fun backspaceFollowsTheFilesOwnIndentationOverTheChoice() {
        // this file is indented by two spaces, so a level is two spaces whatever the choice is
        val src = "a {\n  b\n  "
        assertEquals("a {\n  b\n|", apply(src, TypingLogic.backspace(src, src.length, src.length, kotlin, true, true, eight)!!))
        val deeper = "a {\n  b {\n    "
        assertEquals("a {\n  b {\n  |", apply(deeper, TypingLogic.backspace(deeper, deeper.length, deeper.length, kotlin, true, true, eight)!!))
    }

    @Test
    fun backspaceDoesNothingSpecialWithSmartIndentOff() {
        val eightSpaces = "        "
        assertNull(TypingLogic.backspace(eightSpaces, 8, 8, kotlin, true, false, eight))
    }

    @Test
    fun closingBracketAtTheStartOfALineStepsBackOneLevelOfTheChoice() {
        val src = "fun f() {\n        "
        val e = TypingLogic.typed(src, src.length, src.length, '}', kotlin, autoClose = true, smartIndent = true, indentFallback = eight)!!
        assertEquals("fun f() {\n}|", apply(src, e))
        val e2 = TypingLogic.typed(src, src.length, src.length, '}', kotlin, autoClose = true, smartIndent = true, indentFallback = two)!!
        assertEquals("fun f() {\n      }|", apply(src, e2))
    }

    @Test
    fun theLineToolsUseTheChoice() {
        val src = "if (x) {"
        assertEquals("if (x) {\n\t|", apply(src, EditOps.insertLineEdit(src, src.length, false, kotlin, true, tab)))
        assertEquals("if (x) {\n    |", apply(src, EditOps.insertLineEdit(src, src.length, false, kotlin, true, null)))
    }

    @Test
    fun aHugeFileWithoutAPatternStillGetsTheChoice() {
        assertEquals("\t", TypingLogic.indentUnit("a\n" + "x".repeat(500_000), kotlin, tab))
        assertTrue(TypingLogic.indentUnit("a\n" + "x".repeat(500_000), kotlin, null) == "    ")
    }
}
