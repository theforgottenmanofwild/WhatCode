package com.whatcode.editor

/**
 * What the line number gutter needs that has nothing to do with Android: how many digits it must have, and which
 * rows of the laid-out text get a number. A line of the file may be shown on several rows when it wraps, and only
 * the first row of a line is numbered. [CodeEditText] does the drawing with the answers. Plain logic, so it can be
 * tested without a device.
 */
object LineNumbers {

    /** How many digits the number of the last line has: 1 to 9 need one, 10 to 99 two, and so on. */
    fun digits(lines: Int): Int {
        var n = if (lines < 1) 1 else lines
        var d = 1
        while (n >= 10) {
            n /= 10
            d++
        }
        return d
    }

    /** How many line breaks (`\n`) there are in [text] from [from] up to [to]. */
    fun breaksIn(text: CharSequence, from: Int, to: Int): Int {
        var n = 0
        val end = minOf(to, text.length)
        for (i in maxOf(from, 0) until end) if (text[i] == '\n') n++
        return n
    }

    /**
     * Calls [draw] with the row and the line number of every row from [firstRow] to [lastRow] that is the first row of
     * its line. [rowStart] gives the position in [text] where a row starts, and [firstNumber] is the number of the
     * line that [firstRow] belongs to (it may be a wrapped row in the middle of that line; it then gets no number).
     * A row starts a new line when the character before it is a line break, or when it is the very start of the text.
     */
    fun label(
        firstRow: Int,
        lastRow: Int,
        rowStart: (Int) -> Int,
        text: CharSequence,
        firstNumber: Int,
        draw: (row: Int, number: Int) -> Unit,
    ) {
        var number = firstNumber
        for (row in firstRow..lastRow) {
            val start = rowStart(row)
            val startsLine = start <= 0 || (start <= text.length && text[start - 1] == '\n')
            if (row > firstRow && startsLine) number++
            if (startsLine) draw(row, number)
        }
    }
}

/**
 * How many lines the text has, kept up to date from the edits instead of counting the whole text again: the gutter
 * needs it on every change to know how many digits to make room for, and a big file must not be read from start to
 * end for each key press. Tell it about each change with [removed] (called before the change, with the text as it
 * was) and [added] (called after it, with the text as it is).
 */
class LineTotal {
    private var breaks = 0

    /** The number of lines: one more than the number of line breaks. */
    val lines: Int get() = breaks + 1

    fun removed(text: CharSequence, start: Int, count: Int) {
        breaks = maxOf(0, breaks - LineNumbers.breaksIn(text, start, start + count))
    }

    fun added(text: CharSequence, start: Int, count: Int) {
        breaks += LineNumbers.breaksIn(text, start, start + count)
    }
}
