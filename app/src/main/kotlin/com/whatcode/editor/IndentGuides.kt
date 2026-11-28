package com.whatcode.editor

/**
 * Where the indent guides go. A guide stands at the start of each level of indentation that a line is inside: at
 * column 0, then every step columns, as far as the line is indented. A tab moves to the next such column, so
 * one tab is one level. A line with nothing on it takes its guides from the lines round it. Only the white space at the
 * start of a line is read. No Android here.
 */
object IndentGuides {

    /** No more guides than this are drawn on one line, so a line of nothing but spaces costs little. */
    const val MAX_LEVELS = 40

    /** The width of a level for a file indented with [unit] (see [TypingLogic.indentUnit]). */
    fun stepOf(unit: String): Int = if (unit.isEmpty() || unit.startsWith("\t")) 4 else unit.length

    /** The columns of white space at the start of the line at [lineStart], or -1 if the line has nothing else. */
    fun indent(t: CharSequence, lineStart: Int, step: Int): Int {
        var i = lineStart
        var column = 0
        while (i < t.length) {
            if (column >= step * MAX_LEVELS) return column
            column = when (t[i]) {
                ' ' -> column + 1
                '\t' -> (column / step + 1) * step
                '\n', '\r' -> return -1
                else -> return column
            }
            i++
        }
        return -1
    }

    /** The places in the line at [lineStart] where its guides go: the characters that start each level below [depth]. */
    fun offsets(t: CharSequence, lineStart: Int, step: Int, depth: Int): IntArray {
        val out = IntArray(MAX_LEVELS)
        var count = 0
        var i = lineStart
        var column = 0
        while (i < t.length && column < depth && count < MAX_LEVELS && (t[i] == ' ' || t[i] == '\t')) {
            if (column % step == 0) out[count++] = i
            column = if (t[i] == '\t') (column / step + 1) * step else column + 1
            i++
        }
        return out.copyOf(count)
    }

    /**
     * How far the guides of an empty line go, from the indentation of the nearest lines with text [before] and [after]
     * it (-1 for none). Under a line that opens a block it is one level deeper than that line.
     */
    fun blankDepth(before: Int, after: Int, step: Int): Int = when {
        before < 0 || after < 0 -> 0
        after > before -> minOf(after, before + step)
        else -> after
    }
}
