package com.whatcode.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.widget.TextView

/**
 * The line numbers and fold markers of [CodeEditText]: works out how much room the gutter needs, and draws the
 * numbers and chevrons of the visible rows. The gutter is made by widening the left padding of the text, so the text,
 * the cursor and the touch positions all move over with it. Collapsed fold rows (with zero height) are skipped so
 * real file line numbers stay accurate.
 */
internal class LineGutter(private val view: TextView) {

    // How many lines there are, and where the first row on screen and the cursor are, without counting from the top.
    private val lineTotal = LineTotal()
    private val topLine = LineCounter()
    private val caretLine = LineCounter()

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val currentNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPath = Path()
    private val clipRect = Rect()

    // What the width of the gutter was worked out for, so it is only measured again when one of these changes.
    private var lastNumbers = false
    private var lastMarkers = false
    private var gutterDigits = 0
    private var gutterSize = 0f
    private var gutterFace: Typeface? = null
    private var gutterPx = 0
    private var numbersPx = 0f
    private var markersPx = 0f
    private var markerCenterXOffset = 0f
    private var dividerXOffset = 0f

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
    fun digitsChanged(): Boolean = lastNumbers && LineNumbers.digits(lineTotal.lines) != gutterDigits

    /** True if [viewX] (in view coordinates) falls inside the gutter while fold markers are shown. */
    fun inGutter(viewX: Float, baseLeft: Int, numbers: Boolean, markers: Boolean): Boolean {
        if (!markers) return false
        val w = width(numbers, true)
        return viewX >= baseLeft && viewX <= baseLeft + w
    }

    /**
     * The room the gutter takes for line [numbers] and/or fold [markers]. Returns 0 when neither is shown.
     */
    fun width(numbers: Boolean, markers: Boolean): Int {
        if (!numbers && !markers) {
            lastNumbers = false
            lastMarkers = false
            gutterDigits = 0
            gutterPx = 0
            numbersPx = 0f
            markersPx = 0f
            return 0
        }
        val digits = if (numbers) LineNumbers.digits(lineTotal.lines) else 0
        val p = view.paint
        if (numbers != lastNumbers || markers != lastMarkers || digits != gutterDigits ||
            p.textSize != gutterSize || p.typeface !== gutterFace
        ) {
            lastNumbers = numbers
            lastMarkers = markers
            gutterDigits = digits
            gutterSize = p.textSize
            gutterFace = p.typeface
            if (numbers) {
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
            } else {
                numbersPx = 0f
            }
            val density = view.resources.displayMetrics.density
            markersPx = if (markers) MARKER_STRIP_DP * density else 0f
            when {
                numbers && markers -> {
                    val inner = MARKER_INNER_GAP_DP * density
                    val rightPad = MARKER_RIGHT_GAP_DP * density
                    markerCenterXOffset = numbersPx + inner + markersPx / 2f
                    gutterPx = Math.ceil((numbersPx + inner + markersPx + rightPad).toDouble()).toInt()
                    dividerXOffset = gutterPx - rightPad / 2f
                }
                numbers -> {
                    val gap = GUTTER_GAP_DP * density
                    markerCenterXOffset = 0f
                    gutterPx = Math.ceil((numbersPx + gap).toDouble()).toInt()
                    dividerXOffset = (numbersPx + gutterPx) / 2f
                }
                else -> {
                    val pad = MARKER_ONLY_PAD_DP * density
                    markerCenterXOffset = pad + markersPx / 2f
                    gutterPx = Math.ceil((pad + markersPx + pad).toDouble()).toInt()
                    dividerXOffset = gutterPx - pad / 2f
                }
            }
        }
        return gutterPx
    }

    /**
     * Draws line [numbers] and/or fold [markers]. [baseLeft] is the left padding of the text without the gutter.
     * [foldState] takes a line's start offset and returns -1 (not foldable), 0 (foldable, open) or 1 (folded).
     */
    fun draw(
        canvas: Canvas,
        l: Layout,
        numbers: Boolean,
        markers: Boolean,
        baseLeft: Int,
        color: Int,
        currentColor: Int,
        dividerColor: Int,
        foldColor: Int,
        foldedColor: Int,
        foldState: (Int) -> Int,
    ) {
        if (!numbers && !markers) return
        val t = view.text
        val offset = view.totalPaddingTop
        canvas.getClipBounds(clipRect)
        val first = l.getLineForVertical(maxOf(0, clipRect.top - offset))
        val last = l.getLineForVertical(maxOf(0, clipRect.bottom - offset))
        val firstNumber = if (numbers) topLine.locate(t, l.getLineStart(first)).line else 1
        val current = if (numbers) caretLine.locate(t, view.selectionStart.coerceIn(0, t.length)).line else -1
        numberPaint.color = color
        currentNumberPaint.color = currentColor
        val left = view.scrollX + baseLeft
        val right = left + numbersPx
        numberPaint.textAlign = Paint.Align.RIGHT
        currentNumberPaint.textAlign = Paint.Align.RIGHT
        canvas.save()
        view.clipToText(canvas)
        LineNumbers.label(first, last, l::getLineStart, t, firstNumber) { row, n ->
            val top = l.getLineTop(row)
            val bottom = l.getLineBottom(row)
            if (bottom <= top) return@label
            if (numbers) {
                val baseline = (l.getLineBaseline(row) + offset).toFloat()
                canvas.drawText(n.toString(), right, baseline, if (n == current) currentNumberPaint else numberPaint)
            }
            if (markers) {
                val state = foldState(l.getLineStart(row))
                if (state >= 0) {
                    val cy = (top + bottom) / 2f + offset
                    val cx = left + markerCenterXOffset
                    drawChevron(canvas, cx, cy, collapsed = state > 0, color = if (state > 0) foldedColor else foldColor)
                }
            }
        }
        if (dividerColor != 0) {
            dividerPaint.color = dividerColor
            val x = left + dividerXOffset
            canvas.drawRect(x, clipRect.top.toFloat(), x + 1f, clipRect.bottom.toFloat(), dividerPaint)
        }
        canvas.restore()
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, collapsed: Boolean, color: Int) {
        val r = minOf(markersPx * 0.28f, view.paint.textSize * 0.24f)
        markerPaint.color = color
        markerPath.rewind()
        if (collapsed) {
            markerPath.moveTo(cx - r * 0.45f, cy - r)
            markerPath.lineTo(cx - r * 0.45f, cy + r)
            markerPath.lineTo(cx + r * 0.7f, cy)
        } else {
            markerPath.moveTo(cx - r, cy - r * 0.45f)
            markerPath.lineTo(cx + r, cy - r * 0.45f)
            markerPath.lineTo(cx, cy + r * 0.7f)
        }
        markerPath.close()
        canvas.drawPath(markerPath, markerPaint)
    }

    private companion object {
        /** Room between the line numbers and the text, in dp, when fold markers are off. */
        const val GUTTER_GAP_DP = 12
        const val MARKER_STRIP_DP = 14f
        const val MARKER_INNER_GAP_DP = 4f
        const val MARKER_RIGHT_GAP_DP = 6f
        const val MARKER_ONLY_PAD_DP = 4f
    }
}
