package com.whatcode.editor.multi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.EditOps
import com.whatcode.settings.EditorSettings

/**
 * Editing in several places at once, for [CodeEditText]. The cursor the platform knows is the primary one; the others
 * are kept here as [extras] and drawn here. While there are any, what is typed, deleted, pasted, cut or copied goes
 * through [MultiSelect], which makes it one change to the text, so it is one undo step.
 *
 * The extra cursors are dropped when something else changes the text (undo, redo, a tool) or the primary cursor is
 * moved by a touch, because their places would no longer mean anything.
 */
class MultiCursor(private val view: CodeEditText) {

    /** See [EditorSettings.multiCursor]. Off, nothing here can start. */
    var enabled = EditorSettings.DEFAULT_MULTI_CURSOR

    /** See [EditorSettings.multiCursorMax]: the most cursors at once, the first one included. */
    var max = EditorSettings.DEFAULT_MULTI_CURSOR_MAX

    /** See [EditorSettings.multiCursorMatchCase] and [EditorSettings.multiCursorWholeWord]: how the next place is found. */
    var matchCase = EditorSettings.DEFAULT_MULTI_CURSOR_MATCH_CASE
    var wholeWord = EditorSettings.DEFAULT_MULTI_CURSOR_WHOLE_WORD

    /** See [EditorSettings.multiCursorPasteLines]: a paste with as many lines as there are cursors gives one line to each. */
    var pasteLines = EditorSettings.DEFAULT_MULTI_CURSOR_PASTE_LINES

    /** Says something to the person, such as why a cursor was not added. */
    var onMessage: ((String) -> Unit)? = null

    /** The cursors besides the one the platform keeps, sorted, and apart from it and from each other. */
    var extras: List<Sel> = emptyList()
        private set

    val active: Boolean get() = extras.isNotEmpty()

    /** How many cursors there are, the primary one included. */
    val count: Int get() = if (active) extras.size + 1 else 1

    /** True while this class changes the text or the selection itself, so that the view does not take it for a touch. */
    var applying = false
        private set

    /** The text a keyboard is composing, which was put in at every cursor; it is replaced as the word grows. */
    private var composing = ""

    private fun say(msg: String) {
        onMessage?.invoke(msg)
    }

    private fun primarySel(): Sel {
        val a = view.selectionStart
        val b = view.selectionEnd
        val n = view.text.length
        val lo = minOf(a, b).coerceIn(0, n)
        val hi = maxOf(a, b).coerceIn(0, n)
        return Sel(lo, hi)
    }

    /** Every cursor, sorted, and the index of the primary one. */
    private fun all(): Pair<List<Sel>, Int> = MultiSelect.normalize(extras + primarySel(), primarySel())

    // ----------------------------------------------------------------- starting and stopping

    /** Drops the extra cursors; the primary one stays. */
    fun clear() {
        if (extras.isEmpty() && composing.isEmpty()) return
        extras = emptyList()
        composing = ""
        view.invalidate()
    }

    /** Called when the primary cursor was moved by something other than this class. */
    fun selectionMoved() {
        if (!applying) clear()
    }

    /** Called when the text was changed by something other than this class. */
    fun textChanged() {
        if (!applying) clear()
    }

    /** Settles the keyboard's idea of what is being composed, so that it starts again from the new places. */
    private fun resetKeyboard() {
        composing = ""
        BaseInputConnection.removeComposingSpans(view.text)
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.restartInput(view)
    }

    private fun needle(): Sel? {
        val p = primarySel()
        return if (p.empty) MultiSelect.wordAt(view.text, p.start) else p
    }

    /**
     * Adds a cursor at the next place after the selection that holds the same text, and makes it the primary one. With
     * nothing selected it selects the word at the cursor first. Returns a message when nothing was done, or null.
     */
    fun addNext(): String? {
        if (!enabled) return "Multiple cursors are off. Turn them on in Settings > Multiple cursors."
        val t = view.text
        val p = primarySel()
        if (p.empty) {
            val w = MultiSelect.wordAt(t, p.start) ?: return "Put the cursor in a word, or select some text, first."
            applying = true
            try {
                view.setSelection(w.start, w.end)
            } finally {
                applying = false
            }
            return null
        }
        if (p.end - p.start > MultiSelect.MAX_NEEDLE) return "That is too much text to look for."
        if (count >= max) return "That is the most cursors there can be ($max). Change it in Settings > Multiple cursors."
        val (all, _) = all()
        val text = t.subSequence(p.start, p.end).toString()
        val next = MultiSelect.nextOccurrence(t, text, p.end, all, matchCase, wholeWord)
            ?: return "No more of that text to add."
        resetKeyboard()
        val keep = extras + p
        extras = keep.sortedBy { it.start }
        applying = true
        try {
            view.setSelection(next.start, next.end)
        } finally {
            applying = false
        }
        view.invalidate()
        return null
    }

