package com.whatcode.editor

/**
 * Whole-text conversions: the indentation between tabs and spaces, and the line endings between LF and CRLF. Each
 * returns the one [TextEdit] that does it, which covers only the part of the text that changes, and moves the
 * selection along with the characters it was on. Plain logic, no Android.
 */
object TextConvert {

    /** The width of a tab when the indentation is measured. */
    const val DEFAULT_TAB = 4

    // ------------------------------------------------------------------ indentation

    /**
     * Rewrites the whitespace at the start of every line: with [toTabs] a tab for each [width] columns and the spaces
     * left over, otherwise spaces only. Only the start of a line changes; a tab inside a line, in a string or after
     * text is left alone. Returns null if every line already is as asked.
     */
    fun indentation(t: CharSequence, toTabs: Boolean, width: Int = DEFAULT_TAB, selStart: Int = 0, selEnd: Int = 0): TextEdit? {
        val w = width.coerceIn(1, 16)
        val edits = ArrayList<IntArray>() // start, end, columns of each leading run that changes
        var i = 0
        val n = t.length
        while (i < n) {
            val ls = i
            var col = 0
            var j = i
            while (j < n && (t[j] == ' ' || t[j] == '\t')) {
                col = if (t[j] == '\t') (col / w + 1) * w else col + 1
                j++
            }
            val run = t.subSequence(ls, j)
            val want = if (toTabs) "\t".repeat(col / w) + " ".repeat(col % w) else " ".repeat(col)
            // A line of nothing but white space is left as it is: its width means nothing.
            val blank = j >= n || t[j] == '\n' || t[j] == '\r'
            if (!blank && run.toString() != want) edits.add(intArrayOf(ls, j, col))
            while (j < n && t[j] != '\n' && t[j] != '\r') j++
            if (j < n && t[j] == '\r' && j + 1 < n && t[j + 1] == '\n') j++
            i = j + 1
        }
        if (edits.isEmpty()) return null
        val start = edits.first()[0]
        val end = edits.last()[1]
        val sb = StringBuilder()
        var at = start
        for (e in edits) {
            sb.append(t, at, e[0])
            sb.append(if (toTabs) "\t".repeat(e[2] / w) + " ".repeat(e[2] % w) else " ".repeat(e[2]))
            at = e[1]
        }
        sb.append(t, at, end)
        fun moved(pos: Int): Int {
            var delta = 0
            for (e in edits) {
                if (e[0] >= pos) break
                val newLen = if (toTabs) e[2] / w + e[2] % w else e[2]
                val keptOld = minOf(pos, e[1]) - e[0]
                delta += if (pos >= e[1]) newLen - (e[1] - e[0]) else minOf(keptOld, newLen) - keptOld
            }
            return pos + delta
        }
        return TextEdit(start, end, sb.toString(), moved(selStart), moved(selEnd))
    }

    // ------------------------------------------------------------------ line endings

    /** The text with every line break (`\n`, `\r\n` or a lone `\r`) changed to [to]: `\n` or `\r\n`. */
    fun lineEndings(t: CharSequence, to: String, selStart: Int = 0, selEnd: Int = 0): TextEdit? {
        require(to == "\n" || to == "\r\n")
        var first = -1
        var last = -1
        val n = t.length
        var i = 0
        while (i < n) {
            val c = t[i]
            if (c == '\n' || c == '\r') {
                val len = if (c == '\r' && i + 1 < n && t[i + 1] == '\n') 2 else 1
                if (t.subSequence(i, i + len).toString() != to) {
                    if (first < 0) first = i
                    last = i + len
                }
                i += len
            } else {
                i++
            }
        }
        if (first < 0) return null
        val sb = StringBuilder(last - first + 16)
        var at = first
        i = first
        while (i < last) {
            val c = t[i]
            if (c == '\n' || c == '\r') {
                val len = if (c == '\r' && i + 1 < n && t[i + 1] == '\n') 2 else 1
                sb.append(t, at, i)
                sb.append(to)
                i += len
                at = i
            } else {
                i++
            }
        }
        sb.append(t, at, last)
        fun moved(pos: Int): Int {
            var delta = 0
            var k = first
            while (k < minOf(pos, last)) {
                val c = t[k]
                if (c == '\n' || c == '\r') {
                    val len = if (c == '\r' && k + 1 < n && t[k + 1] == '\n') 2 else 1
                    if (k + len <= pos) delta += to.length - len
                    else if (len == 2 && to.length == 1) delta -= 1 // a cursor between \r and \n
                    k += len
                } else {
                    k++
                }
            }
            return pos + delta
        }
        return TextEdit(first, last, sb.toString(), moved(selStart), moved(selEnd))
    }
}
