package com.whatcodeeditor.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.whatcodeeditor.settings.EditorSettings

/**
 * The editor view. On top of EditText it adds: cursor reporting for the status bar, its own undo/redo
 * history, a way to change text without the auto-indent filter, and hooks so the on-screen
 * modifier keys (CTRL, ALT, SHFT) can turn the next typed letter into a shortcut.
 */
class CodeEditText(context: Context) : EditText(context) {

    var onCursorMoved: (() -> Unit)? = null

    /** A single character was typed on the soft keyboard. Return true to swallow it. */
    var onModifiedChar: ((Char) -> Boolean)? = null

    /** A hardware-keyboard shortcut: letter, ctrl, alt, shift. Return true if it was handled. */
    var onShortcut: ((Char, Boolean, Boolean, Boolean) -> Boolean)? = null

    var onHistoryChanged: (() -> Unit)? = null

    /** The language of the open file, which decides which quotes are paired and how Enter indents. */
    var lang: Lang = Highlighter.languageFor(null)

    /** See [EditorSettings.autoClose] and [EditorSettings.smartIndent]; the editor screen keeps them up to date. */
    var autoClose = EditorSettings.DEFAULT_AUTO_CLOSE
    var smartIndent = EditorSettings.DEFAULT_SMART_INDENT

    // What the view was last told, so asking for the same thing again does not lay out a big file once more.
    private var wrapApplied: Boolean? = null

    /**
     * See [EditorSettings.wordWrap]; the editor screen keeps it up to date. On, long lines wrap at the right edge.
     * Off, a line stays on one screen line and the text scrolls sideways. Changing it keeps the cursor in view.
     */
    var wordWrap = EditorSettings.DEFAULT_WORD_WRAP
        set(v) {
            field = v
            val first = wrapApplied == null
            if (wrapApplied == v) return
            wrapApplied = v
            setHorizontallyScrolling(!v)
            // A sideways scroll left over from the other mode would leave the text off to one side.
            if (v) scrollTo(0, scrollY)
            // The first call is the screen starting up, where the saved scroll position is still being restored.
            if (!first) post { if (selectionEnd >= 0) bringPointIntoView(selectionEnd) }
        }

    /**
     * Background colour of the line that has the cursor, or 0 for none (the default). See
     * [AppearanceSettings.highlightLine]. Only the screen line is coloured, so a long wrapped line is not repainted
     * from end to end each time the cursor moves.
     */
    var currentLineColor = 0
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    private val lineRect = android.graphics.Rect()

    override fun onDraw(canvas: android.graphics.Canvas) {
        val l = layout
        if (currentLineColor != 0 && l != null && selectionStart >= 0 && selectionStart == selectionEnd) {
            val line = l.getLineForOffset(selectionStart.coerceIn(0, text.length))
            val top = l.getLineTop(line) + totalPaddingTop
            val bottom = l.getLineBottom(line) + totalPaddingTop
            lineRect.set(scrollX, top, scrollX + width, bottom)
            canvas.drawRect(lineRect, currentLinePaint.apply { color = currentLineColor })
        }
        super.onDraw(canvas)
    }

    private val currentLinePaint = android.graphics.Paint()

    // ------------------------------------------------------------------ bracket matching

    /** The background of the bracket next to the cursor and of its partner, or 0 for no highlight (see [BracketMatch]). */
    var bracketColor = 0
        set(v) {
            if (field != v) {
                field = v
                refreshBracketMatch()
            }
        }

    private class BracketSpan(color: Int) : android.text.style.BackgroundColorSpan(color)

    private val bracketSpans = ArrayList<BracketSpan>(2)
    private val bracketTask = Runnable { showBracketMatch() }

    /**
     * Looks again for the pair at the cursor, a moment from now. It is not done in the middle of [onSelectionChanged]
     * because that can run while the text is still being changed, and one look after a burst of key presses is enough.
     */
    fun refreshBracketMatch() {
        removeCallbacks(bracketTask)
        post(bracketTask)
    }

    private fun clearBracketMatch() {
        val t = text
        for (s in bracketSpans) t.removeSpan(s)
        bracketSpans.clear()
    }

