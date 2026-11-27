package com.whatcode.editor

/**
 * Which characters of a stretch of text get a mark when whitespace is shown. [CodeEditText] asks only about the
 * text that is on screen, and draws the marks. Plain logic, so it can be tested without a device.
 */
object WhitespaceMarks {

    /** Calls [mark] for every space and tab from [from] up to [to]; [mark] is told which of the two it is. */
    fun find(text: CharSequence, from: Int, to: Int, mark: (at: Int, tab: Boolean) -> Unit) {
        for (i in maxOf(from, 0) until minOf(to, text.length)) {
            val c = text[i]
            if (c == ' ' || c == '\t') mark(i, c == '\t')
        }
    }
}
