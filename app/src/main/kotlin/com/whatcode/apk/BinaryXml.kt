package com.whatcode.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Pure Kotlin decoder for compiled Android binary XML (`AndroidManifest.xml`) and simple global string lookups from
 * compiled `resources.arsc`. Reads string pools (UTF-8 and UTF-16LE), resource ID maps, namespaces, nested tags and
 * typed attribute values, and skips unknown chunk types cleanly without failing.
 */
object BinaryXml {

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RES_TABLE = 0x0002
    private const val CHUNK_XML = 0x0003
    private const val CHUNK_XML_START_NS = 0x0100
    private const val CHUNK_XML_END_NS = 0x0101
    private const val CHUNK_XML_START_TAG = 0x0102
    private const val CHUNK_XML_END_TAG = 0x0103
    private const val CHUNK_XML_CDATA = 0x0104
    private const val CHUNK_XML_RES_MAP = 0x0180
    private const val CHUNK_RES_PACKAGE = 0x0200
    private const val CHUNK_RES_TYPE = 0x0201
    private const val CHUNK_RES_TYPE_SPEC = 0x0202

    /** True when [bytes] starts with the Android binary XML header (`0x0003`). */
    fun isBinaryXml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val type = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return type == CHUNK_XML
    }

    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_FIRST_INT = 0x10
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_INT_COLOR_ARGB8 = 0x1C
    private const val TYPE_INT_COLOR_RGB8 = 0x1D
    private const val TYPE_INT_COLOR_ARGB4 = 0x1E
    private const val TYPE_INT_COLOR_RGB4 = 0x1F
    private const val TYPE_LAST_INT = 0x1F

    /** Minimal table mapping `@0x...` resource IDs to simple string values read from `resources.arsc`. */
    class ArscStrings(private val map: Map<Int, String>) {
        fun resolve(resId: Int): String? = map[resId]

        companion object {
            val EMPTY = ArscStrings(emptyMap())

            /** Reads simple string entries from `resources.arsc`; returns [EMPTY] on malformed or unsupported tables. */
            fun parse(bytes: ByteArray): ArscStrings {
                if (bytes.size < 12) return EMPTY
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val type = buf.getShort(0).toInt() and 0xFFFF
                if (type != CHUNK_RES_TABLE) return EMPTY
                val headerSize = buf.getShort(2).toInt() and 0xFFFF
                val chunkSize = buf.getInt(4).coerceAtMost(bytes.size)
                var pos = headerSize.coerceAtLeast(12)
                var globalStrings: List<String> = emptyList()
                val resolved = HashMap<Int, String>()

                while (pos + 8 <= chunkSize) {
                    val cType = buf.getShort(pos).toInt() and 0xFFFF
                    val cHead = buf.getShort(pos + 2).toInt() and 0xFFFF
                    val cSize = buf.getInt(pos + 4)
                    if (cHead < 8 || cSize < cHead || pos + cSize > chunkSize) break
                    when (cType) {
                        CHUNK_STRING_POOL -> globalStrings = parseStringPool(bytes, pos, cSize)
                        CHUNK_RES_PACKAGE -> parsePackageStrings(bytes, pos, cSize, globalStrings, resolved)
                    }
                    pos += cSize
                }
                return ArscStrings(resolved)
            }

            private fun parsePackageStrings(
                bytes: ByteArray,
                pkgStart: Int,
                pkgSize: Int,
                globalStrings: List<String>,
                out: MutableMap<Int, String>,
            ) {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val pkgHeader = buf.getShort(pkgStart + 2).toInt() and 0xFFFF
                if (pkgStart + pkgHeader > bytes.size || pkgHeader < 12) return
                val pkgId = buf.getInt(pkgStart + 8)
                var pos = pkgStart + pkgHeader
                val pkgEnd = pkgStart + pkgSize
                while (pos + 8 <= pkgEnd) {
                    val cType = buf.getShort(pos).toInt() and 0xFFFF
                    val cHead = buf.getShort(pos + 2).toInt() and 0xFFFF
                    val cSize = buf.getInt(pos + 4)
                    if (cHead < 8 || cSize < cHead || pos + cSize > pkgEnd) break
                    when (cType) {
                        CHUNK_RES_TYPE -> parseTypeEntries(buf, pos, cHead, cSize, pkgId, globalStrings, out)
                        CHUNK_RES_TYPE_SPEC -> Unit
                        else -> Unit
                    }
                    pos += cSize
                }
            }

            private fun parseTypeEntries(
                buf: ByteBuffer,
                pos: Int,
                headSize: Int,
                chunkSize: Int,
                pkgId: Int,
                globalStrings: List<String>,
                out: MutableMap<Int, String>,
            ) {
                if (headSize < 20) return
                val typeId = buf.get(pos + 8).toInt() and 0xFF
                val entryCount = buf.getInt(pos + 12)
                val entriesStart = buf.getInt(pos + 16)
                val offsetsPos = pos + headSize
                val baseEntries = pos + entriesStart
                if (entryCount <= 0 || offsetsPos + entryCount * 4 > pos + chunkSize) return
                for (i in 0 until entryCount) {
                    val rel = buf.getInt(offsetsPos + i * 4)
                    if (rel == -1) continue
                    val ePos = baseEntries + rel
                    if (ePos + 16 > pos + chunkSize) continue
                    val flags = buf.getShort(ePos + 2).toInt() and 0xFFFF
                    val isComplex = (flags and 0x0001) != 0
                    if (isComplex) continue
                    val valType = buf.get(ePos + 8 + 3).toInt() and 0xFF
                    val valData = buf.getInt(ePos + 8 + 4)
                    if (valType == TYPE_STRING && valData in globalStrings.indices) {
                        val resId = (pkgId shl 24) or (typeId shl 16) or i
                        out.putIfAbsent(resId, globalStrings[valData])
                    }
                }
            }
        }
    }

    /** Decodes binary XML [bytes] into indented XML text, resolving `@0x...` string refs against [arsc] when available. */
    fun decode(bytes: ByteArray, arsc: ArscStrings = ArscStrings.EMPTY): String {
        if (bytes.size < 8) throw IllegalArgumentException("Not a binary XML file")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeader = buf.getShort(2).toInt() and 0xFFFF
        val rootSize = buf.getInt(4).coerceAtMost(bytes.size)
        if (rootType != CHUNK_XML || rootHeader < 8) throw IllegalArgumentException("Invalid binary XML header")

        var strings: List<String> = emptyList()
        var resourceIds = IntArray(0)
        val nsPrefixes = LinkedHashMap<String, String>()
        val pendingNs = LinkedHashMap<String, String>()
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var pos = rootHeader
        var depth = 0
        val emptyTags = ArrayDeque<String>()

        fun str(idx: Int): String = if (idx in strings.indices) strings[idx] else ""

        fun nsPrefixFor(uriIdx: Int): String? {
            if (uriIdx < 0) return null
            val uri = str(uriIdx)
            if (uri.isEmpty()) return null
            return nsPrefixes[uri] ?: "ns"
        }

        while (pos + 8 <= rootSize) {
            val type = buf.getShort(pos).toInt() and 0xFFFF
            val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (headerSize < 8 || chunkSize < headerSize || pos + chunkSize > rootSize) break

            when (type) {
                CHUNK_STRING_POOL -> strings = parseStringPool(bytes, pos, chunkSize)
                CHUNK_XML_RES_MAP -> {
                    val count = (chunkSize - headerSize) / 4
                    resourceIds = IntArray(count) { i -> buf.getInt(pos + headerSize + i * 4) }
                }
                CHUNK_XML_START_NS -> if (chunkSize >= headerSize + 8) {
                    val pfx = str(buf.getInt(pos + headerSize))
                    val uri = str(buf.getInt(pos + headerSize + 4))
                    if (uri.isNotEmpty() && pfx.isNotEmpty()) {
                        nsPrefixes[uri] = pfx
                        pendingNs[pfx] = uri
                    }
                }
                CHUNK_XML_END_NS -> Unit
                CHUNK_XML_START_TAG -> if (pos + headerSize + 20 <= pos + chunkSize) {
                    val base = pos + headerSize
                    val nsIdx = buf.getInt(base)
                    val nameIdx = buf.getInt(base + 4)
                    val attrStart = buf.getShort(base + 8).toInt() and 0xFFFF
                    val attrSize = buf.getShort(base + 10).toInt() and 0xFFFF
                    val attrCount = buf.getShort(base + 12).toInt() and 0xFFFF

                    val tagName = qualified(nsPrefixFor(nsIdx), str(nameIdx).ifEmpty { "tag" })
                    indent(out, depth)
                    out.append('<').append(tagName)
                    if (pendingNs.isNotEmpty()) {
                        for ((pfx, uri) in pendingNs) {
                            out.append(" xmlns:").append(pfx).append("=\"").append(escape(uri)).append('"')
                        }
                        pendingNs.clear()
                    }
                    val attrsOffset = base + attrStart
                    for (i in 0 until attrCount) {
                        val aPos = attrsOffset + i * attrSize
                        if (aPos + 20 > pos + chunkSize) break
                        val aNsIdx = buf.getInt(aPos)
                        val aNameIdx = buf.getInt(aPos + 4)
                        val aRawIdx = buf.getInt(aPos + 8)
                        val aType = buf.get(aPos + 15).toInt() and 0xFF
                        val aData = buf.getInt(aPos + 16)

                        val rawName = str(aNameIdx).ifEmpty {
                            val resId = if (aNameIdx in resourceIds.indices) resourceIds[aNameIdx] else 0
                            knownAndroidAttr(resId) ?: "attr_$aNameIdx"
                        }
                        val aName = qualified(nsPrefixFor(aNsIdx), rawName)
                        val aVal = formatValue(aType, aData, aRawIdx, strings, arsc)
                        out.append(' ').append(aName).append("=\"").append(escape(aVal)).append('"')
                    }
                    out.append(">\n")
                    emptyTags.addLast(tagName)
                    depth++
                }
                CHUNK_XML_END_TAG -> if (pos + headerSize + 8 <= pos + chunkSize) {
                    val base = pos + headerSize
                    val nsIdx = buf.getInt(base)
                    val nameIdx = buf.getInt(base + 4)
                    val tagName = qualified(nsPrefixFor(nsIdx), str(nameIdx).ifEmpty { emptyTags.lastOrNull() ?: "tag" })
                    if (emptyTags.isNotEmpty()) emptyTags.removeLast()
                    depth = maxOf(0, depth - 1)
                    indent(out, depth)
                    out.append("</").append(tagName).append(">\n")
                }
                CHUNK_XML_CDATA -> if (pos + headerSize + 4 <= pos + chunkSize) {
                    val cdata = str(buf.getInt(pos + headerSize))
                    if (cdata.isNotEmpty()) {
                        indent(out, depth)
                        out.append(escape(cdata)).append('\n')
                    }
                }
                else -> Unit // Skip unknown chunk types safely using header/chunkSize.
            }
            pos += chunkSize
        }
        return out.toString()
    }

    internal fun parseStringPool(bytes: ByteArray, pos: Int, chunkSize: Int): List<String> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (chunkSize < 28) return emptyList()
        val stringCount = buf.getInt(pos + 8)
        val flags = buf.getInt(pos + 16)
        val stringsStart = buf.getInt(pos + 20)
        val utf8 = (flags and (1 shl 8)) != 0
        val headSize = buf.getShort(pos + 2).toInt() and 0xFFFF
        val offsetsStart = pos + headSize
        val poolStart = pos + stringsStart
        if (stringCount <= 0 || offsetsStart + stringCount * 4 > pos + chunkSize || poolStart > pos + chunkSize) {
            return emptyList()
        }
        val out = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val rel = buf.getInt(offsetsStart + i * 4)
            val off = poolStart + rel
            if (off < poolStart || off >= pos + chunkSize) {
                out.add("")
                continue
            }
            out.add(if (utf8) readUtf8(bytes, off, pos + chunkSize) else readUtf16(bytes, off, pos + chunkSize))
        }
        return out
    }

    private fun readUtf8(bytes: ByteArray, start: Int, limit: Int): String {
        var p = start
        // 1) character count (varint 1 or 2 bytes)
        if (p >= limit) return ""
        val u1 = bytes[p++].toInt() and 0xFF
        if ((u1 and 0x80) != 0 && p < limit) p++
        // 2) byte length (varint 1 or 2 bytes)
        if (p >= limit) return ""
        var byteLen = bytes[p++].toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            if (p >= limit) return ""
            byteLen = ((byteLen and 0x7F) shl 8) or (bytes[p++].toInt() and 0xFF)
        }
        val safeLen = byteLen.coerceIn(0, limit - p)
        return String(bytes, p, safeLen, Charsets.UTF_8)
    }

    private fun readUtf16(bytes: ByteArray, start: Int, limit: Int): String {
        if (start + 2 > limit) return ""
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var p = start
        var charLen = buf.getShort(p).toInt() and 0xFFFF
        p += 2
        if ((charLen and 0x8000) != 0) {
            if (p + 2 > limit) return ""
            charLen = ((charLen and 0x7FFF) shl 16) or (buf.getShort(p).toInt() and 0xFFFF)
            p += 2
        }
        val safeBytes = (charLen * 2).coerceIn(0, limit - p)
        return String(bytes, p, safeBytes, Charsets.UTF_16LE)
    }

    private fun formatValue(
        type: Int,
        data: Int,
        rawIdx: Int,
        strings: List<String>,
        arsc: ArscStrings,
    ): String {
        if (rawIdx in strings.indices) return strings[rawIdx]
        return when (type) {
            TYPE_STRING -> if (data in strings.indices) strings[data] else ""
            TYPE_REFERENCE -> arsc.resolve(data) ?: String.format(Locale.US, "@0x%08x", data)
            TYPE_ATTRIBUTE -> String.format(Locale.US, "?0x%08x", data)
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
            TYPE_DIMENSION -> complexToFloat(data).toString() + DIMENSION_UNITS[data and 0x0F]
            TYPE_FRACTION -> (complexToFloat(data) * 100f).toString() + FRACTION_UNITS[data and 0x0F]
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_INT_HEX -> String.format(Locale.US, "0x%x", data)
            TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_ARGB4 -> String.format(Locale.US, "#%08x", data)
            TYPE_INT_COLOR_RGB8, TYPE_INT_COLOR_RGB4 -> String.format(Locale.US, "#%06x", data and 0xFFFFFF)
            TYPE_NULL -> ""
            in TYPE_FIRST_INT..TYPE_LAST_INT -> data.toString()
            else -> String.format(Locale.US, "0x%x", data)
        }
    }

    private fun complexToFloat(complex: Int): Float {
        val mantissa = (complex and 0xFFFFFF00.toInt()) shr 8
        val radix = (complex shr 4) and 0x03
        return mantissa * RADIX_MULTS[radix]
    }

    private fun qualified(prefix: String?, name: String): String =
        if (prefix.isNullOrEmpty()) name else "$prefix:$name"

    private fun indent(sb: StringBuilder, depth: Int) {
        repeat(depth) { sb.append("  ") }
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun knownAndroidAttr(resId: Int): String? = when (resId) {
        0x01010001 -> "label"
        0x01010002 -> "icon"
        0x01010003 -> "name"
        0x01010006 -> "permission"
        0x01010010 -> "exported"
        0x01010011 -> "process"
        0x0101020c -> "minSdkVersion"
        0x0101021b -> "versionCode"
        0x0101021c -> "versionName"
        0x01010270 -> "targetSdkVersion"
        0x01010280 -> "allowBackup"
        else -> null
    }

    private val RADIX_MULTS = floatArrayOf(1f, 1f / (1 shl 7), 1f / (1 shl 15), 1f / (1 shl 23))
    private val DIMENSION_UNITS = arrayOf("px", "dp", "sp", "pt", "in", "mm", "px", "px")
    private val FRACTION_UNITS = arrayOf("%", "%p", "%", "%p", "%", "%p", "%", "%p")
}
