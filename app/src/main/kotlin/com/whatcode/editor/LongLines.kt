package com.whatcode.editor

/**
 * Finds lines that are too long to be coloured or wrapped. A line is long when it holds more than a limit of
 * characters, not counting its `\n`. Minified scripts and one-line JSON files are the usual cause: such a line has
 * to be read whole for every piece of colour on it, and laid out whole for every wrap.
 *
 * Nothing here knows about Android. [text] is any `CharSequence`, so the editor's own text can be read in place.
 */
object LongLines {

    /** The limit when nothing else was chosen; see Settings > Large files. */
    const val DEFAULT_LIMIT = 20_000

    /** True if any line of [text] from [from] to [to] (both inside it) is longer than [limit]. */
    fun exists(text: CharSequence, from: Int, to: Int, limit: Int): Boolean {
        var i = from.coerceAtLeast(0)
        val end = to.coerceAtMost(text.length)
        while (i <= end) {
            val nl = indexOfBreak(text, i, end)
            val lineEnd = if (nl < 0) end else nl
            if (lineEnd - i > limit) return true
            i = lineEnd + 1
        }
        return false
    }

    /** True if any line of [text] is longer than [limit]. */
    fun exists(text: CharSequence, limit: Int): Boolean = exists(text, 0, text.length, limit)

    /**
     * True if a line that the stretch from [start] to [end] touches is longer than [limit]: the lines inside it, and
     * the whole of the first and last line, which reach out of it. The search outward stops after [limit] characters,
     * so the cost of an edit never depends on the size of the text.
     */
    fun touched(text: CharSequence, start: Int, end: Int, limit: Int): Boolean {
        var a = start.coerceIn(0, text.length)
        var steps = 0
        while (a > 0 && text[a - 1] != '\n') {
            a--
            if (++steps > limit) return true
        }
        var b = end.coerceIn(a, text.length)
        steps = 0
        while (b < text.length && text[b] != '\n') {
            b++
            if (++steps > limit) return true
        }
        return exists(text, a, b, limit)
    }

    /**
     * The offset where the first long line begins that starts at or after [pos], or -1 when there is none. [pos] must
     * be the start of a line.
     */
    fun nextStart(text: String, pos: Int, limit: Int): Int {
        var i = pos
        while (i <= text.length) {
            val nl = text.indexOf('\n', i)
            val lineEnd = if (nl < 0) text.length else nl
            if (lineEnd - i > limit) return i
            if (nl < 0) return -1
            i = nl + 1
        }
        return -1
    }

    private fun indexOfBreak(text: CharSequence, from: Int, to: Int): Int {
        if (text is String) {
            val i = text.indexOf('\n', from)
            return if (i in 0 until to) i else -1
        }
        for (i in from until to) if (text[i] == '\n') return i
        return -1
    }
}
