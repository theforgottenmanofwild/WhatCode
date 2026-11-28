package com.whatcode.bigfile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import com.whatcode.editor.Lang
import com.whatcode.editor.SyntaxScheme
import com.whatcode.settings.AppearanceSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Contrast
import java.util.concurrent.Executors

/**
 * Dedicated Canvas-backed read-only text viewer for large and multi-gigabyte files. Never subclasses EditText and never
 * loads the file into memory: draws only visible lines from [PagedFile] + [SparseLineIndex], with virtual 64-bit line
 * scrolling, pinch zoom, fling, fast-scroll thumb, selection + copy, and on-demand expansion of very long lines.
 */
class LargeFileView(context: Context) : View(context) {

    var onStatusChanged: (() -> Unit)? = null
    var onSelectionChanged: ((Boolean) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onLineTappedInEditMode: ((Long) -> Unit)? = null
    var editMode: Boolean = false

    private var palette = AppTheme.palette(context)
    private var font: Typeface = Typeface.MONOSPACE
    private var fontSizeSp = 14f
    private var lineSpacingMult = 1.25f
    private var showLineNumbers = true
    private var syntaxEnabled = true
    private var scheme: SyntaxScheme = SyntaxScheme.of(light = false, editorBackground = palette.editor)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint()
    private val selPaint = Paint()
    private val hitPaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var file: PagedFile? = null
    private var index: SparseLineIndex? = null
    private var lang: Lang = com.whatcode.editor.Highlighter.languageFor(null)
    private var longLineLimit = 20_000

    var wrapLines: Boolean = false
        set(v) {
            if (field != v) {
                field = v
                subRow = 0
                horizontalOffset = 0f
                wrapLayouts.evictAll()
                invalidate()
            }
        }

    // Virtual 64-bit scroll state: topLine (0-based) and visual sub-row within topLine when wrapping.
    var topLine: Long = 0L
        private set
    private var subRow: Int = 0
    private var horizontalOffset: Float = 0f

    // Highlighted search match (optional).
    private var activeHit: ViewerSearch.Hit? = null

    // Selection endpoints (line, char offset within line's slice).
    private var hasSelection = false
    private var selAnchorLine = 0L
    private var selAnchorCol = 0
    private var selFocusLine = 0L
    private var selFocusCol = 0

    // Caches kept tightly bounded regardless of file size.
    private val lineCache = LruCache<Long, SparseLineIndex.LineSlice>(400)
    private val wrapLayouts = LruCache<Long, StaticLayout>(120)
    private val expandedLines = HashSet<Long>()

    private data class ExpandBadge(val line: Long, val rect: RectF)
    private val expandBadges = ArrayList<ExpandBadge>()

    // Windowed syntax highlighting computed on a single worker thread.
    private val ui = Handler(Looper.getMainLooper())
    private val hlExecutor = Executors.newSingleThreadExecutor()
    private var hlTokens: ViewerHighlight.WindowTokens? = null
    private var hlFirst = -1L
    private var hlLast = -1L
    private var hlSerial = 0

    private val scroller = OverScroller(context)
    private var draggingThumb = false
    private var selectingDrag = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (fontSizeSp * detector.scaleFactor).coerceIn(MIN_SP, MAX_SP)
                if (next != fontSizeSp) {
                    fontSizeSp = next
                    applyMetrics()
                    wrapLayouts.evictAll()
                    invalidate()
                }
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.abortAnimation()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (draggingThumb || selectingDrag || scaleDetector.isInProgress) return false
                scrollByDelta(distanceX, distanceY)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (draggingThumb || selectingDrag) return false
                scroller.fling(
                    0, 0,
                    if (wrapLines) 0 else -velocityX.toInt(),
                    -velocityY.toInt(),
                    Int.MIN_VALUE / 2, Int.MAX_VALUE / 2,
                    Int.MIN_VALUE / 2, Int.MAX_VALUE / 2,
                )
                lastFlingX = 0
                lastFlingY = 0
                postInvalidateOnAnimation()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                for (b in expandBadges) {
                    if (b.rect.contains(e.x, e.y)) {
                        toggleExpandLine(b.line)
                        return true
                    }
                }
                if (hasSelection) {
                    clearSelection()
                    return true
                }
                if (editMode) {
                    val pos = hitTest(e.x, e.y)
                    if (pos != null) {
                        onLineTappedInEditMode?.invoke(pos.first)
                        return true
                    }
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                if (draggingThumb || scaleDetector.isInProgress) return
                val pos = hitTest(e.x, e.y) ?: return
                selectingDrag = true
                hasSelection = true
                selAnchorLine = pos.first
                selAnchorCol = pos.second
                selFocusLine = pos.first
                selFocusCol = pos.second
                onSelectionChanged?.invoke(true)
                invalidate()
            }
        },
    )

    private var lastFlingX = 0
    private var lastFlingY = 0

    init {
        isFocusable = true
        applyAppearance(AppearanceSettings(context), AppTheme.palette(context))
    }

    fun applyAppearance(appearance: AppearanceSettings, newPalette: AppTheme.Palette) {
        palette = newPalette
        font = appearance.font.typeface()
        fontSizeSp = appearance.fontSize.toFloat().coerceIn(MIN_SP, MAX_SP)
        lineSpacingMult = appearance.lineSpacing / 100f
        showLineNumbers = appearance.lineNumbers
        syntaxEnabled = appearance.syntaxHighlighting
        val light = newPalette.isLight
        scheme = SyntaxScheme.of(light, palette.editor, appearance.syntaxOverrides(light))
        setBackgroundColor(palette.editor)
        applyMetrics()
        wrapLayouts.evictAll()
        invalidate()
    }

    private fun applyMetrics() {
        val d = resources.displayMetrics.density
        val spPx = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, fontSizeSp, resources.displayMetrics,
        )
        textPaint.typeface = font
        textPaint.textSize = spPx
        textPaint.color = palette.text
        gutterPaint.typeface = font
        gutterPaint.textSize = spPx
        gutterPaint.color = palette.muted
        gutterPaint.textAlign = Paint.Align.RIGHT
        dividerPaint.color = palette.outline
        selPaint.color = palette.selection
        hitPaint.color = Contrast.blend(palette.editor, palette.accent, 0.35f)
        thumbPaint.color = Contrast.blend(palette.editor, palette.muted, 0.5f)
        badgeBgPaint.color = Contrast.blend(palette.editor, palette.accent, 0.22f)
        badgeTextPaint.color = palette.accent
        badgeTextPaint.typeface = font
        badgeTextPaint.textSize = spPx * 0.84f
        badgeTextPaint.textAlign = Paint.Align.LEFT
        rowHeightPx = maxOf(1, Math.ceil((textPaint.fontSpacing * lineSpacingMult).toDouble()).toInt())
        padLeftPx = (12f * d).toInt()
        padTopPx = (10f * d).toInt()
    }

    private var rowHeightPx = 36
    private var padLeftPx = 24
    private var padTopPx = 20

    fun bind(paged: PagedFile, lineIndex: SparseLineIndex, language: Lang, longLimit: Int) {
        file = paged
        index = lineIndex
        lang = language
        longLineLimit = longLimit
        topLine = 0L
        subRow = 0
        horizontalOffset = 0f
        lineCache.evictAll()
        wrapLayouts.evictAll()
        expandedLines.clear()
        hlTokens = null
        hlFirst = -1L
        hlLast = -1L
        clearSelection()
        invalidate()
        onStatusChanged?.invoke()
    }

    fun notifyIndexUpdated() {
        lineCache.evictAll()
        wrapLayouts.evictAll()
        invalidate()
        onStatusChanged?.invoke()
    }

    fun jumpToLine(lineZeroBased: Long, hit: ViewerSearch.Hit? = null) {
        val total = index?.lineCount ?: 1L
        topLine = lineZeroBased.coerceIn(0L, maxOf(0L, total - 1L))
        subRow = 0
        activeHit = hit
        if (hit != null && !wrapLines) {
            val slice = readLineCached(topLine)
            if (slice != null) {
                val endCol = hit.charStart.coerceIn(0, slice.text.length)
                val targetX = textPaint.measureText(slice.text, 0, endCol)
                val avail = (width - gutterWidthPx() - padLeftPx * 2).coerceAtLeast(100)
                horizontalOffset = maxOf(0f, targetX - avail * 0.4f)
            }
        }
        invalidate()
        onStatusChanged?.invoke()
    }

    fun scrollToEnd() {
        val total = index?.lineCount ?: 1L
        val vis = visibleRowCapacity()
        topLine = maxOf(0L, total - vis + 2)
        subRow = 0
        invalidate()
        onStatusChanged?.invoke()
    }

    fun clearSelection() {
        if (!hasSelection) return
        hasSelection = false
        onSelectionChanged?.invoke(false)
        invalidate()
    }

    fun copySelection(): Boolean {
        if (!hasSelection) return false
        val pf = file ?: return false
        val idx = index ?: return false
        val (sLine, sCol, eLine, eCol) = orderedSelection()
        val sb = StringBuilder()
        var l = sLine
        while (l <= eLine) {
            val slice = idx.readLine(pf, l, longLineLimit, expanded = l in expandedLines) ?: break
            val t = slice.text
            val a = if (l == sLine) sCol.coerceIn(0, t.length) else 0
            val b = if (l == eLine) eCol.coerceIn(a, t.length) else t.length
            sb.append(t, a, b)
            if (l < eLine) sb.append('\n')
            if (sb.length > MAX_COPY_CHARS) {
                onMessage?.invoke("Selection is larger than the 1,000,000-character copy limit.")
                return false
            }
            l++
        }
        if (sb.isEmpty()) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", sb.toString()))
        onMessage?.invoke("Copied ${sb.length} characters")
        clearSelection()
        return true
    }

    private fun toggleExpandLine(line: Long) {
        if (!expandedLines.add(line)) expandedLines.remove(line)
        lineCache.remove(line)
        wrapLayouts.remove(line)
        invalidate()
    }

    private fun readLineCached(line: Long): SparseLineIndex.LineSlice? {
        val pf = file ?: return null
        val idx = index ?: return null
        lineCache.get(line)?.let { return it }
        val cap = minOf(longLineLimit, MAX_DRAW_CHARS)
        val slice = idx.readLine(pf, line, cap, expanded = line in expandedLines) ?: return null
        lineCache.put(line, slice)
        return slice
    }

    private fun gutterWidthPx(): Int {
        if (!showLineNumbers) return 0
        val total = index?.lineCount ?: 1L
        var digits = 1
        var v = total
        while (v >= 10) {
            v /= 10
            digits++
        }
        val d = resources.displayMetrics.density
        return Math.ceil((gutterPaint.measureText("8") * digits + 16f * d).toDouble()).toInt()
    }

    private fun textLeftPx(): Float = gutterWidthPx().toFloat() + padLeftPx

    private fun textWidthPx(): Int = (width - textLeftPx().toInt() - (16f * resources.displayMetrics.density).toInt()).coerceAtLeast(100)

    private fun wrapLayoutFor(line: Long, text: String): StaticLayout {
        wrapLayouts.get(line)?.let { return it }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidthPx())
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMult)
            .build()
        wrapLayouts.put(line, layout)
        return layout
    }

    private fun visibleRowCapacity(): Int = ((height - padTopPx).coerceAtLeast(rowHeightPx) / rowHeightPx) + 1

    private fun requestHighlightIfNeeded(first: Long, last: Long) {
        val pf = file ?: return
        val idx = index ?: return
        if (!syntaxEnabled) return
        if (first >= hlFirst && last <= hlLast && hlTokens != null) return
        val winFirst = maxOf(0L, first - 15L)
        val winLast = minOf(idx.lineCount - 1, last + 25L)
        val serial = ++hlSerial
        hlExecutor.execute {
            val res = ViewerHighlight.tokenizeLines(
                pf, idx, winFirst, winLast, lang, longLineLimit, minOf(longLineLimit, MAX_DRAW_CHARS),
            )
            ui.post {
                if (serial == hlSerial) {
                    hlTokens = res
                    hlFirst = winFirst
                    hlLast = winLast
                    invalidate()
                }
            }
        }
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        val cx = scroller.currX
        val cy = scroller.currY
        val dx = (cx - lastFlingX).toFloat()
        val dy = (cy - lastFlingY).toFloat()
        lastFlingX = cx
        lastFlingY = cy
        if (!scrollByDelta(dx, dy) && dy != 0f) {
            scroller.abortAnimation()
        }
        if (!scroller.isFinished) postInvalidateOnAnimation()
    }

    private var pixelAccumY = 0f

    private fun scrollByDelta(dx: Float, dy: Float): Boolean {
        val total = index?.lineCount ?: 1L
        if (!wrapLines) {
            horizontalOffset = maxOf(0f, horizontalOffset + dx)
        }
        pixelAccumY += dy
        val rowSteps = (pixelAccumY / rowHeightPx).toInt()
        if (rowSteps == 0) {
            invalidate()
            return true
        }
        pixelAccumY -= rowSteps * rowHeightPx
        val prevTop = topLine
        val prevSub = subRow
        if (!wrapLines) {
            topLine = (topLine + rowSteps).coerceIn(0L, maxOf(0L, total - 1L))
            subRow = 0
        } else {
            stepWrapped(rowSteps, total)
        }
        invalidate()
        onStatusChanged?.invoke()
        return topLine != prevTop || subRow != prevSub
    }

    private fun stepWrapped(steps: Int, total: Long) {
        var remaining = steps
        while (remaining > 0) {
            val slice = readLineCached(topLine)
            val rows = if (slice == null) 1 else wrapLayoutFor(topLine, slice.text).lineCount.coerceAtLeast(1)
            if (subRow + 1 < rows) {
                subRow++
            } else if (topLine + 1 < total) {
                topLine++
                subRow = 0
            } else {
                break
            }
            remaining--
        }
        while (remaining < 0) {
            if (subRow > 0) {
                subRow--
            } else if (topLine > 0) {
                topLine--
                val slice = readLineCached(topLine)
                val rows = if (slice == null) 1 else wrapLayoutFor(topLine, slice.text).lineCount.coerceAtLeast(1)
                subRow = rows - 1
            } else {
                break
            }
            remaining++
        }
    }

    private data class RenderedRow(val line: Long, val text: String, val startChar: Int, val endChar: Int, val yTop: Float)
    private val lastRendered = ArrayList<RenderedRow>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val idx = index ?: return
        if (file == null) return
        val total = idx.lineCount
        val gutterW = gutterWidthPx().toFloat()
        val textLeft = textLeftPx()
        val d = resources.displayMetrics.density

        lastRendered.clear()
        expandBadges.clear()

        val capacity = visibleRowCapacity() + 2
        val firstVis = topLine
        var curLine = topLine
        var curSub = subRow
        var y = padTopPx.toFloat()
        var lastVis = topLine

        requestHighlightIfNeeded(firstVis, minOf(total - 1, firstVis + capacity))

        val sel = if (hasSelection) orderedSelection() else null
        val baselineOff = -textPaint.ascent()

        canvas.save()
        canvas.clipRect(gutterW, 0f, width.toFloat(), height.toFloat())
        var drawnRows = 0
        while (drawnRows < capacity && curLine < total) {
            lastVis = curLine
            val slice = readLineCached(curLine) ?: break
            val txt = slice.text
            if (wrapLines) {
                val sl = wrapLayoutFor(curLine, txt)
                val rc = sl.lineCount.coerceAtLeast(1)
                while (curSub < rc && drawnRows < capacity) {
                    val sChar = sl.getLineStart(curSub)
                    val eChar = sl.getLineEnd(curSub)
                    val base = y + baselineOff
                    drawLineHighlightsAndText(canvas, curLine, txt, sChar, eChar, textLeft, y, base, sel)
                    lastRendered.add(RenderedRow(curLine, txt, sChar, eChar, y))
                    if (curSub == rc - 1 && slice.truncated) {
                        drawExpandBadge(canvas, curLine, textLeft + sl.getLineRight(curSub) + 6f * d, y, base)
                    }
                    y += rowHeightPx
                    drawnRows++
                    curSub++
                }
                curLine++
                curSub = 0
            } else {
                val base = y + baselineOff
                val drawX = textLeft - horizontalOffset
                drawLineHighlightsAndText(canvas, curLine, txt, 0, txt.length, drawX, y, base, sel)
                lastRendered.add(RenderedRow(curLine, txt, 0, txt.length, y))
                if (slice.truncated) {
                    val endX = drawX + textPaint.measureText(txt) + 6f * d
                    drawExpandBadge(canvas, curLine, endX, y, base)
                }
                y += rowHeightPx
                drawnRows++
                curLine++
            }
        }
        canvas.restore()

        // Line number gutter drawn last over horizontally scrolled text.
        if (showLineNumbers && gutterW > 0f) {
            canvas.save()
            canvas.clipRect(0f, 0f, gutterW, height.toFloat())
            canvas.drawColor(palette.editor)
            var lastLabeled = -1L
            val numRight = gutterW - 8f * d
            for (r in lastRendered) {
                if (r.line != lastLabeled) {
                    lastLabeled = r.line
                    canvas.drawText((r.line + 1).toString(), numRight, r.yTop + baselineOff, gutterPaint)
                }
            }
            canvas.drawLine(gutterW - 2f * d, 0f, gutterW - 2f * d, height.toFloat(), dividerPaint)
            canvas.restore()
        }

        // Fast-scroll thumb on the right edge when the file spans multiple screens.
        if (total > capacity) {
            val trackH = (height - 24f * d).coerceAtLeast(40f)
            val thumbH = maxOf(36f * d, trackH * minOf(1f, capacity.toFloat() / total.toFloat()))
            val frac = (topLine.toDouble() / maxOf(1L, total - 1L)).toFloat().coerceIn(0f, 1f)
            val thumbY = 12f * d + (trackH - thumbH) * frac
            val right = width - 4f * d
            canvas.drawRoundRect(RectF(right - 6f * d, thumbY, right, thumbY + thumbH), 3f * d, 3f * d, thumbPaint)
        }
    }

    private fun drawLineHighlightsAndText(
        canvas: Canvas,
        line: Long,
        txt: String,
        sChar: Int,
        eChar: Int,
        drawX: Float,
        yTop: Float,
        baseline: Float,
        sel: QuadSel?,
    ) {
        // Active search hit highlight
        val hit = activeHit
        if (hit != null && hit.line == line) {
            val hs = maxOf(sChar, hit.charStart.coerceIn(0, txt.length))
            val he = minOf(eChar, hit.charEnd.coerceIn(hs, txt.length))
            if (he > hs) {
                val x1 = drawX + textPaint.measureText(txt, sChar, hs)
                val x2 = drawX + textPaint.measureText(txt, sChar, he)
                canvas.drawRect(x1, yTop, x2, yTop + rowHeightPx, hitPaint)
            }
        }
        // User selection highlight
        if (sel != null && line in sel.sLine..sel.eLine) {
            val a = if (line == sel.sLine) sel.sCol.coerceIn(0, txt.length) else 0
            val b = if (line == sel.eLine) sel.eCol.coerceIn(a, txt.length) else txt.length
            val vs = maxOf(sChar, a)
            val ve = minOf(eChar, b)
            if (ve > vs || (a == 0 && b == 0 && sChar == 0 && line != sel.eLine)) {
                val x1 = drawX + textPaint.measureText(txt, sChar, vs)
                val x2 = if (ve > vs) drawX + textPaint.measureText(txt, sChar, ve) else x1 + textPaint.measureText(" ")
                canvas.drawRect(x1, yTop, x2, yTop + rowHeightPx, selPaint)
            }
        }
        // Text with optional syntax tokens from WindowTokens
        val wt = hlTokens
        if (wt == null || line < wt.firstLine || line > wt.lastLine) {
            textPaint.color = palette.text
            canvas.drawText(txt, sChar, eChar, drawX, baseline, textPaint)
            return
        }
        val idx = (line - wt.firstLine).toInt()
        val winStart = wt.lineOffsets[idx]
        val lineEnd = winStart + wt.lineLengths[idx]
        var cursor = sChar
        val tokens = wt.tokens
        for (i in 0 until tokens.size) {
            val b = i * 3
            val ts = tokens.data[b]
            val te = tokens.data[b + 1]
            if (te <= winStart + cursor) continue
            if (ts >= minOf(lineEnd, winStart + eChar)) break
            val ls = maxOf(cursor, ts - winStart)
            val le = minOf(eChar, te - winStart)
            if (ls > cursor) {
                textPaint.color = palette.text
                canvas.drawText(txt, cursor, ls, drawX + textPaint.measureText(txt, sChar, cursor), baseline, textPaint)
            }
            if (le > ls) {
                textPaint.color = scheme.resolve(tokens.data[b + 2])
                canvas.drawText(txt, ls, le, drawX + textPaint.measureText(txt, sChar, ls), baseline, textPaint)
                cursor = le
            }
        }
        if (cursor < eChar) {
            textPaint.color = palette.text
            canvas.drawText(txt, cursor, eChar, drawX + textPaint.measureText(txt, sChar, cursor), baseline, textPaint)
        }
    }

    private fun drawExpandBadge(canvas: Canvas, line: Long, x: Float, yTop: Float, baseline: Float) {
        val d = resources.displayMetrics.density
        val label = "+ more"
        val w = badgeTextPaint.measureText(label) + 10f * d
        val rect = RectF(x, yTop + 2f * d, x + w, yTop + rowHeightPx - 2f * d)
        canvas.drawRoundRect(rect, 4f * d, 4f * d, badgeBgPaint)
        canvas.drawText(label, x + 5f * d, baseline, badgeTextPaint)
        expandBadges.add(ExpandBadge(line, rect))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val d = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x >= width - 28f * d && (index?.lineCount ?: 0L) > visibleRowCapacity()) {
                    draggingThumb = true
                    seekToTouchY(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingThumb) {
                    seekToTouchY(event.y)
                    return true
                }
                if (selectingDrag) {
                    val pos = hitTest(event.x, event.y)
                    if (pos != null) {
                        selFocusLine = pos.first
                        selFocusCol = pos.second
                        invalidate()
                    }
                    if (event.y < rowHeightPx * 1.5f) scrollByDelta(0f, -rowHeightPx.toFloat())
                    else if (event.y > height - rowHeightPx * 1.5f) scrollByDelta(0f, rowHeightPx.toFloat())
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingThumb = false
                if (selectingDrag) {
                    selectingDrag = false
                    return true
                }
            }
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun seekToTouchY(y: Float) {
        val pf = file ?: return
        val idx = index ?: return
        val d = resources.displayMetrics.density
        val trackH = (height - 24f * d).coerceAtLeast(40f)
        val f = ((y - 12f * d) / trackH).coerceIn(0f, 1f)
        jumpToLine(idx.lineForPercent(pf, f))
    }

    private fun hitTest(viewX: Float, viewY: Float): Pair<Long, Int>? {
        val row = lastRendered.firstOrNull { viewY >= it.yTop && viewY < it.yTop + rowHeightPx }
            ?: lastRendered.lastOrNull()
            ?: return null
        val startX = if (wrapLines) textLeftPx() else textLeftPx() - horizontalOffset
        val relX = (viewX - startX).coerceAtLeast(0f)
        val offset = textPaint.breakText(row.text, row.startChar, row.endChar, true, relX, null)
        return row.line to (row.startChar + offset).coerceIn(row.startChar, row.endChar)
    }

    private data class QuadSel(val sLine: Long, val sCol: Int, val eLine: Long, val eCol: Int)

    private fun orderedSelection(): QuadSel = when {
        selAnchorLine < selFocusLine -> QuadSel(selAnchorLine, selAnchorCol, selFocusLine, selFocusCol)
        selAnchorLine > selFocusLine -> QuadSel(selFocusLine, selFocusCol, selAnchorLine, selAnchorCol)
        selAnchorCol <= selFocusCol -> QuadSel(selAnchorLine, selAnchorCol, selFocusLine, selFocusCol)
        else -> QuadSel(selFocusLine, selFocusCol, selAnchorLine, selAnchorCol)
    }

    override fun onDetachedFromWindow() {
        hlExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MIN_SP = 8f
        private const val MAX_SP = 48f
        private const val MAX_DRAW_CHARS = 24_000
        private const val MAX_COPY_CHARS = 1_000_000
    }
}
