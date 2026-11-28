package com.whatcode.bigfile

import com.whatcode.search.FindLogic
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * Streaming, cancellable line-by-line search over a [PagedFile]. Never loads the entire file into memory, caps hit
 * storage at [cap], handles `\n`, `\r\n`, and `\r`, and reports growing match counts while scanning.
 * Pure Kotlin, no Android.
 */
object ViewerSearch {

    data class Hit(
        val line: Long,
        val charStart: Int,
        val charEnd: Int,
        val lineByteStart: Long,
        val preview: String,
    )

    data class Result(
        val hits: List<Hit>,
        val totalMatched: Long,
        val capped: Boolean,
        val bytesScanned: Long,
    )

    fun search(
        file: PagedFile,
        pattern: Pattern,
        cap: Int = DEFAULT_CAP,
        cancelled: () -> Boolean = { false },
        onProgress: (hits: List<Hit>, totalMatched: Long, capped: Boolean, bytesScanned: Long) -> Unit = { _, _, _, _ -> },
    ): Result {
        val hits = ArrayList<Hit>()
        var total = 0L
        var capped = false
        val len = file.length
        val chunk = ByteArray(128 * 1024)
        val lineBuf = ByteArrayOutputStream(512)
        var pos = 0L
        var line = 0L
        var lineStart = 0L
        var crPending = false
        var lastReportAt = 0L

        fun processLine(endPos: Long) {
            if (lineBuf.size() == 0) {
                lineStart = endPos
                return
            }
            val raw = lineBuf.toByteArray()
            lineBuf.reset()
            val text = Utf8Window.decodeBytes(raw)
            val m = pattern.matcher(text)
            while (m.find()) {
                if (m.end() == m.start()) continue
                total++
                if (hits.size < cap) {
                    val snippet = text.take(MAX_PREVIEW_CHARS)
                    hits.add(Hit(line, m.start(), m.end(), lineStart, snippet))
                } else {
                    capped = true
                }
            }
            lineStart = endPos
        }

        while (pos < len) {
            if (cancelled()) break
            val n = file.readBytes(pos, chunk.size, chunk)
            if (n <= 0) break
            var i = 0
            if (crPending && chunk[0] == '\n'.code.toByte()) {
                crPending = false
                lineStart = pos + 1
                i = 1
            } else {
                crPending = false
            }
            while (i < n) {
                val b = chunk[i]
                if (b == '\n'.code.toByte()) {
                    processLine(pos + i + 1)
                    line++
                } else if (b == '\r'.code.toByte()) {
                    if (i + 1 < n && chunk[i + 1] == '\n'.code.toByte()) {
                        processLine(pos + i + 2)
                        i++
                    } else if (i + 1 == n) {
                        processLine(pos + i + 1)
                        crPending = true
                    } else {
                        processLine(pos + i + 1)
                    }
                    line++
                } else if (lineBuf.size() < MAX_SEARCH_LINE_BYTES) {
                    lineBuf.write(b.toInt())
                }
                i++
            }
            pos += n
            if (pos - lastReportAt >= PROGRESS_BYTES) {
                lastReportAt = pos
                onProgress(hits.toList(), total, capped, pos)
            }
        }
        if (!cancelled() && lineBuf.size() > 0) {
            processLine(pos)
        }
        onProgress(hits.toList(), total, capped, pos)
        return Result(hits, total, capped, pos)
    }

    const val DEFAULT_CAP = 5_000
    const val MIN_CAP = 100
    const val MAX_CAP = 50_000
    private const val PROGRESS_BYTES = 1024 * 1024L
    private const val MAX_PREVIEW_CHARS = 120
    private const val MAX_SEARCH_LINE_BYTES = 512 * 1024
}
