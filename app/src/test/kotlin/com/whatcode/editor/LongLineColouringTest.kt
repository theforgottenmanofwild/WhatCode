package com.whatcode.editor

import com.whatcode.editor.highlight.ScanStates
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLinesTest {

    @Test
    fun aLineIsLongOnlyAboveTheLimit() {
        assertFalse(LongLines.exists("", 5))
        assertFalse(LongLines.exists("abcde", 5))
        assertTrue(LongLines.exists("abcdef", 5))
        assertFalse(LongLines.exists("abc\ndef\n", 5))
        assertTrue(LongLines.exists("abc\ndefghi\nx", 5))
        assertTrue(LongLines.exists("abc\nx\nabcdefg", 5))
        assertFalse(LongLines.exists("\n\n\n", 0))
    }

    @Test
    fun theLineBreakIsNotCounted() {
        assertFalse(LongLines.exists("abcde\n", 5))
        assertFalse(LongLines.exists("abc\r\nde\r\n", 5)) // a \r is a character of its line, three and two here
        assertTrue(LongLines.exists("abcde\r\nx", 5))
    }

    @Test
    fun anyCharSequenceCanBeRead() {
        val sb = StringBuilder("one\ntwo three four\nx")
        assertTrue(LongLines.exists(sb, 8))
        assertFalse(LongLines.exists(sb, 14))
        assertTrue(LongLines.exists(sb, 5, 20, 8))
        assertFalse(LongLines.exists(sb, 0, 3, 3))
    }

    @Test
    fun nextStartFindsTheFirstLongLine() {
        val text = "ab\ncdefgh\nij\nklmnopq\n"
        assertEquals(3, LongLines.nextStart(text, 0, 5))
        assertEquals(3, LongLines.nextStart(text, 3, 5))
        assertEquals(13, LongLines.nextStart(text, 10, 5))
        assertEquals(-1, LongLines.nextStart(text, 21, 8))
        assertEquals(-1, LongLines.nextStart("", 0, 5))
        assertEquals(0, LongLines.nextStart("abcdefgh", 0, 5))
    }

    @Test
    fun anEditTouchesTheWholeOfItsLine() {
        val text = "abc\nabcdefgh\nxy\n"
        assertTrue(LongLines.touched(text, 6, 6, 5)) // inside the long line
        assertTrue(LongLines.touched(text, 4, 4, 5)) // at its start
        assertTrue(LongLines.touched(text, 12, 12, 5)) // at its end
        assertFalse(LongLines.touched(text, 1, 2, 5))
        assertFalse(LongLines.touched(text, 14, 14, 5))
    }

    @Test
    fun joiningTwoLinesCanMakeALongOne() {
        val text = "aaa" + "bbb" + "\nc" // the break between aaa and bbb was just taken out
        assertTrue(LongLines.touched(text, 3, 3, 5))
        assertFalse(LongLines.touched("aaa\nbbb", 3, 3, 5))
    }

    @Test
    fun aLongLineInsideWhatWasInsertedIsFound() {
        val text = "a\nb\n" + "z".repeat(40) + "\nc\n"
        assertTrue(LongLines.touched(text, 2, 46, 10))
        assertFalse(LongLines.touched("a\nb\nc\n", 2, 4, 10))
    }

    @Test
    fun theSearchOutwardIsBoundedByTheLimit() {
        val text = "x".repeat(3_000_000)
        val t = System.nanoTime()
        assertTrue(LongLines.touched(text, 1_500_000, 1_500_000, 100))
        assertTrue("took ${(System.nanoTime() - t) / 1_000_000} ms", (System.nanoTime() - t) / 1_000_000 < 200)
    }

    @Test
    fun emojiCountAsTheirCharacters() {
        val text = "\uD83D\uDE00".repeat(3) // six chars
        assertTrue(LongLines.exists(text, 5))
        assertFalse(LongLines.exists(text, 6))
    }
}

/**
 * A line longer than the limit is left out of the colouring: nothing on it is coloured and what was open before it
 * ends there. The colours of a window must still be those of the whole text, and the states must stay right when
 * edits make lines long or short.
 */
class LongLineColouringTest {

    private val limit = 120

