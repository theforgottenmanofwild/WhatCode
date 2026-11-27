package com.whatcode.editor

/**
 * Removes the spaces and tabs at the ends of lines, for "Trim trailing whitespace on save". A line may end in LF,
 * CRLF or a lone CR, and every line keeps the ending it has. Plain logic, so it can be tested without a device.
 */
object TrimWhitespace {

    /** False for languages in which spaces at the end of a line mean something: two of them break a line in Markdown. */
    fun appliesTo(lang: Lang): Boolean = lang.family != Family.MARKDOWN

    /** The runs of spaces and tabs that end a line, in order. */
    private fun trailing(text: CharSequence): List<IntRange> {
        val out = ArrayList<IntRange>()
        var from = -1
        for (i in 0 until text.length) {
            val c = text[i]
            if (c == ' ' || c == '\t') {
                if (from < 0) from = i
            } else {
                if (from >= 0 && (c == '\n' || c == '\r')) out.add(from until i)
                from = -1
            }
        }
        if (from >= 0) out.add(from until text.length)
        return out
    }

    /** [text] from [start] up to [end] without the runs in [cut], which all lie inside it. */
    private fun without(text: CharSequence, cut: List<IntRange>, start: Int, end: Int): String {
        val out = StringBuilder(end - start)
        var at = start
        for (r in cut) {
            out.append(text, at, r.first)
            at = r.last + 1
        }
        out.append(text, at, end)
        return out.toString()
    }

    /** [text] without any trailing whitespace. */
    fun trim(text: String): String {
        val cut = trailing(text)
        return if (cut.isEmpty()) text else without(text, cut, 0, text.length)
    }

    /**
     * The one change that trims [text], or null if there is nothing to trim. The cursor and the selection stay on
     * the same characters; one that was in the removed whitespace goes to the new end of its line.
     */
    fun edit(text: CharSequence, selStart: Int, selEnd: Int): TextEdit? {
        val cut = trailing(text)
        if (cut.isEmpty()) return null
        val start = cut.first().first
        val end = cut.last().last + 1
        fun moved(pos: Int): Int {
            var removed = 0
            for (r in cut) {
                if (r.first >= pos) break
                removed += minOf(pos, r.last + 1) - r.first
            }
            return pos - removed
        }
        return TextEdit(start, end, without(text, cut, start, end), moved(selStart), moved(selEnd))
    }
}
