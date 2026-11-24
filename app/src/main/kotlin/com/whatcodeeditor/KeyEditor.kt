package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

/** What the two key rows contain, and where it is stored. Shared by [KeyBar] and [KeyEditor]. */
object KeyLayout {
    const val PREFS = "keys"
    const val MAX_KEYS = 60

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

    fun nameOf(id: String): String =
        if (id.startsWith("t:")) "Text: ${id.substring(2).ifBlank { "(space)" }}" else BUILTIN.firstOrNull { it.first == id }?.second ?: id

    fun load(prefs: SharedPreferences, name: String, default: List<String>): MutableList<String> {
        val s = prefs.getString(name, null) ?: return default.toMutableList()
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) { a.getString(it) }
        } catch (_: Exception) {
            default.toMutableList()
        }
    }

    fun save(prefs: SharedPreferences, rows: List<List<String>>) {
        prefs.edit()
            .putString("row1", JSONArray(rows[0]).toString())
            .putString("row2", JSONArray(rows[1]).toString())
            .apply()
    }
}

/**
 * The quick dialogs behind the editor menu's "Edit keys". They change the same saved layout as the fuller
 * Settings > Code Editor > Key Editor page ([KeyEditorActivity]). [onChanged] runs after every change is
 * saved, which the editor screen uses to redraw its rows with [KeyBar.reload].
 */
class KeyEditor(private val activity: Activity, private val onChanged: () -> Unit = {}) {

    private val prefs = activity.getSharedPreferences(KeyLayout.PREFS, Context.MODE_PRIVATE)
    private val rows: List<MutableList<String>> = listOf(
        KeyLayout.load(prefs, "row1", KeyLayout.DEFAULT_ROW1),
        KeyLayout.load(prefs, "row2", KeyLayout.DEFAULT_ROW2),
    )

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    private fun persist() {
        KeyLayout.save(prefs, rows)
        onChanged()
    }

    fun show() {
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
                            rows[0].addAll(KeyLayout.DEFAULT_ROW1)
                            rows[1].clear()
                            rows[1].addAll(KeyLayout.DEFAULT_ROW2)
                            persist()
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
                row.mapIndexed { n, id -> "${n + 1}.  ${KeyLayout.nameOf(id)}" }
            )
        }
        refresh()
        list.setOnItemClickListener { _, _, pos, _ ->
            val id = row[pos]
            AlertDialog.Builder(activity)
                .setTitle(KeyLayout.nameOf(id))
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
        if (rows[i].size >= KeyLayout.MAX_KEYS) {
            toast("A row can have up to ${KeyLayout.MAX_KEYS} keys")
            editRow(i)
            return
        }
        val names = KeyLayout.BUILTIN.map { it.second } + "Text or symbol…"
        AlertDialog.Builder(activity)
            .setTitle("Add to row ${i + 1}")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < KeyLayout.BUILTIN.size) {
                    rows[i].add(KeyLayout.BUILTIN[which].first)
                    persist()
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
