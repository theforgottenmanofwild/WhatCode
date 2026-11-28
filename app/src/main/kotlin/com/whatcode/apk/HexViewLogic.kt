package com.whatcode.apk

import com.whatcode.bigfile.PagedFile
import java.util.Locale

/**
 * Pure Kotlin logic for the read-only hex viewer (C8): window byte sources, offset/hex/ASCII row formatting, offset
 * parsing, and streaming byte/text search shared with [PagedFile] from Part D.
 */
object HexViewLogic {

    interface ByteSource {
        val length: Long
        fun read(offset: Long, len: Int, dst: ByteArray): Int
    }

    class PagedByteSource(private val paged: PagedFile) : ByteSource {
        override val length: Long get() = paged.length
        override fun read(offset: Long, len: Int, dst: ByteArray): Int = paged.readBytes(offset, len, dst)
    }

    class MemoryByteSource(private val data: ByteArray) : ByteSource {
        override val length: Long get() = data.size.toLong()
        override fun read(offset: Long, len: Int, dst: ByteArray): Int {
            if (offset < 0L || offset >= data.size || len <= 0) return 0
            val count = minOf(len.toLong(), data.size.toLong() - offset).toInt()
            System.arraycopy(data, offset.toInt(), dst, 0, count)
            return count
        }
    }

    const val BYTES_PER_ROW = 16

    /** Formats one hex row starting at [offset] for [count] bytes (`0..16`) of [bytes]. */
    fun formatRow(offset: Long, bytes: ByteArray, count: Int, offsetDigits: Int = 8): String {
        val n = count.coerceIn(0, BYTES_PER_ROW)
        val sb = StringBuilder(offsetDigits + 2 + 48 + 3 + BYTES_PER_ROW + 2)
        val offHex = java.lang.Long.toHexString(offset).uppercase(Locale.US)
        for (i in offHex.length until offsetDigits) sb.append('0')
        sb.append(offHex).append(": ")
        for (i in 0 until BYTES_PER_ROW) {
            if (i == 8) sb.append(' ')
            if (i < n) {
                val b = bytes[i].toInt() and 0xFF
                sb.append(HEX[b ushr 4]).append(HEX[b and 0x0F]).append(' ')
            } else {
                sb.append("   ")
            }
        }
        sb.append(" |")
        for (i in 0 until n) {
            val c = bytes[i].toInt() and 0xFF
            sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
        }
        sb.append('|')
        return sb.toString()
    }

    fun offsetDigitsFor(length: Long): Int {
        var digits = 8
        var v = length ushr 32
        while (v > 0L) {
            digits += 2
            v = v ushr 8
        }
        return digits
    }

    /** Parses a hex (`0x1a0`, `1A0`) or decimal (`dec:416`) offset into `0..maxOffset`, or null if invalid. */
    fun parseOffset(raw: String, maxOffset: Long): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val value = when {
            t.startsWith("0x", ignoreCase = true) -> t.substring(2).toLongOrNull(16)
            t.startsWith("#") -> t.substring(1).toLongOrNull(16)
            else -> t.toLongOrNull(16)
        } ?: return null
        return value.coerceIn(0L, maxOf(0L, maxOffset))
    }

    /** Parses a hex byte string like `"50 4B 03 04"` or `"504b0304"`, or returns null if malformed. */
    fun parseHexBytes(raw: String): ByteArray? {
        val clean = buildString(raw.length) {
            for (c in raw) if (!c.isWhitespace()) append(c)
        }
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Streaming cancellable byte-pattern search across [source], starting at [fromOffset], returning match offsets. */
    fun findAll(
        source: ByteSource,
        needle: ByteArray,
        cap: Int = DEFAULT_SEARCH_CAP,
        cancelled: () -> Boolean = { false },
        onProgress: (foundCount: Int, scanned: Long) -> Unit = { _, _ -> },
    ): List<Long> {
        if (needle.isEmpty() || source.length < needle.size) return emptyList()
        val hits = ArrayList<Long>()
        val overlap = needle.size - 1
        val chunkSize = 128 * 1024
        val buf = ByteArray(chunkSize + overlap)
        var pos = 0L
        val total = source.length
        while (pos < total && hits.size < cap && !cancelled()) {
            val toRead = minOf(buf.size.toLong(), total - pos).toInt()
            val n = source.read(pos, toRead, buf)
            if (n < needle.size) break
            val searchLimit = if (pos + n < total) minOf(n - needle.size + 1, chunkSize) else n - needle.size + 1
            var i = 0
            while (i < searchLimit && hits.size < cap) {
                if (matchesAt(buf, i, needle)) {
                    hits.add(pos + i)
                    i += needle.size
                } else {
                    i++
                }
            }
            pos += minOf(n, chunkSize)
            onProgress(hits.size, pos)
        }
        return hits
    }

    private fun matchesAt(buf: ByteArray, off: Int, needle: ByteArray): Boolean {
        for (k in needle.indices) {
            if (buf[off + k] != needle[k]) return false
        }
        return true
    }

    const val DEFAULT_SEARCH_CAP = 5_000
    private val HEX = "0123456789ABCDEF".toCharArray()
}
