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
 * and [MARGIN_LINES] more above and below. What it needs to start reading in the middle of a text (see
 * [ScanStates]) is built and kept up to date on a background thread, and an edit only tells it which stretch of the
 * text changed, so a pause in typing costs a few lines of reading, not the whole file. Scrolling colours the lines
 * that come into view.
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
            edited(start, before, count, s.length)
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

    /** [before] characters at [start] were replaced by [count], and the text is now [length] long. */
    private fun edited(start: Int, before: Int, count: Int, length: Int) {
        editCount++
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
        if (lang.family == Family.PLAIN || length > Highlighter.MAX_CHARS) {
            snapshot = null
            paintedFirst = -1
            everything = true
            Highlighter.clear(e)
            painted?.invoke()
            return
        }
        if (length <= SHORT_CHARS) {
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
        val first = maxOf(0, top - MARGIN_LINES)
        val end = minOf(last, bottom + MARGIN_LINES)
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
        paintedFirst = -1
        worker.execute {
            val tokens = work(text, language, full, change, from, to) { gen != generation }
            if (tokens != null) {
                ui.post {
                    if (gen == generation && count == editCount) {
                        Highlighter.paint(editor.text, tokens, colours)
                        paintedFirst = first
                        paintedLast = end
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

    /** Returns the colours of the offsets [from] to [to] of [text], or null if the work was overtaken. */
    private fun work(text: String, language: Lang, full: Boolean, change: Change?, from: Int, to: Int, cancelled: () -> Boolean): Tokens? {
        if (cancelled()) {
            workerReady = false // an edit that this request carried is lost
            return null
        }
        val index = workerIndex
        var states = workerStates
        if (full || !workerReady || states == null || workerLang !== language) {
            workerReady = false
            index.build(text)
            states = ScanStates(language)
            workerStates = states
            workerLang = language
            if (!states.build(text, index, cancelled)) return null
        } else if (change != null) {
            workerReady = false
            val splice = index.replace(change.lo, change.oldLength, text.subSequence(change.lo, change.hi))
            if (!states.replace(splice, text, index, cancelled)) return null
        }
        workerReady = true
        val firstLine = index.lineOf(from)
        val lastLine = index.lineOf(maxOf(from, to))
        return Highlighter.tokenizeWindow(text, language, index, states, firstLine, lastLine)
    }

    // ------------------------------------------------------------------ scrolling

    /** The text scrolled or changed shape: colour the lines that came into view, if they are not coloured yet. */
    private fun scrolled() {
        if (paintedFirst < 0 || snapshotCount != editCount) return
        val layout = editor.layout ?: return
        val top = layout.getLineForVertical(editor.scrollY)
        val bottom = layout.getLineForVertical(editor.scrollY + editor.height)
        val slack = MARGIN_LINES / 3
        val coveredAbove = paintedFirst == 0 || top >= paintedFirst + slack
        val coveredBelow = paintedLast >= layout.lineCount - 1 || bottom <= paintedLast - slack
        if (coveredAbove && coveredBelow) return
        ui.removeCallbacks(scrollTask)
        ui.postDelayed(scrollTask, SCROLL_WAIT_MS)
    }

    private companion object {
        /** Texts up to this long are coloured whole, at once. */
        const val SHORT_CHARS = 20_000

        /** How many lines above and below the screen are coloured too. */
        const val MARGIN_LINES = 60

        const val SCROLL_WAIT_MS = 30L

        val worker by lazy {
            Executors.newSingleThreadExecutor { r -> Thread(r, "highlighter").apply { isDaemon = true } }
        }
    }
}
