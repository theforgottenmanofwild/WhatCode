package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {

    @Test
    fun detectsIdenticalAndChangedLines() {
        assertTrue(LineDiff.diff("a\nb\n", "a\nb\n").identical)

        val res = LineDiff.diff("one\ntwo\nthree\n", "one\ntwo-edited\nthree\nfour\n")
        assertFalse(res.identical)
        assertEquals(2, res.addedCount)
        assertEquals(1, res.removedCount)
        val unified = LineDiff.formatUnified(res, "disk.txt", "buffer.txt")
        assertTrue(unified.contains("- two"))
        assertTrue(unified.contains("+ two-edited"))
        assertTrue(unified.contains("+ four"))
    }
}
