package com.whatcode.editor

/**
 * The one stretch of a text that a run of edits changed, all together. Tell it about each edit with [add], in the
 * order they were made, in the positions of the text at that moment. Afterwards [lo] to [hi] (positions of the text
 * now) is the part to read again: everything outside it is the same as before, only moved by [delta] after [hi].
 * The stretch was [oldLength] characters long before the edits.
 */
class DirtyRange {
    /** The start of the changed stretch, or -1 when nothing changed. */
    var lo = -1
        private set
    var hi = 0
        private set

    /** How much longer the text is now than before the edits. */
    var delta = 0
        private set

    val isEmpty: Boolean get() = lo < 0

    /** How long the changed stretch was before the edits. */
    val oldLength: Int get() = hi - lo - delta

    /** [removed] characters at [start] were replaced by [inserted] ones. */
    fun add(start: Int, removed: Int, inserted: Int) {
        val d = inserted - removed
        if (lo < 0) {
            lo = start
            hi = start + inserted
            delta = d
            return
        }
        // An edit that ends inside or before the stretch moves its end; one that goes past it makes the end its own.
        hi = if (hi >= start + removed) hi + d else start + inserted
        lo = minOf(lo, start)
        delta += d
    }

    fun clear() {
        lo = -1
        hi = 0
        delta = 0
    }
}
