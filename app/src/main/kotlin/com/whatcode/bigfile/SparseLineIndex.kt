package com.whatcode.bigfile

/**
 * Sparse line-offset index for huge files stored in [PagedFile]. Records the starting byte offset of every
 * [chunkLines] line, handles `\n`, `\r\n`, and lone `\r` across chunk boundaries, supports background incremental
 * scanning (`counting...`), percentage jumps before indexing finishes, and tail growth while open.
 * Pure Kotlin, no Android.
 */
class SparseLineIndex(val chunkLines: Int = DEFAULT_CHUNK_LINES) {

    data class LineSlice(
        val line: Long,
        val byteStart: Long,
        val contentEnd: Long,
        val nextStart: Long,
        val text: String,
        val truncated: Boolean,
        val ending: String,
    )

    private val lock = Any()
    private var anchors = LongArray(1024)
    private var anchorCount = 1 // line 0 always starts at byte 0

    @Volatile
    var lineCount: Long = 1L
        private set

    @Volatile
    var scannedBytes: Long = 0L
        private set

    @Volatile
    var completed: Boolean = false
        private set

    private var prevWasCr = false
    private var endsWithNewline = false

    /** Optional piece-table overlay for in-place editing without loading the file into memory. */
    @Volatile
    var overlay: PieceTable? = null

    private fun srcLength(file: PagedFile): Long = overlay?.totalLength ?: file.length

    private fun srcReadBytes(file: PagedFile, offset: Long, len: Int, dst: ByteArray): Int =
        overlay?.readBytes(file, offset, len, dst) ?: file.readBytes(offset, len, dst)

    private fun srcReadByte(file: PagedFile, offset: Long): Int {
        val ov = overlay ?: return file.readByte(offset)
        val one = ByteArray(1)
        return if (ov.readBytes(file, offset, 1, one) == 1) one[0].toInt() and 0xFF else -1
    }

    /**
     * Builds the index from byte 0 of [file]. Calls [onProgress] periodically with current counts so the UI can show
     * `counting…` and update immediately as the scan advances. Returns false if [cancelled] became true.
     */
    fun build(
        file: PagedFile,
        cancelled: () -> Boolean = { false },
        onProgress: (lines: Long, bytes: Long, done: Boolean) -> Unit = { _, _, _ -> },
    ): Boolean {
        synchronized(lock) {
            anchors[0] = 0L
            anchorCount = 1
            lineCount = 1L
            scannedBytes = 0L
            completed = false
            prevWasCr = false
            endsWithNewline = false
        }
        return scanFrom(file, 0L, cancelled, onProgress)
    }

    /**
     * Scans newly appended bytes of a growing [file] starting from [scannedBytes]. Returns true if new bytes were
     * processed and line counts changed.
     */
    fun refreshTail(
        file: PagedFile,
        cancelled: () -> Boolean = { false },
    ): Boolean {
        val startAt = scannedBytes
        if (srcLength(file) <= startAt) return false
        val beforeLines = lineCount
        scanFrom(file, startAt, cancelled) { _, _, _ -> }
        return lineCount != beforeLines || scannedBytes != startAt
    }

