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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/** What the key rows need from the screen they sit on. */
interface KeyActions {
    fun save()
    fun openFind()

    /** Closes the find bar if it is open and returns true; returns false if there was nothing to close. */
    fun closeFind(): Boolean

    /** The comment markers of the open file's language: (start, end), with an empty end for line comments. */
    fun commentSyntax(): Pair<String, String>?
}

/**
 * Two rows of extra keys above the keyboard. Each row scrolls sideways on its own, and what is in them
 * (and in which order) can be changed from the menu: Edit keys.
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
        const val MAX_KEYS = 60
        val COLOR_PRESSED = 0xFF4A4A4C.toInt()
        val COLOR_LOCKED_TEXT = 0xFF1E1E1E.toInt()

        /** Every built-in key: id to the name shown when editing. */
        val BUILTIN: List<Pair<String, String>> = listOf(
            "shift" to "Shift (modifier)",
            "ctrl" to "Ctrl (modifier)",
            "alt" to "Alt (modifier)",
            "tab" to "Tab / indent",
            "find" to "Find and replace",
            "undo" to "Undo",
            "redo" to "Redo",
            "up" to "Cursor up",
            "down" to "Cursor down",
            "left" to "Cursor left",
            "right" to "Cursor right",
            "save" to "Save",
            "esc" to "Escape",
            "movedown" to "Move line down",
            "moveup" to "Move line up",
            "copydown" to "Copy line down",
            "comment" to "Toggle comment",
            "goto" to "Go to line",
            "delline" to "Delete line",
        )

        private fun symbols(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { "t:$it" }

        val DEFAULT_ROW1: List<String> =
            listOf("shift", "tab", "find", "undo", "redo", "up", "save", "esc", "comment", "goto") +
                symbols("{ } ( ) [ ] < >")
        val DEFAULT_ROW2: List<String> =
            listOf("ctrl", "alt", "movedown", "moveup", "left", "down", "right", "copydown", "delline") +
                symbols("; : \" ' / \\ = + - _ * & | ! # $")
    }

    private val ui = Handler(Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("keys", Context.MODE_PRIVATE)

    /** Per modifier: 0 = off, 1 = next key only, 2 = locked. */
    private val mods = IntArray(3)
    private val modViews = Array(3) { ArrayList<TextView>() }
    private val undoKeys = ArrayList<TextView>()
    private val redoKeys = ArrayList<TextView>()

    private val rows: List<MutableList<String>> = listOf(load("row1", DEFAULT_ROW1), load("row2", DEFAULT_ROW2))
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

    private fun load(name: String, default: List<String>): MutableList<String> {
        val s = prefs.getString(name, null) ?: return default.toMutableList()
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) { a.getString(it) }
        } catch (_: Exception) {
            default.toMutableList()
        }
    }

    private fun persist() {
        prefs.edit()
            .putString("row1", JSONArray(rows[0]).toString())
            .putString("row2", JSONArray(rows[1]).toString())
            .apply()
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

    private fun nameOf(id: String): String =
        if (id.startsWith("t:")) "Text: ${id.substring(2).ifBlank { "(space)" }}" else BUILTIN.firstOrNull { it.first == id }?.second ?: id

    /** Menu > Edit keys. */
    fun showEditor() {
        AlertDialog.Builder(activity)
            .setTitle("Edit keys")
            .setItems(arrayOf("Row 1", "Row 2", "Reset both rows to default")) { _, which ->
                when (which) {
                    0 -> editRow(0)
                    1 -> editRow(1)
                    else -> AlertDialog.Builder(activity)
                        .setTitle("Reset keys?")
                        .setMessage("Put both rows back to the default keys?")
                        .setPositiveButton("Reset") { _, _ ->
                            rows[0].clear()
                            rows[0].addAll(DEFAULT_ROW1)
                            rows[1].clear()
                            rows[1].addAll(DEFAULT_ROW2)
                            persist()
                            rebuild()
                            toast("Keys reset")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun editRow(i: Int) {
        val row = rows[i]
        val list = ListView(activity)
        fun refresh() {
            list.adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_list_item_1,
                row.mapIndexed { n, id -> "${n + 1}.  ${nameOf(id)}" }
            )
        }
        refresh()
        list.setOnItemClickListener { _, _, pos, _ ->
            val id = row[pos]
            AlertDialog.Builder(activity)
                .setTitle(nameOf(id))
                .setItems(arrayOf("Move earlier", "Move later", "Move to start", "Move to end", "Remove")) { _, which ->
                    val moved = row.removeAt(pos)
                    when (which) {
                        0 -> row.add((pos - 1).coerceAtLeast(0), moved)
                        1 -> row.add((pos + 1).coerceAtMost(row.size), moved)
                        2 -> row.add(0, moved)
                        3 -> row.add(moved)
                        else -> {}
                    }
                    persist()
                    rebuild()
                    refresh()
                }
                .show()
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(
                TextView(activity).apply {
                    text = "Tap a key to move or remove it. New keys are added at the end."
                    textSize = 12f
                    setPadding(dp(4), 0, dp(4), dp(8))
                }
            )
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.5).toInt()))
        }
        AlertDialog.Builder(activity)
            .setTitle("Row ${i + 1}")
            .setView(box)
            .setPositiveButton("Add key") { _, _ -> addKey(i) }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun addKey(i: Int) {
        if (rows[i].size >= MAX_KEYS) {
            toast("A row can have up to $MAX_KEYS keys")
            editRow(i)
            return
        }
        val names = BUILTIN.map { it.second } + "Text or symbol…"
        AlertDialog.Builder(activity)
            .setTitle("Add to row ${i + 1}")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < BUILTIN.size) {
                    rows[i].add(BUILTIN[which].first)
                    persist()
                    rebuild()
                    editRow(i)
                } else {
                    askText(i)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> editRow(i) }
            .show()
    }

    private fun askText(i: Int) {
        val input = EditText(activity).apply {
            hint = "What the key types, e.g. =>"
            setSingleLine()
        }
        val box = LinearLayout(activity).apply {
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Text key")
            .setView(box)
            .setPositiveButton("Add") { _, _ ->
                val s = input.text.toString()
                if (s.isNotEmpty()) {
                    rows[i].add("t:$s")
                    persist()
                    rebuild()
                }
                editRow(i)
            }
            .setNegativeButton("Cancel") { _, _ -> editRow(i) }
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        input.requestFocus()
    }
}