    /** True if the highlighter coloured the character at [pos] as a string, a comment or a regular expression. */
    private fun inLiteral(pos: Int): Boolean {
        for (s in text.getSpans(pos, pos + 1, SyntaxSpan::class.java)) if (s.literal) return true
        return false
    }

    private fun showBracketMatch() {
        clearBracketMatch()
        val pos = selectionStart
        if (bracketColor == 0 || pos < 0 || pos != selectionEnd) return
        val t = text
        val pair = BracketMatch.find(t, pos, lang, ::inLiteral) ?: return
        for (at in intArrayOf(pair.open, pair.close)) {
            val span = BracketSpan(bracketColor)
            t.setSpan(span, at, at + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            bracketSpans.add(span)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (currentLineColor != 0) invalidate()
        if (bracketColor != 0) refreshBracketMatch()
        onCursorMoved?.invoke()
    }

    // ------------------------------------------------------------------ undo / redo

    /** The undo and redo history of one file, set aside while another file is in the editor (see [saveHistory]). */
    class History internal constructor(internal val snapshot: EditHistory.Snapshot)

    private val history = EditHistory()
    private var replaying = false
    private var pendingRemoved = ""

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** A copy of the current undo and redo history, for a tab that is being put away. */
    fun saveHistory(): History = History(history.snapshot())

    /** Brings back a history from [saveHistory]. Call it right after [setTextNoHistory] for the same text. */
    fun loadHistory(h: History?) {
        history.load(h?.snapshot)
        onHistoryChanged?.invoke()
    }

    init {
        // Android would otherwise copy the whole text, plus every color span the highlighter added, into the state
        // it saves when the app goes to the background. A big file makes that state too large for the system
        // (TransactionTooLargeException) and the app crashes as it is closed. The open files are remembered by
        // SessionStore instead (see MainActivity.saveSession).
        freezesText = false
        // Autofill would hand the whole text to the system each time the editor gets focus. For a big file that is
        // too much for the system to take (DeadSystemException), and the app crashes as the file opens. A code
        // editor has nothing to autofill anyway.
        importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                pendingRemoved = if (replaying) "" else s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (replaying) return
                history.record(start, pendingRemoved, s.subSequence(start, start + count).toString(), SystemClock.uptimeMillis())
                onHistoryChanged?.invoke()
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun clearHistory() {
        history.clear()
        onHistoryChanged?.invoke()
    }

    /** Replaces the whole text (opening or closing a file); this is not an undoable edit. */
    fun setTextNoHistory(s: CharSequence) {
        replaying = true
        try {
            setText(s)
        } finally {
            replaying = false
        }
        clearHistory()
    }

    /** Replaces a range without the auto-indent filter; the change is recorded for undo like any edit. */
    fun replaceRaw(start: Int, end: Int, s: CharSequence) {
        val f = filters
        filters = arrayOf<InputFilter>()
        try {
            text.replace(start, end, s)
        } finally {
            filters = f
        }
    }

    private fun replay(start: Int, end: Int, s: String) {
        replaying = true
        try {
            replaceRaw(start, end, s)
        } finally {
            replaying = false
        }
    }

    fun undo(): Boolean {
        val e = history.takeUndo() ?: return false
        try {
            replay(e.start, e.start + e.added.length, e.removed)
            setSelection((e.start + e.removed.length).coerceIn(0, text.length))
            history.addRedo(e)
        } catch (_: Exception) {
            clearHistory()
        }
        onHistoryChanged?.invoke()
        return true
    }

    fun redo(): Boolean {
        val e = history.takeRedo() ?: return false
        try {
            replay(e.start, e.start + e.removed.length, e.added)
            setSelection((e.start + e.added.length).coerceIn(0, text.length))
            history.addUndo(e)
        } catch (_: Exception) {
            clearHistory()
        }
        onHistoryChanged?.invoke()
        return true
    }

    // ------------------------------------------------------------- typed characters

    /** Makes the change [edit] describes (see [TextEdit]) as one undo step and puts the selection where it says. */
    fun applyEdit(edit: TextEdit) {
        replaceRaw(edit.start, edit.end, edit.insert)
        val n = text.length
        setSelection(edit.selStart.coerceIn(0, n), edit.selEnd.coerceIn(0, n))
    }

    private fun lo() = minOf(selectionStart, selectionEnd)
    private fun hi() = maxOf(selectionStart, selectionEnd)

    /** True for the characters the typing rules may act on: brackets, quotes and Enter. */
    private fun isTypingTrigger(c: Char): Boolean = c == '\n' || TypingLogic.isTrigger(c)

    /** A bracket, quote or Enter was typed. Returns true if the typing rules did it instead of the plain character. */
    private fun handleTyped(c: Char): Boolean {
        if (selectionStart < 0 || selectionEnd < 0) return false
        if (c == '\n') {
            if (!smartIndent) return false
            applyEdit(TypingLogic.newline(text, lo(), hi(), lang, true))
            return true
        }
        if (!autoClose && !smartIndent) return false
        val edit = TypingLogic.typed(text, selectionStart, selectionEnd, c, lang, autoClose, smartIndent) ?: return false
        applyEdit(edit)
        return true
    }

    /**
     * Backspace: with auto-close on, removes an empty pair such as () in one go; with smart indent on, a backspace in
     * the indentation of a line removes a whole level of spaces.
     */
    private fun handleBackspace(): Boolean {
        if ((!autoClose && !smartIndent) || selectionStart < 0) return false
        val edit = TypingLogic.backspace(text, selectionStart, selectionEnd, lang, autoClose, smartIndent) ?: return false
        applyEdit(edit)
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(ic, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null && text.length == 1 && text[0] >= ' ' && onModifiedChar?.invoke(text[0]) == true) return true
                if (text != null && text.length == 1 && isTypingTrigger(text[0])) {
                    finishComposingText()
                    if (handleTyped(text[0])) return true
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null && text.length == 1 && text[0] >= ' ' && onModifiedChar?.invoke(text[0]) == true) {
                    finishComposingText()
                    return true
                }
                if (text != null && text.length == 1 && TypingLogic.isTrigger(text[0])) {
                    finishComposingText()
                    if (handleTyped(text[0])) return true
                }
                return super.setComposingText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0) {
                    finishComposingText()
                    if (handleBackspace()) return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val c = event.unicodeChar
                    if (c >= ' '.code && onModifiedChar?.invoke(c.toChar()) == true) return true
                }
                // Enter, backspace and typed brackets sent as key events reach onKeyDown below.
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            val c = event.getUnicodeChar(0)
            if (c != 0 && onShortcut?.invoke(c.toChar().lowercaseChar(), true, event.isAltPressed, event.isShiftPressed) == true) {
                return true
            }
        } else if (!event.isAltPressed && !event.isMetaPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (handleTyped('\n')) return true
                KeyEvent.KEYCODE_DEL -> if (handleBackspace()) return true
                KeyEvent.KEYCODE_TAB -> {
                    EditOps.indent(this, outdent = event.isShiftPressed)
                    return true
                }
                else -> {
                    val c = event.unicodeChar
                    if (c in 1..0xFFFF && TypingLogic.isTrigger(c.toChar()) && handleTyped(c.toChar())) return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------- clipboard

    /**
     * Paste always pastes plain text. Text copied from a web page or a document would otherwise bring its fonts and
     * colours into the editor, where they fight the syntax colours. Copy and cut with nothing selected take the whole
     * line, like most code editors do.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        when (id) {
            android.R.id.paste -> return super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
            android.R.id.copy -> if (selectionStart == selectionEnd && copyLine(cut = false)) return true
            android.R.id.cut -> if (selectionStart == selectionEnd && copyLine(cut = true)) return true
        }
        return super.onTextContextMenuItem(id)
    }

    private fun copyLine(cut: Boolean): Boolean {
        val pos = selectionStart
        if (pos < 0 || text.isEmpty()) return false
        val (s, e) = EditOps.lineWithBreak(text, pos)
        val line = text.subSequence(s, e).toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("line", if (line.endsWith("\n")) line else line + "\n"))
        if (cut) applyEdit(EditOps.deleteLinesEdit(text, pos, pos))
        return true
    }
}
