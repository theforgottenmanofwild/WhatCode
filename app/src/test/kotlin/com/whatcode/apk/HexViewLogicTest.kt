package com.whatcode.apk

import com.whatcode.bigfile.PagedFile
import com.whatcode.files.TempFolder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexViewLogicTest {

    private val temp = TempFolder()

    @After
    fun clean() {
        temp.delete()
    }

    @Test
    fun formatsHexAndAsciiRowWithPaddingAtEndOfFile() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x41, 0x7F)
        val row = HexViewLogic.formatRow(0x20L, bytes, bytes.size, offsetDigits = 8)
        assertEquals("00000020: 50 4B 03 04 00 41 7F                              |PK...A.|", row)
    }

    @Test
    fun parsesOffsetsAndHexBytePatterns() {
        assertEquals(0x1A4L, HexViewLogic.parseOffset("0x1a4", 1000L))
        assertEquals(1000L, HexViewLogic.parseOffset("ffff", 1000L))
        assertNull(HexViewLogic.parseOffset("xyz", 1000L))

        assertArrayEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04), HexViewLogic.parseHexBytes("50 4B 03 04"))
        assertNull(HexViewLogic.parseHexBytes("50 4"))
        assertNull(HexViewLogic.parseHexBytes("zz"))
    }

    @Test
    fun searchesBothPagedFilesAndMemoryByteSources() {
        val data = ByteArray(300) { (it % 251).toByte() }
        data[150] = 0xDE.toByte(); data[151] = 0xAD.toByte(); data[152] = 0xBE.toByte(); data[153] = 0xEF.toByte()
        data[260] = 0xDE.toByte(); data[261] = 0xAD.toByte(); data[262] = 0xBE.toByte(); data[263] = 0xEF.toByte()
        val f = temp.file("blob.bin", "")
        f.writeBytes(data)

        val memHits = HexViewLogic.findAll(HexViewLogic.MemoryByteSource(data), byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        assertEquals(listOf(150L, 260L), memHits)

        PagedFile(f, windowBytes = 64, preferMap = false).use { pf ->
            val pagedHits = HexViewLogic.findAll(
                HexViewLogic.PagedByteSource(pf),
                byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            )
            assertEquals(listOf(150L, 260L), pagedHits)
        }
    }
}
