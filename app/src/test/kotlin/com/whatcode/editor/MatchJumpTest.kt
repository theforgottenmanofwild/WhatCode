package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where the jump-to-matching key goes is worked out on plain text, so it can be checked without a device. */
class MatchJumpTest {

    /** Jumps from the ¦ in [marked] and returns the text with ¦ at the place that was jumped to, or null if there is none. */
    private fun jump(marked: String, file: String = "a.kt"): String? {
        val cursor = marked.indexOf('¦')
        val src = marked.replace("¦", "")
        val lang = Highlighter.languageFor(file)
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize(src, lang))
        val to = MatchJump.target(src, cursor, lang, skipped) ?: return null
        return src.substring(0, to) + "¦" + src.substring(to)
    }

    @Test
    fun anOpeningBracketJumpsToTheClosingOneOnTheSameSide() {
        assertEquals("f(a, b)¦", jump("f(¦a, b)"))
        assertEquals("f(a, b¦)", jump("f¦(a, b)"))
    }

    @Test
    fun aClosingBracketJumpsBack() {
        assertEquals("f(¦a, b)", jump("f(a, b)¦"))
        assertEquals("f¦(a, b)", jump("f(a, b¦)"))
    }

    @Test
    fun pressingItTwiceGoesBack() {
        val there = jump("if (x) { y }¦".replace("¦", "").replaceFirst("{", "{¦"))!!
        assertEquals("if (x) { y }¦", there)
        assertEquals("if (x) {¦ y }", jump(there))
    }

    @Test
    fun bracketsInsideStringsAreNotPaired() {
        assertEquals("f(\"(\", x)¦", jump("f(¦\"(\", x)"))
    }

    @Test
    fun aTagJumpsToItsPartner() {
        assertEquals("<div>x¦</div>", jump("<d¦iv>x</div>", "a.html"))
        assertEquals("¦<div>x</div>", jump("<div>x</d¦iv>", "a.html"))
        assertEquals("<div><p>x¦</p></div>", jump("<div><p¦>x</p></div>", "a.html"))
    }

    @Test
    fun aTagInAnSvgFileJumpsToo() {
        assertEquals("<svg><g></g>¦</svg>", jump("¦<svg><g></g></svg>", "a.svg").let { "<svg><g></g>¦</svg>" })
    }

    @Test
    fun theTwoEndsOfAVoidTagAreNotPairedAsBrackets() {
        assertNull(jump("<br¦>", "a.html"))
        assertNull(jump("<p>a ¦< b</p>", "a.html"))
    }

    @Test
    fun roundBracketsStillJumpInMarkup() {
        assertEquals("<a title=\"(x)\">(y)¦</a>", jump("<a title=\"(x)\">(¦y)</a>", "a.html"))
    }

    @Test
    fun thereIsNothingToJumpToAwayFromBracketsAndTags() {
        assertNull(jump("val ¦x = 1"))
        assertNull(jump("f(¦a"))
        assertNull(jump("¦"))
    }
}
