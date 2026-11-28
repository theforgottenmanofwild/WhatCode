package com.whatcode.editor

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import com.whatcode.editor.highlight.ScanStates
import java.util.concurrent.Executors

/**
 * Puts the colours on the text of one [CodeEditText].
 *
 * A short text is read and coloured at once. A longer one is coloured only where it shows: the lines on the screen
 * and [marginLines] more above and below. What it needs to start reading in the middle of a text (see
 * [ScanStates]) is built and kept up to date on a background thread, and an edit only tells it which stretch of the
 * text changed, so a pause in typing costs a few lines of reading, not the whole file. Scrolling colours the lines
 * that come into view.
 *
 * A line longer than [longLine] characters is never read for colours, and [onLongLines] says when the text has one,
 * so the editor can stop wrapping it (see [LongLines]).
 *
 * Every change to the text bumps a counter. Work that was started for an older count is thrown away when it comes
 * back, so the text is never compared with a copy of itself. Use it from the main thread only.
 */
class EditorColouring(private val editor: CodeEditText) {

    private val ui = Handler(Looper.getMainLooper())

    // ---- the main thread's view of the edits

    /** Changes with every change to the text. */
    private var editCount = 0

    /** What all edits since the last request together changed. */
    private val dirty = DirtyRange()

    /** True when the background thread has to read the whole text again (a new file, a new language, lost work). */
    private var everything = true

    private var generation = 0
    private var snapshot: String? = null
    private var snapshotCount = -1

    /** Lines longer than this are not coloured; see [LongLines] and Settings > Large files. */
    var longLine = LongLines.DEFAULT_LIMIT
        private set

    /** How many lines above and below the screen are coloured too. */
    var marginLines = DEFAULT_MARGIN
        private set

    /** Called on the main thread when the text starts or stops holding a line longer than [longLine]. */
    var onLongLines: ((Boolean) -> Unit)? = null

    /**
     * True while some line is longer than [longLine]. An edit can only make it true, by looking at the lines it
     * touches; the pass that colours the text gives the exact answer and can make it false again.
     */
    private var hasLong = false
        set(v) {
            if (field != v) {
                field = v
                onLongLines?.invoke(v)
            }
        }

    private var lang: Lang = Highlighter.languageFor(null)
    private var scheme: SyntaxScheme? = null
    private var painted: (() -> Unit)? = null

