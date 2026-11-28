package com.whatcode.editor.multi

/** A stretch of the text from [start] up to [end]; it is a place for a cursor when it is empty. Always [start] <= [end]. */
data class Sel(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "bad selection $start..$end" }
    }

    val empty: Boolean get() = start == end
}

/**
 * One change that stands for an edit at every cursor: [insert] replaces the text from [start] to [end], and then
 * [ranges] are where the cursors are (the ones that were [primary]'s companions are all empty). Applying it as a
 * single replacement makes it one undo step.
 */
class Applied(val start: Int, val end: Int, val insert: String, val ranges: List<Sel>, val primary: Int)

/**
 * The rules for editing in several places at once, kept apart from the editor view so they only deal in text and
 * numbers. Every function that edits wants [Sel]s that are sorted and do not overlap (see [normalize]) and answers
 * null when nothing would change, or when the edit would be too big to hold in a single undo step.
 */
object MultiSelect {

    const val MAX_CURSORS = 1000

    /** The most text one multi-cursor edit may cover, from its first place to its last. */
    const val MAX_SPAN = 5_000_000

    /** The longest text that "add the next occurrence" will look for. */
    const val MAX_NEEDLE = 10_000

    fun isWordChar(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    /** The word that touches [pos], or null when there is none. */
    fun wordAt(t: CharSequence, pos: Int): Sel? {
        if (pos < 0 || pos > t.length) return null
        var a = pos
        var b = pos
        while (a > 0 && isWordChar(t[a - 1])) a--
        while (b < t.length && isWordChar(t[b])) b++
        return if (a == b) null else Sel(a, b)
    }

    /**
     * Sorts [all] and joins the ones that overlap or are the same, and says where [primary] went. Selections that only
     * touch are kept apart, so two carets side by side stay two.
     */
    fun normalize(all: List<Sel>, primary: Sel): Pair<List<Sel>, Int> {
        val sorted = all.sortedWith(compareBy({ it.start }, { it.end }))
        val out = ArrayList<Sel>(sorted.size)
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && (s.start < last.end || (s == last))) {
                out[out.size - 1] = Sel(last.start, maxOf(last.end, s.end))
            } else {
                out.add(s)
            }
        }
        val i = out.indexOfFirst { primary.start >= it.start && primary.end <= it.end }
        return out to (if (i >= 0) i else 0)
    }

    private fun matchesAt(t: CharSequence, i: Int, needle: String, matchCase: Boolean): Boolean {
        for (k in needle.indices) if (!t[i + k].equals(needle[k], ignoreCase = !matchCase)) return false
        return true
    }

    private fun boundaryOk(t: CharSequence, i: Int, len: Int, needle: String): Boolean {
        if (isWordChar(needle.first()) && i > 0 && isWordChar(t[i - 1])) return false
        if (isWordChar(needle.last()) && i + len < t.length && isWordChar(t[i + len])) return false
        return true
    }

    private fun overlaps(taken: List<Sel>, i: Int, len: Int): Boolean {
        // The last place that starts before this match ends is the only one that can reach into it.
        var lo = 0
        var hi = taken.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (taken[mid].start < i + len) lo = mid + 1 else hi = mid
        }
        return lo > 0 && taken[lo - 1].end > i
    }

    /**
     * The next place after [from] where [needle] is, going on from the start of the text when the end is reached, that
     * does not overlap [taken] (sorted). Null when there is none. With [wholeWord], a word is not found inside a longer one.
     */
    fun nextOccurrence(
        t: CharSequence, needle: String, from: Int, taken: List<Sel>, matchCase: Boolean, wholeWord: Boolean,
    ): Sel? {
        val len = needle.length
        if (len == 0 || len > t.length) return null
        val last = t.length - len
        val begin = from.coerceIn(0, t.length)
        var i = begin
        var wrapped = false
        while (true) {
            if (i > last || (wrapped && i >= begin)) {
                if (wrapped) return null
                wrapped = true
                i = 0
                continue
            }
            if (matchesAt(t, i, needle, matchCase) && (!wholeWord || boundaryOk(t, i, len, needle)) && !overlaps(taken, i, len)) {
                return Sel(i, i + len)
            }
            i++
        }
    }

    /** Every place where [needle] is, left to right, without overlaps, up to [limit] of them. */
    fun allOccurrences(t: CharSequence, needle: String, matchCase: Boolean, wholeWord: Boolean, limit: Int = MAX_CURSORS + 1): List<Sel> {
        val len = needle.length
        val out = ArrayList<Sel>()
        if (len == 0) return out
        var i = 0
        while (i <= t.length - len && out.size < limit) {
            if (matchesAt(t, i, needle, matchCase) && (!wholeWord || boundaryOk(t, i, len, needle))) {
                out.add(Sel(i, i + len))
                i += len
            } else {
                i++
            }
        }
        return out
    }

    /** Replaces each place by the text [texts] gives for it, as one edit. */
    fun replace(t: CharSequence, sels: List<Sel>, primary: Int, texts: (Int) -> String): Applied? {
        if (sels.isEmpty()) return null
        val start = sels.first().start
        val end = sels.last().end
        if (end - start > MAX_SPAN) return null
        val sb = StringBuilder()
        val ranges = ArrayList<Sel>(sels.size)
        var at = start
        var changed = false
        for ((i, s) in sels.withIndex()) {
            sb.append(t, at, s.start)
            val put = texts(i)
            if (put.length != s.end - s.start || !same(t, s, put)) changed = true
            sb.append(put)
            val caret = start + sb.length
            ranges.add(Sel(caret, caret))
            at = s.end
        }
        if (!changed) return null
        return Applied(start, end, sb.toString(), ranges, primary)
    }

    private fun same(t: CharSequence, s: Sel, put: String): Boolean {
        for (k in put.indices) if (t[s.start + k] != put[k]) return false
        return true
    }

    /** [text] typed at every cursor, over what is selected there. */
    fun insert(t: CharSequence, sels: List<Sel>, primary: Int, text: String): Applied? = replace(t, sels, primary) { text }

    /** Backspace: the selected text, or the character before an empty cursor (a whole emoji, not half of one), is removed. */
    fun backspace(t: CharSequence, sels: List<Sel>, primary: Int, count: Int = 1): Applied? =
        removeAround(t, sels, primary, count, 0)

    /** Delete: the selected text, or the character after an empty cursor, is removed. */
    fun deleteForward(t: CharSequence, sels: List<Sel>, primary: Int, count: Int = 1): Applied? =
        removeAround(t, sels, primary, 0, count)

    /** Removes [before] characters before and [after] characters after each empty cursor, and the selected text of the others. */
    fun removeAround(t: CharSequence, sels: List<Sel>, primary: Int, before: Int, after: Int): Applied? {
        val wide = sels.map { s ->
            if (!s.empty) s else Sel(back(t, s.start, before), forward(t, s.end, after))
        }
        val (merged, p) = normalize(wide, wide[primary.coerceIn(0, wide.size - 1)])
        return replace(t, merged, p) { "" }
    }

    private fun back(t: CharSequence, pos: Int, n: Int): Int {
        var p = pos
        repeat(n) {
            if (p > 0) {
                p--
                if (p > 0 && t[p].isLowSurrogate() && t[p - 1].isHighSurrogate()) p--
            }
        }
        return p
    }

    private fun forward(t: CharSequence, pos: Int, n: Int): Int {
        var p = pos
        repeat(n) {
            if (p < t.length) {
                p++
                if (p < t.length && t[p - 1].isHighSurrogate() && t[p].isLowSurrogate()) p++
            }
        }
        return p
    }

    /** Enter: a line break and the indentation of the line, as far as the cursor is into it, at every cursor. */
    fun newline(t: CharSequence, sels: List<Sel>, primary: Int): Applied? = replace(t, sels, primary) { i ->
        val s = sels[i]
        var ls = s.start
        while (ls > 0 && t[ls - 1] != '\n') ls--
        var e = ls
        while (e < s.start && (t[e] == ' ' || t[e] == '\t')) e++
        val eol = if (ls < t.length && lineHasCr(t, s.start)) "\r\n" else "\n"
        eol + t.subSequence(ls, e)
    }

    /** True when the line that holds [pos] ends in a carriage return and a line feed. */
    private fun lineHasCr(t: CharSequence, pos: Int): Boolean {
        var i = pos
        while (i < t.length && t[i] != '\n') i++
        return i < t.length && i > 0 && t[i - 1] == '\r'
    }

    /**
     * Moves every place: an empty one goes where [step] takes it from its position, and a selection is dropped at its
     * end ([toEnd]) or its start. Places that meet are joined, and the answer says where the primary one is.
     */
    fun move(t: CharSequence, sels: List<Sel>, primary: Int, toEnd: Boolean, step: (CharSequence, Int) -> Int): Pair<List<Sel>, Int> {
        val moved = sels.map { s ->
            val p = if (!s.empty) (if (toEnd) s.end else s.start) else step(t, s.start).coerceIn(0, t.length)
            Sel(p, p)
        }
        return normalize(moved, moved[primary.coerceIn(0, moved.size - 1)])
    }

    /** The selected text of each place, one after another with a line break between. */
    fun joined(t: CharSequence, sels: List<Sel>): String =
        sels.joinToString("\n") { t.subSequence(it.start, it.end).toString() }

    /**
     * [clip] cut into one piece for each of [count] cursors, when it has that many lines; null when it does not, and the
     * whole of it is then put in at every cursor.
     */
    fun splitForPaste(clip: String, count: Int): List<String>? {
        if (count < 2) return null
        val trimmed = clip.removeSuffix("\n").removeSuffix("\r")
        val lines = trimmed.split("\r\n", "\n")
        return if (lines.size == count) lines else null
    }
}
