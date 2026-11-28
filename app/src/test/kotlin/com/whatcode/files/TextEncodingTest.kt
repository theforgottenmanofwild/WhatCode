package com.whatcode.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TextEncodingTest {

    @Test
    fun detectsBomsValidUtf8AndInvalidUtf8Fallback() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hello".toByteArray(Charsets.UTF_8)
        val dec1 = TextEncoding.detectAndDecode(utf8Bom)
        assertEquals(TextEncoding.Spec.UTF_8_BOM, dec1.spec)
        assertEquals("hello", dec1.text)
        assertFalse(dec1.invalidUtf8)

        val utf16 = TextEncoding.encode("abc\n", TextEncoding.Spec.UTF_16LE)
        val dec2 = TextFile.readWithEncoding(ByteArrayInputStream(utf16))
        assertEquals(TextEncoding.Spec.UTF_16LE, dec2.spec)
        assertEquals("abc\n", dec2.text)

        val badUtf8 = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte()) // "café" in Windows-1252 / Latin-1
        val dec3 = TextEncoding.detectAndDecode(badUtf8)
        assertEquals(TextEncoding.Spec.WINDOWS_1252, dec3.spec)
        assertEquals("café", dec3.text)
        assertTrue(dec3.invalidUtf8)
    }

    @Test
    fun lossySaveGuardCatchesUnrepresentableCharacters() {
        assertTrue(TextEncoding.canEncodeExactly("Hello ASCII!", TextEncoding.Spec.US_ASCII))
        assertFalse(TextEncoding.canEncodeExactly("Hello 🚀", TextEncoding.Spec.US_ASCII))
        assertFalse(TextEncoding.canEncodeExactly("Hello café 🚀", TextEncoding.Spec.WINDOWS_1252))
        assertTrue(TextEncoding.canEncodeExactly("Hello 🚀", TextEncoding.Spec.UTF_8))

        val out = ByteArrayOutputStream()
        TextFile.writeWithEncoding(out, "hi", TextEncoding.Spec.UTF_8_BOM)
        assertEquals(TextEncoding.Spec.UTF_8_BOM, TextEncoding.detectBom(out.toByteArray()))
    }
}
