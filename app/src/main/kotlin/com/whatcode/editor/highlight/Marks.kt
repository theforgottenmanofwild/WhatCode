package com.whatcode.editor.highlight

/**
 * Finds the line starts where a scan could begin again and give the same result as one that began at the start of
 * the text. The scanners hand it the gaps between the pieces they matched: a line start inside a piece (a block
 * comment, a string, a script) is no place to begin, and neither is one where a scanner would remember something
 * from the text before it (the word before a function name, an open tag, a half-read CSS declaration).
 *
 * A scanner calls [before] when a piece starts, [after] when it has dealt with it, and [finish] at the end.
 * [ok] answers, for a line start in a gap, whether the scanner is in its starting state there.
 */
internal class Marks(private val text: String, from: Int, private val found: (Int) -> Boolean) {
    private var lastEnd = from
    private var lastReported = from

    /** Where the last piece the scanner read began, or -1 before the first. */
    var lastStart = -1
        private set

    /** True if the scanner has nothing to remember at the line start given. The scanner sets it once. */
    var ok: (Int) -> Boolean = { true }

    /** A piece starts at [s]. Returns false when the caller wants no more line starts. */
    fun before(s: Int): Boolean {
        var p = lineStartAtOrAfter(lastEnd)
        while (p <= s) {
            if (p > lastReported && ok(p)) {
                lastReported = p
                if (!found(p)) return false
            }
            p = lineStartAtOrAfter(p + 1)
        }
        lastStart = s
        return true
    }

    /**
     * What was seen up to [end] has been dealt with. It is the end of the piece, or the start of a piece after
     * the one the scanner turned down (a string outside a tag), which it reads again from there.
     */
    fun after(end: Int) {
        lastEnd = end
    }

    /** The text up to [to] has been read. */
    fun finish(to: Int) {
        before(to)
    }

    // The newline last found and the position it was looked for from, so a long line with no break in it is not
    // searched to its end again for every piece on it.
    private var searchedFrom = Int.MAX_VALUE
    private var newlineAt = 0

    private fun lineStartAtOrAfter(i: Int): Int {
        if (i <= 0) return 0
        if (i > text.length) return Int.MAX_VALUE
        if (text[i - 1] == '\n') return i
        if (i < searchedFrom || i > newlineAt) {
            val nl = text.indexOf('\n', i)
            searchedFrom = i
            newlineAt = if (nl < 0) Int.MAX_VALUE else nl
        }
        return if (newlineAt == Int.MAX_VALUE) Int.MAX_VALUE else newlineAt + 1
    }
}
