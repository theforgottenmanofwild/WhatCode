package com.whatcode.editor.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetFileTest {

    private fun names(list: List<Snippet>) = list.map { "${it.prefix}/${it.lang}" }

    @Test
    fun aGoodSnippetHasNoProblem() {
        assertNull(SnippetFile.problem("todo", "To do", "// TODO: \${1:what}", "Kotlin"))
        assertNull(SnippetFile.problem("t", "", "x", null))
    }

    @Test
    fun theWordMustBeWordCharacters() {
        assertNotNull(SnippetFile.problem("", "n", "x", null))
        assertNotNull(SnippetFile.problem("a-b", "n", "x", null))
        assertNotNull(SnippetFile.problem("a b", "n", "x", null))
        assertNotNull(SnippetFile.problem("x".repeat(SnippetFile.MAX_PREFIX + 1), "n", "x", null))
        assertNull(SnippetFile.problem("ünï_2", "n", "x", null))
    }

    @Test
    fun theNameIsOneLineAndTheBodyIsNotEmpty() {
        assertNotNull(SnippetFile.problem("a", "two\nlines", "x", null))
        assertNotNull(SnippetFile.problem("a", "n".repeat(SnippetFile.MAX_NAME + 1), "x", null))
        assertNotNull(SnippetFile.problem("a", "n", "", null))
        assertNotNull(SnippetFile.problem("a", "n", "x".repeat(SnippetFile.MAX_BODY + 1), null))
    }

    @Test
    fun theLanguageMustBeOneTheEditorKnows() {
        assertNotNull(SnippetFile.problem("a", "n", "x", "Klingon"))
        assertNull(SnippetFile.problem("a", "n", "x", "Python"))
    }

    @Test
    fun aWordIsUsedOnceForALanguage() {
        val others = listOf(Snippet("a", "", "x", "Python"))
        assertNotNull(SnippetFile.problem("a", "n", "y", "Python", others))
        assertNull(SnippetFile.problem("a", "n", "y", "Go", others))
        assertNull(SnippetFile.problem("a", "n", "y", null, others))
    }

    @Test
    fun allTheBodiesTogetherHaveALimit() {
        val full = SnippetFile.MAX_TOTAL / SnippetFile.MAX_BODY - 1
        val big = (0 until full).map { Snippet("s$it", "", "x".repeat(SnippetFile.MAX_BODY), null) } +
            Snippet("last", "", "x".repeat(SnippetFile.MAX_BODY - 10), null)
        val room = 10
        assertNotNull(SnippetFile.problem("one", "", "x".repeat(room + 1), null, big))
        assertNull(SnippetFile.problem("one", "", "x".repeat(room), null, big))
    }

    @Test
    fun theSavedTextReadsBackTheSame() {
        val list = listOf(
            Snippet("todo", "To do", "// TODO: \${1:what}\n\t\$0", "Kotlin"),
            Snippet("sig", "Signature \"quoted\"", "— 😀 \\ back", null),
        )
        val back = SnippetFile.decodeSaved(SnippetFile.encodeSaved(list))
        assertEquals(2, back.size)
        for ((a, b) in list.zip(back)) {
            assertEquals(a.prefix, b.prefix)
            assertEquals(a.name, b.name)
            assertEquals(a.body, b.body)
            assertEquals(a.lang, b.lang)
        }
    }

    @Test
    fun savedTextThatIsBrokenGivesNothing() {
        assertTrue(SnippetFile.decodeSaved(null).isEmpty())
        assertTrue(SnippetFile.decodeSaved("").isEmpty())
        assertTrue(SnippetFile.decodeSaved("[{").isEmpty())
        assertTrue(SnippetFile.decodeSaved("{}").isEmpty())
    }

    @Test
    fun anExportedFileImportsTheSame() {
        val list = listOf(Snippet("a", "A", "1", null), Snippet("b", "B", "2\n3", "Go"))
        val read = SnippetFile.import(SnippetFile.export(list)).read!!
        assertEquals(0, read.skipped)
        assertEquals(listOf("a/null", "b/Go"), names(read.snippets))
        assertEquals("2\n3", read.snippets[1].body)
    }

    @Test
    fun anEmptyExportIsStillAFile() {
        val text = SnippetFile.export(emptyList())
        assertNotNull(SnippetFile.import(text).error)
    }

    @Test
    fun badEntriesAreSkippedAndCounted() {
        val text = """{ "app": "WhatCode", "kind": "snippets", "format": 1, "snippets": [
            { "prefix": "ok", "name": "Fine", "body": "x" },
            { "prefix": "bad word", "body": "x" },
            { "prefix": "nobody" },
            { "prefix": "lang", "body": "x", "lang": 5 },
            { "prefix": "ok", "body": "again" },
            "not a snippet",
            { "prefix": "nameless", "body": "y", "lang": "Python" }
        ] }"""
        val read = SnippetFile.import(text).read!!
        assertEquals(listOf("ok/null", "nameless/Python"), names(read.snippets))
        assertEquals("", read.snippets[1].name)
        assertEquals(5, read.skipped)
    }

    @Test
    fun otherFilesAreRefusedWithAReason() {
        assertNotNull(SnippetFile.import("not json").error)
        assertNotNull(SnippetFile.import("""{ "app": "Other", "kind": "snippets", "format": 1, "snippets": [] }""").error)
        assertNotNull(SnippetFile.import("""{ "app": "WhatCode", "format": 1, "settings": {} }""").error)
        assertNotNull(SnippetFile.import("""{ "app": "WhatCode", "kind": "snippets", "format": 9, "snippets": [] }""").error)
        assertNotNull(SnippetFile.import("""{ "app": "WhatCode", "kind": "snippets", "format": 1 }""").error)
        assertNotNull(SnippetFile.import(" ".repeat(SnippetFile.MAX_BYTES + 1)).error)
    }

    @Test
    fun noMoreThanTheLimitAreRead() {
        val values = (0..SnippetFile.MAX_SNIPPETS + 5).map { mapOf("prefix" to "p$it", "body" to "x") }
        val read = SnippetFile.fromValues(values)
        assertEquals(SnippetFile.MAX_SNIPPETS, read.snippets.size)
        assertEquals(6, read.skipped)
    }

    @Test
    fun mergingReplacesTheSameWordAndLanguageAndAddsTheRest() {
        val mine = listOf(Snippet("a", "old", "1", null), Snippet("b", "", "2", "Go"))
        val incoming = listOf(Snippet("a", "new", "9", null), Snippet("b", "", "3", "Python"))
        val m = SnippetFile.merge(mine, incoming)
        assertEquals(listOf("a/null", "b/Go", "b/Python"), names(m.list))
        assertEquals("new", m.list[0].name)
        assertEquals(1, m.added)
        assertEquals(1, m.replaced)
        assertEquals(0, m.dropped)
    }

    @Test
    fun mergingStopsAtTheLimits() {
        val mine = (0 until SnippetFile.MAX_SNIPPETS).map { Snippet("p$it", "", "x", null) }
        val m = SnippetFile.merge(mine, listOf(Snippet("p0", "", "y", null), Snippet("new", "", "z", null)))
        assertEquals(SnippetFile.MAX_SNIPPETS, m.list.size)
        assertEquals(1, m.replaced)
        assertEquals(1, m.dropped)

        val heavy = listOf(Snippet("h", "", "x".repeat(SnippetFile.MAX_TOTAL), null))
        val n = SnippetFile.merge(heavy, listOf(Snippet("more", "", "x", null)))
        assertEquals(1, n.list.size)
        assertEquals(1, n.dropped)
    }
}
