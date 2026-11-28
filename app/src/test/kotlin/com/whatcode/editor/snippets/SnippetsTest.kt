package com.whatcode.editor.snippets

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetsTest {

    private val source = SnippetSource()
    private val none = SnippetSource(builtIn = false)

    private fun trigger(t: String, cursor: Int = t.length, lang: String = "JavaScript", src: SnippetSource = source) =
        Snippets.trigger(t, cursor, cursor, lang, src)

    // ------------------------------------------------------------------ which snippets

    @Test
    fun theBuiltInSnippetsBelongToTheirLanguage() {
        assertNotNull(Snippets.find("Python", "def", source))
        assertNull(Snippets.find("Text", "def", source))
        assertNull(Snippets.find("JSON", "if", source))
        assertEquals("Class", Snippets.find("Python", "cls", source)!!.name)
        assertEquals("Class", Snippets.find("JavaScript", "cls", source)!!.name)
    }

    @Test
    fun turningTheBuiltInOnesOffLeavesTheOthers() {
        assertNull(Snippets.find("Python", "def", none))
        val own = Snippet("mine", "Mine", "x")
        assertEquals(listOf(own), Snippets.available("Python", SnippetSource(builtIn = false, user = listOf(own))))
    }

    @Test
    fun aSnippetOfYoursReplacesABuiltInOneWithTheSamePrefix() {
        val own = Snippet("def", "My def", "def x", lang = "Python")
        val list = Snippets.available("Python", SnippetSource(user = listOf(own)))
        assertEquals(1, list.count { it.prefix == "def" })
        assertEquals("My def", list.first { it.prefix == "def" }.name)
        assertEquals("Function", Snippets.find("Kotlin", "fun", SnippetSource(user = listOf(own)))!!.name)
    }

    @Test
    fun aSnippetForNoLanguageIsForEveryLanguage() {
        val own = Snippet("sig", "Signature", "Best wishes")
        val src = SnippetSource(builtIn = false, user = listOf(own))
        assertEquals(own, Snippets.find("Markdown", "sig", src))
        assertEquals(own, Snippets.find("Text", "sig", src))
        assertNull(Snippets.find("Text", "sig", SnippetSource(builtIn = false, user = listOf(Snippet("sig", "x", "y", lang = "Python")))))
    }

    // ------------------------------------------------------------------ what Tab expands

    @Test
    fun aPrefixAtTheCursorIsFound() {
        val hit = trigger("x = 1;\n  fn")!!
        assertEquals("fn", hit.snippet.prefix)
        assertEquals(9, hit.start)
    }

    @Test
    fun aPrefixAtTheStartOfTheTextIsFound() {
        assertEquals(0, trigger("log")!!.start)
    }

    @Test
    fun aPrefixInTheMiddleOfAWordIsNot() {
        assertNull(trigger("forEach", cursor = 3))
        assertNull(trigger("xfn"))
        assertNull(trigger("fn1"))
    }

    @Test
    fun aWordThatRunsOnAfterTheCursorIsNot() {
        assertNull(trigger("fnx", cursor = 2))
        assertNull(trigger("fn_", cursor = 2))
    }

    @Test
    fun aWordFollowedByAnythingElseIs() {
        assertNotNull(trigger("fn()", cursor = 2))
        assertNotNull(trigger("fn ", cursor = 2))
    }

    @Test
    fun aMemberOrAPropertyIsNotAPrefix() {
        assertNull(trigger("console.log"))
        assertNull(trigger("a-log"))
        assertNull(trigger("\$log"))
        assertNull(trigger("@log"))
        assertNull(trigger("#log"))
        assertNull(trigger("\\log"))
        assertNotNull(trigger("(log"))
        assertNotNull(trigger("x; log"))
    }

    @Test
    fun aSelectionOrNothingBeforeTheCursorIsNot() {
        assertNull(Snippets.trigger("fn", 0, 2, "JavaScript", source))
        assertNull(trigger(""))
        assertNull(trigger("  "))
        assertNull(trigger("zzz"))
        assertNull(Snippets.trigger("fn", -1, -1, "JavaScript", source))
        assertNull(Snippets.trigger("fn", 5, 5, "JavaScript", source))
    }

    @Test
    fun theOpenLanguageDecides() {
        assertNull(trigger("def", lang = "JavaScript"))
        assertNotNull(trigger("def", lang = "Python"))
        assertNull(trigger("fn", src = none))
    }

    @Test
    fun aPrefixIsCaseSensitive() {
        assertNull(trigger("Fn"))
        assertNull(trigger("LOG"))
    }

    // ------------------------------------------------------------------ putting one in

    @Test
    fun theEditReplacesThePrefixAndSelectsTheFirstStop() {
        val t = "  fn"
        val e = Snippets.expansion(t, 2, 4, Snippet("fn", "F", "f(\${1:a}, \${2:b}) \$0"), "  ")
        assertEquals(2, e.edit.start)
        assertEquals(4, e.edit.end)
        assertEquals("f(a, b) ", e.edit.insert)
        assertEquals(4, e.edit.selStart)
        assertEquals(5, e.edit.selEnd)
        assertArrayEquals(intArrayOf(4, 5, 7, 8), e.stops)
        assertEquals(10, e.finalOffset)
    }

    @Test
    fun theLinesAfterTheFirstGetTheIndentOfTheLineItIsPutIn() {
        val t = "x\n    \tif"
        val e = Snippets.expansion(t, t.length - 2, t.length, Snippet("if", "If", "if (\$1) {\n\t\$0\n}"), "  ")
        assertEquals("if () {\n    \t  \n    \t}", e.edit.insert)
        assertEquals(t.length - 2 + 4, e.edit.selStart)
    }

    @Test
    fun theIndentIsOnlyTheWhiteSpaceBeforeTheText() {
        val t = "  a = fn"
        val e = Snippets.expansion(t, 6, 8, Snippet("fn", "F", "f {\n\t\$0\n}"), "\t")
        assertEquals("f {\n  \t\n  }", e.edit.insert)
    }

    @Test
    fun aSnippetWithNoStopPutsTheCursorAtItsEnd() {
        val e = Snippets.expansion("ab", 0, 2, Snippet("x", "X", "hello"), "\t")
        assertEquals(0, e.stops.size)
        assertEquals(5, e.edit.selStart)
        assertEquals(5, e.edit.selEnd)
        assertEquals(5, e.finalOffset)
    }

    @Test
    fun aSnippetCanReplaceASelection() {
        val e = Snippets.expansion("abc def ghi", 4, 7, Snippet("x", "X", "<\${1}>"), "\t")
        assertEquals(4, e.edit.start)
        assertEquals(7, e.edit.end)
        assertEquals("<>", e.edit.insert)
        assertEquals(5, e.edit.selStart)
    }
}
