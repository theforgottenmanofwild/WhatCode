package com.whatcode.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.widget.TextView

/**
 * The line numbers of [CodeEditText]: works out how much room the gutter needs, and draws the numbers of the rows
 * that are on screen. The gutter is made by widening the left padding of the text, so the text, the cursor and the
 * touch positions all move over with it, and the numbers are drawn into that room.
 */
internal class LineGutter(private val view: TextView) {

    // How many lines there are, and where the first row on screen and the cursor are, without counting from the top.
    private val lineTotal = LineTotal()
    private val topLine = LineCounter()
    private val caretLine = LineCounter()

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val currentNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()
    private val clipRect = Rect()

    // What the width of the gutter was worked out for, so it is only measured again when one of these changes.
    private var gutterDigits = 0
    private var gutterSize = 0f
    private var gutterFace: Typeface? = null
    private var gutterPx = 0
    private var numbersPx = 0f

    /** Text of [count] characters at [start] in [s] is about to be replaced. */
    fun beforeChange(s: CharSequence, start: Int, count: Int) {
        lineTotal.removed(s, start, count)
        topLine.edited(start)
        caretLine.edited(start)
    }

    /** [count] characters were put into [s] at [start]. */
    fun afterChange(s: CharSequence, start: Int, count: Int) {
        lineTotal.added(s, start, count)
        topLine.edited(start)
        caretLine.edited(start)
    }

    /** True if the file now has a different number of digits in its last line number than the gutter was made for. */
    fun digitsChanged(): Boolean = LineNumbers.digits(lineTotal.lines) != gutterDigits

    /** The room the gutter takes: the widest digit of the editor font times the digits of the last line, and a gap. */
    fun width(): Int {
        val digits = LineNumbers.digits(lineTotal.lines)
        val p = view.paint
        if (digits != gutterDigits || p.textSize != gutterSize || p.typeface !== gutterFace) {
            gutterDigits = digits
            gutterSize = p.textSize
            gutterFace = p.typeface
            numberPaint.typeface = p.typeface
            numberPaint.textSize = p.textSize
            currentNumberPaint.typeface = Typeface.create(p.typeface, Typeface.BOLD)
            currentNumberPaint.textSize = p.textSize
            var widest = 0f
            for (d in '0'..'9') {
                val s = d.toString()
                widest = maxOf(widest, numberPaint.measureText(s), currentNumberPaint.measureText(s))
            }
            numbersPx = widest * digits
            gutterPx = Math.ceil((numbersPx + GUTTER_GAP_DP * view.resources.displayMetrics.density).toDouble()).toInt()
        }
        return gutterPx
    }

    /**
     * Draws the numbers. [baseLeft] is the left padding of the text without the gutter; the numbers end right under
     * it and stay put when the text scrolls sideways. [color] is for the numbers, [currentColor] for the number of the
     * line with the cursor, [dividerColor] for the line between gutter and text (0 for none).
     */
    fun draw(canvas: Canvas, l: Layout, baseLeft: Int, color: Int, currentColor: Int, dividerColor: Int) {
        val t = view.text
        val offset = view.totalPaddingTop
        canvas.getClipBounds(clipRect)
        val first = l.getLineForVertical(maxOf(0, clipRect.top - offset))
        val last = l.getLineForVertical(maxOf(0, clipRect.bottom - offset))
        val firstNumber = topLine.locate(t, l.getLineStart(first)).line
        val current = caretLine.locate(t, view.selectionStart.coerceIn(0, t.length)).line
        numberPaint.color = color
        currentNumberPaint.color = currentColor
        val left = view.scrollX + baseLeft
        val right = left + numbersPx
        numberPaint.textAlign = Paint.Align.RIGHT
        currentNumberPaint.textAlign = Paint.Align.RIGHT
        canvas.save()
        view.clipToText(canvas)
        LineNumbers.label(first, last, l::getLineStart, t, firstNumber) { row, n ->
            val baseline = (l.getLineBaseline(row) + offset).toFloat()
            canvas.drawText(n.toString(), right, baseline, if (n == current) currentNumberPaint else numberPaint)
        }
        if (dividerColor != 0) {
            dividerPaint.color = dividerColor
            val x = left + (numbersPx + gutterPx) / 2f
            canvas.drawRect(x, clipRect.top.toFloat(), x + 1f, clipRect.bottom.toFloat(), dividerPaint)
        }
        canvas.restore()
    }

    private companion object {
        /** Room between the line numbers and the text, in dp. */
        const val GUTTER_GAP_DP = 12
    }
}
