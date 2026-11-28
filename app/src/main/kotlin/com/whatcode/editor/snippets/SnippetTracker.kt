package com.whatcode.editor.snippets

/**
 * Keeps track of where the tab stops of a snippet that was put in are, while the text is edited. [stops] holds the
 * start and end of each stop in turn, and [finalAt] is where the cursor goes after the last one. Plain logic, no Android.
 */
class SnippetTracker(private val stops: IntArray, finalAt: Int) {

    var finalAt: Int = finalAt
        private set

    /** The number of tab stops. */
    val count: Int get() = stops.size / 2

    fun start(i: Int): Int = stops[2 * i]

    fun end(i: Int): Int = stops[2 * i + 1]

    /**
     * Moves every place for an edit that replaced [before] characters from [start] with [count] characters. A start
     * stays in front of text put in at it, and an end moves behind it, so typing at a stop makes the stop longer.
     * Places inside what was replaced go to the edges of what replaced it.
     */
    fun changed(start: Int, before: Int, count: Int) {
        val oldEnd = start + before
        val delta = count - before
        for (k in stops.indices) {
            stops[k] = if (k % 2 == 0) moveStart(stops[k], start, oldEnd, delta) else moveEnd(stops[k], start, before, oldEnd, count, delta)
        }
        finalAt = moveEnd(finalAt, start, before, oldEnd, count, delta)
        // A stop never ends in front of where it starts.
        for (i in 0 until this.count) if (stops[2 * i + 1] < stops[2 * i]) stops[2 * i + 1] = stops[2 * i]
    }

    private fun moveStart(p: Int, start: Int, oldEnd: Int, delta: Int): Int = when {
        p <= start -> p
        p >= oldEnd -> p + delta
        else -> start
    }

    private fun moveEnd(p: Int, start: Int, before: Int, oldEnd: Int, count: Int, delta: Int): Int = when {
        p < start || (p == start && before > 0) -> p
        p >= oldEnd -> p + delta
        else -> start + count
    }

    /** True if [pos] is in the stretch from the first place to the last one, the ends included. */
    fun contains(pos: Int): Boolean {
        var lo = finalAt
        var hi = finalAt
        for (i in 0 until count) {
            lo = minOf(lo, stops[2 * i])
            hi = maxOf(hi, stops[2 * i + 1])
        }
        return pos in lo..hi
    }
}
