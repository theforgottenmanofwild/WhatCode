package com.whatcode.editor

/** One entry of the Text tools menu. [key] is its key of the key rows, [keyLabel] what that key shows. */
enum class TextTool(val key: String, val label: String, val keyLabel: String) {
    SORT("sortlines", "Sort lines, A to Z", "A-Z"),
    SORT_DOWN("sortdown", "Sort lines, Z to A", "Z-A"),
    REVERSE("reverselines", "Reverse lines", "Rev"),
    UNIQUE("uniquelines", "Remove duplicate lines", "Uniq"),
    UPPER("uppercase", "Upper case", "ABC"),
    LOWER("lowercase", "Lower case", "abc"),
    TITLE("titlecase", "Title case", "Abc"),
    SWAP("transpose", "Swap characters", "Swap"),
    TO_TABS("totabs", "Indent with tabs", "\u21E5T"),
    TO_SPACES("tospaces", "Indent with spaces", "\u21E5S"),
    TO_LF("tolf", "Line endings to LF", "LF"),
    TO_CRLF("tocrlf", "Line endings to CRLF", "CRLF"),
    FORMAT_JSON("formatjson", "Format JSON", "JSON"),
    MINIFY_JSON("minifyjson", "Minify JSON", "Min");

    /** True for the conversions, which leave the text with the same lines, so bookmarks can stay on theirs. */
    val keepsLines: Boolean get() = this == TO_TABS || this == TO_SPACES || this == TO_LF || this == TO_CRLF

    companion object {
        fun fromKey(key: String): TextTool? = values().firstOrNull { it.key == key }
    }
}

/** Runs a [TextTool] on plain text. No Android here. */
object TextTools {

    /** A text of this many characters or more is worked on off the main thread. */
    const val BACKGROUND_FROM = 200_000

    /** The choices of Settings > Text tools. */
    class Options(
        val sortIgnoreCase: Boolean = true,
        val sortNatural: Boolean = true,
        val uniqueIgnoreCase: Boolean = false,
        val tabWidth: Int = TextConvert.DEFAULT_TAB,
        /** Spaces in one level of formatted JSON, or 0 for a tab. */
        val jsonIndent: Int = DEFAULT_JSON_INDENT,
    )

    const val DEFAULT_JSON_INDENT = 2
    const val MAX_JSON_INDENT = 8

    /** The one edit that does [tool] to [t] with the selection from [a] to [b], or null if nothing would change. */
    fun edit(tool: TextTool, t: CharSequence, a: Int, b: Int, o: Options): TextEdit? {
        val sort = LineTools.Options(o.sortIgnoreCase, o.sortNatural)
        return when (tool) {
            TextTool.SORT -> LineTools.sortLines(t, a, b, descending = false, o = sort)
            TextTool.SORT_DOWN -> LineTools.sortLines(t, a, b, descending = true, o = sort)
            TextTool.REVERSE -> LineTools.reverseLines(t, a, b)
            TextTool.UNIQUE -> LineTools.removeDuplicateLines(t, a, b, o.uniqueIgnoreCase)
            TextTool.UPPER -> LineTools.changeCase(t, a, b, LineTools.Case.UPPER)
            TextTool.LOWER -> LineTools.changeCase(t, a, b, LineTools.Case.LOWER)
            TextTool.TITLE -> LineTools.changeCase(t, a, b, LineTools.Case.TITLE)
            TextTool.SWAP -> LineTools.transpose(t, a, b)
            TextTool.TO_TABS -> TextConvert.indentation(t, toTabs = true, width = o.tabWidth, selStart = a, selEnd = b)
            TextTool.TO_SPACES -> TextConvert.indentation(t, toTabs = false, width = o.tabWidth, selStart = a, selEnd = b)
            TextTool.TO_LF -> TextConvert.lineEndings(t, "\n", a, b)
            TextTool.TO_CRLF -> TextConvert.lineEndings(t, "\r\n", a, b)
            TextTool.FORMAT_JSON -> JsonFormat.edit(t, a, b, pretty = true, indentWidth = o.jsonIndent)
            TextTool.MINIFY_JSON -> JsonFormat.edit(t, a, b, pretty = false, indentWidth = o.jsonIndent)
        }
    }

    /** What to say when [edit] finds nothing to change. */
    fun nothingToDo(tool: TextTool): String = when (tool) {
        TextTool.SORT, TextTool.SORT_DOWN -> "The lines are already in that order"
        TextTool.REVERSE -> "There is nothing to reverse"
        TextTool.UNIQUE -> "There are no duplicate lines"
        TextTool.UPPER, TextTool.LOWER, TextTool.TITLE -> "There is no text to change here"
        TextTool.SWAP -> "Put the cursor between two characters of a line, with nothing selected"
        TextTool.TO_TABS -> "No indentation needs changing to tabs"
        TextTool.TO_SPACES -> "No indentation needs changing to spaces"
        TextTool.TO_LF -> "Every line already ends with LF"
        TextTool.TO_CRLF -> "Every line already ends with CRLF"
        TextTool.FORMAT_JSON -> "The JSON is already formatted that way"
        TextTool.MINIFY_JSON -> "The JSON is already as short as it can be"
    }

    /**
     * The line, counted from 0, of each of the [offsets] (in rising order), where `\n`, `\r\n` and a lone `\r` each end
     * a line. With [lineOffsets] it carries bookmarks over a conversion that rewrites the text but keeps its lines.
     */
    fun lineNumbers(t: CharSequence, offsets: IntArray): IntArray {
        val out = IntArray(offsets.size)
        var line = 0
        var i = 0
        for (k in offsets.indices) {
            val to = offsets[k].coerceAtMost(t.length)
            while (i < to) {
                if (endsLine(t, i)) line++
                i++
            }
            out[k] = line
        }
        return out
    }

    /** Where each of the [lines] (in rising order) starts. A line past the end of the text is left out. */
    fun lineOffsets(t: CharSequence, lines: IntArray): IntArray {
        val out = ArrayList<Int>(lines.size)
        var line = 0
        var i = 0
        var start = 0
        for (want in lines) {
            while (line < want && i < t.length) {
                if (endsLine(t, i)) {
                    line++
                    start = i + 1
                }
                i++
            }
            if (line == want) out.add(start)
        }
        return out.toIntArray()
    }

    private fun endsLine(t: CharSequence, i: Int): Boolean =
        t[i] == '\n' || (t[i] == '\r' && !(i + 1 < t.length && t[i + 1] == '\n'))
}
