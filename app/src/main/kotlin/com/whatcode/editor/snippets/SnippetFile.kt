package com.whatcode.editor.snippets

import com.whatcode.editor.highlight.Languages
import com.whatcode.settings.SettingsBackup

/**
 * The snippets a person writes: the checks one must pass, how a list of them is saved and written to a file, and how
 * a file of them is read and added to the ones there are. A file of snippets comes from outside the app, so nothing
 * in it is trusted: each snippet is checked, and the ones that fail are counted and left out. Plain logic, no Android.
 *
 * A file looks like this (a snippet with no `lang` is for every language):
 *
 *     { "app": "WhatCode", "kind": "snippets", "format": 1,
 *       "snippets": [ { "prefix": "todo", "name": "To do", "lang": "Python", "body": "# TODO: ${1:what}" } ] }
 */
object SnippetFile {

    /** The saved file and key the person's snippets are kept under on the device. */
    const val PREFS = "snippets"
    const val KEY_USER = "user"

    const val KIND = "snippets"
    const val FORMAT = 1

    const val MAX_PREFIX = 32
    const val MAX_NAME = 60
    const val MAX_BODY = 4000
    const val MAX_SNIPPETS = 200

    /** The most characters all the bodies can have together, so a settings backup that holds them stays small. */
    const val MAX_TOTAL = 100_000

    /** The longest file of snippets that is read. */
    const val MAX_BYTES = 256 * 1024

    /** The names of the languages a snippet can be for. */
    val languages: List<String> by lazy { Languages.languages.map { it.name } }

    /**
     * What is wrong with a snippet with these parts, in words for the person, or null if it is fine. [others] are the
     * other snippets there are, so two of them for the same language never share a prefix.
     */
    fun problem(prefix: String, name: String, body: String, lang: String?, others: List<Snippet> = emptyList()): String? = when {
        prefix.isEmpty() -> "Type the word that Tab turns into the snippet."
        prefix.length > MAX_PREFIX -> "The word can have up to $MAX_PREFIX letters."
        !prefix.all { Snippets.isWordChar(it) } -> "The word can have only letters, digits and _."
        name.length > MAX_NAME -> "The name can have up to $MAX_NAME characters."
        name.any { it < ' ' } -> "The name has to be on one line."
        body.isEmpty() -> "Type the code the snippet puts in."
        body.length > MAX_BODY -> "The code can have up to $MAX_BODY characters."
        lang != null && lang !in languages -> "Choose a language from the list."
        others.sumOf { it.body.length } + body.length > MAX_TOTAL ->
            "Your snippets can hold up to ${"%,d".format(MAX_TOTAL)} characters of code in all."
        others.any { it.prefix == prefix && it.lang == lang } ->
            "You have a snippet called $prefix for ${lang ?: "every language"} already."
        else -> null
    }

    // ------------------------------------------------------------------ the list as data

    /** The snippets as JSON values: a list of maps. */
    fun toValues(list: List<Snippet>): List<Map<String, Any?>> = list.map { s ->
        val m = LinkedHashMap<String, Any?>()
        m["prefix"] = s.prefix
        m["name"] = s.name
        if (s.lang != null) m["lang"] = s.lang
        m["body"] = s.body
        m
    }

    /** What [fromValues] read: the [snippets] that passed the checks and how many entries were [skipped]. */
    class Read(val snippets: List<Snippet>, val skipped: Int)

    /**
     * The snippets in [values], a list read from JSON. An entry that is not a snippet, fails a check, or uses a prefix
     * that an earlier entry for its language uses, is skipped; so are entries past the first [MAX_SNIPPETS], and any
     * that would take the bodies past [MAX_TOTAL] characters.
     */
    fun fromValues(values: List<*>): Read {
        val out = ArrayList<Snippet>()
        var skipped = 0
        for (v in values) {
            val m = v as? Map<*, *>
            val prefix = m?.get("prefix") as? String
            val name = (m?.get("name") ?: "") as? String
            val body = m?.get("body") as? String
            val rawLang = m?.get("lang")
            val lang = rawLang as? String
            val ok = prefix != null && name != null && body != null && (rawLang == null || lang != null) &&
                out.size < MAX_SNIPPETS && problem(prefix, name, body, lang, out) == null
            if (ok) out.add(Snippet(prefix!!, name!!, body!!, lang)) else skipped++
        }
        return Read(out, skipped)
    }

