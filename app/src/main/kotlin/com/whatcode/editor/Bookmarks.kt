package com.whatcode.editor

/**
 * The bookmarks of one text: marks on lines that Next bookmark and Previous bookmark go between. A mark is the
 * position of a place in its line, kept up to date as the text is edited ([edited]), so it stays with its line when
 * lines are added above it. No Android here.
 */
class Bookmarks {

    private val marks = ArrayList<Int>()

    val count: Int get() = marks.size

    /** The marks in order, for drawing and for putting a tab away. */
    fun offsets(): IntArray = marks.toIntArray()

    fun copy(): Bookmarks = Bookmarks().also { it.marks.addAll(marks) }

    fun clear() = marks.clear()

    /** True if the line that holds [offset] has a mark. */
    fun has(text: CharSequence, offset: Int): Boolean {
        val line = lineStart(text, offset)
        return marks.any { lineStart(text, it) == line }
    }

    /** Puts a mark on the line that holds [offset], or takes the one that is there away. Returns true if one was put. */
    fun toggle(text: CharSequence, offset: Int): Boolean {
        val line = lineStart(text, offset)
        if (marks.removeAll { lineStart(text, it) == line }) return false
        marks.add(line)
        marks.sort()
        return true
    }

    /** The start of the next marked line after the line with [offset], going round to the first one; null if none. */
    fun next(text: CharSequence, offset: Int): Int? {
        if (marks.isEmpty()) return null
        val here = lineStart(text, offset)
        val starts = lineStarts(text)
        return starts.firstOrNull { it > here } ?: starts.first()
    }

    /** The start of the marked line before the line with [offset], going round to the last one; null if none. */
    fun previous(text: CharSequence, offset: Int): Int? {
        if (marks.isEmpty()) return null
        val here = lineStart(text, offset)
        val starts = lineStarts(text)
        return starts.lastOrNull { it < here } ?: starts.last()
    }

    /** The start of each marked line, in order, without repeats (two marks can end up on one line after a join). */
    fun lineStarts(text: CharSequence): List<Int> = marks.map { lineStart(text, it) }.distinct().sorted()

    /**
     * [removed] characters at [start] were replaced by [added] characters. A mark after the change moves with it, one
     * inside the removed part goes to its start, and marks that end up in the same place become one.
     */
    fun edited(start: Int, removed: Int, added: Int) {
        if (marks.isEmpty()) return
        val end = start + removed
        for (i in marks.indices) {
            val m = marks[i]
            marks[i] = when {
                m >= end -> m - removed + added
                m > start -> start
                else -> m
            }
        }
        val kept = marks.distinct().sorted()
        marks.clear()
        marks.addAll(kept)
    }

    /** Puts the marks back from [offsets], dropping any that are outside a text of [length] characters. */
    fun load(offsets: IntArray, length: Int) {
        marks.clear()
        marks.addAll(offsets.filter { it in 0..length }.distinct().sorted())
    }

    companion object {
        /** The position where the line with [offset] starts. */
        fun lineStart(text: CharSequence, offset: Int): Int {
            var i = offset.coerceIn(0, text.length)
            while (i > 0 && text[i - 1] != '\n') i--
            return i
        }
    }
}
