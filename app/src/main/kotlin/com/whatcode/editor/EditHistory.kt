package com.whatcode.editor

/**
 * The undo and redo steps of one file, kept apart from the editor view so the rules for joining small edits into
 * one step only deal in text and numbers. [CodeEditText] records every change here and replays the steps.
 *
 * A step replaces the text at [Edit.start] that was [Edit.removed] with [Edit.added]. Undo puts the removed text back
 * and redo puts the added text back.
 */
class EditHistory(private val maxEdits: Int = MAX_EDITS, private val maxChars: Int = MAX_CHARS) {

    /**
     * One change. [joined] means it belongs to the step before it, so the two are undone and redone together (see
     * [recordJoined]): renaming a tag also renames its partner, and that is one thing to undo.
     */
    class Edit(var start: Int, var removed: String, var added: String, var time: Long, var joined: Boolean = false) {
        fun copy() = Edit(start, removed, added, time, joined)
    }

    /** The two stacks of one file, set aside while another file is in the editor. */
    class Snapshot internal constructor(internal val undo: List<Edit>, internal val redo: List<Edit>)

    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()

    /** Characters held by both stacks together, so a few huge pastes cannot fill the memory. */
    private var chars = 0L

    /** Set after an undo or redo: the next edit starts a new step instead of joining the one before. */
    private var sealed = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size

    /** True when the step that [takeRedo] would give next is joined to the one that was just redone. */
    val redoJoinsNext: Boolean get() = redoStack.lastOrNull()?.joined == true

    private fun size(e: Edit) = (e.removed.length + e.added.length).toLong()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        chars = 0
        sealed = false
    }

    /** A copy, so the editor can keep changing its own stacks. */
    fun snapshot(): Snapshot = Snapshot(undoStack.map { it.copy() }, redoStack.map { it.copy() })

    fun load(s: Snapshot?) {
        clear()
        if (s == null) return
        for (e in s.undo) undoStack.add(e.copy())
        for (e in s.redo) redoStack.add(e.copy())
        chars = (undoStack + redoStack).sumOf { size(it) }
    }

    /**
     * Records that the text at [start] changed from [removed] to [added] at the time [now] (milliseconds).
     * Edits that follow each other closely and belong together become one step: a run of typed letters, a run of
     * backspaces, a run of forward deletes, and a keyboard that keeps replacing the word it is composing.
     */
    fun record(start: Int, removed: String, added: String, now: Long) {
        if (removed.isEmpty() && added.isEmpty()) return
        for (e in redoStack) chars -= size(e)
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (last != null && !sealed && now - last.time < MERGE_MS) {
            val before = size(last)
            if (merge(last, start, removed, added)) {
                last.time = now
                chars += size(last) - before
                return
            }
        }
        sealed = false
        undoStack.add(Edit(start, removed, added, now))
        chars += removed.length + added.length
        trim()
    }

    /** Drops the oldest steps while there are too many, with the steps joined to them. */
    private fun trim() {
        while (undoStack.size > 1 && (undoStack.size > maxEdits || chars > maxChars)) {
            chars -= size(undoStack.removeAt(0))
            while (undoStack.size > 1 && undoStack[0].joined) chars -= size(undoStack.removeAt(0))
        }
    }

    /**
     * Records a change that was made because of the one recorded just before it, so that both are one step: undo
     * takes them back together and redo does them together. The first change is the one the person made; this one
     * follows from it.
     *
     * A run of such pairs that belong together (a tag renamed one letter at a time, with its partner renamed each
     * time) is folded into a single pair, the same way a run of typed letters is, so one undo takes back the whole
     * word and not one letter.
     */
    fun recordJoined(start: Int, removed: String, added: String, now: Long) {
        if (removed.isEmpty() && added.isEmpty()) return
        val n = undoStack.size
        if (n >= 3 && !sealed) {
            val a2 = undoStack[n - 1]
            val b1 = undoStack[n - 2]
            val a1 = undoStack[n - 3]
            if (b1.joined && !a2.joined && !a1.joined && b1.added == removed && now - b1.time < MERGE_MS) {
                // The partner edit is before the typed one in the text, so it moved what comes after it.
                val shift = if (b1.start < a2.start) b1.added.length - b1.removed.length else 0
                val sizes = size(a1) + size(b1) + size(a2)
                if (merge(a1, a2.start - shift, a2.removed, a2.added)) {
                    undoStack.removeAt(n - 1)
                    a1.time = now
                    b1.start = start
                    b1.added = added
                    b1.time = now
                    chars += size(a1) + size(b1) - sizes
                    return
                }
            }
        }
        undoStack.add(Edit(start, removed, added, now, joined = true))
        chars += removed.length + added.length
        trim()
    }

    private fun merge(last: Edit, start: Int, removed: String, added: String): Boolean {
        // One more typed letter.
        if (last.removed.isEmpty() && removed.isEmpty() && added.length == 1 && added != "\n" &&
            start == last.start + last.added.length
        ) {
            last.added += added
            return true
        }
        // One more backspace.
        if (last.added.isEmpty() && added.isEmpty() && removed.length == 1 && start + 1 == last.start) {
            last.start = start
            last.removed = removed + last.removed
            return true
        }
        // One more forward delete.
        if (last.added.isEmpty() && added.isEmpty() && removed.length == 1 && start == last.start &&
            last.removed.isNotEmpty()
        ) {
            last.removed += removed
            return true
        }
        // A keyboard that replaces the end of what was just typed (a word being composed, a correction).
        if (removed.isNotEmpty() && added.isNotEmpty() && last.removed.isEmpty() && last.added.isNotEmpty() &&
            removed.length <= COMPOSING_MAX && added.length <= COMPOSING_MAX &&
            start >= last.start && start + removed.length == last.start + last.added.length &&
            last.added.endsWith(removed) && '\n' !in added && '\n' !in last.added
        ) {
            last.added = last.added.substring(0, start - last.start) + added
            return true
        }
        return false
    }

    /** Takes the newest step off the undo stack, or null. Give it to [addRedo] once it has been undone. */
    fun takeUndo(): Edit? {
        val e = undoStack.removeLastOrNull() ?: return null
        sealed = true
        return e
    }

    fun addRedo(e: Edit) {
        redoStack.add(e)
    }

    /** Takes the newest step off the redo stack, or null. Give it to [addUndo] once it has been redone. */
    fun takeRedo(): Edit? {
        val e = redoStack.removeLastOrNull() ?: return null
        sealed = true
        return e
    }

    fun addUndo(e: Edit) {
        undoStack.add(e)
    }

    companion object {
        const val MAX_EDITS = 1000
        const val MAX_CHARS = 4_000_000
        const val MERGE_MS = 1000L
        private const val COMPOSING_MAX = 100
    }
}
