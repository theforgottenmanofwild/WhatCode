package com.whatcode.apk

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds synthetic binary XML (`AndroidManifest.xml`) and `resources.arsc` chunks in code so no binary test fixtures
 * need to live in the repository. Covers UTF-8/UTF-16 string pools, namespaces, nested tags, `@0x...` & arsc
 * resolution, unknown chunks and truncated inputs.
 */
class BinaryXmlTest {

    private class BinWriter {
        val out = ByteArrayOutputStream()

        fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }

        fun u32(v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF)
            out.write((v ushr 24) and 0xFF)
        }

        fun bytes(b: ByteArray) = out.write(b)

        fun pad4() {
            while (out.size() % 4 != 0) out.write(0)
        }

        fun toByteArray(): ByteArray = out.toByteArray()
    }

    private fun chunk(type: Int, headerExtra: ByteArray, body: ByteArray): ByteArray {
        val w = BinWriter()
        w.u16(type)
        w.u16(8 + headerExtra.size)
        w.u32(8 + headerExtra.size + body.size)
        w.bytes(headerExtra)
        w.bytes(body)
        return w.toByteArray()
    }

    private fun stringPool(strings: List<String>, utf8: Boolean): ByteArray {
        val data = BinWriter()
        val offsets = IntArray(strings.size)
        for ((i, s) in strings.withIndex()) {
            offsets[i] = data.out.size()
            if (utf8) {
                val raw = s.toByteArray(Charsets.UTF_8)
                data.out.write(s.length and 0x7F)
                data.out.write(raw.size and 0x7F)
                data.bytes(raw)
                data.out.write(0)
            } else {
                data.u16(s.length)
                data.bytes(s.toByteArray(Charsets.UTF_16LE))
                data.u16(0)
            }
        }
        data.pad4()
        val dataBytes = data.toByteArray()

        val headExtra = ByteArray(20)
        val hb = ByteBuffer.wrap(headExtra).order(ByteOrder.LITTLE_ENDIAN)
        hb.putInt(0, strings.size)
        hb.putInt(4, 0) // styleCount
        hb.putInt(8, if (utf8) (1 shl 8) else 0)
        val stringsStart = 28 + strings.size * 4
        hb.putInt(12, stringsStart)
        hb.putInt(16, 0)

        val body = BinWriter()
        for (o in offsets) body.u32(o)
        body.bytes(dataBytes)
        return chunk(0x0001, headExtra, body.toByteArray())
    }

    @Test
    fun decodesManifestWithNamespacesNestedTagsUnknownChunkAndArscStrings() {
        val poolStrings = listOf(
            "http://schemas.android.com/apk/res/android", // 0
            "android", // 1
            "manifest", // 2
            "package", // 3
            "com.example.app", // 4
            "application", // 5
            "label", // 6
            "debuggable", // 7
            "activity", // 8
            "name", // 9
            ".MainActivity", // 10
        )
        val resMapBody = BinWriter().apply {
            for (i in poolStrings.indices) u32(0)
        }.toByteArray()
        val startNs = chunk(0x0100, ByteArray(8), BinWriter().apply { u32(1); u32(0) }.toByteArray())

        fun tagStart(ns: Int, name: Int, attrs: List<IntArray>): ByteArray {
            val body = BinWriter()
            body.u32(ns); body.u32(name)
            body.u16(20); body.u16(20); body.u16(attrs.size)
            body.u16(0); body.u16(0); body.u16(0)
            for (a in attrs) {
                body.u32(a[0]); body.u32(a[1]); body.u32(a[2])
                body.u32((a[3] shl 24) or 0x0800)
                body.u32(a[4])
            }
            return chunk(0x0102, ByteArray(8), body.toByteArray())
        }

        fun tagEnd(ns: Int, name: Int): ByteArray =
            chunk(0x0103, ByteArray(8), BinWriter().apply { u32(ns); u32(name) }.toByteArray())

        val unknownChunk = chunk(0x0999, ByteArray(0), ByteArray(8))

        val inner = BinWriter().apply {
            bytes(stringPool(poolStrings, utf8 = true))
            bytes(chunk(0x0180, ByteArray(0), resMapBody))
            bytes(startNs)
            bytes(tagStart(-1, 2, listOf(intArrayOf(-1, 3, 4, 0x03, 4))))
            bytes(unknownChunk)
            bytes(tagStart(-1, 5, listOf(
                intArrayOf(0, 6, -1, 0x01, 0x7f010000),
                intArrayOf(0, 7, -1, 0x12, 1),
            )))
            bytes(tagStart(-1, 8, listOf(intArrayOf(0, 9, 10, 0x03, 10))))
            bytes(tagEnd(-1, 8))
            bytes(tagEnd(-1, 5))
            bytes(tagEnd(-1, 2))
        }.toByteArray()
        val xmlBytes = chunk(0x0003, ByteArray(0), inner)

        // Check without ARSC -> shows @0x7f010000
        val plain = BinaryXml.decode(xmlBytes)
        assertTrue(plain, plain.contains("xmlns:android=\"http://schemas.android.com/apk/res/android\""))
        assertTrue(plain, plain.contains("package=\"com.example.app\""))
        assertTrue(plain, plain.contains("android:label=\"@0x7f010000\""))
        assertTrue(plain, plain.contains("android:debuggable=\"true\""))
        assertTrue(plain, plain.contains("<activity android:name=\".MainActivity\">"))

        // Check with ARSC string resolution -> resolves 0x7f010000 to "My Test App"
        val arsc = buildSimpleArsc(0x7F, 0x01, listOf("My Test App"))
        val withArsc = BinaryXml.decode(xmlBytes, BinaryXml.ArscStrings.parse(arsc))
        assertTrue(withArsc, withArsc.contains("android:label=\"My Test App\""))
    }

    @Test
    fun rejectsInvalidHeaderAndSurvivesTruncatedChunks() {
        assertThrows(IllegalArgumentException::class.java) { BinaryXml.decode(byteArrayOf(1, 2, 3)) }
        val sp = stringPool(listOf("manifest"), utf8 = false)
        // Truncate midway after valid header
        val truncated = chunk(0x0003, ByteArray(0), sp.copyOf(sp.size / 2))
        val out = BinaryXml.decode(truncated)
        assertTrue(out.startsWith("<?xml"))
    }

    private fun buildSimpleArsc(pkgId: Int, typeId: Int, values: List<String>): ByteArray {
        val pool = stringPool(values, utf8 = false)
        // RES_TABLE_TYPE header (20 bytes before offsets) + offsets (4*N) + entries (16*N)
        val typeHeadExtra = ByteArray(12).also { b ->
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            b[0] = typeId.toByte()
            bb.putInt(4, values.size)
            bb.putInt(8, 20 + values.size * 4)
        }
        val typeBody = BinWriter()
        for (i in values.indices) typeBody.u32(i * 16)
        for (i in values.indices) {
            typeBody.u16(8); typeBody.u16(0); typeBody.u32(0) // ResTable_entry
            typeBody.u16(8); typeBody.out.write(0); typeBody.out.write(0x03) // Res_value TYPE_STRING
            typeBody.u32(i)
        }
        val typeChunk = chunk(0x0201, typeHeadExtra, typeBody.toByteArray())
        val pkgHeadExtra = ByteArray(280).also { b ->
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(0, pkgId)
        }
        val pkgChunk = chunk(0x0200, pkgHeadExtra, typeChunk)
        val tableHeadExtra = ByteArray(4).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 1) }
        return chunk(0x0002, tableHeadExtra, BinWriter().apply { bytes(pool); bytes(pkgChunk) }.toByteArray())
    }
}
