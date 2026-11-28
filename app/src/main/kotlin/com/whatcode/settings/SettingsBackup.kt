package com.whatcode.settings

import com.whatcode.bigfile.LargeFileConfig
import com.whatcode.editor.IndentDefault
import com.whatcode.explorer.ExplorerOptions
import com.whatcode.explorer.ExplorerSettings
import com.whatcode.editor.SyntaxRole
import com.whatcode.editor.snippets.SnippetFile
import com.whatcode.keys.KeyLayout
import com.whatcode.ui.AccentColor
import com.whatcode.ui.ThemeMode

/**
 * What a settings backup holds and how one is written and checked. A backup is one JSON text:
 *
 *     { "app": "WhatCode", "format": 1, "version": "0.0.5", "settings": { "editor": { "word_wrap": true, ... }, ... } }
 *
 * Each entry of `settings` is one saved file of the app (Typing and Tabs are in `editor`, Appearance in `appearance`,
 * and so on) and holds that file's choices. Nothing here touches the device, so it can be tested with plain text;
 * [SettingsBackupStore] connects it to the saved settings.
 *
 * A backup is a file from outside the app, so nothing in it is trusted. Only the settings listed in [SECTIONS] are
 * read, each with the kind of value it takes, and anything else is counted as skipped and thrown away.
 */
object SettingsBackup {

    const val APP = "WhatCode"

    /** Raised when the layout of the file changes in a way an older app cannot read. */
    const val FORMAT = 1

    /** The longest text that is read as a backup. A real one is a few kilobytes. */
    const val MAX_BYTES = 256 * 1024

    private const val MAX_DEPTH = 8

    // ------------------------------------------------------------------ what each setting holds

    /** The kind of value a setting takes. [fromFile] checks a value read from a backup, [fromPref] one that is saved. */
    sealed class Kind {
        /** The value to save for [v] from a backup, or null if it is not valid for this setting. */
        abstract fun fromFile(v: Any?): Any?

        /** The value to write in a backup for [v] as it is saved on the device, or null if it is not valid. */
        abstract fun fromPref(v: Any?): Any?

        /** On or off. */
        object Flag : Kind() {
            override fun fromFile(v: Any?): Any? = v as? Boolean
            override fun fromPref(v: Any?): Any? = v as? Boolean
        }

        /** A whole number from [min] to [max]. */
        class Whole(private val min: Int, private val max: Int) : Kind() {
            override fun fromFile(v: Any?): Any? = (v as? Long)?.takeIf { it in min..max }?.toInt()
            override fun fromPref(v: Any?): Any? = (v as? Int)?.takeIf { it in min..max }
        }

        /** One of a fixed list of words. */
        class Choice(private val options: Set<String>) : Kind() {
            override fun fromFile(v: Any?): Any? = (v as? String)?.takeIf { it in options }
            override fun fromPref(v: Any?): Any? = (v as? String)?.takeIf { it in options }
        }

        /** A line of text of up to [max] characters, with no line breaks or other control characters. */
        class Text(private val max: Int) : Kind() {
            override fun fromFile(v: Any?): Any? = (v as? String)?.takeIf { ok(it) }
            override fun fromPref(v: Any?): Any? = (v as? String)?.takeIf { ok(it) }
            private fun ok(s: String) = s.length <= max && s.none { it < ' ' }
        }

        /** A line of text that [ok] accepts. */
        class Checked(private val ok: (String) -> Boolean) : Kind() {
            override fun fromFile(v: Any?): Any? = (v as? String)?.takeIf(ok)
            override fun fromPref(v: Any?): Any? = (v as? String)?.takeIf(ok)
        }

        /**
         * A row of extra keys: a list of key ids. It is saved on the device as the JSON text of that list and written
         * in a backup as the list itself, so it can be read and edited in the file.
         */
        object Keys : Kind() {
            override fun fromFile(v: Any?): Any? {
                val list = v as? List<*> ?: return null
                if (list.any { it !is String }) return null
                return Json.write(keyIds(list.filterIsInstance<String>()))
            }

