package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Selection
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** What the key rows need from the screen they sit on. */
interface KeyActions {
    fun save()
    fun openFind()

    /** Closes the find bar if it is open and returns true; returns false if there was nothing to close. */
    fun closeFind(): Boolean

    /** The comment markers of the open file's language: (start, end), with an empty end for line comments. */
    fun commentSyntax(): Pair<String, String>?

    /** Closes the open tab (asking first if it has unsaved changes). */
    fun closeTab()

    /** Moves to the next tab (1) or the previous one (-1), wrapping round. */
    fun switchTab(step: Int)
}

/**
 * Two rows of extra keys above the keyboard. Each row scrolls sideways on its own, and what is in them
 * (and in which order) can be changed from the menu (Edit keys) or from Settings > Code Editor > Key Editor.
 *
 * SHFT, CTRL and ALT are sticky: tap once for the next key only, tap again to lock, a third time to release.
 * Keys act when tapped, so a row can be scrolled by dragging. Arrow and line-move keys repeat while held.
 */
class KeyBar(
    private val activity: Activity,
    private val editor: CodeEditText,
    private val colorBar: Int,
    private val colorKey: Int,
    private val colorText: Int,
    private val colorAccent: Int,
    private val actions: KeyActions,
) {
    private companion object {
        const val CTRL = 0
        const val ALT = 1
        const val SHIFT = 2
        const val LEFT = 0
        const val RIGHT = 1
        const val UP = 2
        const val DOWN = 3
        val COLOR_PRESSED = 0xFF4A4A4C.toInt()
        val COLOR_LOCKED_TEXT = 0xFF1E1E1E.toInt()

    }

    private val ui = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("keys", Context.MODE_PRIVATE)
    private val settings = EditorSettings(activity)

    /** The language of the open file; with "keys by language" on, it decides which rows are shown. */
    private var language: Lang? = null

    /** Per modifier: 0 = off, 1 = next key only, 2 = locked. */
    private val mods = IntArray(3)
    private val modViews = Array(3) { ArrayList<TextView>() }
    private val undoKeys = ArrayList<TextView>()
    private val redoKeys = ArrayList<TextView>()

    private val rows: List<MutableList<String>> = KeyLayout.rowsFor(prefs, null, false)
    private val rowLayouts = listOf(LinearLayout(activity), LinearLayout(activity))

    val view: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(colorBar)
    }

    init {
        for (i in 0..1) {
            val scroll = HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(rowLayouts[i])
            }
            view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
        rebuild()
    }

    // ------------------------------------------------------------------ layout store

    /** Re-reads the saved layout, e.g. after it was changed from Settings, and redraws the rows if they changed. */
    fun reload() {
        val fresh = KeyLayout.rowsFor(prefs, language, settings.keysByLanguage)
        if (fresh[0] == rows[0] && fresh[1] == rows[1]) return
        rows[0].clear()
        rows[0].addAll(fresh[0])
        rows[1].clear()
        rows[1].addAll(fresh[1])
        rebuild()
    }

    /** The open file changed language: shows that language's keys when "keys by language" is on. */
    fun setLanguage(lang: Lang) {
        if (language?.name == lang.name) return
        language = lang
        reload()
    }

    private fun rebuild() {
        for (l in modViews) l.clear()
        undoKeys.clear()
        redoKeys.clear()
        for (i in 0..1) {
            rowLayouts[i].removeAllViews()
            for (id in rows[i]) {
                val k = makeKey(id) ?: continue
                rowLayouts[i].addView(k, LinearLayout.LayoutParams(dp(50), ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        paintMods()
        refreshStates()
    }

    // ------------------------------------------------------------------ building keys

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    private fun makeKey(id: String): TextView? {
        if (id.startsWith("t:")) {
            val s = id.substring(2)
            if (s.isEmpty()) return null
            return key(if (s.isBlank()) "␣" else s, "Insert $s") { insertText(s) }
        }
        return when (id) {
            "shift" -> modKey(SHIFT, "SHFT")
            "ctrl" -> modKey(CTRL, "CTRL")
            "alt" -> modKey(ALT, "ALT")
            "tab" -> key(Icon.TAB, "Tab") { EditOps.indent(editor, outdent = mods[SHIFT] != 0) }
            "find" -> key(Icon.FIND, "Find and replace") { actions.openFind() }
            "undo" -> key(Icon.UNDO, "Undo") { editor.undo() }.also { undoKeys.add(it) }
            "redo" -> key(Icon.REDO, "Redo") { editor.redo() }.also { redoKeys.add(it) }
            "up" -> key(Icon.UP, "Up", repeat = true) { arrow(UP) }
            "down" -> key(Icon.DOWN, "Down", repeat = true) { arrow(DOWN) }
            "left" -> key(Icon.PREVIOUS, "Left", repeat = true) { arrow(LEFT) }
            "right" -> key(Icon.NEXT, "Right", repeat = true) { arrow(RIGHT) }
            "save" -> key(Icon.SAVE, "Save") { actions.save() }
            "esc" -> key("ESC", "Escape", releases = false) { escape() }
            "movedown" -> key(Icon.MOVE_DOWN, "Move line down", repeat = true) { EditOps.moveLines(editor, up = false) }
            "moveup" -> key(Icon.MOVE_UP, "Move line up", repeat = true) { EditOps.moveLines(editor, up = true) }
            "copydown" -> key(Icon.COPY_DOWN, "Copy line down") { EditOps.duplicateLines(editor) }
            "comment" -> key("//", "Toggle comment") { toggleComment() }
            "goto" -> key("Go", "Go to line") { goToLine() }
            "delline" -> key(Icon.DELETE_LINE, "Delete line") { EditOps.deleteLines(editor) }
            else -> null
        }
    }

    /**
     * One key. It acts when the finger lifts, so dragging across keys scrolls the row instead of pressing them.
     * Keys marked [repeat] start repeating after a short hold. One-shot modifiers are used up by the key they apply to.
     */
    private fun key(
        label: CharSequence,
        description: String,
        repeat: Boolean = false,
        releases: Boolean = true,
        rest: () -> Int = { colorBar },
        action: () -> Unit,
    ): KeyView {
        val v = KeyView(activity)
        v.text = label
        v.gravity = Gravity.CENTER
        v.textSize = 15f
        v.setTextColor(colorText)
        v.setBackgroundColor(colorBar)
        v.contentDescription = description
        var repeater: Runnable? = null
        var fired = false
        v.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    v.setBackgroundColor(COLOR_PRESSED)
                    if (repeat) {
                        val r = object : Runnable {
                            override fun run() {
                                fired = true
                                action()
                                ui.postDelayed(this, 55)
                            }
                        }
                        repeater = r
                        ui.postDelayed(r, 380)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    repeater?.let { ui.removeCallbacks(it) }
                    repeater = null
                    if (!fired) action()
                    if (releases) releaseOneShot()
                    v.setBackgroundColor(rest())
                }
                MotionEvent.ACTION_CANCEL -> {
                    repeater?.let { ui.removeCallbacks(it) }
                    repeater = null
                    if (fired && releases) releaseOneShot()
                    v.setBackgroundColor(rest())
                }
            }
            true
        }
        return v
    }

    /** A key shown as a drawn icon (see Icons.kt) rather than as text. */
    private fun key(icon: Icon, description: String, repeat: Boolean = false, action: () -> Unit): KeyView =
        key("", description, repeat = repeat, action = action).also { it.setIcon(icon, colorText) }

    private fun modKey(i: Int, name: String): TextView {
        val v = key(name, name, releases = false, rest = { modBackground(i) }) {
            mods[i] = (mods[i] + 1) % 3
            paintMods()
        }
        modViews[i].add(v)
        return v
    }

    private fun modBackground(i: Int): Int = when (mods[i]) {
        0 -> colorBar
        1 -> colorKey
        else -> colorAccent
    }

    private fun paintMods() {
        for (i in 0..2) {
            for (v in modViews[i]) {
                v.setBackgroundColor(modBackground(i))
                v.setTextColor(
                    when (mods[i]) {
                        0 -> colorText
                        1 -> colorAccent
                        else -> COLOR_LOCKED_TEXT
                    }
                )
            }
        }
    }

    private fun releaseOneShot() {
        for (i in 0..2) if (mods[i] == 1) mods[i] = 0
        paintMods()
    }

    /** Dims undo and redo while there is nothing to undo or redo. */
    fun refreshStates() {
        for (v in undoKeys) v.alpha = if (editor.canUndo) 1f else 0.35f
        for (v in redoKeys) v.alpha = if (editor.canRedo) 1f else 0.35f
    }

    // ------------------------------------------------------------------- actions

    private fun insertText(s: String) {
        val a = minOf(editor.selectionStart, editor.selectionEnd)
        val b = maxOf(editor.selectionStart, editor.selectionEnd)
        editor.text.replace(a, b, s)
    }

    private fun escape() {
        if (!actions.closeFind()) {
            val e = editor.selectionEnd
            if (editor.selectionStart != e) editor.setSelection(e)
        }
        mods.fill(0)
        paintMods()
    }

    private fun moveTo(offset: Int, extend: Boolean) {
        val t = editor.text
        val o = offset.coerceIn(0, t.length)
        if (extend) Selection.extendSelection(t, o) else Selection.setSelection(t, o)
    }

    /**
     * Arrow keys. SHFT extends the selection. CTRL jumps by word (left/right) or to the start/end of the text (up/down).
     * ALT jumps to the start/end of the line (left/right).
     */
    private fun arrow(dir: Int) {
        val t = editor.text
        val shift = mods[SHIFT] != 0
        val ctrl = mods[CTRL] != 0
        val alt = mods[ALT] != 0
        val anchor = editor.selectionStart
        val ext = editor.selectionEnd
        when (dir) {
            LEFT -> moveTo(
                when {
                    alt -> EditOps.lineStart(t, ext)
                    ctrl -> EditOps.wordLeft(t, ext)
                    !shift && anchor != ext -> minOf(anchor, ext)
                    else -> ext - 1
                },
                shift
            )
            RIGHT -> moveTo(
                when {
                    alt -> EditOps.lineEnd(t, ext)
                    ctrl -> EditOps.wordRight(t, ext)
                    !shift && anchor != ext -> maxOf(anchor, ext)
                    else -> ext + 1
                },
                shift
            )
            UP -> if (ctrl) {
                moveTo(0, shift)
            } else {
                val layout = editor.layout ?: return
                if (shift) Selection.extendUp(t, layout) else Selection.moveUp(t, layout)
            }
            else -> if (ctrl) {
                moveTo(t.length, shift)
            } else {
                val layout = editor.layout ?: return
                if (shift) Selection.extendDown(t, layout) else Selection.moveDown(t, layout)
            }
        }
        editor.bringPointIntoView(editor.selectionEnd)
    }

    private fun toggleComment() {
        val syntax = actions.commentSyntax()
        if (syntax == null) {
            toast("This file type has no comment style")
            return
        }
        EditOps.toggleComment(editor, syntax.first, syntax.second)
    }

    private fun goToLine() {
        val total = editor.text.count { it == '\n' } + 1
        val input = EditText(activity).apply {
            hint = "1 to $total"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        val box = LinearLayout(activity).apply {
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Go to line")
            .setView(box)
            .setPositiveButton("Go") { _, _ ->
                val n = (input.text.toString().toIntOrNull() ?: 1).coerceIn(1, total)
                val t = editor.text
                var offset = 0
                var line = 1
                while (line < n && offset < t.length) {
                    if (t[offset] == '\n') line++
                    offset++
                }
                editor.setSelection(offset)
                editor.bringPointIntoView(offset)
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    /** Shortcuts, shared by the on-screen CTRL key and a hardware keyboard. */
    fun shortcut(ch: Char, ctrl: Boolean, alt: Boolean, shift: Boolean): Boolean {
        if (!ctrl || alt) return false
        when (ch) {
            's' -> actions.save()
            'f', 'h' -> actions.openFind()
            'z' -> if (shift) editor.redo() else editor.undo()
            'y' -> editor.redo()
            'a' -> editor.selectAll()
            'c' -> editor.onTextContextMenuItem(android.R.id.copy)
            'x' -> editor.onTextContextMenuItem(android.R.id.cut)
            'v' -> editor.onTextContextMenuItem(android.R.id.paste)
            'd' -> EditOps.duplicateLines(editor)
            'k' -> EditOps.deleteLines(editor)
            '/' -> toggleComment()
            'g' -> goToLine()
            'w' -> actions.closeTab()
            '\t' -> actions.switchTab(if (shift) -1 else 1)
            else -> return false
        }
        return true
    }

    /**
     * A character typed on the soft keyboard. With CTRL or ALT armed it runs a shortcut; with only SHFT armed
     * it is typed as a capital. Returns true when the character was used up here.
     */
    fun onTypedChar(c: Char): Boolean {
        val ctrl = mods[CTRL] != 0
        val alt = mods[ALT] != 0
        val shift = mods[SHIFT] != 0
        if (!ctrl && !alt && !shift) return false
        if (ctrl || alt) {
            if (!shortcut(c.lowercaseChar(), ctrl, alt, shift)) {
                toast((if (ctrl) "Ctrl+" else "Alt+") + c.uppercaseChar() + " isn't a shortcut")
            }
        } else {
            insertText(c.uppercaseChar().toString())
        }
        releaseOneShot()
        return true
    }

    // --------------------------------------------------------------- editing the keys

    /**
     * Menu > Edit keys: the quick dialogs, for the open file's language when "keys by language" is on. The
     * fuller page is Settings > Code Editor > Key Editor.
     */
    fun showEditor() {
        KeyEditor(activity, language.takeIf { settings.keysByLanguage }) { reload() }.show()
    }
}
