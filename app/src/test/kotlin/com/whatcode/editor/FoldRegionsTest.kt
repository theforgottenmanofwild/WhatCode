package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldRegionsTest {

    private fun find(text: String, by: FoldBy = FoldBy.BOTH, min: Int = 1, skipped: (Int) -> Boolean = { false }) =
        FoldRegions.find(text, by, 4, min, skipped)

    /** The lines a region hides, as the text, so a test can read what goes out of sight. */
    private fun hidden(text: String, r: FoldRegion) = text.substring(r.start, r.end)

    @Test
    fun aBracketBlockHidesTheLinesBetweenTheTwoBracketsAndKeepsTheClosingLine() {
        val text = "fun f() {\n    a()\n    b()\n}\n"
        val r = find(text, FoldBy.BRACKETS).single()
        assertEquals(0, r.header)
        assertEquals("    a()\n    b()\n", hidden(text, r))
    }

    @Test
    fun aBlockWithNothingBetweenItsBracketsIsNotOffered() {
        assertTrue(find("a {\n}\n", FoldBy.BRACKETS).isEmpty())
        assertTrue(find("a(b,\nc)\n", FoldBy.BRACKETS).isEmpty())
    }

    @Test
    fun theSmallestBlockCanBeChosen() {
        val text = "a {\n  one\n}\nb {\n  one\n  two\n}\n"
        assertEquals(2, find(text, FoldBy.BRACKETS, min = 1).size)
        assertEquals(listOf(text.indexOf("b {")), find(text, FoldBy.BRACKETS, min = 2).map { it.header })
        assertTrue(find(text, FoldBy.BRACKETS, min = 3).isEmpty())
    }

    @Test
    fun aMinimumBelowOneIsReadAsOne() {
        assertEquals(1, find("a {\n  x\n}\n", FoldBy.BRACKETS, min = 0).size)
    }

    @Test
    fun nestedBlocksAreAllFoundInOrderOfTheirHeader() {
        val text = "class A {\n  fun f() {\n    x\n  }\n  fun g() {\n    y\n  }\n}\n"
        val found = find(text, FoldBy.BRACKETS)
        assertEquals(listOf(0, text.indexOf("  fun f"), text.indexOf("  fun g")), found.map { it.header })
    }

    @Test
    fun aLineThatClosesOneBlockAndOpensAnotherIsBoth() {
        val text = "if (a) {\n  x\n} else {\n  y\n}\n"
        val found = find(text, FoldBy.BRACKETS)
        assertEquals(listOf(0, text.indexOf("} else")), found.map { it.header })
        assertEquals("  x\n", hidden(text, found[0]))
        assertEquals("  y\n", hidden(text, found[1]))
    }

    @Test
    fun aLineThatOpensSeveralBracketsFoldsTheWidestBlock() {
        val text = "foo(function() {\n  x\n  y\n});\n"
        val found = find(text, FoldBy.BRACKETS)
        assertEquals(1, found.size)
        assertEquals("  x\n  y\n", hidden(text, found[0]))
    }

    @Test
    fun aBracketInAStringOrCommentIsNotCounted() {
        val text = "if x {\n  a\n  s = \"}\"\n  b\n}\n"
        val inString = text.indexOf("\"}") + 1
        val counted = find(text, FoldBy.BRACKETS, skipped = { it == inString }).single()
        assertEquals("  a\n  s = \"}\"\n  b\n", hidden(text, counted))
        // Without being told, the bracket in the string closes the block early.
        assertEquals("  a\n", hidden(text, find(text, FoldBy.BRACKETS).single()))
    }

    @Test
    fun aBracketThatIsNeverClosedOrNeverOpenedIsIgnored() {
        assertTrue(find("a {\n  b\n  c\n", FoldBy.BRACKETS).isEmpty())
        assertTrue(find("a\n  b\n  c\n}\n", FoldBy.BRACKETS).isEmpty())
        assertTrue(find("a ( \n b \n c ]\n", FoldBy.BRACKETS).isEmpty())
    }

    @Test
    fun aBodyIsEverythingIndentedMoreThanTheHeader() {
        val text = "def f():\n    a\n    b\n\n    c\nnext\n"
        val r = find(text, FoldBy.INDENTATION).single()
        assertEquals(0, r.header)
        // The blank line in the middle goes with the block; a blank line after the last body line would not.
        assertEquals("    a\n    b\n\n    c\n", hidden(text, r))
    }

    @Test
    fun blankLinesAfterTheLastIndentedLineStayOnShow() {
        val text = "def f():\n    a\n\n\ndef g():\n    b\n"
        val found = find(text, FoldBy.INDENTATION)
        assertEquals("    a\n", hidden(text, found[0]))
    }

    @Test
    fun indentedBlocksNestAndEachLineHasOneBlock() {
        val text = "a:\n  b:\n    c: 1\n    d: 2\n  e: 3\nf: 4\n"
        val found = find(text, FoldBy.INDENTATION)
        assertEquals(listOf(0, text.indexOf("  b:")), found.map { it.header })
        assertEquals("  b:\n    c: 1\n    d: 2\n  e: 3\n", hidden(text, found[0]))
        assertEquals("    c: 1\n    d: 2\n", hidden(text, found[1]))
    }

    @Test
    fun aTabCountsAsOneLevel() {
        val text = "a\n\tb\n\tc\nd\n"
        assertEquals("\tb\n\tc\n", hidden(text, find(text, FoldBy.INDENTATION).single()))
    }

    @Test
    fun aBodyThatReachesAnUnfinishedLastLineStopsShortOfIt() {
        val text = "a\n  b\n  c\n  d"
        val r = find(text, FoldBy.INDENTATION).single()
        assertEquals("  b\n  c\n", hidden(text, r))
        assertTrue(r.end <= text.length && text[r.end - 1] == '\n')
        // With nothing left to hide there is no block.
        assertTrue(find("a\n  b", FoldBy.INDENTATION).isEmpty())
    }

    @Test
    fun aFinalLineBreakDoesNotStopTheBody() {
        val text = "a\n  b\n  c\n"
        assertEquals("  b\n  c\n", hidden(text, find(text, FoldBy.INDENTATION).single()))
    }

    @Test
    fun windowsLineEndingsFoldTheSameWay() {
        val text = "a {\r\n  b\r\n  c\r\n}\r\n"
        assertEquals("  b\r\n  c\r\n", hidden(text, find(text, FoldBy.BRACKETS).single()))
        val indented = "a\r\n  b\r\n  c\r\nd\r\n"
        assertEquals("  b\r\n  c\r\n", hidden(indented, find(indented, FoldBy.INDENTATION).single()))
    }

    @Test
    fun bothUsesTheBracketsFirstAndTheIndentationForTheRest() {
        val text = "if x {\n  a\n  b\n}\nwhile y:\n  c\n  d\n"
        val found = find(text, FoldBy.BOTH)
        assertEquals(listOf(0, text.indexOf("while")), found.map { it.header })
        assertEquals("  a\n  b\n", hidden(text, found[0]))
        assertEquals(1, find(text, FoldBy.BRACKETS).size)
    }

    @Test
    fun aBracketBlockWinsOverTheIndentationOfTheSameHeader() {
        val text = "d = {\n    'a': 1,\n    'b': 2,\n}\n"
        val r = find(text, FoldBy.BOTH).single()
        assertEquals("    'a': 1,\n    'b': 2,\n", hidden(text, r))
    }

    @Test
    fun aTextWithoutBlocksHasNone() {
        assertTrue(find("").isEmpty())
        assertTrue(find("one line").isEmpty())
        assertTrue(find("a\nb\nc\n").isEmpty())
        assertTrue(find("\n\n\n\n").isEmpty())
    }

    @Test
    fun everyHiddenTextEndsWithALineBreakAndStartsAfterOne() {
        val text = "a {\n  b {\n    c\n  }\n  d\n}\ne:\n  f\n  g"
        for (r in find(text)) {
            assertEquals('\n', text[r.start - 1])
            assertEquals('\n', text[r.end - 1])
            assertTrue(r.header < r.start && r.start < r.end)
        }
    }

    @Test
    fun veryDeepIndentationIsFollowedOnlyToALimit() {
        val sb = StringBuilder()
        for (i in 0 until FoldRegions.MAX_DEPTH + 50) sb.append(" ".repeat(i)).append("x\n")
        // It must not fail; what is found is not looked at.
        assertNotNull(find(sb.toString()))
    }

    @Test
    fun aHugeNumberOfBlocksIsFoundQuickly() {
        val sb = StringBuilder()
        for (i in 0 until 20_000) sb.append("f$i() {\n  a\n  b\n}\n")
        val started = System.nanoTime()
        val found = find(sb.toString())
        assertEquals(20_000, found.size)
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", (System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun startingAtFindsTheBlockOfAHeaderLine() {
        val text = "a {\n  x\n}\nb {\n  y\n}\n"
        val found = find(text)
        assertEquals(found[1], FoldRegions.startingAt(found, text.indexOf("b {")))
        assertNull(FoldRegions.startingAt(found, 1))
        assertNull(FoldRegions.startingAt(emptyList(), 0))
    }

    @Test
    fun aroundFindsTheHeaderOrTheInnermostBlockHoldingTheLine() {
        val text = "class A {\n  fun f() {\n    x\n  }\n  y\n}\nz\n"
        val found = find(text)
        val outer = found[0]
        val inner = found[1]
        assertEquals(inner, FoldRegions.around(found, text, text.indexOf("  fun f")))
        assertEquals(inner, FoldRegions.around(found, text, text.indexOf("x")))
        assertEquals(outer, FoldRegions.around(found, text, text.indexOf("  y")))
        assertEquals(outer, FoldRegions.around(found, text, 3))
        assertNull(FoldRegions.around(found, text, text.indexOf("z")))
    }

    @Test
    fun shiftedMovesBlocksAfterAnEditAndKeepsOnesBefore() {
        val r = FoldRegion(10, 14, 30)
        // Text added before the header moves all of it.
        assertEquals(listOf(FoldRegion(15, 19, 35)), FoldRegions.shifted(listOf(r), 2, 0, 5))
        // An edit that ends exactly where the header starts moves it too.
        assertEquals(listOf(FoldRegion(12, 16, 32)), FoldRegions.shifted(listOf(r), 5, 5, 7))
        // After the block nothing moves.
        assertEquals(listOf(r), FoldRegions.shifted(listOf(r), 30, 0, 9))
        assertEquals(listOf(r), FoldRegions.shifted(listOf(r), 50, 3, 0))
    }

    @Test
    fun shiftedGrowsABlockThatHoldsTheEdit() {
        val r = FoldRegion(10, 14, 30)
        assertEquals(listOf(FoldRegion(10, 14, 33)), FoldRegions.shifted(listOf(r), 20, 0, 3))
        assertEquals(listOf(FoldRegion(10, 14, 28)), FoldRegions.shifted(listOf(r), 20, 2, 0))
        // An edit in the header line moves where the hidden lines start.
        assertEquals(listOf(FoldRegion(10, 16, 32)), FoldRegions.shifted(listOf(r), 12, 0, 2))
    }

    @Test
    fun shiftedLeavesOutABlockTheEditCutsAcross() {
        val r = FoldRegion(10, 14, 30)
        assertTrue(FoldRegions.shifted(listOf(r), 8, 4, 0).isEmpty()) // starts before the header, ends inside it
        assertTrue(FoldRegions.shifted(listOf(r), 12, 6, 0).isEmpty()) // from the header line into the hidden lines
        assertTrue(FoldRegions.shifted(listOf(r), 25, 10, 0).isEmpty()) // from inside to after the end
        assertTrue(FoldRegions.shifted(emptyList(), 0, 1, 1).isEmpty())
    }
}
