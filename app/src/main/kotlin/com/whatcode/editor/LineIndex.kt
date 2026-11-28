package com.whatcode.editor

/**
 * Where every line of a text starts, kept as one `IntArray` and changed in step with the text instead of being
 * counted again. It answers the line of a position, the start of a line and the number of lines at once, for the
 * gutter, Go to line, the status bar (see [LineCounter.attach]) and the colouring of a window of the text.
 *
 * A line starts at 0 and after every `\n`; a text that ends with `\n` has an empty last line. Tell it about every
 * change with [replace], in the order the changes were made.
 */
class LineIndex {
    private var starts = IntArray(256)
    private var n = 1

    /** What a [replace] did to the list of lines, so what is kept per line can be changed the same way. */
    class Splice(
        /** The line where the change began; it stays, whatever else happened. */
        val line: Int,
        /** How many lines after [line] the change took away. */
        val removed: Int,
        /** How many lines after [line] it added. */
        val added: Int,
    )

    val lineCount: Int get() = n

    /** Starts the index over for [text]. */
    fun build(text: CharSequence) {
        var count = 1
        for (i in text.indices) if (text[i] == '\n') count++
        starts = IntArray(maxOf(256, count + count / 4))
        n = 0
        starts[n++] = 0
        for (i in text.indices) if (text[i] == '\n') starts[n++] = i + 1
    }

    /** The offset where [line] (counted from 0) starts. */
    fun startOf(line: Int): Int = starts[line.coerceIn(0, n - 1)]

    /** The offset just after the last character of [line], not counting its `\n`, in a text of [textLength]. */
    fun endOf(line: Int, textLength: Int): Int =
        if (line >= n - 1) textLength else starts[line + 1] - 1

    /** The line (counted from 0) that has the position [offset]. */
    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = n - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The characters from [start] to [start] + [removed] were replaced by [inserted]. */
    fun replace(start: Int, removed: Int, inserted: CharSequence): Splice {
        val end = start + removed
        val line = lineOf(start)
        // Line starts in (start, end] belonged to a '\n' that is gone.
        var hi = line + 1
        while (hi < n && starts[hi] <= end) hi++
        val gone = hi - (line + 1)
        var add = 0
        for (i in inserted.indices) if (inserted[i] == '\n') add++
        val delta = inserted.length - removed
        val newCount = n - gone + add
        if (newCount > starts.size) starts = starts.copyOf(maxOf(newCount + newCount / 4, starts.size * 2))
        if (add != gone) System.arraycopy(starts, hi, starts, line + 1 + add, n - hi)
        if (delta != 0) for (i in line + 1 + add until newCount) starts[i] += delta
        var k = line + 1
        for (i in inserted.indices) if (inserted[i] == '\n') starts[k++] = start + i + 1
        n = newCount
        return Splice(line, gone, add)
    }
}
