package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpHistoryTest {

    @Test
    fun backAndForwardWalkThroughTheJumps() {
        val h = JumpHistory()
        h.record(0)   // jump from 0 to 50
        h.record(50)  // jump from 50 to 90
        assertEquals(50, h.back(90))
        assertEquals(0, h.back(50))
        assertNull(h.back(0))
        assertEquals(50, h.forward(0))
        assertEquals(90, h.forward(50))
        assertNull(h.forward(90))
    }

    @Test
    fun aNewJumpDropsWhatForwardCouldGoTo() {
        val h = JumpHistory()
        h.record(0)
        h.back(10)
        assertTrue(h.canForward)
        h.record(0)
        assertFalse(h.canForward)
    }

    @Test
    fun aRunOfSearchHitsIsOneJump() {
        val h = JumpHistory()
        h.record(5, run = true)
        h.record(20, run = true)
        h.record(30, run = true)
        assertEquals(5, h.back(40))
        assertNull(h.back(5))
        // A different kind of jump ends the run.
        h.record(1)
        h.record(2, run = true)
        h.record(3)
        h.record(4, run = true)
        assertEquals(4, h.back(0))
        assertEquals(3, h.back(4))
        assertEquals(2, h.back(3))
    }

    @Test
    fun aRunEndsWhenToldTo() {
        val h = JumpHistory()
        h.record(5, run = true)
        h.endRun()
        h.record(9, run = true)
        assertEquals(9, h.back(30))
        assertEquals(5, h.back(9))
    }

    @Test
    fun aJumpFromTheSamePlaceIsNotKeptTwice() {
        val h = JumpHistory()
        h.record(7)
        h.record(7)
        assertEquals(7, h.back(20))
        assertNull(h.back(7))
    }

    @Test
    fun backSkipsAPlaceThatIsWhereTheCursorAlreadyIs() {
        val h = JumpHistory()
        h.record(3)
        h.record(8)
        assertEquals(3, h.back(8))
    }

    @Test
    fun placesFollowEdits() {
        val h = JumpHistory()
        h.record(10)
        h.record(30)
        h.edited(0, 0, 5)
        assertEquals(35, h.back(50))
        assertEquals(15, h.back(35))
        h.edited(12, 10, 0)
        assertEquals(25, h.forward(15))
        assertEquals(40, h.forward(25))
    }

    @Test
    fun onlyTheLastHundredAreKept() {
        val h = JumpHistory(limit = 3)
        for (i in 1..5) h.record(i)
        assertEquals(5, h.back(99))
        assertEquals(4, h.back(5))
        assertEquals(3, h.back(4))
        assertNull(h.back(3))
    }

    @Test
    fun emptyHistoryAndClampAreSafe() {
        val h = JumpHistory()
        assertNull(h.back(0))
        assertNull(h.forward(0))
        h.record(500)
        h.clamp(10)
        assertEquals(10, h.back(0))
    }

    @Test
    fun aCopyIsIndependent() {
        val h = JumpHistory()
        h.record(1)
        val c = h.copy()
        c.clear()
        assertTrue(h.canBack)
        assertFalse(c.canBack)
    }
}
