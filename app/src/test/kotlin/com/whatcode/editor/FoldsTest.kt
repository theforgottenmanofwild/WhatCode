package com.whatcode.editor

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit tests for [Folds] tracking nested, shifted and touched folded stretches. */
class FoldsTest {

    @Test
    fun foldAndUnfoldTrackCountAndIgnoreInvalidOrDuplicateRanges() {
        val folds = Folds()
        assertFalse(folds.fold(10, 10))
        assertFalse(folds.fold(12, 8))
        assertTrue(folds.fold(5, 20))
        assertFalse(folds.fold(5, 20))
        assertEquals(1, folds.count)
        assertTrue(folds.isFolded(5, 20))
        assertArrayEquals(intArrayOf(5, 20), folds.startingAt(5))
        assertEquals(null, folds.startingAt(6))
        assertFalse(folds.unfold(6, 20))
        assertTrue(folds.unfold(5, 20))
        assertEquals(0, folds.count)
        assertFalse(folds.isFolded(5, 20))
    }

    @Test
    fun nestedFoldsStayRememberedInsideOuterAndOnlyOutermostAreShown() {
        val folds = Folds()
        assertTrue(folds.fold(10, 30))
        assertTrue(folds.fold(5, 40))
        assertEquals(2, folds.count)
        val outer = folds.outermost()
        assertEquals(1, outer.size)
        assertArrayEquals(intArrayOf(5, 40), outer[0])

        assertTrue(folds.hides(15))
        assertFalse(folds.hides(40))
        val hiding = folds.hiding(15)
        assertEquals(2, hiding.size)
        assertArrayEquals(intArrayOf(5, 40), hiding[0])
        assertArrayEquals(intArrayOf(10, 30), hiding[1])

        assertTrue(folds.unfold(5, 40))
        assertEquals(1, folds.count)
        assertEquals(1, folds.outermost().size)
        assertArrayEquals(intArrayOf(10, 30), folds.outermost()[0])
    }

    @Test
    fun crossingFoldsAreDroppedWhileNestedOrDisjointStay() {
        val folds = Folds()
        folds.fold(5, 20)
        folds.fold(30, 45)
        // Crosses (5, 20): starts inside and ends outside
        assertTrue(folds.fold(10, 25))
        assertFalse(folds.isFolded(5, 20))
        assertTrue(folds.isFolded(30, 45))
        // Crosses (30, 45): starts before and ends inside
        assertTrue(folds.fold(22, 35))
        assertFalse(folds.isFolded(30, 45))
    }

    @Test
    fun foldManyAddsNonCrossingRegionsAndReportsHowManyWereAdded() {
        val folds = Folds()
        folds.fold(4, 25)
        val regions = listOf(
            FoldRegion(0, 4, 25),   // duplicate
            FoldRegion(6, 10, 18),  // nested inside existing
            FoldRegion(8, 12, 30),  // crosses (4, 25)
            FoldRegion(30, 35, 50), // disjoint
            FoldRegion(40, 42, 42), // empty
        )
        assertEquals(2, folds.foldMany(regions))
        assertEquals(3, folds.count)
        assertTrue(folds.isFolded(4, 25))
        assertTrue(folds.isFolded(10, 18))
        assertTrue(folds.isFolded(35, 50))
        assertFalse(folds.isFolded(12, 30))
    }

    @Test
    fun editAtEndOfHeaderLineShiftsFoldsAndEditInsideOpensTouched() {
        val folds = Folds()
        // Header line ends with '\n' at index 9 -> fold starts at 10, ends at 25.
        folds.fold(10, 25)
        // Disjoint later fold
        folds.fold(30, 40)

        // Typing right before '\n' at 9 does not touch the fold.
        assertFalse(folds.touches(9, 9))
        // Deleting '\n' at 9 does touch it.
        assertTrue(folds.touches(9, 10))
        // Typing on the line right after the fold (offset 25) does not touch it.
        assertFalse(folds.touches(25, 25))
        // Typing at the start of hidden text (offset 10) touches it.
        assertTrue(folds.touches(10, 10))

        // Insert 3 chars at start of header line (offset 2) -> both shift by +3
        folds.edited(start = 2, removed = 0, added = 3)
        assertTrue(folds.isFolded(13, 28))
        assertTrue(folds.isFolded(33, 43))

        // Replace inside first fold (15..16) -> opens first fold and shifts second fold
        val opened = folds.openTouched(15, 16)
        assertEquals(1, opened.size)
        assertArrayEquals(intArrayOf(13, 28), opened[0])
        folds.edited(start = 15, removed = 1, added = 4)
        assertFalse(folds.isFolded(13, 28))
        assertTrue(folds.isFolded(36, 46))
    }

    @Test
    fun loadRestoresOnlyValidNewlineBoundedFoldsAndCopyIsIndependent() {
        val text = "fun a() {\n  x()\n  y()\n}\nfun b() {\n  z()\n}\n"
        val firstStart = text.indexOf('\n') + 1
        val firstEnd = text.indexOf("}\n") + 2
        val badStart = firstStart + 1 // not after '\n'

        val saved = Folds().apply {
            fold(firstStart, firstEnd)
        }
        val offsets = saved.offsets()
        val combined = intArrayOf(offsets[0], offsets[1], badStart, firstEnd, firstEnd, text.length + 5)

        val restored = Folds()
        restored.load(combined, text)
        assertEquals(1, restored.count)
        assertTrue(restored.isFolded(firstStart, firstEnd))

        val copy = restored.copy()
        restored.clear()
        assertEquals(0, restored.count)
        assertEquals(1, copy.count)
    }
}
