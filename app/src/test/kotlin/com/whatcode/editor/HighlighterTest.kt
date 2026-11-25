package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The highlighter's scanner works on plain strings, so it can be checked without a device. */
class HighlighterTest {

    private fun tokens(src: String, file: String): List<Triple<Int, Int, Int>> {
        val t = Highlighter.tokenize(src, Highlighter.languageFor(file))
        return (0 until t.size).map { Triple(t.data[it * 3], t.data[it * 3 + 1], t.data[it * 3 + 2]) }
    }

    private fun assertWellFormed(src: String, file: String) {
        var last = 0
        for ((s, e, c) in tokens(src, file)) {
            assertTrue("$file: piece $s..$e starts before the previous one ended ($last)", s >= last)
            assertTrue("$file: piece $s..$e is empty or outside the text (${src.length})", e > s && e <= src.length)
            assertTrue("$file: colour $c is an internal marker, not a colour", c < -0xFFFF || c > 0xFFFF)
            last = e
        }
    }

    @Test
    fun languagesAreChosenByFileName() {
        assertEquals("Kotlin", Highlighter.languageFor("Main.kt").name)
        assertEquals("Python", Highlighter.languageFor("run.PY").name)
        assertEquals("Dockerfile", Highlighter.languageFor("Dockerfile").name)
        assertEquals("Go module", Highlighter.languageFor("go.mod").name)
        assertEquals("Text", Highlighter.languageFor("notes.unknown").name)
        assertEquals("Text", Highlighter.languageFor(null).name)
    }

    @Test
    fun plainTextHasNoColours() {
        assertEquals(0, tokens("anything 123 \"here\"", "a.txt").size)
    }

    @Test
    fun codeIsColouredInOrderAndInsideTheText() {
        val samples = mapOf(
            "a.kt" to "package a\n// note\nfun main() { val s = \"hi \\\" there\"; println(s + 12) }\n/* block */\n\"\"\"raw\ntext\"\"\"",
            "a.js" to "const r = /ab+c/gi; let x = a / b; // c\nconsole.log(`t ${'$'}{x}`)",
            "a.py" to "def f(x):\n    '''doc'''\n    return f\"{x}\" # c",
            "a.html" to "<!doctype html><div class=\"a\" id='b'>&amp; <script>var a = 1;</script><style>a { color: #fff }</style></div><!-- c -->",
            "a.css" to "a:hover > .b, #c { color: #fff; margin: 0 auto; } @media (x) { /* c */ }",
            "a.json" to "{\"a\": [1, 2.5e3, true, null], \"b\": {\"c\": \"d\"}}",
            "a.yml" to "key: value # c\nlist:\n  - a\n  - \"b\"\n",
            "a.md" to "# Title\n\n- item **bold** `code` [l](http://x.y)\n\n```kotlin\nfun a() {}\n```\n",
            "a.sql" to "SELECT a, b FROM t WHERE c = 'x' -- c\n",
            "Dockerfile" to "FROM alpine:3\nRUN echo \"hi\" # c\n",
        )
        for ((file, src) in samples) assertWellFormed(src, file)
    }

    @Test
    fun brokenCodeDoesNotThrowAndStillColoursWhatCameBefore() {
        val broken = listOf(
            "\"never closed", "'", "/* never closed", "\"\"\"never closed", "`never closed ${'$'}{", "<div class=\"x", "<!-- ", "a = (",
            "\\", "\"\\", "/", "/[", "#", "@", "'''", "{\"a\":", "---\n- [",
        )
        for (file in listOf("a.kt", "a.js", "a.py", "a.html", "a.css", "a.json", "a.yml", "a.md", "a.sql", "a.rs", "a.go", "a.sh")) {
            for (src in broken) assertWellFormed(src, file)
        }
    }

    @Test
    fun anUnclosedStringStopsAtTheEndOfItsLine() {
        val src = "val a = \"oops\nval b = 1"
        val t = tokens(src, "a.kt")
        val firstLineEnd = src.indexOf('\n')
        assertTrue("a string must not run past its line", t.none { it.first < firstLineEnd && it.second > firstLineEnd })
        assertTrue("the second line is still read", t.any { it.first > firstLineEnd })
    }

    @Test
    fun aLongLineOfEscapesIsStillColouredToTheEnd() {
        // 20,000 escapes in one string, then more code: a pattern that recurses once per escape could give up half way.
        val src = "val a = \"" + "\\n".repeat(20_000) + "\"\nval b = 1\n"
        var result: List<Triple<Int, Int, Int>> = emptyList()
        val worker = Thread(null, { result = tokens(src, "a.kt") }, "highlighter-test", 512 * 1024) // a phone's thread, not a desktop's
        worker.start()
        worker.join()
        assertTrue("the code after the long string was never coloured", result.any { it.first > src.indexOf('\n') })
    }
}
