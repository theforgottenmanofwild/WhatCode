package com.whatcode.editor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IndentGuidesTest {

    @Test
    fun theStepFollowsTheIndentationOfTheFile() {
        assertEquals(2, IndentGuides.stepOf("  "))
        assertEquals(4, IndentGuides.stepOf("    "))
        assertEquals(4, IndentGuides.stepOf("\t"))
        assertEquals(4, IndentGuides.stepOf(""))
    }

    @Test
    fun measuresSpacesAndTabs() {
        assertEquals(0, IndentGuides.indent("a", 0, 4))
        assertEquals(6, IndentGuides.indent("      a", 0, 4))
        assertEquals(8, IndentGuides.indent("\t\ta", 0, 4))
        assertEquals(4, IndentGuides.indent("  \ta", 0, 4))
        assertEquals(2, IndentGuides.indent("a\n  b", 2, 4))
    }

    @Test
    fun aLineWithNothingOnItHasNoIndentation() {
        for (t in listOf("", "    ", "  \n", "\t\r\n", "\n")) assertEquals(t, -1, IndentGuides.indent(t, 0, 4))
    }

    @Test
    fun veryDeepIndentationIsCapped() {
        val t = " ".repeat(100_000) + "a"
        assertEquals(4 * IndentGuides.MAX_LEVELS, IndentGuides.indent(t, 0, 4))
        assertEquals(IndentGuides.MAX_LEVELS, IndentGuides.offsets(t, 0, 4, 100_000).size)
    }

    @Test
    fun aGuideStartsEveryLevelTheLineIsInside() {
        assertArrayEquals(intArrayOf(0, 4), IndentGuides.offsets("        a", 0, 4, 8))
        assertArrayEquals(intArrayOf(0, 4), IndentGuides.offsets("      a", 0, 4, 6))
        assertArrayEquals(intArrayOf(0, 1), IndentGuides.offsets("\t\ta", 0, 4, 8))
        assertArrayEquals(intArrayOf(2, 4), IndentGuides.offsets("x\n    a", 2, 2, 4))
        assertArrayEquals(intArrayOf(), IndentGuides.offsets("a", 0, 4, 0))
    }

    @Test
    fun guidesStopAtTheTextEvenWhenMoreAreAskedFor() {
        assertArrayEquals(intArrayOf(0), IndentGuides.offsets("  a", 0, 4, 12))
    }

    @Test
    fun anEmptyLineTakesItsDepthFromTheLinesRoundIt() {
        assertEquals(4, IndentGuides.blankDepth(0, 4, 4)) // under a line that opens a block
        assertEquals(4, IndentGuides.blankDepth(0, 12, 4))
        assertEquals(8, IndentGuides.blankDepth(8, 8, 4))
        assertEquals(0, IndentGuides.blankDepth(8, 0, 4)) // after the block
        assertEquals(0, IndentGuides.blankDepth(-1, 8, 4))
        assertEquals(0, IndentGuides.blankDepth(8, -1, 4))
    }
}
