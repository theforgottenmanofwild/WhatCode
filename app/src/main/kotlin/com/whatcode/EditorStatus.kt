package com.whatcode

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.view.View
import android.widget.TextView
import com.whatcode.bigfile.LargeFileText
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.Family
import com.whatcode.editor.Lang
import com.whatcode.editor.LineCounter
import com.whatcode.editor.LineEndings
import com.whatcode.editor.Outline
import com.whatcode.ui.AppTheme

/**
 * The status bar under the editor: line and column, the number of characters, the language and the line ending.
 * The editor's text watcher tells it about edits ([beforeEdit], [onEdit], [afterEdit]); [refresh] draws it again.
 */
internal class EditorStatus(
    context: Context,
    palette: AppTheme.Palette,
    private val handler: Handler,
    private val editor: () -> CodeEditText,
    private val lang: () -> Lang,
) {
    val view = TextView(context).apply {
        val density = context.resources.displayMetrics.density
        setTextColor(palette.onStatus)
        setBackgroundColor(palette.status)
        textSize = 12f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
    }

    /** Remembers where the line count reached, so the status bar does not count a big file from its start each time. */
    private val lineCounter = LineCounter()

    /** True while the text holds a line longer than the limit of Settings > Large files; the bar says what that means. */
    var longLines = false

    /** True while the text is big enough for large file mode to have turned something off (see Settings > Large files). */
    var largeMode = false

    /** True when Settings > Navigation asks for the current scope in the bar. */
    var showScope = true
        set(v) {
            if (field == v) return
            field = v
            symbols = null
            refresh()
        }

    /** The outline of the text as last read, and the language it was read for; null when there is none to use. */
    private var symbols: List<Outline.Symbol>? = null
    private var symbolsLang: String? = null
    private var scopePending = false
    private val scopeTask = Runnable { readSymbols() }

    private fun scopeWanted() = showScope && !largeMode && lang().family != Family.PLAIN && editor().text.length <= SCOPE_MAX_CHARS

    /** Reads the outline again after [delay] ms, so typing does not read it at every key. */
    private fun scheduleScope(delay: Long) {
        handler.removeCallbacks(scopeTask)
        scopePending = true
        handler.postDelayed(scopeTask, delay)
    }

    /** Reads the outline of a text that is not big, a moment after the last edit, and draws the bar again. */
    private fun readSymbols() {
        scopePending = false
        if (!scopeWanted()) {
            symbols = null
            return
        }
        symbols = Outline.find(editor().text, lang())
        symbolsLang = lang().name
        refresh()
    }

    /** The scope of the cursor for the bar, with its leading separator; empty when there is none to show. */
    private fun scopeText(text: CharSequence, pos: Int): String {
        if (!scopeWanted()) return ""
        val found = symbols
        if (found == null || symbolsLang != lang().name) {
            if (!scopePending) scheduleScope(0)
            return ""
        }
        val label = Outline.label(Outline.scopeAt(found, text, pos))
        return if (label.isEmpty()) "" else "  \u00B7  $label"
    }

    /** Called when the bar is tapped. */
    var onTap: (() -> Unit)?
        get() = tap
        set(v) {
            tap = v
            view.setOnClickListener(if (v == null) null else View.OnClickListener { v() })
            view.isClickable = v != null
        }
    private var tap: (() -> Unit)? = null

    fun refresh() {
        val text = editor().text
        val pos = editor().selectionStart.coerceIn(0, text.length)
        val at = lineCounter.locate(text, pos)
        val ending = lineEnding?.let { "  \u00B7  ${it.label}" } ?: ""
        val long = if (!longLines) "" else if (lang().family == Family.PLAIN) "  \u00B7  Long lines: not wrapped" else "  \u00B7  Long lines: not coloured or wrapped"
        val big = if (largeMode) "  \u00B7  ${LargeFileText.STATUS}" else ""
        val scope = scopeText(text, pos)
        view.text = "Ln ${at.line}, Col ${at.column}  \u00B7  ${text.length} chars  \u00B7  ${lang().name}$ending$scope$long$big"
    }

    /** The line ending of the showing text; null while a big file is still being scanned. */
    private var lineEnding: LineEndings.Kind? = LineEndings.Kind.LF

    /** Counts the scans, so that the answer of an old one (a text since edited or replaced) is thrown away. */
    @Volatile
    private var lineEndingRun = 0
    private val lineEndingTask = Runnable {
        val before = lineEnding
        updateLineEnding(fresh = false)
        if (lineEnding != before) refresh()
    }

    /** Set by the text watcher when an edit added or removed a `\n` or `\r`. */
    private var lineBreakEdited = false

    /** True if the [len] characters of [s] from [start] hold a line break. Stops at the first one. */
    private fun hasLineBreak(s: CharSequence, start: Int, len: Int): Boolean {
        val end = (start + len).coerceAtMost(s.length)
        for (i in start.coerceAtLeast(0) until end) {
            val c = s[i]
            if (c == '\n' || c == '\r') return true
        }
        return false
    }

    /**
     * Works out the line ending of the showing text. A small text is scanned at once. A big one is scanned off the
     * main thread, so typing and opening stay quick; until it is done the status bar leaves the ending out when
     * [fresh] (a different text was just loaded), and keeps showing the last one otherwise.
     */
    private fun updateLineEnding(fresh: Boolean) {
        val run = ++lineEndingRun
        val snapshot = editor().text.toString()
        if (snapshot.length <= LineEndings.SYNC_LIMIT) {
            lineEnding = LineEndings.detect(snapshot)
            return
        }
        if (fresh) lineEnding = null
        Thread {
            val kind = LineEndings.detect(snapshot) { run != lineEndingRun }
            if (kind != null) handler.post {
                if (run == lineEndingRun && kind != lineEnding) {
                    lineEnding = kind
                    refresh()
                }
            }
        }.start()
    }

    /** [count] characters at [start] of [s] are about to be replaced. */
    fun beforeEdit(s: CharSequence?, start: Int, count: Int) {
        lineCounter.edited(start)
        if (s != null && hasLineBreak(s, start, count)) lineBreakEdited = true
    }

    /** [count] characters were put into [s] at [start]. */
    fun onEdit(s: CharSequence?, start: Int, count: Int) {
        lineCounter.edited(start)
        if (s != null && hasLineBreak(s, start, count)) lineBreakEdited = true
    }

    /**
     * The text changed. A different text was loaded when [loading]: its line ending is found now. Otherwise only an
     * edit that touched a line break can change it, so ordinary typing costs nothing here.
     */
    fun afterEdit(loading: Boolean) {
        handler.removeCallbacks(lineEndingTask)
        if (loading) {
            updateLineEnding(fresh = true)
        } else if (lineBreakEdited) {
            handler.postDelayed(lineEndingTask, 150)
        }
        lineBreakEdited = false
        if (scopeWanted()) scheduleScope(if (loading) 0 else SCOPE_DELAY_MS) else symbols = null
        refresh()
    }

    private companion object {
        /** A text with more characters than this shows no scope: reading its outline after each edit would cost too much. */
        const val SCOPE_MAX_CHARS = 200_000

        /** The wait after the last edit before the outline is read again. */
        const val SCOPE_DELAY_MS = 250L
    }
}