            override fun fromPref(v: Any?): Any? {
                val text = v as? String ?: return null
                val list = try {
                    Json.parse(text) as? List<*>
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return null
                if (list.any { it !is String }) return null
                return keyIds(list.filterIsInstance<String>())
            }

            /** The ids that this version has a key for, and no more than a row can hold. */
            private fun keyIds(ids: List<String>): List<String> =
                ids.filter { it in BUILTIN_KEYS || (it.startsWith("t:") && it.length <= MAX_TEXT_KEY) }.take(KeyLayout.MAX_KEYS)
        }
    }

    /**
     * The snippets a person wrote. They are saved on the device as the JSON text of their list and written in a backup
     * as the list itself; each one is checked as a file of snippets is (see [SnippetFile]), and the ones that fail are
     * left out.
     */
    object SnippetList : Kind() {
        override fun fromFile(v: Any?): Any? {
            val list = v as? List<*> ?: return null
            return SnippetFile.encodeSaved(SnippetFile.fromValues(list).snippets)
        }

        override fun fromPref(v: Any?): Any? {
            val text = v as? String ?: return null
            return SnippetFile.toValues(SnippetFile.decodeSaved(text))
        }
    }

    private const val MAX_TEXT_KEY = 32

    private val BUILTIN_KEYS: Set<String> by lazy { KeyLayout.BUILTIN.map { it.first }.toSet() }

    /** One saved file of the app: its name on the device, the name shown to people, and which of its keys are settings. */
    class Section(val file: String, val label: String, private val kinds: (String) -> Kind?) {
        /** The kind of value saved under [key], or null if [key] is not a setting (state such as the last check is not). */
        fun kindOf(key: String): Kind? = kinds(key)
    }

    private val ACCENTS = AccentColor.values().map { it.key }.toSet()
    private val FONTS = EditorFont.values().map { it.key }.toSet()
    private val SPACINGS = EditorSpacing.values().map { it.key }.toSet()
    private val MODES = ThemeMode.values().map { it.key }.toSet()
    private val INDENTS = IndentDefault.values().map { it.key }.toSet()

    private val ACCENT = Kind.Choice(ACCENTS)
    private val FONT = Kind.Choice(FONTS)
    private val SPACING = Kind.Choice(SPACINGS)
    private val MODE = Kind.Choice(MODES)
    private val INDENT = Kind.Choice(INDENTS)
    private val FONT_SIZE = Kind.Whole(AppearanceSettings.MIN_FONT_SIZE, AppearanceSettings.MAX_FONT_SIZE)
    private val LINE_SPACING = Kind.Whole(AppearanceSettings.MIN_LINE_SPACING, AppearanceSettings.MAX_LINE_SPACING)
    private val RULER_COLUMN = Kind.Whole(AppearanceSettings.MIN_RULER_COLUMN, AppearanceSettings.MAX_RULER_COLUMN)
    private val COLOR = Kind.Whole(Int.MIN_VALUE, Int.MAX_VALUE)
    private val DELAY = Kind.Whole(1, 3600)
    private val TAB_WIDTH = Kind.Whole(EditorSettings.MIN_TAB_WIDTH, EditorSettings.MAX_TAB_WIDTH)
    private val COMPLETION_MIN = Kind.Whole(EditorSettings.MIN_COMPLETION_MIN, EditorSettings.MAX_COMPLETION_MIN)
    private val COMPLETION_DELAY = Kind.Whole(0, EditorSettings.MAX_COMPLETION_DELAY)
    private val JSON_INDENT = Kind.Whole(0, com.whatcode.editor.TextTools.MAX_JSON_INDENT)
    private val ROW_MENU = Kind.Checked { com.whatcode.explorer.logic.RowMenu.isValid(it) }

    private val APPEARANCE_FLAGS = setOf("pure_black", "highlight_line", "syntax_highlighting", "line_numbers", "show_whitespace",
        "indent_guides", "ruler")

    private val EDITOR_FLAGS = setOf(
        "auto_close", "smart_indent", "close_tags", "split_tags", "rename_tags", "match_highlight",
        "whole_line_clipboard", "plain_paste", "hardware_shortcuts", "word_wrap", "hide_keys",
        "keys_by_language", "open_in_tabs", "reopen_last", "show_keys", "show_status_bar", "quote_attributes",
        "trim_on_save", "show_scope", "bookmarks", "jump_history", "text_tools", "sort_ignore_case", "sort_natural",
        "unique_ignore_case", "snippets", "snippets_tab", "snippets_builtin",
        "abbreviations", "completion", "completion_other", "completion_enter",
    )

