package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In each text, `|` marks the cursor; the steps are compared as the text each of them selects. */
class SelectionExpanderTest {

    private val kotlin = Highlighter.languageFor("Main.kt")
    private val html = Highlighter.languageFor("page.html")

    /** Every selection reached by pressing the key until nothing is left to grow. */
    private fun steps(marked: String, lang: Lang = kotlin, skipped: (Int) -> Boolean = { false }): List<String> {
        val pos = marked.indexOf('|')
        val text = marked.removeRange(pos, pos + 1)
        val expander = SelectionExpander()
        var a = pos
        var b = pos
        val out = ArrayList<String>()
        while (true) {
            val next = expander.expand(text, a, b, lang, skipped) ?: return out
            a = next.start
            b = next.end
            out.add(text.substring(a, b))
        }
    }

    @Test
    fun wordLineBracketsAndTheWholeText() {
        assertEquals(
            listOf(
                "value",
                "    val value = 1",
                "\n    val value = 1\n",
                "{\n    val value = 1\n}",
                "fun f() {\n    val value = 1\n}",
            ),
            steps("fun f() {\n    val va|lue = 1\n}"),
        )
    }

    @Test
    fun bracketsInsideALineComeBeforeTheLine() {
        assertEquals(listOf("a", "a, b", "(a, b)", "x = f(a, b)"), steps("x = f(|a, b)"))
    }

    @Test
    fun nestedBracketsGrowOutwardsOneAtATime() {
        assertEquals(listOf("a", "[a]", "([a])", "{([a])}"), steps("{([|a])}"))
    }

    @Test
    fun tagsGiveTheirInsideAndThenTheWholeElement() {
        assertEquals(
            listOf(
                "Hello",
                "Hello world",
                "<p>Hello world</p>",
                "  <p>Hello world</p>",
                "\n  <p>Hello world</p>\n",
                "<div>\n  <p>Hello world</p>\n</div>",
            ),
            steps("<div>\n  <p>Hel|lo world</p>\n</div>", html),
        )
    }

    @Test
    fun tagsWithoutAPartnerCommentsAndValuesAreNotElements() {
        assertEquals(
            listOf("x", "a<br><img src=\">\">x<!-- </p> -->", "<p>a<br><img src=\">\">x<!-- </p> --></p>"),
            steps("<p>a<br><img src=\">\">|x<!-- </p> --></p>", html),
        )
    }

    @Test
    fun skippedBracketsDoNotCount() {
        val marked = "f(\")\", |a)"
        val inString = marked.indexOf("\")\"") + 1
        assertEquals(listOf("a", "\")\", a", "(\")\", a)", "f(\")\", a)"), steps(marked, skipped = { it == inString }))
    }

    @Test
    fun theLineLeavesItsBreakOut() {
        assertEquals(listOf("two", "two = 2", "one\r\ntwo = 2\r\n"), steps("one\r\nt|wo = 2\r\n"))
    }

    @Test
    fun shrinkingGoesBackThroughTheSameSteps() {
        val text = "x = f(a, b)"
        val expander = SelectionExpander()
        val taken = ArrayList<SelectionExpander.Range>()
        taken.add(SelectionExpander.Range(6, 6))
        while (true) {
            val last = taken.last()
            taken.add(expander.expand(text, last.start, last.end, kotlin) ?: break)
        }
        var now = taken.removeAt(taken.size - 1)
        while (taken.isNotEmpty()) {
            now = expander.shrink(now.start, now.end)!!
            assertEquals(taken.removeAt(taken.size - 1), now)
        }
        assertNull(expander.shrink(now.start, now.end))
    }

    @Test
    fun expandingAgainAfterShrinkingKeepsTheEarlierSteps() {
        val text = "f(ab)"
        val e = SelectionExpander()
        val word = e.expand(text, 3, 3, kotlin)!!
        val outer = e.expand(text, word.start, word.end, kotlin)!!
        assertEquals(word, e.shrink(outer.start, outer.end))
        val again = e.expand(text, word.start, word.end, kotlin)!!
        assertEquals(word, e.shrink(again.start, again.end))
        assertEquals(SelectionExpander.Range(3, 3), e.shrink(word.start, word.end))
    }

    @Test
    fun aSelectionChangedByHandOrANewTextHasNothingToShrinkTo() {
        val e = SelectionExpander()
        e.expand("abc def", 1, 1, kotlin)
        assertNull(e.shrink(4, 7))
        val r = e.expand("abc def", 1, 1, kotlin)!!
        e.reset()
        assertNull(e.shrink(r.start, r.end))
    }

    @Test
    fun emptyTextHasNothingToSelect() {
        assertEquals(emptyList<String>(), steps("|"))
    }
}
