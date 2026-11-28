package com.whatcode.editor

/**
 * The blocks that one open file has folded, kept as the stretches of text that are out of sight: each one starts
 * after the line break that ends the line that stays on show, and ends after the line break that ends its last hidden
 * line (see [FoldRegion]). The stretches are kept up to date as the text is edited ([edited]), so a fold stays with its
 * lines when lines are added above it. A block can hold another that is folded too; that one is kept, and shows
 * folded again when the outer block is opened. No Android here.
 */
class Folds {

    private val ranges = ArrayList<IntArray>()

    val count: Int get() = ranges.size

    fun copy(): Folds = Folds().also { f -> ranges.forEach { f.ranges.add(it.copyOf()) } }

    fun clear() = ranges.clear()

    /** The stretches, as start and end one after the other, for putting a tab away. */
    fun offsets(): IntArray {
        val out = IntArray(ranges.size * 2)
        for ((i, r) in ranges.withIndex()) {
            out[i * 2] = r[0]
            out[i * 2 + 1] = r[1]
        }
        return out
    }

    /** True if the stretch from [start] to [end] is folded. */
    fun isFolded(start: Int, end: Int): Boolean = ranges.any { it[0] == start && it[1] == end }

    /** The fold that starts right at [start] (the line after its header), or null. */
    fun startingAt(start: Int): IntArray? = ranges.firstOrNull { it[0] == start }?.copyOf()

    /**
     * Folds the stretch from [start] to [end]. A fold that crosses it (starts inside it and ends outside, or the other
     * way round) is dropped; one that lies inside it is kept. Returns false if it was folded already.
     */
    fun fold(start: Int, end: Int): Boolean {
        if (start >= end || isFolded(start, end)) return false
        ranges.removeAll { (it[0] < start && it[1] > start && it[1] < end) || (it[0] > start && it[0] < end && it[1] > end) }
        ranges.add(intArrayOf(start, end))
        ranges.sortWith(compareBy({ it[0] }, { -it[1] }))
        return true
    }

    /**
     * Folds every stretch of [regions] (each a [FoldRegion]'s start and end) as well as the ones that are folded now,
     * in one pass, so a text with thousands of blocks is not slow. A stretch that crosses another is left out. Returns
     * how many were folded that were not before.
     */
    fun foldMany(regions: List<FoldRegion>): Int {
        val before = ranges.size
        val all = ArrayList<IntArray>(before + regions.size)
        all.addAll(ranges)
        for (r in regions) if (r.start < r.end) all.add(intArrayOf(r.start, r.end))
        all.sortWith(compareBy({ it[0] }, { -it[1] }))
        ranges.clear()
        val open = ArrayList<IntArray>()
        for (r in all) {
            while (open.isNotEmpty() && open.last()[1] <= r[0]) open.removeAt(open.size - 1)
            // A stretch that starts inside an open one must also end inside it; one that is the same twice is kept once.
            if (open.isNotEmpty() && r[1] > open.last()[1]) continue
            if (ranges.isNotEmpty() && ranges.last()[0] == r[0] && ranges.last()[1] == r[1]) continue
            ranges.add(r)
            open.add(r)
        }
        return ranges.size - before
    }

    /** Opens the stretch from [start] to [end]. Returns false if it was not folded. */
    fun unfold(start: Int, end: Int): Boolean = ranges.removeAll { it[0] == start && it[1] == end }

    /** True if the character at [offset] is out of sight. */
    fun hides(offset: Int): Boolean = ranges.any { offset >= it[0] && offset < it[1] }

    /** The folds that put the character at [offset] out of sight, the outermost first. */
    fun hiding(offset: Int): List<IntArray> = ranges.filter { offset >= it[0] && offset < it[1] }.map { it.copyOf() }

    /**
     * The folds that are not inside another one: the only ones that need to be shown as hidden, because what a fold
     * holds is hidden with it. In order of start.
     */
    fun outermost(): List<IntArray> {
        val out = ArrayList<IntArray>()
        var reach = -1
        for (r in ranges) {
            if (r[0] >= reach) {
                out.add(r.copyOf())
                reach = r[1]
            }
        }
        return out
    }

    /**
     * True if an edit that replaces the characters from [start] to [end] touches a fold: it reaches into the hidden
     * text, or it takes away or goes in front of the line break that ends the line that stays on show, or it joins
     * the next line to the hidden ones. Typing at the end of the line that stays on show does not touch it.
     */
    fun touches(start: Int, end: Int): Boolean = ranges.any { touched(it, start, end) }

    /** Opens every fold that [touches] the same edit, and returns the ones that were opened. */
    fun openTouched(start: Int, end: Int): List<IntArray> {
        val gone = ranges.filter { touched(it, start, end) }
        ranges.removeAll(gone)
        return gone.map { it.copyOf() }
    }

    private fun touched(r: IntArray, start: Int, end: Int): Boolean = end >= r[0] && start < r[1]

    /**
     * [removed] characters at [start] were replaced by [added] characters. A fold after the change moves with it, one
     * before it stays, and one the change touches (see [touches]) is dropped.
     */
    fun edited(start: Int, removed: Int, added: Int) {
        if (ranges.isEmpty()) return
        val end = start + removed
        val delta = added - removed
        val keep = ArrayList<IntArray>(ranges.size)
        for (r in ranges) {
            when {
                touched(r, start, end) -> {}
                start >= r[1] -> keep.add(r)
                else -> keep.add(intArrayOf(r[0] + delta, r[1] + delta))
            }
        }
        ranges.clear()
        ranges.addAll(keep)
    }

    /**
     * Puts the folds back from [offsets] (see [offsets]) for [text], keeping only the ones that still fit it: both
     * ends must come after a line break and lie inside the text.
     */
    fun load(offsets: IntArray, text: CharSequence) {
        ranges.clear()
        var i = 0
        while (i + 1 < offsets.size) {
            val a = offsets[i]
            val b = offsets[i + 1]
            if (a in 1 until b && b <= text.length && text[a - 1] == '\n' && text[b - 1] == '\n') fold(a, b)
            i += 2
        }
    }
}