    private val SYNTAX_COLORS: Set<String> = buildSet {
        for (theme in listOf("light", "dark")) for (role in SyntaxRole.values()) add("syntax_${theme}_${role.key}")
    }

    /** Every saved file that a backup holds, in the order they are written. */
    val SECTIONS: List<Section> = listOf(
        Section(AppearanceSettings.FILE, "Appearance") { key ->
            when {
                key == "accent" -> ACCENT
                key == "font" -> FONT
                key == "font_size" -> FONT_SIZE
                key == "line_spacing" -> LINE_SPACING
                key == "ruler_column" -> RULER_COLUMN
                key == "spacing" -> SPACING
                key == "color_mode" -> MODE
                key in APPEARANCE_FLAGS -> Kind.Flag
                key in SYNTAX_COLORS -> COLOR
                else -> null
            }
        },
        Section("editor", "Typing, Tabs, Navigation, Text tools and Completion") { key ->
            when {
                key == "indent_default" -> INDENT
                key == "tab_width" -> TAB_WIDTH
                key == "json_indent" -> JSON_INDENT
                key == "completion_min" -> COMPLETION_MIN
                key == "completion_delay" -> COMPLETION_DELAY
                key in EDITOR_FLAGS -> Kind.Flag
                else -> null
            }
        },
        Section("autosave", "Autosave") { key ->
            when (key) {
                "enabled", "on_leave" -> Kind.Flag
                "delay" -> DELAY
                else -> null
            }
        },
        Section(LargeFileSettings.FILE, "Large files") { key ->
            when (key) {
                LargeFileSettings.KEY_LONG_LINE -> Kind.Whole(LargeFileSettings.MIN_LONG_LINE, LargeFileSettings.MAX_LONG_LINE)
                LargeFileSettings.KEY_DELAY -> Kind.Whole(LargeFileSettings.MIN_DELAY, LargeFileSettings.MAX_DELAY)
                LargeFileSettings.KEY_MARGIN -> Kind.Whole(LargeFileSettings.MIN_MARGIN, LargeFileSettings.MAX_MARGIN)
                LargeFileSettings.KEY_MODE -> Kind.Flag
                LargeFileSettings.KEY_LARGE, LargeFileSettings.KEY_VERY_LARGE -> Kind.Whole(LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE)
                LargeFileSettings.KEY_LARGE_OFF, LargeFileSettings.KEY_VERY_LARGE_OFF -> Kind.Whole(0, LargeFileConfig.ALL_BITS)
                LargeFileSettings.KEY_UNDO -> Kind.Whole(LargeFileConfig.MIN_UNDO_STEPS, LargeFileConfig.NORMAL_UNDO_STEPS)
                else -> null
            }
        },
        Section(KeyLayout.PREFS, "Key rows") { key ->
            val name = key.substringAfter('@', "")
            when {
                key == "row1" || key == "row2" -> Kind.Keys
                (key.startsWith("row1@") || key.startsWith("row2@")) && name.isNotBlank() && name.length <= 60 -> Kind.Keys
                else -> null
            }
        },
        Section(ExplorerOptions.FILE, "File explorer view and sorting") { key ->
            when (key) {
                in ExplorerOptions.FLAGS -> Kind.Flag
                in ExplorerOptions.CHOICES -> Kind.Choice(ExplorerOptions.CHOICES.getValue(key))
                else -> null
            }
        },
        Section(ExplorerSettings.FILE, "File explorer") { key ->
            when (key) {
                in ExplorerSettings.FLAGS -> Kind.Flag
                in ExplorerSettings.CHOICES -> Kind.Choice(ExplorerSettings.CHOICES.getValue(key))
                ExplorerSettings.KEY_START_FOLDER -> Kind.Text(ExplorerSettings.MAX_PATH)
                ExplorerSettings.KEY_ROW_MENU -> ROW_MENU
                else -> null
            }
        },
        Section(SnippetFile.PREFS, "Your snippets") { key -> if (key == SnippetFile.KEY_USER) SnippetList else null },
        Section("updates", "Update check") { key -> if (key == "auto_check") Kind.Flag else null },
    )

