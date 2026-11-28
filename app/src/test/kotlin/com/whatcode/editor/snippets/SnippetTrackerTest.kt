package com.whatcode.editor.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetTrackerTest {

    private fun tracker() = SnippetTracker(intArrayOf(4, 7, 12, 12), finalAt = 20)

    private fun places(t: SnippetTracker) = listOf(t.start(0), t.end(0), t.start(1), t.end(1), t.finalAt)

    @Test
    fun anEditBeforeEverythingMovesEverything() {
        val t = tracker()
        t.changed(0, 0, 3)
        assertEquals(listOf(7, 10, 15, 15, 23), places(t))
        t.changed(0, 2, 0)
        assertEquals(listOf(5, 8, 13, 13, 21), places(t))
    }

    @Test
    fun anEditAfterEverythingMovesNothing() {
        val t = tracker()
        t.changed(25, 0, 4)
        t.changed(21, 1, 0)
        assertEquals(listOf(4, 7, 12, 12, 20), places(t))
    }

    @Test
    fun typingInAStopMakesItLonger() {
        val t = tracker()
        t.changed(5, 0, 2)
        assertEquals(listOf(4, 9, 14, 14, 22), places(t))
    }

    @Test
    fun typingAtTheEndOfAStopMakesItLonger() {
        val t = tracker()
        t.changed(7, 0, 1)
        assertEquals(listOf(4, 8, 13, 13, 21), places(t))
    }

    @Test
    fun typingAtTheStartOfAStopGoesInFrontOfIt() {
        val t = tracker()
        t.changed(4, 0, 1)
        assertEquals(listOf(4, 8, 13, 13, 21), places(t))
    }

    @Test
    fun typingOverAWholeSelectedStopLeavesTheTypedTextAsTheStop() {
        val t = tracker()
        t.changed(4, 3, 1)
        assertEquals(listOf(4, 5, 10, 10, 18), places(t))
    }

    @Test
    fun deletingAcrossAStopPullsItsEdgesTogether() {
        val t = tracker()
        t.changed(5, 10, 0)
        // The first stop started before what was deleted, so only its end moves.
        assertEquals(4, t.start(0))
        assertEquals(5, t.end(0))
        assertEquals(5, t.start(1))
        assertEquals(5, t.end(1))
        assertEquals(10, t.finalAt)
    }

    @Test
    fun aStopNeverEndsBeforeItStarts() {
        val t = SnippetTracker(intArrayOf(5, 5), 9)
        t.changed(3, 4, 1)
        assertTrue(t.end(0) >= t.start(0))
    }

    @Test
    fun theStretchOfTheSnippetIncludesItsEnds() {
        val t = tracker()
        assertFalse(t.contains(3))
        assertTrue(t.contains(4))
        assertTrue(t.contains(13))
        assertTrue(t.contains(20))
        assertFalse(t.contains(21))
    }

    @Test
    fun theStretchFollowsTheEdits() {
        val t = tracker()
        t.changed(0, 0, 10)
        assertTrue(t.contains(14))
        assertFalse(t.contains(13))
        assertTrue(t.contains(30))
        assertFalse(t.contains(31))
    }
}
