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

    // ------------------------------------------------------------------ keys by language

    /**
     * Symbol keys that replace the text keys of the rows for these languages (row 1, row 2), until the person
     * edits the keys of that language. Languages that are not listed use the plain layout.
     */
    private val SYMBOLS: Map<String, Pair<String, String>> = mapOf(
        "Go" to ("{ } ( ) [ ] := <-" to "\" ' ` ; : = == != & * | ! , . ... _"),
        "Python" to ("( ) [ ] { } : ->" to "\" ' # = == != + - * / % @ , . _ \\"),
        "JavaScript" to ("{ } ( ) [ ] => `" to "; : \" ' = === !== && || ! ? . , \$ \${ _"),
        "TypeScript" to ("{ } ( ) [ ] => `" to "; : \" ' = === !== && || ! ? . , \$ \${ _"),
        "Kotlin" to ("{ } ( ) [ ] < > ->" to "; : \" ' = == != && || ? ?. ?: . , \$ _ ::"),
        "Rust" to ("{ } ( ) [ ] < > ::" to "; : \" ' & * | ! = => -> _ # ? . ,"),
        "Shell" to ("( ) { } [ ] \$ \${" to "| & ; > < \" ' ` = ! ~ / \\ # *"),
        "SQL" to ("( ) , ; ' \" * =" to "% _ . < > != -- /* */ ?"),
        "JSON" to ("{ } [ ] : , \"" to "true false null . - + _ \\ /"),
        "JSON with comments" to ("{ } [ ] : , \"" to "true false null . - + _ \\ /"),
        "Makefile" to ("\$( ) { } [ ] := ?=" to "@ % \\ # ; : = + \" ' / . - _ *"),
    )

    /** The same for a whole kind of language, used when the language itself is not listed above. */
    private val FAMILY_SYMBOLS: Map<Family, Pair<String, String>> = mapOf(
        Family.MARKUP to ("< > </> /> = \" '" to "& ; # ! - / : . , _ ( ) [ ]"),
        Family.CSS to ("{ } ( ) : ; #" to "\" ' . , % - _ / * > ~ + @ ! ="),
        Family.YAML to ("- : { } [ ] | >" to "\" ' # & * ! ? , . = _ / %"),
        Family.INI to ("[ ] = \" ' { }" to "# ; : . , - _ / \\ \$ %"),
        Family.MARKDOWN to ("# * _ ` ``` [ ] ( )" to "- > ! | ~ \" ' < = + . :"),
    )

    private fun profileKey(row: Int, lang: Lang) = "row$row@${lang.name}"

    /** True when this language has built-in symbol keys of its own. */
    fun hasPreset(lang: Lang): Boolean = SYMBOLS.containsKey(lang.name) || FAMILY_SYMBOLS.containsKey(lang.family)

    /** True when the person has changed the keys of this language. */
    fun hasSaved(prefs: SharedPreferences, lang: Lang): Boolean =
        prefs.contains(profileKey(1, lang)) || prefs.contains(profileKey(2, lang))

    private fun withSymbols(row: List<String>, extra: String): MutableList<String> =
        (row.filter { !it.startsWith("t:") } + symbols(extra)).toMutableList()

    /**
     * The rows for [lang] before the person has changed them: the saved layout for all languages, with the text
     * keys swapped for the language's built-in symbols if it has any. With no language it is the saved layout.
     */
    fun baseRows(prefs: SharedPreferences, lang: Lang?): List<MutableList<String>> {
        val g1 = load(prefs, "row1", DEFAULT_ROW1)
        val g2 = load(prefs, "row2", DEFAULT_ROW2)
        if (lang == null) return listOf(g1, g2)
        val preset = SYMBOLS[lang.name] ?: FAMILY_SYMBOLS[lang.family] ?: return listOf(g1, g2)
        return listOf(withSymbols(g1, preset.first), withSymbols(g2, preset.second))
    }

    /**
     * The rows to show for [lang]. With [byLanguage] off, or with no language, that is the saved layout for all
     * languages. Otherwise it is the layout the person saved for the language, else [baseRows].
     */
    fun rowsFor(prefs: SharedPreferences, lang: Lang?, byLanguage: Boolean): List<MutableList<String>> {
        if (!byLanguage || lang == null) return baseRows(prefs, null)
        val base = baseRows(prefs, lang)
        if (!hasSaved(prefs, lang)) return base
        return listOf(
            load(prefs, profileKey(1, lang), base[0]),
            load(prefs, profileKey(2, lang), base[1]),
        )
    }

    /** Saves the rows as the layout of one language. */
    fun saveFor(prefs: SharedPreferences, lang: Lang, rows: List<List<String>>) {
        prefs.edit()
            .putString(profileKey(1, lang), JSONArray(rows[0]).toString())
            .putString(profileKey(2, lang), JSONArray(rows[1]).toString())
            .apply()
    }

    /** Forgets the layout saved for one language, so it goes back to its built-in keys. */
    fun clearFor(prefs: SharedPreferences, lang: Lang) {
        prefs.edit().remove(profileKey(1, lang)).remove(profileKey(2, lang)).apply()
    }
}

/**
 * The quick dialogs behind the editor menu's "Edit keys". They change the same saved layout as the fuller
 * Settings > Code Editor > Key Editor page ([KeyEditorActivity]). With a [lang] they edit the keys of that
 * language only; without one they edit the layout used for every language. [onChanged] runs after every change
 * is saved, which the editor screen uses to redraw its rows with [KeyBar.reload].
 */
class KeyEditor(
    private val activity: Activity,
    private val lang: Lang? = null,
    private val onChanged: () -> Unit = {},
) {

    private val prefs = activity.getSharedPreferences(KeyLayout.PREFS, Context.MODE_PRIVATE)
    private val rows: List<MutableList<String>> = KeyLayout.rowsFor(prefs, lang, lang != null)

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()

    private fun persist() {
        if (lang == null) KeyLayout.save(prefs, rows) else KeyLayout.saveFor(prefs, lang, rows)
        onChanged()
    }

    fun show() {
        AlertDialog.Builder(activity)
            .setTitle(if (lang == null) "Edit keys" else "Edit keys · ${lang.name}")
            .setItems(arrayOf("Row 1", "Row 2", "Reset both rows to default")) { _, which ->
                when (which) {
                    0 -> editRow(0)
                    1 -> editRow(1)
                    else -> AlertDialog.Builder(activity)
                        .setTitle("Reset keys?")
                        .setMessage("Put both rows back to the default keys?")
                        .setPositiveButton("Reset") { _, _ ->
                            if (lang == null) {
                                rows[0].clear()
                                rows[0].addAll(KeyLayout.DEFAULT_ROW1)
                                rows[1].clear()
                                rows[1].addAll(KeyLayout.DEFAULT_ROW2)
                                persist()
                            } else {
                                // Forget what was saved for this language; it goes back to its built-in keys.
                                KeyLayout.clearFor(prefs, lang)
                                val fresh = KeyLayout.rowsFor(prefs, lang, true)
                                rows[0].clear()
                                rows[0].addAll(fresh[0])
                                rows[1].clear()
                                rows[1].addAll(fresh[1])
                                onChanged()
                            }
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
