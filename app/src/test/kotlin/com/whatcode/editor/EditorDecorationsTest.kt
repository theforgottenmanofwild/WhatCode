package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorDecorationsTest {

    @Test
    fun findsHexColorLiteralsAndUrls() {
        val sample = "color: #4EA1FF; short: #f0a; link: https://example.com/docs?a=1."
        val colors = EditorDecorations.findHexColors(sample)
        assertEquals(2, colors.size)
        assertEquals(0xFF4EA1FF.toInt(), colors[0].argb)
        assertEquals(0xFFFF00AA.toInt(), colors[1].argb)

        val urls = EditorDecorations.findUrls(sample)
        assertEquals(1, urls.size)
        assertEquals("https://example.com/docs?a=1", urls[0].url)
        assertEquals("https://example.com/docs?a=1", EditorDecorations.urlAt(sample, urls[0].start + 3))
        assertNull(EditorDecorations.urlAt(sample, 0))
    }
}