    private val fragments = mapOf(
        "a.js" to listOf(
            "var x = 10; // note", "/* open", " middle */", "const s = \"str\";", "let t = `tpl", "more \${x} tpl`;", "function foo(a) {",
            "}", "x = a / b;", "return", "name(1)", "", "a.b(c)", "'single'", "/* one-line */", "if (x) return /re/;",
        ),
        "a.py" to listOf("def foo():", "\"\"\"doc", "text\"\"\"", "x = 1  # c", "'a'", "class A:", "    pass", "name(1)", "'''", "", "@decorator"),
        "a.html" to listOf(
            "<div class=\"a\">", "</div>", "<!-- c", "-->", "<script>", "var a = 1; /* x", "*/ b = 2;", "</script>", "<style>",
            "a { color: red }", "</style>", "<p>text &amp; more</p>", "<a href=\"x", "y\">", "<br/>", "<img", "  src='a.png'>",
        ),
        "a.css" to listOf("a {", "color: red;", "/* c", "*/", "}", ".b > c { margin: 0 }", "@media (x) {", "margin", ": 0;", "#id { }"),
        "a.yml" to listOf("key: value", "- item", "# c", "\"str\"", "---", "list:", "  nested: 1", "name: \"multi", "line\"", "on: yes"),
        "a.md" to listOf("# Title", "```", "code here", "```", "text **bold** and `code`", "> quote", "- item", "<!-- c", "-->", "", "~~~"),
        "a.kt" to listOf("fun foo(a: Int) {", "}", "val s = \"\"\"raw", "text\"\"\"", "/* c", "*/", "// n", "class A", "return", "x / y"),
    )

    /** Lines that are too long, in the shapes minified files have, several of them opening something. */
    private val longOnes = listOf(
        "var s = '" + "q".repeat(200) + "'; /* start",
        "call(" + "1, ".repeat(100) + ")",
        "\"" + "w".repeat(180),
        "<a href=\"" + "h".repeat(150) + "\"",
        "x ".repeat(100) + "`open",
        "{" + "\"k\":1,".repeat(60) + "}",
    )

    private fun generate(name: String, rnd: Random, lines: Int): String {
        val frags = fragments.getValue(name)
        val sb = StringBuilder()
        repeat(lines) {
            sb.append(if (rnd.nextInt(9) == 0) longOnes[rnd.nextInt(longOnes.size)] else frags[rnd.nextInt(frags.size)])
            sb.append(if (rnd.nextInt(10) == 0) " " else "\n")
        }
        return sb.toString()
    }

    private fun tokensOf(t: Tokens): Set<Triple<Int, Int, Int>> =
        (0 until t.size).map { Triple(t.data[it * 3], t.data[it * 3 + 1], t.data[it * 3 + 2]) }.toSet()

