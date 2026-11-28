package com.whatcode.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IoPlanTest {

    @Test
    fun aSmallFileIsReadAtOnceAndAnUnknownOrBigOneInTheBackground() {
        assertFalse(IoPlan.readInBackground(0))
        assertFalse(IoPlan.readInBackground(IoPlan.BACKGROUND_FROM - 1))
        assertTrue(IoPlan.readInBackground(IoPlan.BACKGROUND_FROM))
        assertTrue(IoPlan.readInBackground(5_000_000_000L))
        assertTrue(IoPlan.readInBackground(-1))
    }

    @Test
    fun aTextIsWrittenInTheBackgroundFromTheSameSize() {
        assertFalse(IoPlan.writeInBackground(0))
        assertFalse(IoPlan.writeInBackground(999_999))
        assertTrue(IoPlan.writeInBackground(1_000_000))
    }

    @Test
    fun percentStaysBetweenZeroAndAHundred() {
        assertEquals(0, IoPlan.percent(0, 10))
        assertEquals(50, IoPlan.percent(5, 10))
        assertEquals(100, IoPlan.percent(10, 10))
        assertEquals(100, IoPlan.percent(99, 10)) // a file that grew while it was read
        assertEquals(0, IoPlan.percent(-5, 10))
        assertEquals(-1, IoPlan.percent(5, 0))
        assertEquals(-1, IoPlan.percent(5, -1))
        assertEquals(99, IoPlan.percent(Long.MAX_VALUE / 200 * 199, Long.MAX_VALUE / 200 * 200))
    }

    @Test
    fun theWordsNameTheTotalOnlyWhenItIsKnown() {
        assertTrue(IoPlan.describe(1024, 4096).contains(" of "))
        assertFalse(IoPlan.describe(1024, -1).contains(" of "))
        assertFalse(IoPlan.describe(-3, 0).contains(" of "))
    }
}
