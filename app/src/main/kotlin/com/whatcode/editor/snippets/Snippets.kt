package com.whatcode.editor.snippets

import com.whatcode.editor.TextEdit

/**
 * One snippet: typing its [prefix] and pressing Tab puts in its [body] (see [SnippetBody]). [lang] is the name of the
 * language it is for, such as `Python`, or null for every language.
 */
class Snippet(val prefix: String, val name: String, val body: String, val lang: String? = null)

/** Where the snippets come from: the ones built in, and the ones the person wrote. */
class SnippetSource(val builtIn: Boolean = true, val user: List<Snippet> = emptyList())

/** Finding a snippet and working out the edit that puts it in. Plain logic, no Android. */
object Snippets {

    /** The characters of a prefix. */
    fun isWordChar(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    /** A word right after one of these is a member, a property, a class or a variable, never a snippet to expand. */
    private const val NOT_AFTER = ".-\$@#\\"

    /**
     * The snippets for the language called [langName]: the person's own first, then the built-in ones, which a person's
     * snippet with the same prefix replaces.
     */
    fun available(langName: String, source: SnippetSource): List<Snippet> {
        val own = source.user.filter { it.lang == null || it.lang == langName }
        val taken = own.mapTo(HashSet()) { it.prefix }
        val built = if (source.builtIn) BuiltInSnippets.forLanguage(langName).filter { it.prefix !in taken } else emptyList()
        return own + built
    }

    /** The snippet of [langName] with this [prefix], or null. */
    fun find(langName: String, prefix: String, source: SnippetSource): Snippet? =
        available(langName, source).firstOrNull { it.prefix == prefix }

    /** A snippet that Tab expands: [start] is where its prefix starts, and the prefix ends at the cursor. */
    class Trigger(val start: Int, val snippet: Snippet)

    /**
     * The snippet the word before the cursor at [cursor] asks for, or null. Nothing may be selected, the word must end
     * at the cursor (not run on after it), and it must not follow a `.`, `-`, `$`, `@`, `#` or `\`.
     */
    fun trigger(t: CharSequence, selStart: Int, selEnd: Int, langName: String, source: SnippetSource): Trigger? {
        if (selStart != selEnd || selStart < 0 || selStart > t.length) return null
        val cursor = selStart
        if (cursor < t.length && isWordChar(t[cursor])) return null
        var start = cursor
        while (start > 0 && isWordChar(t[start - 1])) start--
        if (start == cursor) return null
        if (start > 0 && t[start - 1] in NOT_AFTER) return null
        val snippet = find(langName, t.subSequence(start, cursor).toString(), source) ?: return null
        return Trigger(start, snippet)
    }

    /**
     * What putting a snippet in takes: [edit] replaces the text from `from` to `to` with the body and selects the first
     * tab stop (or puts the cursor at the end); [stops] holds the start and end of each tab stop in the whole text, in
     * the order Tab visits them; [finalOffset] is where the cursor ends up after the last one.
     */
    class Expansion(val edit: TextEdit, val stops: IntArray, val finalOffset: Int)

    /**
     * The expansion of [snippet] over the text from [from] to [to] of [t]. The lines after the first start with the
     * white space the line of [from] starts with, and a tab in the body is [unit].
     */
    fun expansion(t: CharSequence, from: Int, to: Int, snippet: Snippet, unit: String): Expansion {
        var lineStart = from
        while (lineStart > 0 && t[lineStart - 1] != '\n' && t[lineStart - 1] != '\r') lineStart--
        var indentEnd = lineStart
        while (indentEnd < from && (t[indentEnd] == ' ' || t[indentEnd] == '\t')) indentEnd++
        val parsed = SnippetBody.parse(snippet.body, t.subSequence(lineStart, indentEnd).toString(), unit)
        val stops = IntArray(parsed.stops.size * 2)
        for ((i, s) in parsed.stops.withIndex()) {
            stops[2 * i] = from + s.start
            stops[2 * i + 1] = from + s.end
        }
        val finalAt = from + parsed.finalOffset
        val edit = if (stops.isEmpty()) TextEdit(from, to, parsed.text, finalAt) else TextEdit(from, to, parsed.text, stops[0], stops[1])
        return Expansion(edit, stops, finalAt)
    }
}