    /** Puts a cursor at every place that holds the selected text, or the word at the cursor. Returns a message when nothing was done. */
    fun addAll(): String? {
        if (!enabled) return "Multiple cursors are off. Turn them on in Settings > Multiple cursors."
        val n = needle() ?: return "Put the cursor in a word, or select some text, first."
        val t = view.text
        if (n.end - n.start > MultiSelect.MAX_NEEDLE) return "That is too much text to look for."
        val text = t.subSequence(n.start, n.end).toString()
        val found = MultiSelect.allOccurrences(t, text, matchCase, wholeWord, max + 1)
        if (found.size > max) return "More than $max places hold that text. Change the most cursors in Settings > Multiple cursors."
        if (found.size < 2) {
            applying = true
            try {
                view.setSelection(n.start, n.end)
            } finally {
                applying = false
            }
            return "That text is only in one place."
        }
        resetKeyboard()
        val primary = found.firstOrNull { it.start >= primarySel().start } ?: found.first()
        extras = found.filter { it != primary }
        applying = true
        try {
            view.setSelection(primary.start, primary.end)
        } finally {
            applying = false
        }
        view.invalidate()
        return null
    }

    // ----------------------------------------------------------------- changing the text

    private fun tooWide(all: List<Sel>): Boolean = all.last().end - all.first().start > MultiSelect.MAX_SPAN

    /** Makes [a] as one change and puts the cursors where it says. Returns true when there was something to change. */
    private fun apply(a: Applied?): Boolean {
        if (a == null) return false
        applying = true
        try {
            view.multiEdit(a.start, a.end, a.insert)
            val n = view.text.length
            val ranges = a.ranges.map { Sel(it.start.coerceIn(0, n), it.end.coerceIn(0, n)) }
            val primary = ranges[a.primary.coerceIn(0, ranges.size - 1)]
            extras = ranges.filterIndexed { i, _ -> i != a.primary }
            view.setSelection(primary.start, primary.end)
        } finally {
            applying = false
        }
        view.invalidate()
        return true
    }

    private fun run(change: (List<Sel>, Int) -> Applied?): Boolean {
        val (all, p) = all()
        if (tooWide(all)) {
            say("The cursors are too far apart to change them all at once.")
            return true
        }
        apply(change(all, p))
        return true
    }

    /** [text] typed at every cursor. */
    fun insert(text: String): Boolean {
        composing = ""
        return run { all, p -> MultiSelect.insert(view.text, all, p, text) }
    }

    /**
     * What a keyboard sends while it composes a word: it replaces the word it sent before. The text is put in at every
     * cursor; [commit] is the same and ends the word.
     */
    fun compose(text: String): Boolean = replaceComposing(text, keep = true)

    fun commit(text: String): Boolean = replaceComposing(text, keep = false)

    private fun replaceComposing(text: String, keep: Boolean): Boolean {
        val (all, p) = all()
        if (tooWide(all)) {
            say("The cursors are too far apart to change them all at once.")
            return true
        }
        val len = composing.length
        val wide = if (len == 0) all else all.map { if (it.empty) Sel((it.start - len).coerceAtLeast(0), it.end) else it }
        composing = if (keep) text else ""
        apply(MultiSelect.replace(view.text, wide, p) { text })
        return true
    }

    /** The keyboard ended the word it was composing; the text stays. */
    fun finishComposing() {
        composing = ""
    }

    fun backspace(count: Int = 1): Boolean {
        composing = ""
        return run { all, p -> MultiSelect.backspace(view.text, all, p, count) }
    }

    fun deleteForward(count: Int = 1): Boolean {
        composing = ""
        return run { all, p -> MultiSelect.deleteForward(view.text, all, p, count) }
    }

    /** A keyboard asked to delete [before] characters before and [after] after each cursor. */
    fun removeAround(before: Int, after: Int): Boolean {
        composing = ""
        return run { all, p -> MultiSelect.removeAround(view.text, all, p, before, after) }
    }

    fun newline(): Boolean {
        composing = ""
        return run { all, p -> MultiSelect.newline(view.text, all, p) }
    }

    /** Tab: one level of indentation at every cursor. Shift+Tab does nothing while there are several. */
    fun tab(outdent: Boolean): Boolean {
        if (outdent) return true
        return insert(view.indentUnitNow())
    }

    // ----------------------------------------------------------------- moving

    /** Moves every cursor with [step]; a selection is dropped at its start, or its end with [toEnd]. */
    fun move(toEnd: Boolean, step: (CharSequence, Int) -> Int): Boolean {
        val (all, p) = all()
        val (moved, np) = MultiSelect.move(view.text, all, p, toEnd, step)
        composing = ""
        extras = moved.filterIndexed { i, _ -> i != np }
        applying = true
        try {
            val s = moved[np]
            view.setSelection(s.start, s.end)
        } finally {
            applying = false
        }
        view.invalidate()
        return true
    }

