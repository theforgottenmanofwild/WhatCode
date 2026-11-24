package com.whatcodeeditor.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The typing rules work on plain text and numbers, so they can be checked without a device. */
class TypingLogicTest {

    private val kotlin = Highlighter.languageFor("a.kt")
    private val python = Highlighter.languageFor("a.py")
    private val text = Highlighter.languageFor("a.txt")

    /** Applies [e] to [src] the way the editor does and returns the new text with | where the cursor ends up. */
    private fun apply(src: String, e: TextEdit): String {
        val out = src.substring(0, e.start) + e.insert + src.substring(e.end)
        return out.substring(0, e.selStart) + "|" + out.substring(e.selStart)
    }

    private fun typed(src: String, cursor: Int, c: Char, lang: Lang = kotlin, end: Int = cursor): TextEdit? =
        TypingLogic.typed(src, cursor, end, c, lang, autoClose = true, smartIndent = true)

    @Test
    fun openingBracketAddsItsPair() {
        assertEquals("(|)", apply("", typed("", 0, '(')!!))
        assertEquals("foo[|]", apply("foo", typed("foo", 3, '[')!!))
    }

    @Test
    fun openingBracketBeforeAWordIsLeftAlone() {
        assertNull(typed("abc", 0, '('))
    }

    @Test
    fun openingBracketOverASelectionWrapsIt() {
        val e = typed("abc", 0, '(', end = 3)!!
        assertEquals("(abc)", e.insert)
        assertEquals(1, e.selStart)
        assertEquals(4, e.selEnd)
    }

    @Test
    fun closingBracketStepsOverTheOneThatIsThere() {
        val e = typed("()", 1, ')')!!
        assertEquals("", e.insert)
        assertEquals(2, e.selStart)
    }

    @Test
    fun quoteOpensAPairButNotAfterALetter() {
        assertEquals("\"|\"", apply("", typed("", 0, '"')!!))
        assertNull(typed("don", 3, '\''))
    }

    @Test
    fun quoteClosingAStringStepsOver() {
        val e = typed("\"a\"", 2, '"')!!
        assertEquals(3, e.selStart)
        assertEquals("", e.insert)
    }

    @Test
    fun noQuotePairsInPlainText() {
        assertNull(typed("", 0, '"', lang = text))
    }

    @Test
    fun backspaceBetweenAnEmptyPairRemovesBoth() {
        assertEquals("|", apply("()", TypingLogic.backspace("()", 1, 1, kotlin)!!))
        assertNull(TypingLogic.backspace("(a)", 1, 1, kotlin))
        assertNull(TypingLogic.backspace("()", 1, 2, kotlin)) // something is selected
    }

    @Test
    fun enterKeepsTheIndentOfTheLineAbove() {
        val src = "    foo"
        assertEquals("    foo\n    |", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true)))
    }

    @Test
    fun enterAfterAnOpeningBracketIndentsOneMoreLevel() {
        val src = "fun a() {"
        assertEquals("fun a() {\n    |", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true)))
    }

    @Test
    fun enterBetweenBracketsOpensAnEmptyLine() {
        val src = "x {}"
        assertEquals("x {\n    |\n}", apply(src, TypingLogic.newline(src, 3, 3, kotlin, true)))
    }

    @Test
    fun enterAfterAColonIndentsOnlyInPython() {
        val src = "if x:"
        assertEquals("if x:\n    |", apply(src, TypingLogic.newline(src, src.length, src.length, python, true)))
        assertEquals("if x:\n|", apply(src, TypingLogic.newline(src, src.length, src.length, kotlin, true)))
    }

    @Test
    fun aColonInsideAPythonCommentDoesNotIndent() {
        val src = "x = 1  # note:"
        assertEquals("x = 1  # note:\n|", apply(src, TypingLogic.newline(src, src.length, src.length, python, true)))
    }

    @Test
    fun indentUnitFollowsTheFile() {
        assertEquals("\t", TypingLogic.indentUnit("a\n\tb\n", kotlin))
        assertEquals("  ", TypingLogic.indentUnit("a:\n  b: 1\n", Highlighter.languageFor("a.yml")))
        assertEquals("    ", TypingLogic.indentUnit("a\nb\n", kotlin))
        assertEquals("\t", TypingLogic.indentUnit("a\nb\n", Highlighter.languageFor("main.go")))
    }

    @Test
    fun aClosingBracketAloneOnALineMovesBackOneLevel() {
        val src = "if (a) {\n    "
        val e = typed(src, src.length, '}')
        assertNotNull(e)
        assertEquals("if (a) {\n}|", apply(src, e!!))
    }

    @Test
    fun editsStayInsideTheText() {
        for (src in listOf("", "(", ")", "\"", "a\n", "\n\n")) {
            for (c in "([{)]}\"'`") {
                for (pos in 0..src.length) {
                    val e = TypingLogic.typed(src, pos, pos, c, kotlin, autoClose = true, smartIndent = true) ?: continue
                    assertTrue("bad range for '$c' at $pos in ${src.length}", e.start in 0..src.length && e.end in e.start..src.length)
                    assertTrue("bad cursor for '$c' at $pos in ${src.length}", e.selStart >= 0)
                }
            }
        }
    }
}