    private fun scanFrom(
        file: PagedFile,
        fromByte: Long,
        cancelled: () -> Boolean,
        onProgress: (Long, Long, Boolean) -> Unit,
    ): Boolean {
        val buf = ByteArray(SCAN_BUFFER_BYTES)
        var pos = fromByte
        var nextAnchorLine = synchronized(lock) { anchorCount.toLong() * chunkLines }
        var lines = lineCount
        var crPending = prevWasCr
        var endedBreak = endsWithNewline

        while (true) {
            if (cancelled()) {
                synchronized(lock) {
                    lineCount = lines
                    scannedBytes = pos
                    prevWasCr = crPending
                    endsWithNewline = endedBreak
                    completed = false
                }
                return false
            }
            val n = srcReadBytes(file, pos, buf.size, buf)
            if (n <= 0) break

            var i = 0
            // If the previous chunk ended on '\r' and the new chunk starts with '\n', that '\n' completes '\r\n'.
            if (crPending && buf[0] == '\n'.code.toByte()) {
                crPending = false
                // Move the line start recorded after '\r' forward past '\n'.
                if (lines == nextAnchorLine - chunkLines + 1 && anchorCount > 1 && anchors[anchorCount - 1] == pos) {
                    anchors[anchorCount - 1] = pos + 1
                }
                endedBreak = true
                i = 1
            } else {
                crPending = false
            }

            while (i < n) {
                val b = buf[i]
                if (b == '\n'.code.toByte()) {
                    lines++
                    val start = pos + i + 1
                    if (lines - 1 == nextAnchorLine) {
                        appendAnchor(start)
                        nextAnchorLine += chunkLines
                    }
                    endedBreak = true
                } else if (b == '\r'.code.toByte()) {
                    if (i + 1 < n && buf[i + 1] == '\n'.code.toByte()) {
                        lines++
                        val start = pos + i + 2
                        if (lines - 1 == nextAnchorLine) {
                            appendAnchor(start)
                            nextAnchorLine += chunkLines
                        }
                        i++
                        endedBreak = true
                    } else if (i + 1 == n) {
                        // '\r' right at buffer boundary: count the line now and consume '\n' if the next chunk starts with it.
                        lines++
                        val start = pos + i + 1
                        if (lines - 1 == nextAnchorLine) {
                            appendAnchor(start)
                            nextAnchorLine += chunkLines
                        }
                        crPending = true
                        endedBreak = true
                    } else {
                        lines++
                        val start = pos + i + 1
                        if (lines - 1 == nextAnchorLine) {
                            appendAnchor(start)
                            nextAnchorLine += chunkLines
                        }
                        endedBreak = true
                    }
                } else {
                    endedBreak = false
                }
                i++
            }
            pos += n
            synchronized(lock) {
                lineCount = lines
                scannedBytes = pos
                prevWasCr = crPending
                endsWithNewline = endedBreak
            }
            onProgress(lines, pos, false)
        }
        synchronized(lock) {
            lineCount = lines
            scannedBytes = pos
            prevWasCr = crPending
            endsWithNewline = endedBreak
            completed = true
        }
        onProgress(lines, pos, true)
        return true
    }

    private fun appendAnchor(offset: Long) {
        synchronized(lock) {
            if (anchorCount == anchors.size) {
                anchors = anchors.copyOf(anchors.size * 2)
            }
            anchors[anchorCount++] = offset
        }
    }

    /** Byte start of [line] (0-based), or null if beyond what has been indexed so far. */
    fun startByteOf(file: PagedFile, line: Long): Long? {
        if (line < 0L) return null
        val anchorIdx = (line / chunkLines).toInt()
        val (anchorOffset, total) = synchronized(lock) {
            if (anchorIdx >= anchorCount) return null
            anchors[anchorIdx] to lineCount
        }
        if (line >= total) return null
        val baseLine = anchorIdx.toLong() * chunkLines
        if (line == baseLine) return anchorOffset
        var curLine = baseLine
        var pos = anchorOffset
        val limit = srcLength(file)
        val buf = ByteArray(16 * 1024)
        var crPending = false
        while (pos < limit) {
            val n = srcReadBytes(file, pos, buf.size, buf)
            if (n <= 0) break
            var i = 0
            if (crPending && buf[0] == '\n'.code.toByte()) {
                crPending = false
                pos += 1
                i = 1
                if (curLine == line) return pos
                continue
            }
            crPending = false
            while (i < n) {
                val b = buf[i]
                if (b == '\n'.code.toByte()) {
                    curLine++
                    val next = pos + i + 1
                    if (curLine == line) return next
                } else if (b == '\r'.code.toByte()) {
                    if (i + 1 < n && buf[i + 1] == '\n'.code.toByte()) {
                        curLine++
                        i++
                        val next = pos + i + 1
                        if (curLine == line) return next
                    } else if (i + 1 == n) {
                        // Look ahead 1 byte so CRLF split on buf edge returns exact next-line byte.
                        val nextByte = srcReadByte(file, pos + i + 1)
                        curLine++
                        val next = pos + i + if (nextByte == '\n'.code) 2 else 1
                        if (curLine == line) return next
                    } else {
                        curLine++
                        val next = pos + i + 1
                        if (curLine == line) return next
                    }
                }
                i++
            }
            pos += n
        }
        return null
    }

