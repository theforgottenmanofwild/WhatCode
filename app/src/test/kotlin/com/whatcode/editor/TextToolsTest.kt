package com.whatcode.editor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TextToolsTest {

    private val options = TextTools.Options()

    @Test
    fun everyToolHasItsOwnKey() {
        val keys = TextTool.values().map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (tool in TextTool.values()) assertEquals(tool, TextTool.fromKey(tool.key))
        assertNull(TextTool.fromKey("tools"))
    }

    @Test
    fun noToolFailsOnAnEmptyText() {
        for (tool in TextTool.values()) {
            if (tool == TextTool.FORMAT_JSON || tool == TextTool.MINIFY_JSON) continue
            assertNull(tool.name, TextTools.edit(tool, "", 0, 0, options))
        }
    }

    @Test
    fun theJsonToolsSayThereIsNothingToFormatInAnEmptyText() {
        for (tool in listOf(TextTool.FORMAT_JSON, TextTool.MINIFY_JSON)) {
            val failure = runCatching { TextTools.edit(tool, "  \n", 1, 1, options) }.exceptionOrNull()
            assertEquals("There is no JSON here to format", failure?.message)
        }
    }

    @Test
    fun eachToolDoesSomethingToATextThatNeedsIt() {
        val t = "    b\r\n\ta\n    b\n    c"
        for (tool in TextTool.values()) {
            val cursor = t.indexOf('a') + 1
            if (tool == TextTool.LOWER || tool == TextTool.FORMAT_JSON || tool == TextTool.MINIFY_JSON) continue
            assertNotNull(tool.name, TextTools.edit(tool, t, cursor, cursor, options))
        }
        val json = "{\"a\": [1,2]}"
        assertNotNull(TextTools.edit(TextTool.FORMAT_JSON, json, 0, 0, options))
        assertNotNull(TextTools.edit(TextTool.MINIFY_JSON, json, 0, 0, options))
    }

    @Test
    fun theJsonIndentOfTheSettingsIsUsed() {
        val tab = TextTools.edit(TextTool.FORMAT_JSON, "[1]", 0, 0, TextTools.Options(jsonIndent = 0))!!
        assertEquals("[\n\t1\n]", tab.insert)
        val four = TextTools.edit(TextTool.FORMAT_JSON, "[1]", 0, 0, TextTools.Options(jsonIndent = 4))!!
        assertEquals("[\n    1\n]", four.insert)
    }

    @Test
    fun theTabWidthOfTheSettingsIsUsed() {
        val e = TextTools.edit(TextTool.TO_SPACES, "\ta", 0, 0, TextTools.Options(tabWidth = 2))!!
        assertEquals("  ", e.insert)
    }

    @Test
    fun bookmarkedLinesAreFoundAgainAfterAConversion() {
        val before = "one\ntwo\r\nthree\rfour"
        val marks = intArrayOf(before.indexOf("two"), before.indexOf("four"))
        val lines = TextTools.lineNumbers(before, marks)
        assertArrayEquals(intArrayOf(1, 3), lines)
        val after = "one\r\ntwo\r\nthree\r\nfour"
        assertArrayEquals(intArrayOf(after.indexOf("two"), after.indexOf("four")), TextTools.lineOffsets(after, lines))
    }

    @Test
    fun aLinePastTheEndIsLeftOut() {
        assertArrayEquals(intArrayOf(0, 2), TextTools.lineOffsets("a\n", intArrayOf(0, 1, 5)))
    }

    @Test
    fun aLongTextSortsInOneEdit() {
        val t = (20_000 downTo 1).joinToString("\n") { "line $it" }
        val e = TextTools.edit(TextTool.SORT, t, 0, 0, options)!!
        assertEquals("line 1", e.insert.substringBefore('\n'))
        assertEquals(t.length, e.insert.length)
    }
}