    /** The start and end offset of each line longer than the limit. */
    private fun longRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = 0
        while (start <= text.length) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl
            if (end - start > limit) out.add(start..end)
            if (nl < 0) break
            start = nl + 1
        }
        return out
    }

    private fun assertWindowsAgree(name: String, text: String, index: LineIndex, states: ScanStates, rnd: Random, tries: Int) {
        val lang = Highlighter.languageFor(name)
        val whole = tokensOf(Highlighter.tokenize(text, lang, limit))
        for (i in 0 until tries) {
            val first = rnd.nextInt(index.lineCount)
            val last = minOf(index.lineCount - 1, first + 4 + rnd.nextInt(30))
            val window = tokensOf(Highlighter.tokenizeWindow(text, lang, index, states, first, last))
            val fromOff = index.startOf(first)
            val upTo = index.startOf(maxOf(first, last - 4))
            for (t in window) {
                if (t.second <= upTo) assertTrue("$name window $first..$last has $t that the whole text does not", t in whole)
            }
            for (t in whole) {
                if (t.second > fromOff && t.second <= upTo) assertTrue("$name window $first..$last lacks $t", t in window)
            }
        }
    }

    @Test
    fun nothingOnALongLineIsColoured() {
        for (name in fragments.keys) {
            for (seed in 1..25) {
                val rnd = Random(seed * 13L + name.hashCode())
                val text = generate(name, rnd, 50 + rnd.nextInt(100))
                val longs = longRanges(text)
                val tokens = tokensOf(Highlighter.tokenize(text, Highlighter.languageFor(name), limit))
                for (t in tokens) {
                    for (r in longs) {
                        assertTrue("$name seed $seed: $t reaches into the long line $r", t.second <= r.first || t.first > r.last)
                    }
                }
            }
        }
    }

    @Test
    fun windowsGiveTheColoursOfTheWholeText() {
        for (name in fragments.keys) {
            for (seed in 1..30) {
                val rnd = Random(seed * 31L + name.hashCode())
                val text = generate(name, rnd, 40 + rnd.nextInt(120))
                val index = LineIndex().also { it.build(text) }
                val states = ScanStates(Highlighter.languageFor(name), limit)
                assertTrue(states.build(text, index))
                assertEquals(longRanges(text).size, states.longLines)
                assertWindowsAgree(name, text, index, states, rnd, 8)
            }
        }
    }

    @Test
    fun aCommentOpenBeforeALongLineDoesNotReachPastIt() {
        val text = "a /* open\n" + "z".repeat(300) + "\nvar b = 1; */ x\n"
        val lang = Highlighter.languageFor("a.js")
        val at = text.indexOf("var b")
        val limited = tokensOf(Highlighter.tokenize(text, lang, 100))
        val whole = tokensOf(Highlighter.tokenize(text, lang))
        assertTrue("var is a keyword on the line after", limited.any { it.first == at && it.second == at + 3 })
        assertFalse("without the limit it is inside the comment", whole.any { it.first == at && it.second == at + 3 })
        val index = LineIndex().also { it.build(text) }
        val states = ScanStates(lang, 100).also { assertTrue(it.build(text, index)) }
        assertTrue(states.isStart(2))
        assertEquals(1, states.longLines)
        val w = tokensOf(Highlighter.tokenizeWindow(text, lang, index, states, 2, 2))
        assertTrue(w.any { it.first == at && it.second == at + 3 })
    }

    @Test
    fun withNoLongLineTheLimitChangesNothing() {
        for (name in fragments.keys) {
            val rnd = Random(name.hashCode().toLong())
            val frags = fragments.getValue(name)
            val text = (0 until 80).joinToString("\n") { frags[rnd.nextInt(frags.size)] } + "\n"
            val lang = Highlighter.languageFor(name)
            assertEquals(name, tokensOf(Highlighter.tokenize(text, lang)), tokensOf(Highlighter.tokenize(text, lang, limit)))
            val index = LineIndex().also { it.build(text) }
            val states = ScanStates(lang, limit).also { assertTrue(it.build(text, index)) }
            assertEquals(0, states.longLines)
        }
    }

    @Test
    fun statesStayRightThroughEditsThatMakeLinesLongAndShort() {
        for (name in fragments.keys) {
            val lang = Highlighter.languageFor(name)
            val frags = fragments.getValue(name)
            for (seed in 1..10) {
                val rnd = Random(seed * 17L + name.hashCode())
                val sb = StringBuilder(generate(name, rnd, 50 + rnd.nextInt(50)))
                val index = LineIndex().also { it.build(sb) }
                val states = ScanStates(lang, limit)
                assertTrue(states.build(sb.toString(), index))
                repeat(40) {
                    val start = rnd.nextInt(sb.length + 1)
                    val removed = rnd.nextInt(minOf(if (rnd.nextInt(3) == 0) 260 else 12, sb.length - start) + 1)
                    val ins = when (rnd.nextInt(6)) {
                        0 -> ""
                        1 -> frags[rnd.nextInt(frags.size)]
                        2 -> frags[rnd.nextInt(frags.size)] + "\n"
                        3 -> "y".repeat(100 + rnd.nextInt(100))
                        4 -> longOnes[rnd.nextInt(longOnes.size)] + "\n"
                        else -> "x"
                    }
                    sb.replace(start, start + removed, ins)
                    val splice = index.replace(start, removed, ins)
                    val text = sb.toString()
                    assertTrue(states.replace(splice, text, index))
                    val fresh = ScanStates(lang, limit)
                    assertTrue(fresh.build(text, index))
                    assertEquals("$name seed $seed: long lines after an edit", fresh.longLines, states.longLines)
                    assertEquals(longRanges(text).size, states.longLines)
                    for (l in 0 until index.lineCount) {
                        assertEquals(
                            "$name seed $seed: next long line from $l",
                            fresh.nextLongStart(index, index.startOf(l)), states.nextLongStart(index, index.startOf(l)),
                        )
                        if (states.isStart(l)) assertTrue("$name seed $seed: line $l wrongly a start", fresh.isStart(l))
                    }
                }
                assertWindowsAgree(name, sb.toString(), index, states, rnd, 10)
            }
        }
    }

    @Test
    fun anEditInsideAHugeLineReadsAFewLinesAndNotTheLine() {
        val before = (0 until 3000).joinToString("\n") { "val v$it = $it" }
        val huge = "a".repeat(2_000_000)
        val sb = StringBuilder(before + "\n" + huge + "\n" + before + "\n")
        val lang = Highlighter.languageFor("a.kt")
        val index = LineIndex().also { it.build(sb) }
        val states = ScanStates(lang, 1_000)
        assertTrue(states.build(sb.toString(), index))
        assertEquals(1, states.longLines)
        val start = before.length + 1 + 1_000_000
        sb.insert(start, "x")
        val splice = index.replace(start, 0, "x")
        var asked = 0
        val t = System.nanoTime()
        assertTrue(states.replace(splice, sb.toString(), index) { asked++; false })
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("read $asked line starts for one edit", asked < 40)
        assertTrue("took $ms ms", ms < 500)
        assertEquals(1, states.longLines)
    }

    @Test
    fun aHugeLineIsNotReadWhenAWindowNextToItIsColoured() {
        val text = "val a = 1\n" + "b".repeat(3_000_000) + "\nval c = 2\n"
        val lang = Highlighter.languageFor("a.kt")
        val index = LineIndex().also { it.build(text) }
        val states = ScanStates(lang, 1_000).also { assertTrue(it.build(text, index)) }
        val t = System.nanoTime()
        val all = tokensOf(Highlighter.tokenizeWindow(text, lang, index, states, 0, 2))
        val ms = (System.nanoTime() - t) / 1_000_000
        val c = text.indexOf("val c")
        assertTrue(all.any { it.first == c && it.second == c + 3 })
        assertTrue("took $ms ms", ms < 500)
    }

    @Test
    fun emptyTextTextWithoutLineBreaksAndCrlfWork() {
        val lang = Highlighter.languageFor("a.js")
        val empty = LineIndex().also { it.build("") }
        val e = ScanStates(lang, limit).also { assertTrue(it.build("", empty)) }
        assertEquals(0, e.longLines)
        assertEquals(-1, e.nextLongStart(empty, 0))
        val one = "var a = 1; " + "x".repeat(500)
        val oneIndex = LineIndex().also { it.build(one) }
        val s = ScanStates(lang, limit).also { assertTrue(it.build(one, oneIndex)) }
        assertEquals(1, s.longLines)
        assertEquals(0, s.nextLongStart(oneIndex, 0))
        assertEquals(0, Highlighter.tokenize(one, lang, limit).size)
        val crlf = ("let a = 'b';\r\n" + "k".repeat(300) + "\r\nlet c = 'd';\r\n")
        val crlfIndex = LineIndex().also { it.build(crlf) }
        val c = ScanStates(lang, limit).also { assertTrue(it.build(crlf, crlfIndex)) }
        assertEquals(1, c.longLines)
        assertTrue(Highlighter.tokenize(crlf, lang, limit).size > 0)
    }

    @Test
    fun aLineOfExactlyTheLimitIsStillColoured() {
        val lang = Highlighter.languageFor("a.js")
        val line = "var s = \"" + "x".repeat(limit - 11) + "\";"
        assertEquals(limit, line.length)
        val text = "$line\nvar t = 1;\n"
        assertTrue(Highlighter.tokenize(text, lang, limit).size > 0)
        val index = LineIndex().also { it.build(text) }
        assertEquals(0, ScanStates(lang, limit).also { assertTrue(it.build(text, index)) }.longLines)
        val longer = "x$line\nvar t = 1;\n"
        val index2 = LineIndex().also { it.build(longer) }
        assertEquals(1, ScanStates(lang, limit).also { assertTrue(it.build(longer, index2)) }.longLines)
    }
}
