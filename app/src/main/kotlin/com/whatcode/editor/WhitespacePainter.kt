package com.whatcode.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.widget.TextView

/** Draws the faint marks of [CodeEditText] that show where the spaces and tabs are. */
internal class WhitespacePainter(private val view: TextView) {
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipRect = Rect()

    /**
     * A dot in the middle of each space and an arrow across each tab, for the text that is on screen only, in [color]
     * drawn faintly. With [wordWrap] off a line can be very long, so only the part between the two edges is looked at.
     */
    fun draw(canvas: Canvas, l: Layout, color: Int, wordWrap: Boolean) {
        val t = view.text
        val dx = view.totalPaddingLeft
        val dy = view.totalPaddingTop
        val scrollX = view.scrollX
        val scrollY = view.scrollY
        val compoundPaddingLeft = view.compoundPaddingLeft
        val paint = view.paint
        canvas.getClipBounds(clipRect)
        val first = l.getLineForVertical(maxOf(0, clipRect.top - dy))
        val last = l.getLineForVertical(maxOf(0, clipRect.bottom - dy))
        val size = paint.textSize
        val spaceWidth = paint.measureText(" ")
        markPaint.color = color
        markPaint.alpha = WHITESPACE_ALPHA
        markPaint.strokeWidth = maxOf(1f, size * 0.06f)
        canvas.save()
        view.clipToText(canvas)
        canvas.clipRect(scrollX + compoundPaddingLeft, scrollY, scrollX + view.width - view.compoundPaddingRight, scrollY + view.height)
        for (row in first..last) {
            var from = l.getLineStart(row)
            val rowEnd = l.getLineEnd(row)
            var to = rowEnd
            // A line that is not wrapped can be very long: only the part between the two edges is looked at.
            if (!wordWrap) {
                val a = l.getOffsetForHorizontal(row, (scrollX + compoundPaddingLeft - dx).toFloat())
                val b = l.getOffsetForHorizontal(row, (scrollX + view.width - dx).toFloat())
                from = maxOf(from, minOf(a, b) - 1)
                to = minOf(to, maxOf(a, b) + 1)
            }
            val y = l.getLineBaseline(row) + dy - size * 0.3f
            WhitespaceMarks.find(t, from, to) { at, tab ->
                val x = l.getPrimaryHorizontal(at)
                // The position after the last character of a row belongs to the next row, so it is not asked for.
                val next = when {
                    at + 1 < rowEnd -> l.getPrimaryHorizontal(at + 1)
                    tab -> l.getLineRight(row)
                    else -> x + spaceWidth
                }
                val left = minOf(x, next) + dx
                val right = maxOf(x, next) + dx
                if (tab) {
                    val inset = minOf(size * 0.15f, (right - left) / 4f)
                    val head = minOf(size * 0.18f, (right - left) / 2f)
                    val tip = right - inset
                    canvas.drawLine(left + inset, y, tip, y, markPaint)
                    canvas.drawLine(tip - head, y - head, tip, y, markPaint)
                    canvas.drawLine(tip - head, y + head, tip, y, markPaint)
                } else {
                    canvas.drawCircle((left + right) / 2f, y, maxOf(1f, size * 0.07f), markPaint)
                }
            }
        }
        canvas.restore()
    }

    private companion object {
        /** How strong the marks are, out of 255. */
        const val WHITESPACE_ALPHA = 130
    }
}
