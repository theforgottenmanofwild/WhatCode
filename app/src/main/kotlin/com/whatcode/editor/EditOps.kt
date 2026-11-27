package com.whatcode.editor

/**
 * Line and word operations used by the key rows and the shortcuts. Every operation is a single undo step.
 *
 * Each operation is worked out on plain text by a function that returns a [TextEdit] (the `...Edit` functions), so
 * it can be tested without a device. The functions that take the editor only apply that edit.
 */
object EditOps {

    fun lineStart(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i > 0 && t[i - 1] != '\n') i--
        return i
    }

    fun lineEnd(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i < t.length && t[i] != '\n') i++
        return i
    }

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_'

    fun wordLeft(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i > 0 && t[i - 1].isWhitespace()) i--
        if (i > 0 && isWord(t[i - 1])) {
            while (i > 0 && isWord(t[i - 1])) i--
        } else if (i > 0) {
            i--
        }
        return i
    }

    fun wordRight(t: CharSequence, pos: Int): Int {
        var i = pos.coerceIn(0, t.length)
        while (i < t.length && t[i].isWhitespace()) i++
        if (i < t.length && isWord(t[i])) {
            while (i < t.length && isWord(t[i])) i++
        } else if (i < t.length) {
            i++
        }
        return i
    }

    /** The position one character before [pos]. An emoji or other pair of surrogates counts as one character. */
    fun prevIndex(t: CharSequence, pos: Int): Int {
        val p = pos.coerceIn(0, t.length)
        if (p == 0) return 0
        var i = p - 1
        if (i > 0 && t[i].isLowSurrogate() && t[i - 1].isHighSurrogate()) i--
        return i
    }

    /** The position one character after [pos]. An emoji or other pair of surrogates counts as one character. */
    fun nextIndex(t: CharSequence, pos: Int): Int {
        val p = pos.coerceIn(0, t.length)
        if (p >= t.length) return t.length
        var i = p + 1
        if (i < t.length && t[p].isHighSurrogate() && t[i].isLowSurrogate()) i++
        return i
    }

    /**
     * Where the "start of line" key goes: to the first character that is not white space, and from there to the very
     * start of the line, so pressing it twice toggles. On a line that is not indented it is simply the line start.
     */
    fun smartHome(t: CharSequence, pos: Int): Int {
        val ls = lineStart(t, pos)
        val le = lineEnd(t, pos)
        var first = ls
        while (first < le && (t[first] == ' ' || t[first] == '\t')) first++
        return if (pos.coerceIn(0, t.length) == first) ls else first
    }

    /** The line that has [pos], from its start to the start of the next line (or the end of the text). */
    fun lineWithBreak(t: CharSequence, pos: Int): Pair<Int, Int> {
        val ls = lineStart(t, pos)
        val le = lineEnd(t, pos)
        return ls to (if (le < t.length) le + 1 else le)
    }

    /** First and last line touched by the selection, as (start of first line, end of last line). */
    fun lineBlock(t: CharSequence, a: Int, b: Int): Pair<Int, Int> {
        val s = minOf(a, b).coerceIn(0, t.length)
        val e = maxOf(a, b).coerceIn(0, t.length)
        val endPos = if (e > s && t[e - 1] == '\n') e - 1 else e
        return lineStart(t, s) to lineEnd(t, endPos)
    }

    /** The selection that covers every line touched by [a] to [b], with the line break after the last one. */
    fun selectLinesRange(t: CharSequence, a: Int, b: Int): Pair<Int, Int> {
        val (ls, le) = lineBlock(t, a, b)
        return ls to (if (le < t.length) le + 1 else le)
    }

    // ------------------------------------------------------------------ the edits, on plain text

    fun moveLinesEdit(t: CharSequence, a: Int, b: Int, up: Boolean): TextEdit? {
        val (ls, le) = lineBlock(t, a, b)
        val block = t.subSequence(ls, le).toString()
        val delta: Int
        val start: Int
        val end: Int
        val insert: String
        if (up) {
            if (ls == 0) return null
            val prevStart = lineStart(t, ls - 1)
            val prev = t.subSequence(prevStart, ls - 1).toString()
            start = prevStart
            end = le
            insert = block + "\n" + prev
            delta = -(prev.length + 1)
        } else {
            if (le >= t.length) return null
            val nextEnd = lineEnd(t, le + 1)
            val next = t.subSequence(le + 1, nextEnd).toString()
            start = ls
            end = nextEnd
            insert = next + "\n" + block
            delta = next.length + 1
        }
        val len = t.length // the text keeps its length: two lines swap places
        return TextEdit(start, end, insert, (a + delta).coerceIn(0, len), (b + delta).coerceIn(0, len))
    }

    fun duplicateLinesEdit(t: CharSequence, a: Int, b: Int): TextEdit {
        val (ls, le) = lineBlock(t, a, b)
        val block = t.subSequence(ls, le).toString()
        val delta = block.length + 1
        val len = t.length + delta
        return TextEdit(le, le, "\n" + block, (a + delta).coerceIn(0, len), (b + delta).coerceIn(0, len))
    }

    /**
     * Tab: insert one level of indentation ([unit]) at the cursor, or indent every selected line. With [outdent],
     * remove one level from every selected line.
     */
    fun indentEdit(t: CharSequence, a: Int, b: Int, outdent: Boolean, unit: String): TextEdit {
        val s = minOf(a, b)
        val e = maxOf(a, b)
        if (!outdent && s == e) return TextEdit(s, e, unit, s + unit.length)
        val spaces = if (unit.startsWith(" ")) unit.length else 4
        val (ls, le) = lineBlock(t, a, b)
        val lines = t.subSequence(ls, le).toString().split("\n")
        var firstDelta = 0
        var total = 0
        val out = ArrayList<String>(lines.size)
        for ((i, line) in lines.withIndex()) {
            val d: Int
            if (outdent) {
                val r = if (line.startsWith("\t")) 1 else line.takeWhile { it == ' ' }.length.coerceAtMost(spaces)
                out.add(line.substring(r))
                d = -r
            } else if (line.isEmpty()) {
                out.add(line)
                d = 0
            } else {
                out.add(unit + line)
                d = unit.length
            }
            if (i == 0) firstDelta = d
            total += d
        }
        val len = t.length + total
        val newS = maxOf(ls, s + firstDelta).coerceIn(0, len)
        val newE = maxOf(newS, e + total).coerceIn(0, len)
        return TextEdit(ls, le, out.joinToString("\n"), newS, newE)
    }

    /** Removes the current line (or every selected line). */
    fun deleteLinesEdit(t: CharSequence, a: Int, b: Int): TextEdit {
        val (ls, le) = lineBlock(t, a, b)
        val from: Int
        val to: Int
        if (le < t.length) {
            from = ls
            to = le + 1
        } else if (ls > 0) {
            from = ls - 1 // the last line: take the newline before it instead
            to = le
        } else {
            from = ls
            to = le
        }
        return TextEdit(from, to, "", from)
    }

    /**
     * Comments or uncomments the current line (or every selected line). Line comments use [prefix] alone;
     * wrapping comments (HTML) pass a [suffix] too. If every non-blank line is already commented they are all
     * uncommented, otherwise they are all commented, at the shallowest indent so the code stays aligned.
     */
    fun toggleCommentEdit(t: CharSequence, a: Int, b: Int, prefix: String, suffix: String): TextEdit? {
        val (ls, le) = lineBlock(t, a, b)
        val lines = t.subSequence(ls, le).toString().split("\n")
        val filled = lines.filter { it.isNotBlank() }
        if (filled.isEmpty()) return null
        val p = prefix.trim()
        val sf = suffix.trim()
        fun isCommented(l: String): Boolean {
            val x = l.trim()
            return x.length >= p.length + sf.length && x.startsWith(p) && (sf.isEmpty() || x.endsWith(sf))
        }
        val uncomment = filled.all { isCommented(it) }
        val indent = filled.minOf { l -> l.takeWhile { it == ' ' || it == '\t' }.length }
        val out = lines.map { line ->
            if (line.isBlank()) {
                line
            } else if (uncomment) {
                val lead = line.takeWhile { it == ' ' || it == '\t' }
                var body = line.substring(lead.length).removePrefix(p)
                if (body.startsWith(" ")) body = body.substring(1)
                if (sf.isNotEmpty()) {
                    body = body.trimEnd().removeSuffix(sf)
                    if (body.endsWith(" ")) body = body.substring(0, body.length - 1)
                }
                lead + body
            } else {
                line.substring(0, indent) + p + " " + line.substring(indent) + if (sf.isNotEmpty()) " $sf" else ""
            }
        }
        val totalDelta = out.sumOf { it.length } - lines.sumOf { it.length }
        val len = t.length + totalDelta
        val firstDelta = out.first().length - lines.first().length
        val selS: Int
        val selE: Int
        if (a == b) {
            selS = maxOf(ls, a + firstDelta).coerceIn(0, len)
            selE = selS
        } else {
            selS = maxOf(ls, minOf(a, b) + firstDelta).coerceIn(0, len)
            selE = maxOf(selS, maxOf(a, b) + totalDelta).coerceIn(0, len)
        }
        return TextEdit(ls, le, out.joinToString("\n"), selS, selE)
    }

    /**
     * Opens an empty line below the line that has the cursor (or above it with [above]) and puts the cursor on it,
     * wherever in the line the cursor was. The new line is indented like the one it follows; with [smartIndent] the
     * rules of Enter apply, so a line that ends in a bracket opens an indented block.
     */
    fun insertLineEdit(
        t: CharSequence,
        pos: Int,
        above: Boolean,
        lang: Lang,
        smartIndent: Boolean,
        indentFallback: String? = null,
        splitTags: Boolean = true,
    ): TextEdit {
        val ls = lineStart(t, pos)
        if (above) {
            var i = ls
            while (i < t.length && (t[i] == ' ' || t[i] == '\t')) i++
            val indent = t.subSequence(ls, i).toString()
            return TextEdit(ls, ls, indent + "\n", ls + indent.length)
        }
        val le = lineEnd(t, pos)
        return TypingLogic.newline(t, le, le, lang, smartIndent, indentFallback, splitTags)
    }

    /**
     * Joins the line with the one after it, or all the selected lines into one. Each line break and the indentation
     * after it become a single space (none next to white space or an empty line). The cursor goes to the last join.
     */
    fun joinLinesEdit(t: CharSequence, a: Int, b: Int): TextEdit? {
        var (ls, le) = lineBlock(t, a, b)
        if (lineEnd(t, ls) == le) { // one line: join it with the next one
            if (le >= t.length) return null
            le = lineEnd(t, le + 1)
        }
        val lines = t.subSequence(ls, le).toString().split("\n")
        val sb = StringBuilder(lines[0])
        var joinAt = sb.length
        for (i in 1 until lines.size) {
            val next = lines[i].trimStart()
            joinAt = sb.length
            if (next.isEmpty()) continue
            val gap = sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '\t'
            if (gap) sb.append(' ')
            sb.append(next)
        }
        return TextEdit(ls, le, sb.toString(), ls + joinAt)
    }

    // ------------------------------------------------------------------ applying them to the editor

    private fun run(editor: CodeEditText, edit: TextEdit?) {
        if (edit == null) return
        editor.applyEdit(edit)
        editor.bringPointIntoView(editor.selectionEnd)
    }

    fun moveLines(editor: CodeEditText, up: Boolean) =
        run(editor, moveLinesEdit(editor.text, editor.selectionStart, editor.selectionEnd, up))

    /** Copies the current line (or every selected line) below itself and moves the cursor to the copy. */
    fun duplicateLines(editor: CodeEditText) =
        run(editor, duplicateLinesEdit(editor.text, editor.selectionStart, editor.selectionEnd))

    /** Tab: one level of indentation at the cursor, or indent every selected line. With [outdent], remove one level. */
    fun indent(editor: CodeEditText, outdent: Boolean) {
        val unit = TypingLogic.indentUnit(editor.text, editor.lang, editor.indentDefault.unit)
        run(editor, indentEdit(editor.text, editor.selectionStart, editor.selectionEnd, outdent, unit))
    }

    /** Removes the current line (or every selected line). */
    fun deleteLines(editor: CodeEditText) =
        run(editor, deleteLinesEdit(editor.text, editor.selectionStart, editor.selectionEnd))

    fun toggleComment(editor: CodeEditText, prefix: String, suffix: String) =
        run(editor, toggleCommentEdit(editor.text, editor.selectionStart, editor.selectionEnd, prefix, suffix))

    fun insertLine(editor: CodeEditText, above: Boolean) =
        run(editor, insertLineEdit(editor.text, editor.selectionEnd, above, editor.lang, editor.smartIndent, editor.indentDefault.unit, editor.splitTags))

    fun joinLines(editor: CodeEditText) =
        run(editor, joinLinesEdit(editor.text, editor.selectionStart, editor.selectionEnd))

    /** Selects every line the selection touches, with the line break after the last one. */
    fun selectLines(editor: CodeEditText) {
        val (s, e) = selectLinesRange(editor.text, editor.selectionStart, editor.selectionEnd)
        editor.setSelection(s, e)
        editor.bringPointIntoView(e)
    }
}
