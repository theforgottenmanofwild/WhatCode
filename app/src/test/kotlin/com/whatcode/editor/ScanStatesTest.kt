package com.whatcode.editor

import com.whatcode.editor.highlight.ScanStates
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Colouring a window of a text must give what colouring the whole text gives for those lines, whatever comes
 * before the window (block comments, strings and templates that go over several lines, scripts in a page, an
 * open tag), and the states must stay right through edits.
 */
class ScanStatesTest {

    private val fragments = mapOf(
        "a.js" to listOf(
            "var x = 10; // note", "/* open", " middle */", "const s = \"str\";", "let t = `tpl", "more \${x} tpl`;",
            "function foo(a) {", "}", "x = a / b;", "r = /ab+c/g.test(s);", "return", "name(1)", "function", "", "  ",
            "a.b(c)", "'single'", "/* one-line */", "}) // end", "const o = { key: 1, \"q\": 2 };", "if (x) return /re/;",
        ),
        "a.py" to listOf(
            "def foo():", "\"\"\"doc", "text\"\"\"", "x = 1  # c", "'a'", "class A:", "    pass", "def", "name(1)", "'''", "f\"x{y}\"",
            "", "    return None", "@decorator",
        ),
        "a.html" to listOf(
            "<div class=\"a\">", "</div>", "<!-- c", "-->", "<script>", "var a = 1; /* x", "*/ b = 2;", "</script>", "<style>",
            "a { color: red }", "</style>", "<p>text &amp; more</p>", "<a href=\"x", "y\">", "<br/>", "\"stray quote", "<img",
            "  src='a.png'>",
        ),
        "a.css" to listOf(
            "a {", "color: red;", "/* c", "*/", "}", ".b > c { margin: 0 }", "@media (x) {", "background: url(a.png);", "margin",
            ": 0;", "#id { }", "--v: 1;", "// line",
        ),
        "a.scss" to listOf("\$v: 1;", "a {", "  b { color: #fff; }", "}", "/* c", "*/", "// x", "@include m;"),
        "a.yml" to listOf(
            "key: value", "- item", "# c", "\"str\"", "---", "list:", "  nested: 1", "  - 'q'", "name: \"multi", "line\"", "on: yes", "&a ref",
        ),
        "a.md" to listOf(
            "# Title", "```", "code here", "```", "text **bold** and `code`", "> quote", "- item", "<!-- c", "-->", "", "[l](http://x.y)", "~~~",
        ),
        "a.json" to listOf("{", "\"k\": 1,", "\"a\": [1,2],", "}", "\"s\": \"x\"", "[", "]"),
        "a.toml" to listOf("[table]", "key = 1", "# c", "s = \"\"\"", "multi", "\"\"\"", "a = true"),
        "a.kt" to listOf(
            "fun foo(a: Int) {", "}", "val s = \"\"\"raw", "text\"\"\"", "/* c", "*/", "// n", "class A", "return", "x / y",
        ),
    )

    private fun generate(name: String, rnd: Random, lines: Int): String {
        val frags = fragments.getValue(name)
        val sb = StringBuilder()
        repeat(lines) {
            sb.append(frags[rnd.nextInt(frags.size)])
            sb.append(if (rnd.nextInt(8) == 0) " " else "\n")
        }
        return sb.toString()
    }

    private class Setup(val text: String, val lang: Lang) {
        val index = LineIndex().also { it.build(text) }
        val states = ScanStates(lang).also { check(it.build(text, index)) }
    }

    private fun tokensOf(t: Tokens): Set<Triple<Int, Int, Int>> =
        (0 until t.size).map { Triple(t.data[it * 3], t.data[it * 3 + 1], t.data[it * 3 + 2]) }.toSet()

    /** Windows of the text must agree with the whole text for the pieces that end before the last few lines. */
    private fun assertWindowsAgree(name: String, text: String, index: LineIndex, states: ScanStates, rnd: Random, tries: Int) {
        val lang = Highlighter.languageFor(name)
        val whole = tokensOf(Highlighter.tokenize(text, lang))
        for (i in 0 until tries) {
            val first = rnd.nextInt(index.lineCount)
            val last = minOf(index.lineCount - 1, first + 4 + rnd.nextInt(30))
            val window = Highlighter.tokenizeWindow(text, lang, index, states, first, last)
            val fromOff = index.startOf(first)
            // The last lines of a window are its margin: a piece that is only decided by text after them may differ.
            val limit = index.startOf(maxOf(first, last - 4))
            val inWindow = tokensOf(window)
            for (t in inWindow) {
                if (t.second <= limit) assertTrue("$name window $first..$last has $t that the whole text does not", t in whole)
            }
            for (t in whole) {
                if (t.second > fromOff && t.second <= limit) {
                    assertTrue("$name window $first..$last lacks $t", t in inWindow)
                }
            }
        }
    }

    @Test
    fun windowsGiveTheColoursOfTheWholeText() {
        for (name in fragments.keys) {
            for (seed in 1..40) {
                val rnd = Random(seed * 31L + name.hashCode())
                val text = generate(name, rnd, 40 + rnd.nextInt(160))
                val s = Setup(text, Highlighter.languageFor(name))
                assertWindowsAgree(name, text, s.index, s.states, rnd, 8)
            }
        }
    }

    @Test
    fun theFirstLineIsAlwaysAPlaceToBegin() {
        for (name in fragments.keys) {
            val s = Setup(generate(name, Random(3), 50), Highlighter.languageFor(name))
            assertTrue(name, s.states.isStart(0))
            assertEquals(0, s.states.resyncLine(0))
        }
    }

