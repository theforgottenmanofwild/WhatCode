package com.whatcode.editor

/**
 * Where the cursor was before each jump (Go to line, Go to symbol, a bookmark, a search hit), so Back and Forward can
 * walk through them. A run of search hits counts as one jump. Positions follow the edits made since ([edited]).
 * No Android here.
 */
class JumpHistory(private val limit: Int = LIMIT) {

    private val before = ArrayList<Int>()
    private val after = ArrayList<Int>()
    private var inRun = false

    val canBack: Boolean get() = before.isNotEmpty()
    val canForward: Boolean get() = after.isNotEmpty()

    fun copy(): JumpHistory = JumpHistory(limit).also {
        it.before.addAll(before)
        it.after.addAll(after)
        it.inRun = inRun
    }

    fun clear() {
        before.clear()
        after.clear()
        inRun = false
    }

    /**
     * The cursor is about to jump away from [from]. With [run] the jump is one of several in a row of the same kind
     * (the next hit of a search): only the first of them is kept. A jump drops whatever Forward could have gone to.
     */
    fun record(from: Int, run: Boolean = false) {
        if (run && inRun) return
        inRun = run
        after.clear()
        if (before.lastOrNull() == from) return
        before.add(from)
        if (before.size > limit) before.removeAt(0)
    }

    /** The run of jumps that [record] was told about ends: the next one is kept again. */
    fun endRun() {
        inRun = false
    }

    /** The place to go back to from [current], or null if there is none. [current] is what Forward will return to. */
    fun back(current: Int): Int? {
        inRun = false
        while (before.isNotEmpty()) {
            val to = before.removeAt(before.size - 1)
            if (to == current) continue
            after.add(current)
            return to
        }
        return null
    }

    /** The place to go forward to from [current], or null if there is none. */
    fun forward(current: Int): Int? {
        inRun = false
        while (after.isNotEmpty()) {
            val to = after.removeAt(after.size - 1)
            if (to == current) continue
            before.add(current)
            return to
        }
        return null
    }

    /** [removed] characters at [start] were replaced by [added] characters; the places move with the text. */
    fun edited(start: Int, removed: Int, added: Int) {
        val end = start + removed
        fun move(list: ArrayList<Int>) {
            for (i in list.indices) {
                val m = list[i]
                list[i] = when {
                    m >= end -> m - removed + added
                    m > start -> start
                    else -> m
                }
            }
        }
        move(before)
        move(after)
    }

    /** Drops places beyond a text of [length] characters (after a text is loaded or replaced). */
    fun clamp(length: Int) {
        for (i in before.indices) before[i] = before[i].coerceIn(0, length)
        for (i in after.indices) after[i] = after[i].coerceIn(0, length)
    }

    companion object {
        /** How many places are kept. */
        const val LIMIT = 100
    }
}