    /** The section for the saved file [file], or null if a backup does not hold it. */
    fun section(file: String): Section? = SECTIONS.firstOrNull { it.file == file }

    // ------------------------------------------------------------------ reading a backup

    /**
     * A backup that passed the checks: [sections] holds, for each saved file in it, the values to save, ready to be
     * written (a row of keys is its JSON text). [count] is how many settings that is in all, and [skipped] how many
     * entries were left out because this version has no such setting, or the value is not one it can take.
     */
    class Backup(val sections: Map<String, Map<String, Any>>, val count: Int, val skipped: Int)

    /** The outcome of [parse]: the [backup], or if it could not be read, [error] in words for the person. */
    class Parsed(val backup: Backup?, val error: String?)

    fun parse(text: String): Parsed {
        if (text.length > MAX_BYTES) return Parsed(null, "The file is too big to be a settings backup.")
        val root = try {
            Json.parse(text)
        } catch (e: IllegalArgumentException) {
            return Parsed(null, "This is not a settings backup: it is not valid JSON.")
        }
        if (root !is Map<*, *> || root["app"] != APP) return Parsed(null, "This is not a $APP settings backup.")
        val format = root["format"] as? Long
        if (format == null || format < 1) return Parsed(null, "This is not a $APP settings backup.")
        if (format > FORMAT) return Parsed(null, "This backup was made by a newer version of $APP. Update the app and try again.")
        val settings = root["settings"] as? Map<*, *> ?: return Parsed(null, "This backup holds no settings.")

        val out = LinkedHashMap<String, Map<String, Any>>()
        var count = 0
        var skipped = 0
        for ((file, entries) in settings) {
            val section = (file as? String)?.let { section(it) }
            if (section == null || entries !is Map<*, *>) {
                skipped += if (entries is Map<*, *>) entries.size.coerceAtLeast(1) else 1
                continue
            }
            val values = LinkedHashMap<String, Any>()
            for ((key, raw) in entries) {
                val kind = (key as? String)?.let { section.kindOf(it) }
                val value = kind?.fromFile(raw)
                if (value == null) {
                    skipped++
                } else {
                    values[key as String] = value
                    count++
                }
            }
            out[section.file] = values
        }
        if (out.isEmpty()) return Parsed(null, "This backup has none of the settings of this version.")
        return Parsed(Backup(out, count, skipped), null)
    }

    // ------------------------------------------------------------------ writing a backup