    /** The layout lines that were coloured last, with their margin; -1 when nothing is coloured by window. */
    private var paintedFirst = -1
    private var paintedLast = -1
    private var retries = 0

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            edited(s, start, before, count)
        }

        override fun afterTextChanged(s: Editable) {}
    }

    private val scrollTask = Runnable { request(withChange = false) }

    init {
        editor.addTextChangedListener(watcher)
        editor.setOnScrollChangeListener { _, _, _, _, _ -> scrolled() }
        editor.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot) scrolled()
        }
    }

    // ------------------------------------------------------------------ edits

    /**
     * Sets how long a line may be before it is left out, and how many lines around the screen are coloured. Returns
     * true if something changed, and then the text has to be coloured again.
     */
    fun configure(limit: Int, margin: Int): Boolean {
        if (limit == longLine && margin == marginLines) return false
        val limitChanged = limit != longLine
        longLine = limit
        marginLines = margin
        if (limitChanged) {
            everything = true
            val e = editor.text
            hasLong = e.length > limit && LongLines.exists(e, limit)
        }
        return true
    }

    /** [before] characters at [start] of [s] were replaced by [count]. */
    private fun edited(s: CharSequence, start: Int, before: Int, count: Int) {
        editCount++
        val length = s.length
        if (start == 0 && count == length) {
            hasLong = length > longLine && LongLines.exists(s, longLine) // a different text
        } else if (!hasLong && length > longLine && LongLines.touched(s, start, start + count, longLine)) {
            hasLong = true
        }
        if (everything) return
        if (start == 0 && count == length) {
            everything = true // the whole text was replaced
            return
        }
        dirty.add(start, before, count)
        if (dirty.oldLength < 0) everything = true
    }

    // ------------------------------------------------------------------ colouring

    /**
     * Colours the editor's text for [language]. Call it after a pause in typing, or when the text, the language or
     * the colours changed. [done] is called on the main thread each time colours were put on the text, which for a
     * long text is after this call returns.
     */
    fun apply(language: Lang, colours: SyntaxScheme, done: (() -> Unit)? = null) {
        if (language !== lang) everything = true
        lang = language
        scheme = colours
        painted = done
        retries = 0
        request(withChange = true)
    }

    /** Takes every colour off the text and drops work that was under way. */
    fun remove() {
        generation++
        everything = true
        snapshot = null
        paintedFirst = -1
        Highlighter.clear(editor.text)
    }

    private fun request(withChange: Boolean) {
        val colours = scheme ?: return
        val e = editor.text
        val gen = ++generation
        val length = e.length
        if (length <= longLine) hasLong = false
        if (lang.family == Family.PLAIN || length > Highlighter.MAX_CHARS) {
            snapshot = null
            paintedFirst = -1
            everything = true
            Highlighter.clear(e)
            painted?.invoke()
            return
        }
        if (length <= minOf(SHORT_CHARS, longLine)) {
            snapshot = null
            paintedFirst = -1
            everything = true
            Highlighter.paint(e, Highlighter.tokenize(e.toString(), lang), colours)
            painted?.invoke()
            return
        }
        val layout = editor.layout
        if (layout == null || editor.height <= 0) {
            // Not laid out yet: the lines that show are not known. Look again soon.
            if (++retries < 20) ui.postDelayed({ if (gen == generation) request(withChange) }, 30)
            return
        }
        val last = layout.lineCount - 1
        val top = layout.getLineForVertical(editor.scrollY)
        val bottom = layout.getLineForVertical(editor.scrollY + editor.height)
        val first = maxOf(0, top - marginLines)
        val end = minOf(last, bottom + marginLines)
        val from = layout.getLineStart(first)
        val to = maxOf(from, layout.getLineEnd(end) - 1)

        val text = if (withChange || snapshot == null || snapshotCount != editCount) {
            e.toString().also {
                snapshot = it
                snapshotCount = editCount
            }
        } else {
            snapshot!!
        }
        val change = if (withChange && !everything && !dirty.isEmpty) Change(dirty.lo, dirty.hi, dirty.oldLength) else null
        val full = everything
        everything = false
        if (withChange) dirty.clear()
        val count = editCount
        val language = lang
        val limit = longLine
        paintedFirst = -1
        worker.execute {
            val result = work(text, language, limit, full, change, from, to) { gen != generation }
            if (result != null) {
                ui.post {
                    if (gen == generation && count == editCount) {
                        Highlighter.paint(editor.text, result.tokens, colours)
                        paintedFirst = first
                        paintedLast = end
                        hasLong = result.longLines > 0
                        painted?.invoke()
                    }
                }
            }
        }
    }

    /** What the edits since the last request did, in positions of the text now: [lo] to [hi], [oldLength] before. */
    private class Change(val lo: Int, val hi: Int, val oldLength: Int)

    // ---- the background thread's own data; only [worker] touches it

    private var workerLang: Lang? = null
    private val workerIndex = LineIndex()
    private var workerStates: ScanStates? = null
    private var workerReady = false

    /** What one pass of the worker found: the colours, and how many lines it left out for being too long. */
    private class Result(val tokens: Tokens, val longLines: Int)

    private var workerLimit = 0

    /** Returns the colours of the offsets [from] to [to] of [text], or null if the work was overtaken. */
    private fun work(text: String, language: Lang, limit: Int, full: Boolean, change: Change?, from: Int, to: Int, cancelled: () -> Boolean): Result? {
        if (cancelled()) {
            workerReady = false // an edit that this request carried is lost
            return null
        }
        val index = workerIndex
        var states = workerStates
        if (full || !workerReady || states == null || workerLang !== language || workerLimit != limit) {
            workerReady = false
            index.build(text)
            states = ScanStates(language, limit)
            workerStates = states
            workerLang = language
            workerLimit = limit
            if (!states.build(text, index, cancelled)) return null
        } else if (change != null) {
            workerReady = false
            val splice = index.replace(change.lo, change.oldLength, text.subSequence(change.lo, change.hi))
            if (!states.replace(splice, text, index, cancelled)) return null
        }
        workerReady = true
        val firstLine = index.lineOf(from)
        val lastLine = index.lineOf(maxOf(from, to))
        return Result(Highlighter.tokenizeWindow(text, language, index, states, firstLine, lastLine), states.longLines)
    }

    // ------------------------------------------------------------------ scrolling

    /** The text scrolled or changed shape: colour the lines that came into view, if they are not coloured yet. */
    private fun scrolled() {
        if (paintedFirst < 0 || snapshotCount != editCount) return
        val layout = editor.layout ?: return
        val top = layout.getLineForVertical(editor.scrollY)
        val bottom = layout.getLineForVertical(editor.scrollY + editor.height)
        val slack = marginLines / 3
        val coveredAbove = paintedFirst == 0 || top >= paintedFirst + slack
        val coveredBelow = paintedLast >= layout.lineCount - 1 || bottom <= paintedLast - slack
        if (coveredAbove && coveredBelow) return
        ui.removeCallbacks(scrollTask)
        ui.postDelayed(scrollTask, SCROLL_WAIT_MS)
    }

    companion object {
        /** Texts up to this long are coloured whole, at once. */
        private const val SHORT_CHARS = 20_000

        /** How many lines above and below the screen are coloured, when nothing else was chosen. */
        const val DEFAULT_MARGIN = 60

        private const val SCROLL_WAIT_MS = 30L

        private val worker by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "highlighter").apply { isDaemon = true } }
        }
    }
}
