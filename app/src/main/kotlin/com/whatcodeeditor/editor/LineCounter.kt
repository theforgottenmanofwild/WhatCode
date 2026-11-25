package com.whatcodeeditor.editor

/**
 * Finds the line and column of a position for the status bar. Counting from the start of the text on every key
 * press is slow in a big file, so this remembers where it counted to last time and counts on from there. The
 * cursor is nearly always close to where it was.
 *
 * Tell it about every change to the text with [edited] (the position where the change begins), so what it
 * remembers never describes text that is gone.
 */
class LineCounter {

    class Position(val line: Int, val column: Int)

    // Where the last count ended: the position, its line (counted from 1) and the start of that line.
    private var anchorPos = 0
    private var anchorLine = 1
    private var anchorLineStart = 0

    /** The text changed from [start] on: what was counted before [start] is still right, the rest is not. */
    fun edited(start: Int) {
        if (start >= anchorPos) return
        if (start >= anchorLineStart) {
            anchorPos = anchorLineStart // the change is inside the remembered line, so that line's start is still right
        } else {
            reset()
        }
    }

    private fun reset() {
        anchorPos = 0
        anchorLine = 1
        anchorLineStart = 0
    }

    /** The line and column (both counted from 1) of [pos] in [t]. */
    fun locate(t: CharSequence, pos: Int): Position {
        val p = pos.coerceIn(0, t.length)
        if (anchorPos > t.length || anchorLineStart > anchorPos ||
            (anchorLineStart > 0 && t[anchorLineStart - 1] != '\n')
        ) {
            reset()
        }
        var line = anchorLine
        var lineStart = anchorLineStart
        if (p >= anchorPos) {
            for (i in anchorPos until p) {
                if (t[i] == '\n') {
                    line++
                    lineStart = i + 1
                }
            }
        } else {
            for (i in p until anchorPos) if (t[i] == '\n') line--
            lineStart = p
            while (lineStart > 0 && t[lineStart - 1] != '\n') lineStart--
        }
        anchorPos = p
        anchorLine = line
        anchorLineStart = lineStart
        return Position(line, p - lineStart + 1)
    }
}
