package com.whatcode.editor

/**
 * The undo and redo steps of one file, kept apart from the editor view so the rules for joining small edits into
 * one step only deal in text and numbers. [CodeEditText] records every change here and replays the steps.
 *
 * A step replaces the text at [Edit.start] that was [Edit.removed] with [Edit.added]. Undo puts the removed text back
 * and redo puts the added text back.
 */
class EditHistory(private val maxEdits: Int = MAX_EDITS, private val maxChars: Int = MAX_CHARS) {

    class Edit(var start: Int, var removed: String, var added: String, var time: Long) {
        fun copy() = Edit(start, removed, added, time)
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
        while (undoStack.size > 1 && (undoStack.size > maxEdits || chars > maxChars)) {
            chars -= size(undoStack.removeAt(0))
        }
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
