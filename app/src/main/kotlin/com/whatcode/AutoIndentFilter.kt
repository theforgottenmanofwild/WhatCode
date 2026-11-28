package com.whatcode

import android.text.InputFilter
import android.text.Spanned

/**
 * Auto-indent: a typed newline copies the current line's leading whitespace. (With smart indentation on,
 * CodeEditText handles Enter itself and this only sees the Enter that it lets through.)
 */
internal object AutoIndentFilter : InputFilter {
    override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
        if (end - start != 1 || source[start] != '\n') return null
        var lineStart = dstart
        while (lineStart > 0 && dest[lineStart - 1] != '\n') lineStart--
        val sb = StringBuilder("\n")
        var i = lineStart
        while (i < dstart && (dest[i] == ' ' || dest[i] == '\t')) {
            sb.append(dest[i])
            i++
        }
        return sb.toString()
    }
}
