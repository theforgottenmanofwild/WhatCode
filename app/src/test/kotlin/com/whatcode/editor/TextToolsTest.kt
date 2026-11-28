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
        for (tool in TextTool.values()) assertNull(tool.name, TextTools.edit(tool, "", 0, 0, options))
    }

    @Test
    fun eachToolDoesSomethingToATextThatNeedsIt() {
        val t = "    b\r\n\ta\n    b\n    c"
        for (tool in TextTool.values()) {
            val cursor = t.indexOf('a') + 1
            if (tool == TextTool.LOWER) continue
            assertNotNull(tool.name, TextTools.edit(tool, t, cursor, cursor, options))
        }
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
