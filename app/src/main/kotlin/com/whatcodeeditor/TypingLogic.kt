package com.whatcodeeditor

/**
 * A change to make to the text: replace the characters from [start] up to [end] with [insert], then select
 * from [selStart] to [selEnd]. The two selection numbers count from the start of the whole text as it is
 * after the change.
 */
class TextEdit(val start: Int, val end: Int, val insert: String, val selStart: Int, val selEnd: Int = selStart)

/**
 * The rules for auto-closing brackets and quotes and for smart indentation, kept apart from the editor view
 * so they only deal in text and numbers. [CodeEditText] applies what they return.
 */
object TypingLogic {
    private const val NUL = '\u0000'
    private const val OPENERS = "([{"
    private const val CLOSERS = ")]}"
    private const val QUOTES = "\"'`"

    /** A new pair may be opened right before one of these (or white space, or the end of the text). */
    private const val BEFORE_PAIR = ")]}>,;:"

    /** Languages in which a line ending in a colon opens an indented block. */
    private val COLON_BLOCKS = setOf("Python", "YAML")

    /** Languages that are indented with tabs unless the file shows otherwise. */
    private val TAB_LANGUAGES = setOf("Go", "Go module", "Makefile")

    /** True for the characters that [typed] can act on. */
    fun isTrigger(c: Char): Boolean = c in OPENERS || c in CLOSERS || c in QUOTES

