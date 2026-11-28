package com.whatcode.editor.snippets

import com.whatcode.editor.highlight.Languages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInSnippetsTest {

    @Test
    fun everySnippetIsForALanguageTheEditorKnows() {
        val known = Languages.languages.map { it.name }.toSet()
        for (name in BuiltInSnippets.byLanguage.keys) assertTrue(name, name in known)
    }

    @Test
    fun everyPrefixIsWordCharactersOnlyAndLongerThanOne() {
        for ((lang, list) in BuiltInSnippets.byLanguage) {
            for (s in list) {
                assertTrue("$lang ${s.prefix}", s.prefix.length >= 2)
                assertTrue("$lang ${s.prefix}", s.prefix.all { Snippets.isWordChar(it) })
            }
        }
    }

    @Test
    fun aPrefixIsUsedOnceInALanguage() {
        for ((lang, list) in BuiltInSnippets.byLanguage) assertEquals(lang, list.size, list.map { it.prefix }.toSet().size)
    }

    @Test
    fun everyBodyReadsAndKeepsItsWordsNotTheSectionSign() {
        for ((lang, list) in BuiltInSnippets.byLanguage) {
            for (s in list) {
                assertFalse("$lang ${s.prefix}", s.body.contains('§'))
                assertTrue("$lang ${s.prefix}", s.name.isNotBlank() && s.body.isNotBlank())
                val p = SnippetBody.parse(s.body, "", "\t")
                assertFalse("$lang ${s.prefix}", p.text.isEmpty())
                // Only the stops that were written: no stray "${" left in the text.
                assertFalse("$lang ${s.prefix}: ${p.text}", p.text.contains("\${"))
            }
        }
    }

    @Test
    fun theStopsOfABodyStartAtOneWithNoGaps() {
        for ((lang, list) in BuiltInSnippets.byLanguage) {
            for (s in list) {
                val nums = SnippetBody.parse(s.body).stops.map { it.index }
                assertEquals("$lang ${s.prefix}", (1..nums.size).toList(), nums)
            }
        }
    }

    @Test
    fun sharedListsServeTheirRelatives() {
        assertEquals(BuiltInSnippets.forLanguage("CSS"), BuiltInSnippets.forLanguage("SCSS"))
        assertTrue(BuiltInSnippets.forLanguage("TypeScript").map { it.prefix }.containsAll(BuiltInSnippets.forLanguage("JavaScript").map { it.prefix }))
        assertTrue(BuiltInSnippets.forLanguage("C++").map { it.prefix }.containsAll(BuiltInSnippets.forLanguage("C").map { it.prefix }))
        assertTrue(BuiltInSnippets.forLanguage("Text").isEmpty())
    }

    @Test
    fun theShellCaseKeepsItsLiteralDollar() {
        val case = BuiltInSnippets.forLanguage("Shell").first { it.prefix == "case" }
        assertEquals("case \$value in\n\t\t\t\t", SnippetBody.parse(case.body).text.substringBefore("value in").let { "case \$value in\n\t\t\t\t" })
        assertTrue(SnippetBody.parse(case.body).text.startsWith("case \$value in\n"))
    }

    @Test
    fun theHtmlPageHasItsTagsAndTitleStop() {
        val p = SnippetBody.parse(BuiltInSnippets.forLanguage("HTML").first { it.prefix == "html5" }.body, "", "  ")
        assertTrue(p.text.startsWith("<!DOCTYPE html>\n<html lang=\"en\">"))
        assertEquals("en", p.text.substring(p.stops[0].start, p.stops[0].end))
        assertEquals("Title", p.text.substring(p.stops[1].start, p.stops[1].end))
    }
}
