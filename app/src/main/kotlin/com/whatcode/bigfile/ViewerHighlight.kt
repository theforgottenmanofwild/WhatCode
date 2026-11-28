package com.whatcode.bigfile

import com.whatcode.editor.Family
import com.whatcode.editor.Highlighter
import com.whatcode.editor.Lang
import com.whatcode.editor.LineIndex
import com.whatcode.editor.Tokens
import com.whatcode.editor.highlight.ScanStates

/**
 * Highlights a small window of lines for the large-file viewer using [LineIndex], [ScanStates] checkpoints, and
 * [Highlighter.tokenizeWindow], giving each visible line the same token colours as the editor without scanning the whole
 * huge file. Beyond [MAX_HIGHLIGHT_FILE_BYTES], or for plain text / long lines, it returns plain text (no tokens).
 * Pure Kotlin, no Android.
 */
object ViewerHighlight {

    /**
     * One highlighted slice: [tokens] has `[start, end, roleMarker]` relative to the concatenated window text, where
     * [lineOffsets] maps `(line - firstLine)` to its char offset inside that window text.
     */
    class WindowTokens(
        val firstLine: Long,
        val lastLine: Long,
        val lineOffsets: IntArray,
        val lineLengths: IntArray,
        val tokens: Tokens,
    )

    fun tokenizeLines(
        file: PagedFile,
        index: SparseLineIndex,
        firstLine: Long,
        lastLine: Long,
        lang: Lang,
        longLineLimit: Int,
        maxCharsPerLine: Int,
        maxFileBytes: Long = MAX_HIGHLIGHT_FILE_BYTES,
    ): WindowTokens? {
        if (lang.family == Family.PLAIN || file.length > maxFileBytes) return null
        val total = index.lineCount
        if (total <= 0L) return null
        val first = firstLine.coerceIn(0L, total - 1)
        val last = lastLine.coerceIn(first, total - 1)
        val ctxStart = maxOf(0L, first - CONTEXT_LINES)

        val lineCountInt = (last - ctxStart + 1).toInt()
        val lineStarts = IntArray(lineCountInt)
        val lineLens = IntArray(lineCountInt)
        val sb = StringBuilder()

        for (i in 0 until lineCountInt) {
            val l = ctxStart + i
            lineStarts[i] = sb.length
            val slice = index.readLine(file, l, maxCharsPerLine)
            val txt = slice?.text ?: ""
            lineLens[i] = txt.length
            sb.append(txt)
            if (i + 1 < lineCountInt) sb.append('\n')
        }

        val windowText = sb.toString()
        val li = LineIndex().apply { build(windowText) }
        val states = ScanStates(lang, longLineLimit)
        states.build(windowText, li)
        val relFirst = (first - ctxStart).toInt()
        val relLast = (last - ctxStart).toInt()
        val tokens = Highlighter.tokenizeWindow(windowText, lang, li, states, relFirst, relLast)

        val visCount = (last - first + 1).toInt()
        val visOffsets = IntArray(visCount)
        val visLengths = IntArray(visCount)
        for (i in 0 until visCount) {
            visOffsets[i] = lineStarts[relFirst + i]
            visLengths[i] = lineLens[relFirst + i]
        }
        return WindowTokens(first, last, visOffsets, visLengths, tokens)
    }

    /** Above 100 MB the viewer stays fast with plain text rather than colouring lines. */
    const val MAX_HIGHLIGHT_FILE_BYTES = 100_000_000L
    private const val CONTEXT_LINES = 80L
}
