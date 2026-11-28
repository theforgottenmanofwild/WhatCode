package com.whatcode.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.whatcode.bigfile.LargeFileConfig
import com.whatcode.bigfile.Plan
import com.whatcode.bigfile.Saving
import com.whatcode.bigfile.TextWindow
import com.whatcode.settings.EditorSettings

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

    /**
     * See [EditorSettings.closeTags], [EditorSettings.splitTags] and [EditorSettings.renameTags]: closing a tag when
     * its `>` is typed, Enter between two tags, and renaming the partner tag along with the one being edited.
     */
    var closeTags = EditorSettings.DEFAULT_CLOSE_TAGS
    var splitTags = EditorSettings.DEFAULT_SPLIT_TAGS
    var renameTags = EditorSettings.DEFAULT_RENAME_TAGS

    /** See [EditorSettings.quoteAttributes]: `=` after an attribute name in markup adds the quotes of its value. */
    var quoteAttributes = EditorSettings.DEFAULT_QUOTE_ATTRIBUTES

    /** See [EditorSettings.wholeLineClipboard], [EditorSettings.plainPaste] and [EditorSettings.hardwareShortcuts]. */
    var wholeLineClipboard = EditorSettings.DEFAULT_WHOLE_LINE_CLIPBOARD
    var plainPaste = EditorSettings.DEFAULT_PLAIN_PASTE
    var hardwareShortcuts = EditorSettings.DEFAULT_HARDWARE_SHORTCUTS

    /** See [EditorSettings.indentDefault]: the indentation for a file that shows no pattern of its own. */
    var indentDefault = EditorSettings.DEFAULT_INDENT

    // What the view was last told, so asking for the same thing again does not lay out a big file once more.
    private var wrapApplied: Boolean? = null

    /**
     * See [EditorSettings.wordWrap]; the editor screen keeps it up to date. On, long lines wrap at the right edge.
     * Off, a line stays on one screen line and the text scrolls sideways. Changing it keeps the cursor in view.
     */
    var wordWrap = EditorSettings.DEFAULT_WORD_WRAP
        set(v) {
            field = v
            applyWrap()
        }

    /**
     * True while the text holds a line longer than the limit of Settings > Large files (see [LongLines]). The text is
     * not wrapped then, whatever [wordWrap] says, because laying out one huge line to wrap it would freeze the editor.
     */
    var longLines = false
        set(v) {
            field = v
            applyWrap()
        }

    /**
     * What the size of the text turns off (see [LargeFileConfig]): wrapping, the marks for spaces, matching a bracket or
     * a tag beyond the screen, and most of the undo history. Set by the screen each time the text crosses a size.
     */
    var largeFile: Plan = Plan.NONE
        set(v) {
            if (field == v) return
            field = v
            applyWrap()
            history.limitEdits(v.undoSteps)
            invalidate()
            refreshBracketMatch()
        }

    private val wrapping: Boolean get() = wordWrap && !longLines && !largeFile.isOff(Saving.WRAP)

    private fun applyWrap() {
        val v = wrapping
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
        if (l != null && showWhitespace && !largeFile.isOff(Saving.WHITESPACE)) whitespace.draw(canvas, l, whitespaceColor, wrapping)
        if (l != null && showLineNumbers) gutter.draw(canvas, l, super.getCompoundPaddingLeft(), gutterColor, gutterCurrentColor, gutterDividerColor)
    }

    private val currentLinePaint = android.graphics.Paint()

    // ------------------------------------------------------------------ line numbers and whitespace marks

    private val gutter = LineGutter(this)
    private val whitespace = WhitespacePainter(this)

    /**
     * See [AppearanceSettings.lineNumbers]. On, a gutter on the left numbers the lines of the file. It is made by
     * widening the left padding of the text (see [LineGutter]), so the text, the cursor and the touch positions all
     * move over with it. Only the rows that are on screen are numbered.
     */
    var showLineNumbers = false
        set(v) {
            if (field != v) {
                field = v
                requestLayout()
                invalidate()
            }
        }

    /** Colours of the numbers, of the number of the line with the cursor, and of the line between gutter and text. */
    var gutterColor = 0
    var gutterCurrentColor = 0
    var gutterDividerColor = 0

    /** The text starts after the gutter; the cursor, the clipping of the text and the wrapping all follow from this. */
    override fun getCompoundPaddingLeft(): Int = super.getCompoundPaddingLeft() + if (showLineNumbers) gutter.width() else 0

    /** See [AppearanceSettings.showWhitespace]. On, faint marks show where the spaces and tabs are. */
    var showWhitespace = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }

    /** The colour of the marks; it is drawn faintly. */
    var whitespaceColor = 0

    // ------------------------------------------------------------------ bracket matching

    /** The background of the bracket next to the cursor and of its partner, or 0 for no highlight (see [BracketMatch]). */
    var bracketColor = 0
        set(v) {
            if (field != v) {
                field = v
                refreshBracketMatch()
            }
        }

    /** See [EditorSettings.matchHighlight]. Off, nothing is marked even though [bracketColor] is set. */
    var matchHighlight = EditorSettings.DEFAULT_MATCH_HIGHLIGHT
        set(v) {
            if (field != v) {
                field = v
                refreshBracketMatch()
            }
        }

    private val marking: Boolean get() = bracketColor != 0 && matchHighlight

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
        if (!marking) return
        val t = text
        val pos = if (selectionStart == selectionEnd) selectionStart else -1
        // In a big text only the lines on the screen are read, so a pair that reaches off the screen is not marked.
        val lay = layout
        val onlyScreen = largeFile.isOff(Saving.MATCHING) && lay != null
        val from = if (onlyScreen) lay!!.getLineStart(lay.getLineForVertical(scrollY)) else 0
        val to = if (onlyScreen) lay!!.getLineEnd(lay.getLineForVertical(scrollY + height)).coerceAtMost(t.length) else t.length
        val view: CharSequence = if (onlyScreen) TextWindow(t, from, to) else t
        val literal = { at: Int -> inLiteral(at + from) }
        if (pos in from..to) {
            val pair = BracketMatch.find(view, pos - from, lang, literal)
            if (pair != null) {
                for (at in intArrayOf(pair.open, pair.close)) mark(at + from, at + from + 1)
            }
        }
        // In markup, a tag and the tag that goes with it are marked as well: the one under a hovering mouse or
        // stylus if there is one, otherwise the one the cursor is in.
        val tags = (if (hoverPos in from..to) TagMatch.find(view, hoverPos - from, lang, literal) else null)
            ?: (if (pos in from..to) TagMatch.find(view, pos - from, lang, literal) else null) ?: return
        mark(tags.openStart + from, tags.openEnd + from)
        mark(tags.closeStart + from, tags.closeEnd + from)
    }

    /**
     * Moves the cursor to the partner of the tag it is in, or of the bracket next to it (see [MatchJump]); with
     * [extend] the selection is stretched to there instead. Returns false if there is nothing to jump to.
     */
    fun jumpToMatch(extend: Boolean): Boolean {
        val pos = selectionEnd
        if (pos < 0) return false
        val to = MatchJump.target(text, pos, lang, ::inLiteral) ?: return false
        if (extend) setSelection(selectionStart, to) else setSelection(to)
        bringPointIntoView(to)
        return true
    }

    private val expander = SelectionExpander()

    /**
     * Grows the selection by one step (see [SelectionExpander]), or with [shrink] goes back one step. Returns false
     * if there is no step to take.
     */
    fun expandSelection(shrink: Boolean): Boolean {
        if (selectionStart < 0 || selectionEnd < 0) return false
        val to = if (shrink) {
            expander.shrink(selectionStart, selectionEnd)
        } else {
            expander.expand(text, selectionStart, selectionEnd, lang, ::inLiteral)
        } ?: return false
        setSelection(to.start, to.end)
        return true
    }

    private fun mark(from: Int, to: Int) {
        val span = BracketSpan(bracketColor)
        text.setSpan(span, from, to, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        bracketSpans.add(span)
    }

    /** The place in the text under a hovering mouse or stylus, or -1. */
    private var hoverPos = -1

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val at = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1 else getOffsetForPosition(event.x, event.y)
        if (at != hoverPos) {
            hoverPos = at
            if (marking) refreshBracketMatch()
        }
        return super.onHoverEvent(event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (currentLineColor != 0) invalidate()
        if (marking) refreshBracketMatch()
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
        history.limitEdits(largeFile.undoSteps)
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
                expander.reset()
                gutter.beforeChange(s, start, count)
                pendingRemoved = if (replaying) "" else s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                gutter.afterChange(s, start, count)
                // A gutter that has to grow or shrink for one more or one less digit lays the text out again.
                if (showLineNumbers && gutter.digitsChanged()) requestLayout()
                if (replaying) return
                val added = s.subSequence(start, start + count).toString()
                if (renamingPartner) {
                    // The partner tag renamed along with the one being edited is one step with that edit.
                    history.recordJoined(start, pendingRemoved, added, SystemClock.uptimeMillis())
                } else {
                    history.record(start, pendingRemoved, added, SystemClock.uptimeMillis())
                    if (renameTags) renameCheck = Triple(start, pendingRemoved, added)
                }
                onHistoryChanged?.invoke()
            }

            override fun afterTextChanged(s: Editable) {
                val edit = renameCheck ?: return
                renameCheck = null
                renamePartner(edit.first, edit.second, edit.third)
            }
        })
    }

    /** The last edit a person made, to see in [afterTextChanged] whether it was in a tag name; null once looked at. */
    private var renameCheck: Triple<Int, String, String>? = null
    private var renamingPartner = false

    /**
     * An edit of [added] over [removed] at [start] was made. If it was in the name of a tag, the other tag of the pair
     * gets the same name (see [TagMatch.renameEdit]), as part of the same undo step, and the cursor stays where it was.
     */
    private fun renamePartner(start: Int, removed: String, added: String) {
        if (lang.family != Family.MARKUP) return
        val edit = TagMatch.renameEdit(text, start, removed, added, lang, ::inLiteral) ?: return
        val a = selectionStart
        val b = selectionEnd
        val delta = edit.insert.length - (edit.end - edit.start)
        renamingPartner = true
        try {
            replaceRaw(edit.start, edit.end, edit.insert)
        } finally {
            renamingPartner = false
        }
        // Text changed before the cursor moves it along already; this only puts it right if that did not happen.
        val n = text.length
        val na = (if (a >= edit.end) a + delta else a).coerceIn(0, n)
        val nb = (if (b >= edit.end) b + delta else b).coerceIn(0, n)
        if (na != selectionStart || nb != selectionEnd) setSelection(na, nb)
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

    /** Undoes the newest step: one change, or the changes that are joined to it (see [EditHistory.recordJoined]). */
    fun undo(): Boolean {
        var e = history.takeUndo() ?: return false
        try {
            while (true) {
                replay(e.start, e.start + e.added.length, e.removed)
                // The cursor goes where the change the person made was, not to the partner that followed from it.
                if (!e.joined) setSelection((e.start + e.removed.length).coerceIn(0, text.length))
                history.addRedo(e)
                if (!e.joined) break
                e = history.takeUndo() ?: break
            }
        } catch (_: Exception) {
            clearHistory()
        }
        onHistoryChanged?.invoke()
        return true
    }

    fun redo(): Boolean {
        var e = history.takeRedo() ?: return false
        try {
            while (true) {
                replay(e.start, e.start + e.removed.length, e.added)
                if (!e.joined) setSelection((e.start + e.added.length).coerceIn(0, text.length))
                history.addUndo(e)
                if (!history.redoJoinsNext) break
                e = history.takeRedo() ?: break
            }
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

    /** [applyEdit] as an undo step of its own, never joined to the typing just before or after it. */
    fun applyStep(edit: TextEdit) {
        history.seal()
        applyEdit(edit)
        history.seal()
    }

    /** Puts [tag] round the selection or the word at the cursor (see [TagWrap.wrap]), as one undo step. */
    fun wrapInTag(tag: TagWrap.Tag) {
        if (selectionStart < 0 || selectionEnd < 0) return
        val unit = TypingLogic.indentUnit(text, lang, indentDefault.unit)
        applyStep(TagWrap.wrap(text, selectionStart, selectionEnd, tag, unit))
        bringPointIntoView(selectionEnd)
    }

    /** Removes the tag pair around the cursor and keeps its content. Returns false if there is none. */
    fun removeSurroundingTag(): Boolean {
        if (selectionStart < 0 || selectionEnd < 0) return false
        val edit = TagWrap.unwrap(text, selectionStart, selectionEnd, lang, ::inLiteral) ?: return false
        applyStep(edit)
        bringPointIntoView(selectionEnd)
        return true
    }

    /**
     * Removes the whitespace at the ends of lines as one undo step (see [TrimWhitespace]). The cursor stays where it
     * was and the editor does not scroll.
     */
    fun trimTrailingWhitespace() {
        val edit = TrimWhitespace.edit(text, selectionStart, selectionEnd) ?: return
        val x = scrollX
        val y = scrollY
        applyStep(edit)
        // The editor would scroll to the cursor as it draws next; put it back where the person was looking.
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                scrollTo(x, y)
                return true
            }
        })
    }

    private fun lo() = minOf(selectionStart, selectionEnd)
    private fun hi() = maxOf(selectionStart, selectionEnd)

    /** True for the characters the typing rules may act on: brackets, quotes, `>`, `=` and Enter. */
    private fun isTypingTrigger(c: Char): Boolean = c == '\n' || TypingLogic.isTrigger(c)

    /** A character the typing rules know, or Enter, was typed. Returns true if the typing rules did it instead of the plain character. */
    private fun handleTyped(c: Char): Boolean {
        if (selectionStart < 0 || selectionEnd < 0) return false
        if (c == '\n') {
            if (!smartIndent) return false
            applyEdit(TypingLogic.newline(text, lo(), hi(), lang, true, indentDefault.unit, splitTags))
            return true
        }
        if (!autoClose && !smartIndent && !(c == '>' && closeTags) && !quoteAttributes) return false
        val edit = TypingLogic.typed(
            text, selectionStart, selectionEnd, c, lang, autoClose, smartIndent, indentDefault.unit, ::inLiteral, closeTags,
            quoteAttributes,
        ) ?: return false
        applyEdit(edit)
        return true
    }

    /**
     * Backspace: with auto-close on, removes an empty pair such as () in one go; with smart indent on, a backspace in
     * the indentation of a line removes a whole level of spaces.
     */
    private fun handleBackspace(): Boolean {
        if ((!autoClose && !smartIndent) || selectionStart < 0) return false
        val edit = TypingLogic.backspace(text, selectionStart, selectionEnd, lang, autoClose, smartIndent, indentDefault.unit) ?: return false
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
            if (hardwareShortcuts && c != 0 &&
                onShortcut?.invoke(c.toChar().lowercaseChar(), true, event.isAltPressed, event.isShiftPressed) == true
            ) {
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
     * With [plainPaste], paste always pastes plain text. Text copied from a web page or a document would otherwise
     * bring its fonts and colours into the editor, where they fight the syntax colours. With [wholeLineClipboard],
     * copy and cut with nothing selected take the whole line, like most code editors do.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        when (id) {
            android.R.id.paste -> if (plainPaste) return super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
            android.R.id.copy -> if (wholeLineClipboard && selectionStart == selectionEnd && copyLine(cut = false)) return true
            android.R.id.cut -> if (wholeLineClipboard && selectionStart == selectionEnd && copyLine(cut = true)) return true
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
