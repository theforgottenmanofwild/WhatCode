package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** In each text, `|` marks the cursor, or two of them the ends of the selection. */
class TagWrapTest {

    private val html = Highlighter.languageFor("page.html")
    private val xml = Highlighter.languageFor("data.xml")

    private class Marked(val text: String, val start: Int, val end: Int)

    private fun read(marked: String): Marked {
        val a = marked.indexOf('|')
        var text = marked.removeRange(a, a + 1)
        val b = text.indexOf('|')
        if (b < 0) return Marked(text, a, a)
        text = text.removeRange(b, b + 1)
        return Marked(text, a, b)
    }

    private fun show(text: String, edit: TextEdit): String {
        val out = text.substring(0, edit.start) + edit.insert + text.substring(edit.end)
        val head = out.substring(0, edit.selStart)
        if (edit.selStart == edit.selEnd) return head + "|" + out.substring(edit.selStart)
        return head + "|" + out.substring(edit.selStart, edit.selEnd) + "|" + out.substring(edit.selEnd)
    }

    private fun wrap(marked: String, tag: String, unit: String = "  "): String {
        val m = read(marked)
        return show(m.text, TagWrap.wrap(m.text, m.start, m.end, TagWrap.tag(tag, html)!!, unit))
    }

    private fun unwrap(marked: String, lang: Lang = html): String? {
        val m = read(marked)
        return TagWrap.unwrap(m.text, m.start, m.end, lang)?.let { show(m.text, it) }
    }

    @Test
    fun attributesStayInTheOpeningTagOnly() {
        val tag = TagWrap.tag("  a href=\"x>y\" title='t' ", html)!!
        assertEquals("a", tag.name)
        assertEquals("<a href=\"x>y\" title='t'>", tag.open)
        assertEquals("</a>", tag.close)
        assertEquals("<ui:item>", TagWrap.tag("<ui:item>", xml)!!.open)
    }

    @Test
    fun whatIsNotATagIsRefused() {
        for (bad in listOf("", " ", "<>", "1a", "/div", "div/", "a<b", "a>b", "a x=\"1", "br", "<img src=x>", "!x")) {
            assertNull(bad, TagWrap.tag(bad, html))
        }
        assertNotNull(TagWrap.tag("br", xml))
    }

    @Test
    fun theSelectionIsWrappedAndStaysSelected() {
        assertEquals("say <em>|big words|</em> now", wrap("say |big words| now", "em"))
    }

    @Test
    fun theWordAtTheCursorIsWrapped() {
        assertEquals("a <b>|bold|</b> one", wrap("a bo|ld one", "b"))
        assertEquals("a <b>|bold|</b> one", wrap("a bold| one", "b"))
    }

    @Test
    fun withNoWordAnEmptyPairIsAdded() {
        assertEquals("a <b>|</b> c", wrap("a | c", "b"))
    }

    @Test
    fun wholeLinesGetTagLinesAndOneMoreLevel() {
        assertEquals(
            "<ul>\n  <div class=\"x\">\n|    <li>a</li>\n\n    <li>b</li>|\n  </div>\n</ul>",
            wrap("<ul>\n|  <li>a</li>\n\n  <li>b</li>|\n</ul>", "div class=\"x\""),
        )
    }

    @Test
    fun aSelectionEndingAfterTheBreakOrStartingAfterTheIndentationCoversTheLine() {
        assertEquals("<p>\n|\ttext|\n</p>\nnext", wrap("|text\n|next", "p", "\t"))
        assertEquals("  <p>\n|    one two|\n  </p>", wrap("  |one two|", "p"))
    }

    @Test
    fun crlfLinesStayCrlf() {
        assertEquals("<p>\r\n|  a\r\n  b|\r\n</p>\r\nc", wrap("|a\r\nb|\r\nc", "p"))
    }

    @Test
    fun partOfALineIsWrappedInPlace() {
        assertEquals("one <i>|two|</i>", wrap("one |two|", "i"))
    }

    @Test
    fun theInnermostPairIsRemoved() {
        assertEquals("<p>a bo|ld c</p>", unwrap("<p>a <b>bo|ld</b> c</p>"))
        assertEquals("a |<b>x</b> c", unwrap("<p>a |<b>x</b> c</p>"))
    }

    @Test
    fun aSelectedElementIsTheOneRemoved() {
        assertEquals("<p>|x|</p>", unwrap("<p>|<b>x</b>|</p>"))
    }

    @Test
    fun contentOnItsOwnLinesIsMovedBack() {
        assertEquals(
            "<ul>\n  <li>a</li>|\n  <li>\n    b\n  </li>\n</ul>",
            unwrap("<ul>\n  <div>\n    <li>a</li>|\n    <li>\n      b\n    </li>\n  </div>\n</ul>"),
        )
    }

    @Test
    fun removingUndoesWrapping() {
        assertEquals("<ul>\n  a|\n  b\n</ul>", unwrap("<ul>\n  <div>\n    a|\n    b\n  </div>\n</ul>"))
        assertEquals("a\r\n|b\r\nc", unwrap("<p>\r\n  a\r\n  |b\r\n</p>\r\nc"))
    }

    @Test
    fun emptyTagLinesGoAltogether() {
        assertEquals("a\n|b", unwrap("a\n<div>\n|</div>\nb"))
    }

    @Test
    fun thereIsNothingToRemoveOutsideAPair() {
        assertNull(unwrap("a| <b>x</b>"))
        assertNull(unwrap("<br>|"))
        assertNull(unwrap("<!-- <b>|x</b> -->"))
        assertNull(unwrap("<b>|x</b>", Highlighter.languageFor("Main.kt")))
    }
}
