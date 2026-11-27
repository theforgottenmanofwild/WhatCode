package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In each text, `|` marks the cursor. */
class AttributeQuotesTest {

    private val html = Highlighter.languageFor("page.html")
    private val xml = Highlighter.languageFor("data.xml")
    private val svg = Highlighter.languageFor("icon.svg")

    /** The text with its cursor after typing [c], or null if the character is left to be typed as usual. */
    private fun type(marked: String, c: Char, lang: Lang = html): String? {
        val pos = marked.indexOf('|')
        val text = marked.removeRange(pos, pos + 1)
        val edit = AttributeQuotes.typed(text, pos, c, lang) ?: return null
        val out = text.substring(0, edit.start) + edit.insert + text.substring(edit.end)
        return out.substring(0, edit.selStart) + "|" + out.substring(edit.selStart)
    }

    @Test
    fun anEqualsAfterAnAttributeNameAddsTheQuotes() {
        assertEquals("<div class=\"|\">", type("<div class|>", '='))
        assertEquals("<div class=\"|\"", type("<div class|", '='))
        assertEquals("<br id=\"|\"/>", type("<br id|/>", '='))
        assertEquals("<a href=\"x\" title=\"|\">", type("<a href=\"x\" title|>", '='))
        assertEquals("<a\n  href=\"|\"\n>", type("<a\n  href|\n>", '='))
        assertEquals("<input disabled value =\"|\">", type("<input disabled value |>", '='))
    }

    @Test
    fun namesWithPunctuationCountAndSoDoXmlAndSvg() {
        assertEquals("<div data-id=\"|\">", type("<div data-id|>", '='))
        assertEquals("<b @click=\"|\">", type("<b @click|>", '='))
        assertEquals("<use xlink:href=\"|\"/>", type("<use xlink:href|/>", '=', svg))
        assertEquals("<item key=\"|\"/>", type("<item key|/>", '=', xml))
    }

    @Test
    fun nothingHappensOutsideAnOpeningTag() {
        assertNull(type("a|", '='))
        assertNull(type("<p>x |</p>", '='))
        assertNull(type("<p a>x y|", '='))
        assertNull(type("</div x|>", '='))
        assertNull(type("<!-- <a href| -->", '='))
        assertNull(type("<!-- <a href|", '='))
        assertNull(type("<!DOCTYPE html x|>", '='))
        assertNull(type("<![CDATA[ <a b| ]]>", '=', xml))
        assertNull(type("<div|>", '='))
        assertNull(type("<div |>", '='))
    }

    @Test
    fun nothingHappensInScriptsAndStyles() {
        assertNull(type("<script>if (a <b && c d|) {}</script>", '='))
        assertNull(type("<style>a <b c|</style>", '='))
        assertNull(type("<SCRIPT type=\"x\">var s = '<a href|'", '='))
        assertEquals("<script></script><a b=\"|\">", type("<script></script><a b|>", '='))
        assertEquals("<script src|></script>".replace("|", "=\"|\""), type("<script src|></script>", '='))
    }

    @Test
    fun aGreaterThanInAValueDoesNotEndTheTag() {
        assertEquals("<a t=\"x>y\" b=\"|\">", type("<a t=\"x>y\" b|>", '='))
        assertNull(type("<a t=\"x> b|\">", '='))
    }

    @Test
    fun nothingHappensInAValue() {
        assertNull(type("<a href=\"page?q a|\">", '='))
        assertNull(type("<a href='x |'>", '='))
        assertNull(type("<a href=page?q|>", '='))
    }

    @Test
    fun nothingHappensWhenAQuoteOrAValueAlreadyFollows() {
        assertNull(type("<a href|\"x\">", '='))
        assertNull(type("<a href| 'x'>", '='))
        assertNull(type("<a href|=\"x\">", '='))
        assertNull(type("<a href| =x>", '='))
        assertNull(type("<a href|x>", '='))
        assertNull(type("<a href= x|>", '='))
    }

    @Test
    fun otherLanguagesAreLeftAlone() {
        assertNull(type("<div class|>", '=', Highlighter.languageFor("Main.kt")))
        assertNull(type("<div class|>", '=', Highlighter.languageFor("notes.md")))
    }

    @Test
    fun theClosingQuoteIsSteppedOver() {
        assertEquals("<a b=\"\"|>", type("<a b=\"|\">", '"'))
        assertEquals("<a b=\"two words\"|>", type("<a b=\"two words|\">", '"'))
        assertEquals("<a b='x'|>", type("<a b='x|'>", '\''))
        assertEquals("<a\n b=\"x\ny\"|>", type("<a\n b=\"x\ny|\">", '"'))
    }

    @Test
    fun otherQuotesAreTypedAsUsual() {
        assertNull(type("<a b=\"it|\">", '\''))
        assertNull(type("<a b=|\"x\">", '"'))
        assertNull(type("<p>say |\"</p>", '"'))
        assertNull(type("<!-- a=\"|\" -->", '"'))
        assertNull(type("<a b=\"x|y\">", '"'))
    }

    @Test
    fun theTypingRulesUseItOnlyWhenAsked() {
        val on = TypingLogic.typed("<a b", 4, 4, '=', html, false, false, quoteAttributes = true)
        assertEquals("=\"\"", on!!.insert)
        assertNull(TypingLogic.typed("<a b", 4, 4, '=', html, true, true, quoteAttributes = false))
        assertNull(TypingLogic.typed("<a b", 3, 4, '=', html, true, true, quoteAttributes = true))
        assertEquals(true, TypingLogic.isTrigger('='))
    }
}
