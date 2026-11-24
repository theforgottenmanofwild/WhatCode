package com.whatcodeeditor

/** Line and word operations used by the key rows. Every operation is a single undo step. */
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

    /** First and last line touched by the selection, as (start of first line, end of last line). */
    private fun lineBlock(editor: CodeEditText): Pair<Int, Int> {
        val t = editor.text
        val s = minOf(editor.selectionStart, editor.selectionEnd)
        val e = maxOf(editor.selectionStart, editor.selectionEnd)
        val endPos = if (e > s && t[e - 1] == '\n') e - 1 else e
        return lineStart(t, s) to lineEnd(t, endPos)
    }

    fun moveLines(editor: CodeEditText, up: Boolean) {
        val t = editor.text
        val a = editor.selectionStart
        val b = editor.selectionEnd
        val (ls, le) = lineBlock(editor)
        val block = t.subSequence(ls, le).toString()
        val delta: Int
        if (up) {
            if (ls == 0) return
            val prevStart = lineStart(t, ls - 1)
            val prev = t.subSequence(prevStart, ls - 1).toString()
            editor.replaceRaw(prevStart, le, block + "\n" + prev)
            delta = -(prev.length + 1)
        } else {
            if (le >= t.length) return
            val nextEnd = lineEnd(t, le + 1)
            val next = t.subSequence(le + 1, nextEnd).toString()
            editor.replaceRaw(ls, nextEnd, next + "\n" + block)
            delta = next.length + 1
        }
        val len = editor.text.length
        editor.setSelection((a + delta).coerceIn(0, len), (b + delta).coerceIn(0, len))
        editor.bringPointIntoView(editor.selectionEnd)
    }

    /** Copies the current line (or every selected line) below itself and moves the cursor to the copy. */
    fun duplicateLines(editor: CodeEditText) {
        val t = editor.text
        val a = editor.selectionStart
        val b = editor.selectionEnd
        val (ls, le) = lineBlock(editor)
        val block = t.subSequence(ls, le).toString()
        editor.replaceRaw(le, le, "\n" + block)
        val delta = block.length + 1
        val len = editor.text.length
        editor.setSelection((a + delta).coerceIn(0, len), (b + delta).coerceIn(0, len))
        editor.bringPointIntoView(editor.selectionEnd)
    }

    /** Tab: insert spaces at the cursor, or indent every selected line. With [outdent], remove one level. */
    fun indent(editor: CodeEditText, outdent: Boolean) {
        val t = editor.text
        val s = minOf(editor.selectionStart, editor.selectionEnd)
        val e = maxOf(editor.selectionStart, editor.selectionEnd)
        if (!outdent && s == e) {
            editor.replaceRaw(s, e, "    ")
            editor.setSelection(s + 4)
            return
        }
        val (ls, le) = lineBlock(editor)
        val lines = t.subSequence(ls, le).toString().split("\n")
        var firstDelta = 0
        var total = 0
        val out = ArrayList<String>(lines.size)
        for ((i, line) in lines.withIndex()) {
            val d: Int
            if (outdent) {
                val r = if (line.startsWith("\t")) 1 else line.takeWhile { it == ' ' }.length.coerceAtMost(4)
                out.add(line.substring(r))
                d = -r
            } else if (line.isEmpty()) {
                out.add(line)
                d = 0
            } else {
                out.add("    $line")
                d = 4
            }
            if (i == 0) firstDelta = d
            total += d
        }
        editor.replaceRaw(ls, le, out.joinToString("\n"))
        val len = editor.text.length
        val newS = maxOf(ls, s + firstDelta).coerceIn(0, len)
        val newE = maxOf(newS, e + total).coerceIn(0, len)
        editor.setSelection(newS, newE)
    }

    /** Removes the current line (or every selected line). */
    fun deleteLines(editor: CodeEditText) {
        val t = editor.text
        val (ls, le) = lineBlock(editor)
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
        editor.replaceRaw(from, to, "")
        editor.setSelection(from.coerceIn(0, editor.text.length))
        editor.bringPointIntoView(editor.selectionEnd)
    }

    /**
     * Comments or uncomments the current line (or every selected line). Line comments use [prefix] alone;
     * wrapping comments (HTML) pass a [suffix] too. If every non-blank line is already commented they are all
     * uncommented, otherwise they are all commented, at the shallowest indent so the code stays aligned.
     */
    fun toggleComment(editor: CodeEditText, prefix: String, suffix: String) {
        val t = editor.text
        val a = editor.selectionStart
        val b = editor.selectionEnd
        val (ls, le) = lineBlock(editor)
        val lines = t.subSequence(ls, le).toString().split("\n")
        val filled = lines.filter { it.isNotBlank() }
        if (filled.isEmpty()) return
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
        editor.replaceRaw(ls, le, out.joinToString("\n"))
        val len = editor.text.length
        val firstDelta = out.first().length - lines.first().length
        val totalDelta = out.sumOf { it.length } - lines.sumOf { it.length }
        if (a == b) {
            editor.setSelection(maxOf(ls, a + firstDelta).coerceIn(0, len))
        } else {
            val s = maxOf(ls, minOf(a, b) + firstDelta).coerceIn(0, len)
            editor.setSelection(s, maxOf(s, maxOf(a, b) + totalDelta).coerceIn(0, len))
        }
    }
}