    /**
     * The JSON text of a backup written by the app version [version]. [saved] holds, for each saved file, the values
     * as they are on the device; whatever is not a setting, or does not hold a valid value, is left out.
     */
    fun toJson(version: String, saved: Map<String, Map<String, Any?>>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": ").append(Json.write(APP)).append(",\n")
        sb.append("  \"format\": ").append(FORMAT).append(",\n")
        sb.append("  \"version\": ").append(Json.write(version)).append(",\n")
        sb.append("  \"settings\": {")
        var firstSection = true
        for (section in SECTIONS) {
            val values = saved[section.file] ?: continue
            val lines = ArrayList<String>()
            for (key in values.keys.sorted()) {
                val value = section.kindOf(key)?.fromPref(values[key]) ?: continue
                lines.add("      ${Json.write(key)}: ${Json.write(value)}")
            }
            sb.append(if (firstSection) "\n" else ",\n")
            firstSection = false
            sb.append("    ").append(Json.write(section.file)).append(": {")
            if (lines.isNotEmpty()) sb.append("\n").append(lines.joinToString(",\n")).append("\n    ")
            sb.append("}")
        }
        sb.append(if (firstSection) "}\n" else "\n  }\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ------------------------------------------------------------------ a small JSON reader and writer

    /**
     * Just enough JSON for a backup: objects, arrays, strings, whole numbers, true, false and null. Numbers with a
     * fraction or an exponent are read as [Double], which no setting accepts. It is written here, and not taken from
     * Android's own JSON classes, so that it works the same in the unit tests.
     */
    internal object Json {

        /** The value in [text]. Throws [IllegalArgumentException] if it is not valid JSON, or nests deeper than a backup does. */
        fun parse(text: String): Any? {
            val p = Reader(text)
            p.skip()
            val v = p.value(0)
            p.skip()
            if (p.pos != text.length) p.fail()
            return v
        }

        /** The JSON text of [v]: a map with text keys, a list, a string, a number, a boolean or null. */
        fun write(v: Any?): String = when (v) {
            null -> "null"
            is Boolean, is Int, is Long -> v.toString()
            is String -> quote(v)
            is List<*> -> v.joinToString(", ", "[", "]") { write(it) }
            is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { (k, x) -> quote(k as String) + ": " + write(x) }
            else -> throw IllegalArgumentException("Cannot write ${v.javaClass.simpleName}")
        }

        private fun quote(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) {
                when {
                    c == '"' -> sb.append("\\\"")
                    c == '\\' -> sb.append("\\\\")
                    c == '\n' -> sb.append("\\n")
                    c == '\r' -> sb.append("\\r")
                    c == '\t' -> sb.append("\\t")
                    c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                    else -> sb.append(c)
                }
            }
            return sb.append('"').toString()
        }

        private class Reader(val s: String) {
            var pos = 0

            fun fail(): Nothing = throw IllegalArgumentException("Invalid JSON at $pos")

            fun skip() {
                while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
            }

            fun value(depth: Int): Any? {
                if (depth > MAX_DEPTH) fail()
                if (pos >= s.length) fail()
                return when (s[pos]) {
                    '{' -> obj(depth)
                    '[' -> arr(depth)
                    '"' -> str()
                    't' -> word("true", true)
                    'f' -> word("false", false)
                    'n' -> word("null", null)
                    else -> num()
                }
            }

            private fun word(w: String, v: Any?): Any? {
                if (!s.startsWith(w, pos)) fail()
                pos += w.length
                return v
            }

            private fun obj(depth: Int): Map<String, Any?> {
                pos++
                val m = LinkedHashMap<String, Any?>()
                skip()
                if (pos < s.length && s[pos] == '}') {
                    pos++
                    return m
                }
                while (true) {
                    skip()
                    if (pos >= s.length || s[pos] != '"') fail()
                    val k = str()
                    skip()
                    if (pos >= s.length || s[pos] != ':') fail()
                    pos++
                    skip()
                    m[k] = value(depth + 1)
                    skip()
                    if (pos >= s.length) fail()
                    when (s[pos++]) {
                        ',' -> continue
                        '}' -> return m
                        else -> fail()
                    }
                }
            }

            private fun arr(depth: Int): List<Any?> {
                pos++
                val l = ArrayList<Any?>()
                skip()
                if (pos < s.length && s[pos] == ']') {
                    pos++
                    return l
                }
                while (true) {
                    skip()
                    l.add(value(depth + 1))
                    skip()
                    if (pos >= s.length) fail()
                    when (s[pos++]) {
                        ',' -> continue
                        ']' -> return l
                        else -> fail()
                    }
                }
            }

            private fun str(): String {
                pos++
                val sb = StringBuilder()
                while (true) {
                    if (pos >= s.length) fail()
                    val c = s[pos++]
                    when {
                        c == '"' -> return sb.toString()
                        c < ' ' -> fail()
                        c != '\\' -> sb.append(c)
                        else -> {
                            if (pos >= s.length) fail()
                            when (val e = s[pos++]) {
                                '"', '\\', '/' -> sb.append(e)
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000c')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (pos + 4 > s.length) fail()
                                    val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: fail()
                                    sb.append(code.toChar())
                                    pos += 4
                                }
                                else -> fail()
                            }
                        }
                    }
                }
            }

            private fun num(): Any {
                val start = pos
                if (pos < s.length && s[pos] == '-') pos++
                val digits = pos
                while (pos < s.length && s[pos].isDigit()) pos++
                if (pos == digits) fail()
                if (s[digits] == '0' && pos - digits > 1) fail()
                var whole = true
                if (pos < s.length && s[pos] == '.') {
                    whole = false
                    pos++
                    val f = pos
                    while (pos < s.length && s[pos].isDigit()) pos++
                    if (pos == f) fail()
                }
                if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                    whole = false
                    pos++
                    if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                    val x = pos
                    while (pos < s.length && s[pos].isDigit()) pos++
                    if (pos == x) fail()
                }
                val text = s.substring(start, pos)
                return if (whole) text.toLongOrNull() ?: text.toDouble() else text.toDouble()
            }
        }
    }
}
