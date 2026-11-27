package com.whatcode.editor

/**
 * The "expand selection" key. Each [expand] grows the selection to the smallest of these that is bigger than it: the
 * word, the line, the inside of enclosing brackets, those brackets included, the inside of an enclosing tag, the whole
 * element, and at last the whole text. [shrink] goes back through exactly the steps that were taken. Like
 * [MatchJump] it only deals in text and numbers, and a bracket in a string or a comment does not count.
 */
class SelectionExpander {

    /** A selection from [start] to [end]. */
    data class Range(val start: Int, val end: Int)

    // The selections that were grown from, oldest first, and the selection the newest step ended on.
    private val steps = ArrayList<Range>()
    private var current: Range? = null

    /** Forgets the steps, because the text changed. */
    fun reset() {
        steps.clear()
        current = null
    }

    /**
     * The next bigger selection for the one from [selStart] to [selEnd], or null if the whole text is selected.
     * [skipped] says whether the character at a position is in a string, a comment or a regular expression.
     */
    fun expand(
        text: CharSequence,
        selStart: Int,
        selEnd: Int,
        lang: Lang,
        skipped: (Int) -> Boolean = { false },
    ): Range? {
        val now = ordered(selStart, selEnd, text.length)
        val next = grow(text, now.start, now.end, lang, skipped) ?: return null
        // A selection that was changed by hand since the last step starts a new set of steps.
        if (now != current) steps.clear()
        steps.add(now)
        current = next
        return next
    }

    /** The selection before the last step, or null if there is none or the selection was changed since. */
    fun shrink(selStart: Int, selEnd: Int): Range? {
        if (Range(minOf(selStart, selEnd), maxOf(selStart, selEnd)) != current || steps.isEmpty()) {
            reset()
            return null
        }
        val back = steps.removeAt(steps.size - 1)
        current = back
        return back
    }

    private fun ordered(a: Int, b: Int, length: Int) =
        Range(minOf(a, b).coerceIn(0, length), maxOf(a, b).coerceIn(0, length))

    private fun grow(text: CharSequence, a: Int, b: Int, lang: Lang, skipped: (Int) -> Boolean): Range? {
        var best: Range? = null
        fun offer(start: Int, end: Int) {
            if (start > a || end < b || end - start <= b - a) return
            val found = best
            if (found == null || end - start < found.end - found.start) best = Range(start, end)
        }
        offerWord(text, a, b, ::offer)
        offerLine(text, a, b, ::offer)
        val from = maxOf(0, a - MAX_SCAN)
        val to = minOf(text.length, b + MAX_SCAN)
        offerBrackets(text, from, to, skipped, ::offer)
        for (e in MarkupElements.find(text, lang, from, to, skipped)) {
            offer(e.innerStart, e.innerEnd)
            offer(e.start, e.end)
        }
        offer(0, text.length)
        return best
    }

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun offerWord(text: CharSequence, a: Int, b: Int, offer: (Int, Int) -> Unit) {
        for (i in a until b) if (!isWord(text[i])) return
        var start = a
        while (start > 0 && isWord(text[start - 1])) start--
        var end = b
        while (end < text.length && isWord(text[end])) end++
        if (end > start) offer(start, end)
    }

    /** The line, without its line break; only for a selection that is inside one line. */
    private fun offerLine(text: CharSequence, a: Int, b: Int, offer: (Int, Int) -> Unit) {
        for (i in a until b) if (text[i] == '\n') return
        var start = a
        while (start > 0 && text[start - 1] != '\n') start--
        var end = b
        while (end < text.length && text[end] != '\n') end++
        if (end > b && text[end - 1] == '\r') end--
        offer(start, end)
    }

    private fun offerBrackets(text: CharSequence, from: Int, to: Int, skipped: (Int) -> Boolean, offer: (Int, Int) -> Unit) {
        val open = ArrayList<Int>()
        for (i in from until to) {
            val c = text[i]
            if (c == '(' || c == '[' || c == '{') {
                if (!skipped(i)) open.add(i)
            } else if (c == ')' || c == ']' || c == '}') {
                if (skipped(i)) continue
                val want = if (c == ')') '(' else if (c == ']') '[' else '{'
                val at = open.indexOfLast { text[it] == want }
                if (at < 0) continue
                val start = open[at]
                while (open.size > at) open.removeAt(open.size - 1)
                offer(start + 1, i)
                offer(start, i + 1)
            }
        }
    }

    companion object {
        /** How far before and after the selection brackets and tags are looked for, so a huge file stays quick. */
        const val MAX_SCAN = 200_000
    }
}
