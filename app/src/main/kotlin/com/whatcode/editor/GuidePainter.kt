package com.whatcode.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.widget.TextView

/** Draws the indent guides and the column ruler of [CodeEditText], under the text and only where the screen is. */
internal class GuidePainter(private val view: TextView) {
    private val paint = Paint()
    private val clip = Rect()

    private fun begin(canvas: Canvas, color: Int) {
        paint.color = color
        paint.strokeWidth = maxOf(1f, view.resources.displayMetrics.density)
        canvas.save()
        view.clipToText(canvas)
        // Nothing is drawn over the line numbers when the text is scrolled sideways.
        canvas.clipRect(view.scrollX + view.compoundPaddingLeft, view.scrollY, view.scrollX + view.width, view.scrollY + view.height)
    }

    /** A line down the whole editor after [column] characters, measured by the width of a 0 in the editor's font. */
    fun drawRuler(canvas: Canvas, column: Int, color: Int) {
        begin(canvas, color)
        val x = view.totalPaddingLeft + view.paint.measureText("0") * column
        canvas.drawLine(x, view.scrollY.toFloat(), x, (view.scrollY + view.height).toFloat(), paint)
        canvas.restore()
    }

    /** The guides of the lines on the screen, for levels of [step] columns (see [IndentGuides]). */
    fun drawGuides(canvas: Canvas, l: Layout, step: Int, color: Int) {
        val t = view.text
        begin(canvas, color)
        canvas.getClipBounds(clip)
        val dx = view.totalPaddingLeft
        val dy = view.totalPaddingTop
        var row = l.getLineForVertical(maxOf(0, clip.top - dy))
        val last = l.getLineForVertical(maxOf(0, clip.bottom - dy))
        while (row > 0 && !startsLine(t, l, row)) row--
        while (row <= last) {
            var end = row
            while (end + 1 < l.lineCount && !startsLine(t, l, end + 1)) end++
            var from = l.getLineStart(row)
            var depth = IndentGuides.indent(t, from, step)
            if (depth < 0) {
                val before = nearest(t, l, row, -1)
                val after = nearest(t, l, end, 1)
                val a = if (before < 0) -1 else IndentGuides.indent(t, before, step)
                val b = if (after < 0) -1 else IndentGuides.indent(t, after, step)
                depth = IndentGuides.blankDepth(a, b, step)
                from = if (a >= depth) before else after
            }
            if (depth > 0) {
                val firstRow = l.getLineForOffset(from)
                for (at in IndentGuides.offsets(t, from, step, depth)) {
                    // Indentation so deep that it wraps has no place on the later rows.
                    if (l.getLineForOffset(at) != firstRow) break
                    val x = l.getPrimaryHorizontal(at) + dx
                    canvas.drawLine(x, (l.getLineTop(row) + dy).toFloat(), x, (l.getLineBottom(end) + dy).toFloat(), paint)
                }
            }
            row = end + 1
        }
        canvas.restore()
    }

    private fun startsLine(t: CharSequence, l: Layout, row: Int): Boolean {
        val start = l.getLineStart(row)
        return start == 0 || t[start - 1] == '\n'
    }

    /** The start of the nearest line with text before ([direction] -1) or after (1) [row], or -1 if none is close. */
    private fun nearest(t: CharSequence, l: Layout, row: Int, direction: Int): Int {
        var r = row + direction
        var looked = 0
        while (r in 0 until l.lineCount && looked < SEARCH_ROWS) {
            if (startsLine(t, l, r) && IndentGuides.indent(t, l.getLineStart(r), 4) >= 0) return l.getLineStart(r)
            r += direction
            looked++
        }
        return -1
    }

    private companion object {
        /** How many rows are searched for a line with text round an empty one. */
        const val SEARCH_ROWS = 200
    }
}