    fun left(word: Boolean = false, home: Boolean = false): Boolean = move(false) { t, pos ->
        when {
            home -> EditOps.smartHome(t, pos)
            word -> EditOps.wordLeft(t, pos)
            else -> EditOps.prevIndex(t, pos)
        }
    }

    fun right(word: Boolean = false, end: Boolean = false): Boolean = move(true) { t, pos ->
        when {
            end -> EditOps.lineEnd(t, pos)
            word -> EditOps.wordRight(t, pos)
            else -> EditOps.nextIndex(t, pos)
        }
    }

    // ----------------------------------------------------------------- the clipboard

    private fun clipboard() = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    /** Copies the selected text of every cursor, one under the other. Returns false when nothing is selected. */
    fun copy(): Boolean {
        val (all, _) = all()
        if (all.all { it.empty }) return false
        clipboard()?.setPrimaryClip(ClipData.newPlainText("selections", MultiSelect.joined(view.text, all.filter { !it.empty })))
        return true
    }

    fun cut(): Boolean {
        if (!copy()) return false
        composing = ""
        return run { all, p -> MultiSelect.replace(view.text, all, p) { "" } }
    }

    fun paste(): Boolean {
        val clip = clipboard()?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(view.context)?.toString()
            ?: return true
        composing = ""
        return run { all, p ->
            val pieces = if (pasteLines) MultiSelect.splitForPaste(clip, all.size) else null
            MultiSelect.replace(view.text, all, p) { i -> pieces?.get(i) ?: clip }
        }
    }

    // ----------------------------------------------------------------- keys

    /**
     * A key of a hardware keyboard (or one sent by a soft keyboard) while there are several cursors. Returns true when
     * it was used here. Up and down drop the extra cursors and are then handled as usual.
     */
    fun onKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            if (!event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
            // Control moves by words with the arrows; every other shortcut goes on to the shortcut rules.
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> !event.isShiftPressed && left(word = true)
                KeyEvent.KEYCODE_DPAD_RIGHT -> !event.isShiftPressed && right(word = true)
                else -> false
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> backspace()
            KeyEvent.KEYCODE_FORWARD_DEL -> deleteForward()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> newline()
            KeyEvent.KEYCODE_TAB -> tab(event.isShiftPressed)
            KeyEvent.KEYCODE_ESCAPE -> { clear(); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (event.isShiftPressed) { clear(); false } else left()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.isShiftPressed) { clear(); false } else right()
            KeyEvent.KEYCODE_MOVE_HOME -> if (event.isShiftPressed) { clear(); false } else left(home = true)
            KeyEvent.KEYCODE_MOVE_END -> if (event.isShiftPressed) { clear(); false } else right(end = true)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                clear()
                false
            }
            else -> {
                // A key that is a dead accent has a negative value here, so it is not typed.
                val c = event.unicodeChar
                if (c >= ' '.code && c != 0x7f && c <= Character.MAX_CODE_POINT) {
                    insert(String(Character.toChars(c)))
                } else {
                    false
                }
            }
        }
    }

    // ----------------------------------------------------------------- drawing

    private val caretPaint = Paint()
    private val selectionPaint = Paint()
    private val path = Path()
    private val clip = android.graphics.Rect()

    /** Draws the extra cursors, a thin bar for an empty one and a coloured band behind the text for a selection. */
    fun draw(canvas: Canvas, l: Layout) {
        if (extras.isEmpty()) return
        val dx = view.totalPaddingLeft.toFloat()
        val dy = view.totalPaddingTop.toFloat()
        canvas.getClipBounds(clip)
        val n = view.text.length
        val bar = maxOf(2f, 1.5f * view.resources.displayMetrics.density)
        caretPaint.color = view.currentTextColor
        selectionPaint.color = view.highlightColor
        canvas.save()
        canvas.translate(dx, dy)
        for (s in extras) {
            if (s.end > n) continue
            val line = l.getLineForOffset(s.start)
            val top = l.getLineTop(line) + dy
            val lastLine = l.getLineForOffset(s.end)
            val bottom = l.getLineBottom(lastLine) + dy
            if (bottom < clip.top || top > clip.bottom) continue
            if (s.empty) {
                val x = l.getPrimaryHorizontal(s.start)
                canvas.drawRect(x - bar / 2f, l.getLineTop(line).toFloat(), x + bar / 2f, l.getLineBottom(line).toFloat(), caretPaint)
            } else {
                path.reset()
                l.getSelectionPath(s.start, s.end, path)
                canvas.drawPath(path, selectionPaint)
            }
        }
        canvas.restore()
    }
}