    @Test
    fun aLineInsideABlockCommentIsNotAPlaceToBegin() {
        val text = "int a;\n/* one\ntwo\nthree */\nint b;\n"
        val s = Setup(text, Highlighter.languageFor("a.js"))
        assertTrue(s.states.isStart(0))
        assertFalse(s.states.isStart(2))
        assertFalse(s.states.isStart(3))
        assertEquals(1, s.states.resyncLine(3))
        assertTrue(s.states.isStart(4))
    }

    @Test
    fun aLineInsideAScriptOrAnOpenTagIsNotAPlaceToBegin() {
        val text = "<p>\n<script>\nvar a;\nvar b;\n</script>\n<a\nhref=\"x\">\n"
        val s = Setup(text, Highlighter.languageFor("a.html"))
        assertFalse(s.states.isStart(2))
        assertFalse(s.states.isStart(3))
        assertFalse(s.states.isStart(6)) // after "<a", still inside the tag
        assertTrue(s.states.isStart(5))
    }

    @Test
    fun aLineThatFollowsAWordIsNotAPlaceToBeginForCode() {
        // "function" then a name on the next line: the name is a function name only if the scan remembers "function".
        val s = Setup("function\nfoo(1)\n", Highlighter.languageFor("a.js"))
        assertFalse(s.states.isStart(1))
    }

    @Test
    fun plainTextHasNothingToRemember() {
        val s = Setup("a\nb\nc", Highlighter.languageFor("a.txt"))
        for (l in 0..2) assertTrue(s.states.isStart(l))
    }

    @Test
    fun emptyTextAndOneLongLine() {
        val e = Setup("", Highlighter.languageFor("a.js"))
        assertEquals(0, e.states.resyncLine(0))
        val long = "var a = \"" + "x".repeat(200_000) + "\"; /* c */"
        val s = Setup(long, Highlighter.languageFor("a.js"))
        val w = Highlighter.tokenizeWindow(long, s.lang, s.index, s.states, 0, 0)
        assertEquals(tokensOf(Highlighter.tokenize(long, s.lang)), tokensOf(w))
    }

    @Test
    fun cancellingABuildLeavesItNotReady() {
        val text = generate("a.js", Random(5), 500)
        val index = LineIndex().also { it.build(text) }
        val states = ScanStates(Highlighter.languageFor("a.js"))
        var calls = 0
        assertFalse(states.build(text, index) { ++calls > 10 })
        assertFalse(states.ready)
        assertTrue(states.build(text, index))
        assertTrue(states.ready)
    }

    @Test
    fun statesStayRightThroughRandomEdits() {
        for (name in fragments.keys) {
            val lang = Highlighter.languageFor(name)
            val frags = fragments.getValue(name)
            for (seed in 1..12) {
                val rnd = Random(seed * 17L + name.hashCode())
                val sb = StringBuilder(generate(name, rnd, 60 + rnd.nextInt(60)))
                val index = LineIndex().also { it.build(sb) }
                val states = ScanStates(lang)
                assertTrue(states.build(sb.toString(), index))
                repeat(40) {
                    val start = rnd.nextInt(sb.length + 1)
                    val removed = rnd.nextInt(minOf(12, sb.length - start) + 1)
                    val ins = when (rnd.nextInt(4)) {
                        0 -> ""
                        1 -> frags[rnd.nextInt(frags.size)]
                        2 -> frags[rnd.nextInt(frags.size)] + "\n"
                        else -> "x"
                    }
                    sb.replace(start, start + removed, ins)
                    val splice = index.replace(start, removed, ins)
                    val text = sb.toString()
                    assertTrue(states.replace(splice, text, index))
                    val fresh = ScanStates(lang)
                    assertTrue(fresh.build(text, index))
                    // A line the states call a place to begin must be one for a fresh read too. A line they leave out
                    // that a fresh read would take only costs a longer read, but there should be next to none.
                    for (l in 0 until index.lineCount) {
                        if (states.isStart(l)) assertTrue("$name seed $seed: line $l wrongly a start", fresh.isStart(l))
                    }
                }
                val text = sb.toString()
                assertWindowsAgree(name, text, index, states, rnd, 10)
            }
        }
    }

    @Test
    fun anEditOnlyReadsAFewLines() {
        // 5000 lines with a comment-free body: an edit near the top must not make the states read to the end.
        val body = (0 until 5000).joinToString("\n") { "val v$it = $it" } + "\n"
        val lang = Highlighter.languageFor("a.kt")
        val sb = StringBuilder(body)
        val index = LineIndex().also { it.build(sb) }
        val states = ScanStates(lang)
        assertTrue(states.build(sb.toString(), index))
        val start = sb.indexOf("v10 ")
        sb.replace(start, start + 3, "w10")
        val splice = index.replace(start, 3, "w10")
        var asked = 0
        assertTrue(states.replace(splice, sb.toString(), index) { asked++; false })
        assertTrue("read $asked line starts for one edit", asked < 40)
    }
}

class ScanStatesLongLineTest {
    @Test
    fun aVeryLongLineIsBuiltInOnePass() {
        val text = "[" + "{\"id\":1,\"name\":\"abc\"},".repeat(150_000) + "{}]"
        val lang = Highlighter.languageFor("a.json")
        val index = LineIndex().also { it.build(text) }
        val states = ScanStates(lang)
        val t = System.nanoTime()
        assertTrue(states.build(text, index))
        val ms = (System.nanoTime() - t) / 1_000_000
        assertEquals(1, index.lineCount)
        // Searching for the end of the line for every piece on it would take minutes here.
        assertTrue("took $ms ms", ms < 15_000)
    }
}