    private fun closerOf(c: Char): Char = when (c) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        else -> c
    }

    private fun isBlank(text: CharSequence, from: Int, to: Int): Boolean {
        for (i in from until to) if (text[i] != ' ' && text[i] != '\t') return false
        return true
    }

    /** How many times [c] appears on the line before [pos]. */
    private fun countInLine(text: CharSequence, pos: Int, c: Char): Int {
        var n = 0
        var i = pos - 1
        while (i >= 0 && text[i] != '\n') {
            if (text[i] == c) n++
            i--
        }
        return n
    }

    /**
     * What one level of indentation is in this text: a tab if the first indented line starts with one,
     * otherwise the smallest indentation of two to four spaces found in the first lines, otherwise four spaces
     * (or a tab for languages that are normally indented with tabs).
     */
    fun indentUnit(text: CharSequence, lang: Lang): String {
        var pos = 0
        var lines = 0
        var min = Int.MAX_VALUE
        while (pos < text.length && lines < 400) {
            var i = pos
            var spaces = 0
            while (i < text.length && text[i] == ' ') {
                spaces++
                i++
            }
            val hasText = i < text.length && text[i] != '\n' && text[i] != '\r' && text[i] != '\t'
            if (spaces == 0 && i < text.length && text[i] == '\t') {
                var j = i
                while (j < text.length && text[j] == '\t') j++
                if (j < text.length && text[j] != '\n' && text[j] != '\r') return "\t"
            } else if (spaces >= 2 && hasText) {
                min = minOf(min, spaces)
            }
            while (i < text.length && text[i] != '\n') i++
            pos = i + 1
            lines++
        }
        if (min in 2..4) return " ".repeat(min)
        return if (lang.name in TAB_LANGUAGES) "\t" else "    "
    }

    /**
     * A character was typed with the selection from [selStart] to [selEnd]. Returns the change to make instead
     * of the plain typing, or null to let the character be typed as usual.
     */
    fun typed(
        text: CharSequence,
        selStart: Int,
        selEnd: Int,
        c: Char,
        lang: Lang,
        autoClose: Boolean,
        smartIndent: Boolean,
    ): TextEdit? {
        val s = minOf(selStart, selEnd).coerceIn(0, text.length)
        val e = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val next = if (e < text.length) text[e] else NUL
        val prev = if (s > 0) text[s - 1] else NUL
        when {
            c in OPENERS -> {
                if (!autoClose) return null
                val close = closerOf(c)
                if (s != e) {
                    // Typing a bracket over a selection wraps the selection and keeps it selected.
                    val inner = text.subSequence(s, e).toString()
                    return TextEdit(s, e, "$c$inner$close", s + 1, s + 1 + inner.length)
                }
                if (!(next == NUL || next.isWhitespace() || next in BEFORE_PAIR)) return null
                return TextEdit(s, e, "$c$close", s + 1)
            }
            c in CLOSERS -> {
                if (s != e) return null
                if (autoClose && next == c) return TextEdit(s, s, "", s + 1) // step over the closing one
                if (smartIndent) {
                    // A closing bracket alone at the start of a line moves back one level.
                    val ls = EditOps.lineStart(text, s)
                    if (s > ls && isBlank(text, ls, s)) {
                        val n = if (text[s - 1] == '\t') 1 else minOf(indentUnit(text, lang).length, s - ls)
                        return TextEdit(s - n, s, c.toString(), s - n + 1)
                    }
                }
                return null
            }
            c in QUOTES -> {
                if (!autoClose || c !in lang.pairQuotes) return null
                if (s != e) {
                    val inner = text.subSequence(s, e).toString()
                    return TextEdit(s, e, "$c$inner$c", s + 1, s + 1 + inner.length)
                }
                val before = countInLine(text, s, c)
                val inside = before % 2 == 1 // an odd number of quotes before: this one closes a string
                if (next == c && inside) return TextEdit(s, s, "", s + 1)
                if (inside) return null
                if (prev.isLetterOrDigit() || prev == '\\' || prev == c) return null // don't, "", """
                if (next.isLetterOrDigit()) return null
                return TextEdit(s, e, "$c$c", s + 1)
            }
        }
        return null
    }

    /**
     * Backspace with nothing selected: if the cursor sits between an empty pair such as () or "", both are
     * deleted. Returns null for any other backspace.
     */
    fun backspace(text: CharSequence, selStart: Int, selEnd: Int, lang: Lang): TextEdit? {
        if (selStart != selEnd) return null
        val s = selStart
        if (s <= 0 || s >= text.length) return null
        val prev = text[s - 1]
        val next = text[s]
        val bracket = prev in OPENERS && next == closerOf(prev)
        val quote = prev == next && prev in QUOTES && prev in lang.pairQuotes && (s < 2 || !text[s - 2].isLetterOrDigit())
        return if (bracket || quote) TextEdit(s - 1, s + 1, "", s - 1) else null
    }

    /**
     * Enter was pressed with the selection from [start] to [end]. The new line gets the indentation of the
     * line above. With [smartIndent] it gets one more level after an opening bracket (or a colon in Python and
     * YAML), and Enter between a bracket and its closing partner opens an empty indented line between them.
     */
    fun newline(text: CharSequence, start: Int, end: Int, lang: Lang, smartIndent: Boolean): TextEdit {
        val ls = EditOps.lineStart(text, start)
        var i = ls
        while (i < start && (text[i] == ' ' || text[i] == '\t')) i++
        val indent = text.subSequence(ls, i).toString()

        fun plain(extra: String): TextEdit {
            val ins = "\n$indent$extra"
            return TextEdit(start, end, ins, start + ins.length)
        }
        if (!smartIndent) return plain("")

        var p = start - 1
        while (p >= ls && (text[p] == ' ' || text[p] == '\t')) p--
        val last = if (p >= ls) text[p] else NUL
        if (last in OPENERS) {
            val unit = indentUnit(text, lang)
            val between = start > 0 && text[start - 1] == last && end < text.length && text[end] == closerOf(last)
            if (between) {
                val first = "\n$indent$unit"
                return TextEdit(start, end, "$first\n$indent", start + first.length)
            }
            return plain(unit)
        }
        if (last == ':' && lang.name in COLON_BLOCKS && !hasComment(text, ls, p)) {
            return plain(indentUnit(text, lang))
        }
        return plain("")
    }

    private fun hasComment(text: CharSequence, from: Int, to: Int): Boolean {
        for (i in from..to) if (text[i] == '#') return true
        return false
    }
}
