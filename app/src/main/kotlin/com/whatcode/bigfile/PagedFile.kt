package com.whatcode.bigfile

import com.whatcode.files.TextFile
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction

/**
 * Windowed read-only file reader backed by [FileChannel.map] chunks (with automatic [FileChannel.read] fallback when
 * mapping is unavailable). Never reads the entire file into heap memory, and re-checks [length] so growing files such
 * as active logs can be followed while open. Shared by the large-file viewer and the hex viewer.
 */
class PagedFile(
    val file: File,
    private val windowBytes: Int = DEFAULT_WINDOW_BYTES,
    private val preferMap: Boolean = true,
) : Closeable {

    private val raf = RandomAccessFile(file, "r")
    private val channel: FileChannel = raf.channel
    private val lock = Any()

    // Small bounded cache of mapped/loaded windows (at most MAX_WINDOWS entries).
    private val windows = LinkedHashMap<Long, Window>(4, 0.75f, true)

    private class Window(val start: Long, val size: Int, val buf: ByteBuffer)

    /** Current byte size of the file on disk. Increases automatically if another process appends to it. */
    val length: Long
        get() = try {
            channel.size()
        } catch (_: Exception) {
            0L
        }

    /** True when the start of the file passes [TextFile.looksLikeText] (contains no NUL bytes). */
    fun isText(): Boolean {
        val sample = ByteArray(8000)
        val n = readBytes(0L, sample.size, sample)
        return TextFile.looksLikeText(sample, n)
    }

    /**
     * Reads up to [len] bytes starting at [offset] into [dst] at [dstOff], across window boundaries if needed.
     * Returns the number of bytes actually read (0 at or past EOF).
     */
    fun readBytes(offset: Long, len: Int, dst: ByteArray, dstOff: Int = 0): Int {
        if (len <= 0 || offset < 0L) return 0
        val fileLen = length
        if (offset >= fileLen) return 0
        val want = minOf(len.toLong(), fileLen - offset).toInt()
        var done = 0
        while (done < want) {
            val pos = offset + done
            val chunk = readWindowChunk(pos, want - done, fileLen, dst, dstOff + done)
            if (chunk <= 0) break
            done += chunk
        }
        return done
    }

    /** Reads one unsigned byte (0..255) at [offset], or -1 at EOF. */
    fun readByte(offset: Long): Int {
        val one = ByteArray(1)
        return if (readBytes(offset, 1, one) == 1) one[0].toInt() and 0xFF else -1
    }

    private fun readWindowChunk(pos: Long, maxCount: Int, fileLen: Long, dst: ByteArray, dstOff: Int): Int {
        val winStart = (pos / windowBytes) * windowBytes
        val targetEnd = minOf(winStart + windowBytes, fileLen)
        val neededSize = (targetEnd - winStart).toInt()
        if (neededSize <= 0) return 0

        synchronized(lock) {
            var w = windows[winStart]
            if (w == null || w.size < neededSize) {
                w = loadWindow(winStart, neededSize)
                if (w != null) {
                    windows[winStart] = w
                    while (windows.size > MAX_WINDOWS) {
                        val oldest = windows.keys.first()
                        windows.remove(oldest)
                    }
                }
            }
            if (w != null) {
                val localOff = (pos - winStart).toInt()
                val avail = w.size - localOff
                if (avail > 0) {
                    val take = minOf(maxCount, avail)
                    val dup = w.buf.duplicate()
                    dup.position(localOff)
                    dup.get(dst, dstOff, take)
                    return take
                }
            }
        }
        return readDirect(pos, maxCount, dst, dstOff)
    }

    private fun loadWindow(start: Long, size: Int): Window? {
        if (preferMap) {
            try {
                val mapped: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, start, size.toLong())
                return Window(start, size, mapped)
            } catch (_: Throwable) {
                // Fall back to heap-buffered direct channel reads when mmap fails or exceeds virtual address space.
            }
        }
        return null
    }

    private fun readDirect(pos: Long, maxCount: Int, dst: ByteArray, dstOff: Int): Int {
        val bb = ByteBuffer.wrap(dst, dstOff, maxCount)
        var total = 0
        while (total < maxCount) {
            val n = channel.read(bb, pos + total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    override fun close() {
        synchronized(lock) {
            windows.clear()
        }
        runCatching { channel.close() }
        runCatching { raf.close() }
    }

    companion object {
        const val DEFAULT_WINDOW_BYTES = 4 * 1024 * 1024
        private const val MAX_WINDOWS = 3
    }
}

/**
 * Safe UTF-8 decoding across arbitrary byte windows so multi-byte sequences split across window boundaries are
 * adjusted rather than cut in half. Invalid UTF-8 sequences decode with the standard replacement character.
 */
object Utf8Window {

    data class Slice(val startOffset: Long, val text: String, val endOffset: Long)

    /**
     * Reads bytes around `[startByte, endByte)` from [file], backing up at most 3 bytes if [startByte] lands on a
     * UTF-8 continuation byte and finishing any multi-byte character started before [endByte].
     */
    fun decodeRange(file: PagedFile, startByte: Long, endByte: Long): Slice {
        val fileLen = file.length
        val s0 = startByte.coerceIn(0L, fileLen)
        val e0 = endByte.coerceIn(s0, fileLen)
        if (s0 == e0) return Slice(s0, "", e0)

        var realStart = s0
        var back = 0
        while (realStart > 0L && back < 3) {
            val b = file.readByte(realStart)
            if (b < 0 || (b and 0xC0) != 0x80) break
            realStart--
            back++
        }

        // Probe up to 3 bytes beyond e0 if e0 stopped inside a multi-byte sequence.
        val probeLen = ((e0 - realStart).coerceAtMost(Int.MAX_VALUE.toLong() - 8)).toInt()
        val extra = minOf(3L, fileLen - e0).toInt()
        val buf = ByteArray(probeLen + extra)
        val read = file.readBytes(realStart, buf.size, buf)
        val targetEndInBuf = minOf(read, (e0 - realStart).toInt())
        val adjustedEnd = adjustEnd(buf, targetEndInBuf, read)
        val text = decodeBytes(buf, 0, adjustedEnd)
        return Slice(realStart, text, realStart + adjustedEnd)
    }

    /** Decodes `[0, len)` in [bytes] replacing any invalid sequences with U+FFFD. */
    fun decodeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String {
        if (length <= 0) return ""
        val dec = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return dec.decode(ByteBuffer.wrap(bytes, offset, length)).toString()
    }

    private fun adjustEnd(buf: ByteArray, end: Int, total: Int): Int {
        if (end <= 0 || end >= total) return end
        // Check whether the byte just before `end` is part of an unfinished UTF-8 sequence.
        var lead = end - 1
        var back = 0
        while (lead > 0 && back < 3 && (buf[lead].toInt() and 0xC0) == 0x80) {
            lead--
            back++
        }
        val expected = expectedUtf8Length(buf[lead].toInt() and 0xFF)
        if (expected > 1) {
            val needEnd = lead + expected
            if (needEnd in (end + 1)..total) {
                var valid = true
                for (i in end until needEnd) {
                    if ((buf[i].toInt() and 0xC0) != 0x80) {
                        valid = false
                        break
                    }
                }
                if (valid) return needEnd
            }
        }
        return end
    }

    private fun expectedUtf8Length(leadByte: Int): Int = when {
        leadByte < 0x80 -> 1
        (leadByte and 0xE0) == 0xC0 -> 2
        (leadByte and 0xF0) == 0xE0 -> 3
        (leadByte and 0xF8) == 0xF0 -> 4
        else -> 1
    }
}
