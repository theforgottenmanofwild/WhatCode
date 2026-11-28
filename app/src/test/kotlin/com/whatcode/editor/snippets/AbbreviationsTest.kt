package com.whatcode.editor.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbbreviationsTest {

    /** What [abbr] turns into, as the text put in (tab stops filled with their text). */
    private fun html(abbr: String): String? = HtmlAbbreviation.expand(abbr)?.let { SnippetBody.parse(it, "", "  ").text }

    private fun stops(abbr: String): Int = SnippetBody.parse(HtmlAbbreviation.expand(abbr)!!).stops.size

    @Test
    fun aTagWithIdAndClasses() {
        assertEquals("<div id=\"main\" class=\"a b\"></div>", html("div#main.a.b"))
        assertEquals(1, stops("div#main.a.b"))
    }

    @Test
    fun noTagNameIsADivOrWhatSuitsTheParent() {
        assertEquals("<div class=\"box\"></div>", html(".box"))
        assertEquals("<ul>\n  <li class=\"x\"></li>\n</ul>", html("ul>.x"))
        assertEquals("<table>\n  <tr class=\"r\">\n    <td class=\"c\"></td>\n  </tr>\n</table>", html("table>.r>.c"))
    }

    @Test
    fun childrenSiblingsAndClimbing() {
        assertEquals("<nav>\n  <a href=\"\"></a>\n</nav>\n<main></main>", html("nav>a^main"))
        assertEquals("<h1></h1>\n<p></p>", html("h1+p"))
        assertEquals("<div>\n  <p>\n    <span></span>\n  </p>\n</div>\n<footer></footer>", html("div>p>span^^footer"))
    }

    @Test
    fun climbingPastTheTopGoesOnAtTheTop() {
        assertEquals("<div>\n  <p></p>\n</div>\n<span></span>", html("div>p^^^span"))
    }

    @Test
    fun repeatingNumbersTheCopies() {
        assertEquals(
            "<ul>\n  <li class=\"item1\"></li>\n  <li class=\"item2\"></li>\n  <li class=\"item3\"></li>\n</ul>",
            html("ul>li.item$*3"),
        )
        assertEquals(3, stops("ul>li.item$*3"))
        assertEquals("<p>01</p>\n<p>02</p>", html("p{$$}*2"))
    }

    @Test
    fun aChildTakesTheNumberOfItsRepeatedParent() {
        assertEquals("<li>\n  <a href=\"#1\">1</a>\n</li>\n<li>\n  <a href=\"#2\">2</a>\n</li>", html("li*2>a[href=#$]{$}"))
    }

    @Test
    fun groupsRepeat() {
        assertEquals("<dt></dt>\n<dd></dd>\n<dt></dt>\n<dd></dd>", html("(dt+dd)*2"))
        assertEquals("<dl>\n  <dt></dt>\n  <dd></dd>\n</dl>", html("dl>(dt+dd)"))
    }

    @Test
    fun attributesAndText() {
        assertEquals("<input type=\"email\" required=\"\">", html("input[type=email required]"))
        assertEquals("<a href=\"x y\">Go</a>", html("a[href=\"x y\"]{Go}"))
        assertEquals("<p>Hello, world</p>", html("p{Hello, world}"))
    }

    @Test
    fun voidTagsAndDefaults() {
        assertEquals("<img src=\"\" alt=\"\">", html("img"))
        assertEquals(2, stops("img"))
        assertEquals("<br>", html("br"))
        assertEquals("<input type=\"text\">", html("input"))
        assertEquals("<link rel=\"stylesheet\" href=\"\">", html("link"))
    }

    @Test
    fun textKeepsItsDollarsAndBraces() {
        assertEquals("<p>a \\ b</p>", html("p{a \\ b}"))
        assertEquals("<code>x$</code>", html("code{x$}"))
    }

    @Test
    fun customTagsWithAHyphen() {
        assertEquals("<my-card></my-card>", html("my-card"))
    }

    @Test
    fun aWordThatIsNoTagIsLeftAlone() {
        assertNull(html("hello"))
        assertNull(html("file.txt"))
        assertNull(html("e.g."))
        assertNull(html("div>"))
        assertNull(html("p{unclosed"))
        assertNull(html("li*0"))
        assertNull(html("li*1000"))
        assertNull(html("a]"))
        assertNull(html(""))
    }

    @Test
    fun tooManyElementsAreRefused() {
        assertNull(html("div*100>p*100"))
    }

    @Test
    fun theBangIsAPage() {
        val page = html("!")!!
        assertEquals(true, page.startsWith("<!DOCTYPE html>"))
        assertEquals(true, page.contains("<title>Document</title>"))
    }

    // ------------------------------------------------------------------ CSS

    private fun css(abbr: String) = CssAbbreviation.expand(abbr)?.let { SnippetBody.parse(it).text }

    @Test
    fun cssNumbersGetPixelsOrTheirUnit() {
        assertEquals("margin: 10px;", css("m10"))
        assertEquals("padding: 10px 20px;", css("p10-20"))
        assertEquals("width: 50%;", css("w50p"))
        assertEquals("font-size: 1.5rem;", css("fz1.5r"))
        assertEquals("margin: -10px auto;", css("m-10-auto"))
        assertEquals("margin: 0;", css("m0"))
        assertEquals("height: 100vh;", css("h100v"))
    }

    @Test
    fun plainNumbersHaveNoUnit() {
        assertEquals("font-weight: 700;", css("fw700"))
        assertEquals("line-height: 1.4;", css("lh1.4"))
        assertEquals("z-index: 10;", css("zi10"))
    }

    @Test
    fun colours() {
        assertEquals("color: #ffffff;", css("c#f"))
        assertEquals("background-color: #e0e0e0;", css("bgc#e0"))
        assertEquals("color: #abc;", css("c#abc"))
        assertNull(css("c#abcd"))
        assertNull(css("c#xyz"))
    }

    @Test
    fun keywordsAndBareProperties() {
        assertEquals("display: flex;", css("df"))
        assertEquals("justify-content: space-between;", css("jcsb"))
        assertEquals("background-color: ;", css("bgc"))
        assertEquals(1, SnippetBody.parse(CssAbbreviation.expand("bgc")!!).stops.size)
        assertNull(css("m"))
    }

    @Test
    fun cssWordsThatAreNotAbbreviations() {
        assertNull(css("color"))
        assertNull(css("mx"))
        assertNull(css("m10-"))
        assertNull(css("m1.2.3"))
        assertNull(css("zz10"))
    }

    // ------------------------------------------------------------------ finding them before the cursor

    private fun hit(t: String, lang: String) = Abbreviations.trigger(t, t.length, t.length, lang)

    @Test
    fun anHtmlAbbreviationIsFoundAfterTheIndentAndAfterATag() {
        assertEquals(2, hit("  ul>li", "HTML")!!.start)
        assertEquals(3, hit("<p>ul>li", "HTML")!!.start)
        assertEquals(15, hit("<div class=\"a\">ul", "HTML")!!.start)
        assertEquals(0, hit("p{Hello world}", "HTML")!!.start)
    }

    @Test
    fun htmlInsideATagOrPlainWordsIsNot() {
        assertNull(hit("<a href=\"x\" div", "HTML"))
        assertNull(hit("Hello world", "HTML"))
        assertNull(hit("<li>item", "HTML"))
        assertNull(hit("ul>li", "Markdown"))
    }

    @Test
    fun theAbbreviationMustEndAtTheCursor() {
        assertNull(Abbreviations.trigger("ul>lix", 5, 5, "HTML"))
        assertNull(Abbreviations.trigger("ul>li", 2, 5, "HTML"))
    }

    @Test
    fun cssIsOnlyExpandedWhereADeclarationStarts() {
        assertEquals(6, hit("a {\n  m10", "CSS")!!.start)
        assertEquals(16, hit("a { color: red; m10", "SCSS")!!.start)
        assertNull(hit("m10", "CSS"))
        assertNull(hit("a { color: m10", "CSS"))
        assertNull(hit("a {} df", "CSS"))
        assertEquals(4, hit("a { df", "Less")!!.start)
    }
}
