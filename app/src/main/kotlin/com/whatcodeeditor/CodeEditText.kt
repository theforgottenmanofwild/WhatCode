package com.whatcodeeditor

import android.content.Context
import android.os.SystemClock
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.text.Editable
import android.widget.EditText

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

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCursorMoved?.invoke()
    }

    // ------------------------------------------------------------------ undo / redo

    private class Edit(var start: Int, var removed: String, var added: String, var time: Long)

    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()
    private var replaying = false
    private var pendingRemoved = ""

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                pendingRemoved = if (replaying) "" else s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (replaying) return
                record(start, pendingRemoved, s.subSequence(start, start + count).toString())
            }

            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun record(start: Int, removed: String, added: String) {
        if (removed.isEmpty() && added.isEmpty()) return
        redoStack.clear()
        val now = SystemClock.uptimeMillis()
        val last = undoStack.lastOrNull()
        if (last != null && now - last.time < 1000) {
            // A run of typed characters becomes one undo step.
            if (last.removed.isEmpty() && removed.isEmpty() && added.length == 1 && added != "\n" &&
                start == last.start + last.added.length
            ) {
                last.added += added
                last.time = now
                onHistoryChanged?.invoke()
                return
            }
            // So does a run of backspaces.
            if (last.added.isEmpty() && added.isEmpty() && removed.length == 1 && start + 1 == last.start) {
                last.start = start
                last.removed = removed + last.removed
                last.time = now
                onHistoryChanged?.invoke()
                return
            }
        }
        undoStack.add(Edit(start, removed, added, now))
        if (undoStack.size > 1000) undoStack.removeAt(0)
        onHistoryChanged?.invoke()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
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
        val e = undoStack.removeLastOrNull() ?: return false
        try {
            replay(e.start, e.start + e.added.length, e.removed)
            setSelection((e.start + e.removed.length).coerceIn(0, text.length))
            redoStack.add(e)
        } catch (_: Exception) {
            clearHistory()
        }
        onHistoryChanged?.invoke()
        return true
    }

    fun redo(): Boolean {
        val e = redoStack.removeLastOrNull() ?: return false
        try {
            replay(e.start, e.start + e.removed.length, e.added)
            setSelection((e.start + e.added.length).coerceIn(0, text.length))
            undoStack.add(e)
        } catch (_: Exception) {
            clearHistory()
        }
        onHistoryChanged?.invoke()
        return true
    }

    // ------------------------------------------------------------- typed characters

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(ic, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null && text.length == 1 && text[0] >= ' ' && onModifiedChar?.invoke(text[0]) == true) return true
                return super.commitText(text, newCursorPosition)
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null && text.length == 1 && text[0] >= ' ' && onModifiedChar?.invoke(text[0]) == true) {
                    finishComposingText()
                    return true
                }
                return super.setComposingText(text, newCursorPosition)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val c = event.unicodeChar
                    if (c >= ' '.code && onModifiedChar?.invoke(c.toChar()) == true) return true
                }
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
        }
        return super.onKeyDown(keyCode, event)
    }
}
