package com.whatcode.editor.completion

/**
 * Word completion: the words of the open files that start with the letters before the cursor. Plain logic, no Android.
 *
 * Only a stretch of each text is read ([AROUND] characters on each side of the cursor in the file being edited, and the
 * first [OTHER_CHARS] of each other file), so a suggestion comes as quickly in a huge file as in a small one.
 */
object WordCompletion {

    const val AROUND = 100_000
    const val OTHER_CHARS = 300_000
    const val MAX_OTHER_FILES = 10
    const val MAX_WORD = 64
    const val DEFAULT_LIMIT = 8

    fun isWordChar(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    /**
     * Where the word that ends at [cursor] starts, or -1 when there is none: the cursor is not right after a word, the
     * word runs on after the cursor, or it starts with a digit (a number is not completed).
     */
    fun prefixStart(t: CharSequence, cursor: Int): Int {
        if (cursor <= 0 || cursor > t.length) return -1
        if (cursor < t.length && isWordChar(t[cursor])) return -1
        var start = cursor
        while (start > 0 && cursor - start < MAX_WORD && isWordChar(t[start - 1])) start--
        if (start == cursor || t[start].isDigit()) return -1
        if (start > 0 && isWordChar(t[start - 1])) return -1 // longer than a word can be
        return start
    }

    /** Thrown by a [suggest] that was stopped, so its caller can drop it. */
    class Stopped : Exception()

    /**
     * Up to [limit] words that start with [prefix] and are longer than it: first the words of [current] by how close
     * they are to [cursor], then those found only in [others] by how often they appear. A word with the capitals of
     * the prefix comes before one that matches only when capitals are ignored. [stop] is asked now and then and
     * makes the search throw [Stopped] when it says yes.
     */
    fun suggest(
        current: CharSequence,
        cursor: Int,
        prefix: String,
        others: List<CharSequence>,
        limit: Int = DEFAULT_LIMIT,
        stop: () -> Boolean = { false },
    ): List<String> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        // Closest distance from the cursor, for the words of the file being edited.
        val near = HashMap<String, Int>()
        val from = maxOf(0, cursor - AROUND)
        val to = minOf(current.length, cursor + AROUND)
        val skipFrom = cursor - prefix.length
        scan(current, from, to, prefix, stop) { word, start ->
            // The word being typed is not a suggestion of itself.
            if (start == skipFrom) return@scan
            val d = if (start < cursor) cursor - start else start - cursor
            val old = near[word]
            if (old == null || d < old) near[word] = d
        }
        val counts = HashMap<String, Int>()
        for (other in others.take(MAX_OTHER_FILES)) {
            scan(other, 0, minOf(other.length, OTHER_CHARS), prefix, stop) { word, _ ->
                if (word !in near) counts[word] = (counts[word] ?: 0) + 1
            }
        }
        fun exact(w: String) = w.startsWith(prefix)
        val first = near.keys.sortedWith(compareBy<String>({ !exact(it) }, { near.getValue(it) }, { it }))
        val then = counts.keys.sortedWith(compareBy<String>({ !exact(it) }, { -counts.getValue(it) }, { it }))
        return (first + then).take(limit)
    }

    /** Calls [found] with each word of [t] from [from] to [to] that starts with [prefix] (capitals ignored) and is longer. */
    private inline fun scan(
        t: CharSequence, from: Int, to: Int, prefix: String, stop: () -> Boolean, found: (String, Int) -> Unit,
    ) {
        var i = from
        // Start at the beginning of a word, not halfway into one.
        while (i in 1 until to && isWordChar(t[i - 1]) && isWordChar(t[i])) i++
        var sinceCheck = 0
        val first = prefix[0]
        while (i < to) {
            if (++sinceCheck >= 4096) {
                sinceCheck = 0
                if (stop()) throw Stopped()
            }
            val c = t[i]
            if (!isWordChar(c)) {
                i++
                continue
            }
            val start = i
            while (i < to && isWordChar(t[i])) i++
            val len = i - start
            if (len <= prefix.length || len > MAX_WORD) continue
            if (!t[start].equals(first, ignoreCase = true)) continue
            var ok = true
            for (k in 1 until prefix.length) {
                if (!t[start + k].equals(prefix[k], ignoreCase = true)) {
                    ok = false
                    break
                }
            }
            if (ok) found(t.subSequence(start, i).toString(), start)
        }
    }
}