    // ------------------------------------------------------------------ saved on the device

    /** The text the list is saved as on the device. */
    fun encodeSaved(list: List<Snippet>): String = SettingsBackup.Json.write(toValues(list))

    /** The list saved as [text]; anything that cannot be read gives an empty list. */
    fun decodeSaved(text: String?): List<Snippet> {
        if (text.isNullOrEmpty()) return emptyList()
        val values = try {
            SettingsBackup.Json.parse(text) as? List<*>
        } catch (_: IllegalArgumentException) {
            null
        } ?: return emptyList()
        return fromValues(values).snippets
    }

    // ------------------------------------------------------------------ a file to share

    /** The text of a file that holds [list]. */
    fun export(list: List<Snippet>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": ").append(SettingsBackup.Json.write(SettingsBackup.APP)).append(",\n")
        sb.append("  \"kind\": ").append(SettingsBackup.Json.write(KIND)).append(",\n")
        sb.append("  \"format\": ").append(FORMAT).append(",\n")
        sb.append("  \"snippets\": [")
        val items = toValues(list).map { "    " + SettingsBackup.Json.write(it) }
        if (items.isNotEmpty()) sb.append("\n").append(items.joinToString(",\n")).append("\n  ")
        sb.append("]\n}\n")
        return sb.toString()
    }

    /** The outcome of [import]: the snippets read and how many were skipped, or [error] in words for the person. */
    class Imported(val read: Read?, val error: String?)

    /** Reads the file [text]. */
    fun import(text: String): Imported {
        if (text.length > MAX_BYTES) return Imported(null, "The file is too big to be a file of snippets.")
        val root = try {
            SettingsBackup.Json.parse(text)
        } catch (_: IllegalArgumentException) {
            return Imported(null, "This is not a file of snippets: it is not valid JSON.")
        }
        if (root !is Map<*, *> || root["app"] != SettingsBackup.APP || root["kind"] != KIND) {
            return Imported(null, "This is not a ${SettingsBackup.APP} file of snippets.")
        }
        val format = root["format"] as? Long
        if (format == null || format < 1) return Imported(null, "This is not a ${SettingsBackup.APP} file of snippets.")
        if (format > FORMAT) return Imported(null, "This file was made by a newer version of ${SettingsBackup.APP}. Update the app and try again.")
        val values = root["snippets"] as? List<*> ?: return Imported(null, "This file holds no snippets.")
        val read = fromValues(values)
        if (read.snippets.isEmpty()) return Imported(null, "None of the snippets in this file could be read.")
        return Imported(read, null)
    }

    /** What [merge] made: the new [list], and how many snippets were [added] and [replaced]. [dropped] did not fit. */
    class Merged(val list: List<Snippet>, val added: Int, val replaced: Int, val dropped: Int)

    /**
     * [mine] with [incoming] added: one with the prefix and language of one of mine takes its place, and the others go
     * at the end, as long as there is room for them ([MAX_SNIPPETS] snippets and [MAX_TOTAL] characters of code).
     */
    fun merge(mine: List<Snippet>, incoming: List<Snippet>): Merged {
        val out = ArrayList(mine)
        var added = 0
        var replaced = 0
        var dropped = 0
        var total = mine.sumOf { it.body.length }
        for (s in incoming) {
            val at = out.indexOfFirst { it.prefix == s.prefix && it.lang == s.lang }
            val grows = s.body.length - (if (at >= 0) out[at].body.length else 0)
            when {
                total + grows > MAX_TOTAL -> dropped++
                at >= 0 -> {
                    out[at] = s
                    total += grows
                    replaced++
                }
                out.size < MAX_SNIPPETS -> {
                    total += grows
                    out.add(s)
                    added++
                }
                else -> dropped++
            }
        }
        return Merged(out, added, replaced, dropped)
    }
}