    /** Reads [line] (0-based), decoding at most [maxBytes] of its content unless [expanded] is true (capped safely). */
    fun readLine(
        file: PagedFile,
        line: Long,
        maxBytes: Int,
        expanded: Boolean = false,
    ): LineSlice? {
        val start = startByteOf(file, line) ?: return null
        val fileLen = srcLength(file)
        var contentEnd = start
        var nextStart = start
        var ending = ""
        val buf = ByteArray(16 * 1024)
        var pos = start
        var found = false
        while (pos < fileLen && !found) {
            val n = srcReadBytes(file, pos, buf.size, buf)
            if (n <= 0) break
            var i = 0
            while (i < n) {
                val b = buf[i]
                if (b == '\n'.code.toByte()) {
                    contentEnd = pos + i
                    nextStart = contentEnd + 1
                    ending = "\n"
                    found = true
                    break
                } else if (b == '\r'.code.toByte()) {
                    contentEnd = pos + i
                    val hasLf = if (i + 1 < n) buf[i + 1] == '\n'.code.toByte() else srcReadByte(file, pos + i + 1) == '\n'.code
                    if (hasLf) {
                        nextStart = contentEnd + 2
                        ending = "\r\n"
                    } else {
                        nextStart = contentEnd + 1
                        ending = "\r"
                    }
                    found = true
                    break
                }
                i++
            }
            if (!found) pos += n
        }
        if (!found) {
            contentEnd = fileLen
            nextStart = fileLen
        }
        val cap = if (expanded) MAX_EXPANDED_LINE_BYTES else maxBytes.coerceAtLeast(128)
        val lineBytes = (contentEnd - start).coerceAtLeast(0L)
        val truncated = lineBytes > cap
        val sliceEnd = if (truncated) start + cap else contentEnd
        val decoded = if (overlay == null) {
            Utf8Window.decodeRange(file, start, sliceEnd).text
        } else {
            val want = (sliceEnd - start).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            if (want == 0) "" else {
                val dst = ByteArray(want)
                val got = srcReadBytes(file, start, want, dst)
                String(dst, 0, got, Charsets.UTF_8)
            }
        }
        return LineSlice(
            line = line,
            byteStart = start,
            contentEnd = contentEnd,
            nextStart = nextStart,
            text = decoded,
            truncated = truncated,
            ending = ending,
        )
    }

    /** Estimates or locates the 0-based line corresponding to [fraction] (`0f..1f`) of the file's total length. */
    fun lineForPercent(file: PagedFile, fraction: Float): Long {
        val f = fraction.coerceIn(0f, 1f)
        val len = srcLength(file)
        if (len <= 0L || f <= 0f) return 0L
        if (f >= 1f) return maxOf(0L, lineCount - 1)
        val targetByte = (len.toDouble() * f).toLong()
        val (bestAnchorIdx, totalLines, scanned) = synchronized(lock) {
            if (targetByte > scannedBytes && !completed) {
                // Before scanning finishes past targetByte, scale proportionally inside the indexed range.
                val ratio = if (scannedBytes > 0L) targetByte.toDouble() / scannedBytes else 0.0
                return ((lineCount - 1) * ratio).toLong().coerceIn(0L, maxOf(0L, lineCount - 1))
            }
            var lo = 0
            var hi = anchorCount - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (anchors[mid] <= targetByte) lo = mid else hi = mid - 1
            }
            Triple(lo, lineCount, scannedBytes)
        }
        val baseLine = bestAnchorIdx.toLong() * chunkLines
        val anchorOff = synchronized(lock) { anchors[bestAnchorIdx] }
        if (anchorOff >= targetByte) return baseLine.coerceIn(0L, maxOf(0L, totalLines - 1))
        var line = baseLine
        var pos = anchorOff
        val buf = ByteArray(16 * 1024)
        while (pos < targetByte && pos < scanned) {
            val n = srcReadBytes(file, pos, minOf(buf.size.toLong(), targetByte - pos).toInt(), buf)
            if (n <= 0) break
            var i = 0
            while (i < n) {
                val b = buf[i]
                if (b == '\n'.code.toByte()) {
                    line++
                } else if (b == '\r'.code.toByte()) {
                    line++
                    if (i + 1 < n && buf[i + 1] == '\n'.code.toByte()) i++
                }
                i++
            }
            pos += n
        }
        return line.coerceIn(0L, maxOf(0L, totalLines - 1))
    }

    companion object {
        const val DEFAULT_CHUNK_LINES = 256
        const val MIN_CHUNK_LINES = 32
        const val MAX_CHUNK_LINES = 4096
        private const val SCAN_BUFFER_BYTES = 256 * 1024
        const val MAX_EXPANDED_LINE_BYTES = 512 * 1024
    }
}
