package com.whatcode.editor.highlight

import com.whatcode.editor.Family
import com.whatcode.editor.Lang
import com.whatcode.editor.LineIndex
import com.whatcode.editor.Tokens

/** Reads [from] to [to] of [text] with the scanner of [lang]'s family. See [CodeScanner.scan] for [origin], [marks] and [window]. */
internal fun scanText(text: String, from: Int, to: Int, lang: Lang, out: Tokens, origin: Int = from, marks: Marks? = null, window: Boolean = false) {
    when (lang.family) {
        Family.PLAIN -> {}
        Family.CODE -> CodeScanner.scan(text, from, to, lang, out, origin, marks, window)
        Family.MARKUP -> MarkupScanner.scan(text, from, to, out, origin, marks, window)
        Family.CSS -> CssScanner.scan(text, from, to, lang, out, origin, marks, window)
        Family.YAML, Family.INI, Family.MARKDOWN -> ListScanner.scan(text, from, to, lang, out, origin, marks, window)
    }
}

/**
 * For each line of a text, whether the scanner of the language is in its starting state at the start of that line,
 * which means a scan may begin there and colour what follows exactly as a scan from the start of the text would.
 * A line inside a block comment or a string that goes on over several lines is not such a line.
 *
 * With this, only the lines on the screen have to be read to colour them: [resyncLine] says where to begin.
 * [build] finds the states of a whole text (it can be cancelled); [replace] brings them up to date after an
 * edit by reading from the edited line only until the states are the same as they were, which is nearly always a
 * line or two further on.
 */
class ScanStates(private val lang: Lang) {
    private var valid = ByteArray(256)
    private var size = 0

    /** False until [build] has finished, and after it was cancelled. */
    var ready = false
        private set

    /** Reads the whole [text] (its lines are in [index]). Returns false if [cancelled] said so; then [ready] stays false. */
    fun build(text: String, index: LineIndex, cancelled: () -> Boolean = { false }): Boolean {
        ready = false
        size = index.lineCount
        if (valid.size < size) valid = ByteArray(size + size / 4)
        java.util.Arrays.fill(valid, 0, size, 0)
        valid[0] = 1
        if (lang.family == Family.PLAIN) {
            java.util.Arrays.fill(valid, 0, size, 1)
            ready = true
            return true
        }
        val finished = read(text, index, 0, Int.MAX_VALUE, 0, cancelled)
        ready = finished
        return finished
    }

    /** The last line at or before [line] where a scan may begin; line 0 always is one. */
    fun resyncLine(line: Int): Int {
        var l = line.coerceIn(0, maxOf(size - 1, 0))
        while (l > 0 && valid[l].toInt() == 0) l--
        return l
    }

    /** True if a scan may begin at [line]. */
    fun isStart(line: Int): Boolean = line in 0 until size && valid[line].toInt() == 1

    /**
     * Brings the states up to date after the text changed, as [LineIndex.replace] described in [splice], which has
     * been done to [index] already. Returns false if [cancelled] said so; then the states are no longer to be trusted
     * ([ready] is false) and [build] has to be run again.
     */
    fun replace(splice: LineIndex.Splice, text: String, index: LineIndex, cancelled: () -> Boolean = { false }): Boolean {
        if (!ready) return false
        val newSize = index.lineCount
        if (valid.size < newSize) valid = valid.copyOf(newSize + newSize / 4)
        val tail = splice.line + 1 + splice.removed
        val moved = size - tail
        if (splice.added != splice.removed) System.arraycopy(valid, tail, valid, splice.line + 1 + splice.added, moved)
        java.util.Arrays.fill(valid, splice.line + 1, splice.line + 1 + splice.added, 0)
        size = newSize
        if (lang.family == Family.PLAIN) {
            java.util.Arrays.fill(valid, 0, size, 1)
            return true
        }
        // A piece that began on the line before may now reach into the edited line (a quote that was never closed),
        // so reading starts one line earlier than the edit.
        val base = resyncLine(maxOf(splice.line - 1, 0))
        val firstSame = splice.line + 1 + splice.added
        // The changed text ends where the line after the added ones starts, or at the end of the text.
        val changedEnd = if (firstSame < size) index.startOf(firstSame) else text.length
        val finished = read(text, index, base, firstSame, changedEnd, cancelled)
        if (!finished) ready = false
        return finished
    }

    /**
     * Reads from the start of line [base] and sets the state of every line it passes. From line [firstSame] on, the
     * lines of the text are the ones that were there before; it stops at the first of them that is a place to begin
     * in both the old and the new state, once a piece of text after [changedEnd] was read with the new text before it.
     */
    private fun read(text: String, index: LineIndex, base: Int, firstSame: Int, changedEnd: Int, cancelled: () -> Boolean): Boolean {
        var last = base
        var stopped = false
        var cancel = false
        lateinit var marks: Marks
        marks = Marks(text, index.startOf(base)) { p ->
            if (cancelled()) {
                cancel = true
                false
            } else {
                val line = index.lineOf(p)
                java.util.Arrays.fill(valid, last + 1, line, 0)
                if (line >= firstSame && valid[line].toInt() == 1 && marks.lastStart >= changedEnd) {
                    stopped = true
                    false
                } else {
                    valid[line] = 1
                    last = line
                    true
                }
            }
        }
        scanText(text, index.startOf(base), text.length, lang, Tokens(discard = true), 0, marks)
        if (cancel) return false
        if (!stopped) java.util.Arrays.fill(valid, last + 1, size, 0)
        return true
    }
}
