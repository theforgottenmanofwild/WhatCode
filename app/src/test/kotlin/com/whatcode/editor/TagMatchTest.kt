package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tag pairing and closing-tag completion work on plain text, so they can be checked without a device. */
class TagMatchTest {

    /** Finds the pair for [marked], where ¦ shows the cursor. Returns the four marked positions, or null. */
    private fun find(marked: String, file: String = "a.html"): List<Int>? {
        val cursor = marked.indexOf('¦')
        val src = marked.replace("¦", "")
        val lang = Highlighter.languageFor(file)
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize(src, lang))
        val p = TagMatch.find(src, cursor, lang, skipped) ?: return null
        return listOf(p.openStart, p.openEnd, p.closeStart, p.closeEnd)
    }

    /** Types `>` at the ¦ and returns the text afterwards with the cursor shown, or null if typing is left alone. */
    private fun typeGt(marked: String, file: String = "a.html", autoClose: Boolean = true): String? {
        val cursor = marked.indexOf('¦')
        val src = marked.replace("¦", "")
        val lang = Highlighter.languageFor(file)
        val skipped = BracketMatch.skipFrom(Highlighter.tokenize(src, lang))
        val edit = TypingLogic.typed(src, cursor, cursor, '>', lang, autoClose, true, null, skipped) ?: return null
        val out = src.substring(0, edit.start) + edit.insert + src.substring(edit.end)
        return out.substring(0, edit.selStart) + "¦" + out.substring(edit.selStart)
    }

    @Test
    fun theCursorInAnOpeningTagFindsTheClosingTag() {
        // <div><p>x</p></div>: <div is 0..4 and </div> is 13..19
        assertEquals(listOf(0, 4, 13, 19), find("<d¦iv><p>x</p></div>"))
        assertEquals(listOf(0, 4, 13, 19), find("¦<div><p>x</p></div>"))
        assertEquals(listOf(0, 4, 13, 19), find("<div¦><p>x</p></div>"))
    }

    @Test
    fun theCursorInAClosingTagFindsTheOpeningTag() {
        // <div><p>x</p></div>: <p is 5..7 and </p> is 9..13
        assertEquals(listOf(5, 7, 9, 13), find("<div><p>x</p¦></div>"))
        assertEquals(listOf(5, 7, 9, 13), find("<div><p>x¦</p></div>"))
        assertEquals(listOf(0, 4, 13, 19), find("<div><p>x</p></div>¦"))
    }

    @Test
    fun nestedTagsOfTheSameNamePairUpFromTheOutside() {
        assertEquals(listOf(0, 4, 17, 23), find("¦<div><div>x</div></div>"))
        assertEquals(listOf(5, 9, 11, 17), find("<div><d¦iv>x</div></div>"))
    }

    @Test
    fun attributesDoNotGetInTheWay() {
        assertEquals(listOf(0, 2, 28, 32), find("<a h¦ref=\"x>y\" class=\"b\">text</a>"))
    }

    @Test
    fun voidAndSelfClosingTagsHaveNoPartner() {
        assertNull(find("<br¦>"))
        assertNull(find("<p>a<br¦>b</p>"))
        assertNull(find("<p¦ />"))
        assertNull(find("<svg><path¦ d=\"\"/></svg>", "a.svg"))
    }

    @Test
    fun plainTextAndCommentsAreNotTags() {
        assertNull(find("<p>1 ¦< 2</p>"))
        assertNull(find("<!-- <b¦> </b> -->"))
        assertNull(find("<p>te¦xt</p>"))
        assertNull(find("<div¦>x"))
    }

    @Test
    fun otherLanguagesAreLeftAlone() {
        assertNull(find("<d¦iv></div>", "a.kt"))
    }

    // ---------------------------------------------------------------- closing tags

    @Test
    fun endingAnOpeningTagAddsItsClosingTag() {
        assertEquals("<div>¦</div>", typeGt("<div¦"))
        assertEquals("<div class=\"box\">¦</div>", typeGt("<div class=\"box\"¦"))
        assertEquals("<p>a</p><b>¦</b>", typeGt("<p>a</p><b¦"))
        assertEquals("<my-tag>¦</my-tag>", typeGt("<my-tag¦"))
        assertEquals("<svg>¦</svg>", typeGt("<svg¦", "a.svg"))
    }

    @Test
    fun aTagThatIsAlreadyClosedIsLeftAlone() {
        assertNull(typeGt("<div¦</div>"))
    }

    @Test
    fun aClosingTagOfAnotherElementDoesNotCount() {
        assertEquals("<div><p>¦</p></div>", typeGt("<div><p¦</div>"))
    }

    @Test
    fun aTagNestedInOneOfTheSameNameGetsItsOwnClosingTag() {
        assertEquals("<div><div>¦</div></div>", typeGt("<div><div¦</div>"))
        assertNull(typeGt("<div><div¦x</div></div>"))
        assertNull(typeGt("<div¦<div></div></div>"))
    }

    @Test
    fun scriptsStylesAndAttributeValuesGetNothing() {
        assertNull(typeGt("<script>if (a<b && c ¦</script>"))
        assertNull(typeGt("<style><a ¦</style>"))
        assertNull(typeGt("<a title=\"x ¦"))
        assertEquals("<script>¦</script>", typeGt("<script¦"))
        assertEquals("<script></script><b>¦</b>", typeGt("<script></script><b¦"))
    }

    @Test
    fun voidClosingAndSelfClosingTagsGetNothing() {
        assertNull(typeGt("<br¦"))
        assertNull(typeGt("<img src=\"a\"¦"))
        assertNull(typeGt("<div /¦"))
        assertNull(typeGt("</div¦"))
        assertNull(typeGt("<!doctype html¦"))
        assertNull(typeGt("<?xml version=\"1.0\"?¦"))
        assertNull(typeGt("1 < 2 ¦"))
    }

    @Test
    fun otherLanguagesAndTheSettingAreRespected() {
        assertNull(typeGt("List<String¦", "a.kt"))
        assertNull(typeGt("x -¦", "a.js"))
        assertNull(typeGt("<div¦", autoClose = false))
    }
}
