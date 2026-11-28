package com.whatcode.editor

/**
 * The line and text tools of the Text tools key: sort, reverse and de-duplicate lines, change case, swap two characters.
 * Each works on plain text and returns the one [TextEdit] that does it (so it is one undo step), or null when there
 * is nothing to change. Lines keep the ending they had, even in a text that mixes LF, CRLF and CR, and a text that
 * does not end in a line break still does not. No Android here.
 */
object LineTools {

    /** One line of a block: its text, and the break that ended it ("" for the last line of a text with no final break). */
    private class Line(val text: String, val eol: String)

    /** How lines are compared when sorting and de-duplicating. */
    class Options(val ignoreCase: Boolean = true, val natural: Boolean = true)

    /**
     * The range the line tools act on: the lines the selection touches when it covers two or more lines, otherwise
     * the whole text. Returns (start, end) with [end] after the last line's own text but before its break.
     */
    fun block(t: CharSequence, a: Int, b: Int): Pair<Int, Int> =
        if (coversLines(t, a, b)) EditOps.lineBlock(t, a, b) else 0 to t.length

    /** True if the selection from [a] to [b] touches two or more lines. */
    private fun coversLines(t: CharSequence, a: Int, b: Int): Boolean {
        val (ls, le) = EditOps.lineBlock(t, a, b)
        return EditOps.lineEnd(t, ls) < le
    }

    private fun split(t: CharSequence, start: Int, end: Int): List<Line> {
        val out = ArrayList<Line>()
        var i = start
        var from = start
        while (i < end) {
            val c = t[i]
            if (c == '\n' || c == '\r') {
                val len = if (c == '\r' && i + 1 < end && t[i + 1] == '\n') 2 else 1
                out.add(Line(t.subSequence(from, i).toString(), t.subSequence(i, i + len).toString()))
                i += len
                from = i
            } else {
                i++
            }
        }
        if (from < end || out.isEmpty()) out.add(Line(t.subSequence(from, end).toString(), ""))
        return out
    }

    /** The lines joined again: the breaks stay where they were, and the last line keeps the break the last one had. */
    private fun join(contents: List<String>, eols: List<String>): String {
        val sb = StringBuilder()
        for (i in contents.indices) {
            sb.append(contents[i])
            sb.append(if (i < contents.size - 1) eols[i] else eols.last())
        }
        return sb.toString()
    }

    /**
     * The edit that puts [replacement] in the block. Lines that were selected stay selected. Otherwise the whole text
     * was worked on, and the cursor stays where it was, so the next letter typed does not replace everything.
     */
    private fun edit(t: CharSequence, a: Int, b: Int, start: Int, end: Int, replacement: String): TextEdit? {
        if (t.subSequence(start, end).toString() == replacement) return null
        if (coversLines(t, a, b)) return TextEdit(start, end, replacement, start, start + replacement.length)
        val length = t.length - (end - start) + replacement.length
        return TextEdit(start, end, replacement, a.coerceIn(0, length), b.coerceIn(0, length))
    }

    // ------------------------------------------------------------------ sorting

    /** Compares two lines: digits read as numbers when [Options.natural], capitals not first when [Options.ignoreCase]. */
    fun compare(a: String, b: String, o: Options): Int {
        val c = if (o.natural) natural(a, b, o.ignoreCase) else if (o.ignoreCase) a.compareTo(b, ignoreCase = true) else a.compareTo(b)
        return c
    }

    private fun natural(a: String, b: String, ignoreCase: Boolean): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val x = a[i]
            val y = b[j]
            if (x.isAsciiDigit() && y.isAsciiDigit()) {
                var ie = i
                while (ie < a.length && a[ie].isAsciiDigit()) ie++
                var je = j
                while (je < b.length && b[je].isAsciiDigit()) je++
                var is0 = i
                while (is0 < ie - 1 && a[is0] == '0') is0++
                var js0 = j
                while (js0 < je - 1 && b[js0] == '0') js0++
                val la = ie - is0
                val lb = je - js0
                if (la != lb) return la.compareTo(lb)
                val c = a.substring(is0, ie).compareTo(b.substring(js0, je))
                if (c != 0) return c
                i = ie
                j = je
            } else {
                val c = if (ignoreCase) x.lowercaseChar().compareTo(y.lowercaseChar()) else x.compareTo(y)
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    /** Sorts the lines of the block, ascending or [descending]. Equal lines keep their order. */
    fun sortLines(t: CharSequence, a: Int, b: Int, descending: Boolean, o: Options): TextEdit? {
        val (s, e) = block(t, a, b)
        val lines = split(t, s, e)
        if (lines.size < 2) return null
        val cmp = Comparator<String> { x, y -> compare(x, y, o) }
        val sorted = lines.map { it.text }.sortedWith(if (descending) cmp.reversed() else cmp)
        return edit(t, a, b, s, e, join(sorted, lines.map { it.eol }))
    }

    /** Puts the lines of the block in the opposite order. */
    fun reverseLines(t: CharSequence, a: Int, b: Int): TextEdit? {
        val (s, e) = block(t, a, b)
        val lines = split(t, s, e)
        if (lines.size < 2) return null
        return edit(t, a, b, s, e, join(lines.map { it.text }.reversed(), lines.map { it.eol }))
    }

    /** Removes every line that is the same as an earlier one in the block, keeping the first. */
    fun removeDuplicateLines(t: CharSequence, a: Int, b: Int, ignoreCase: Boolean): TextEdit? {
        val (s, e) = block(t, a, b)
        val lines = split(t, s, e)
        if (lines.size < 2) return null
        val seen = HashSet<String>()
        val kept = lines.filter { seen.add(if (ignoreCase) it.text.lowercase() else it.text) }
        if (kept.size == lines.size) return null
        // Each line that stays keeps its own break; the last one takes the break (or none) that ended the block.
        val eols = kept.dropLast(1).map { it.eol } + lines.last().eol
        return edit(t, a, b, s, e, join(kept.map { it.text }, eols))
    }

    // ------------------------------------------------------------------ case

    enum class Case { UPPER, LOWER, TITLE }

    /** [text] in the given case. Title case capitalises each word (a run of letters, digits and apostrophes). */
    fun convertCase(text: String, to: Case): String = when (to) {
        Case.UPPER -> text.uppercase()
        Case.LOWER -> text.lowercase()
        Case.TITLE -> {
            val sb = StringBuilder(text.length)
            var inWord = false
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val n = Character.charCount(cp)
                val piece = text.substring(i, i + n)
                val word = Character.isLetterOrDigit(cp) ||
                    (inWord && (cp == '\''.code || cp == '\u2019'.code || isMark(cp)))
                sb.append(if (word && !inWord) piece.uppercase() else if (word) piece.lowercase() else piece)
                inWord = word
                i += n
            }
            sb.toString()
        }
    }

    /** Changes the case of the selection, or of the word at the cursor when nothing is selected. */
    fun changeCase(t: CharSequence, a: Int, b: Int, to: Case): TextEdit? {
        var s = minOf(a, b).coerceIn(0, t.length)
        var e = maxOf(a, b).coerceIn(0, t.length)
        if (s == e) {
            while (s > 0) {
                val cp = Character.codePointBefore(t, s)
                if (!isWordPart(cp)) break
                s -= Character.charCount(cp)
            }
            while (e < t.length) {
                val cp = Character.codePointAt(t, e)
                if (!isWordPart(cp)) break
                e += Character.charCount(cp)
            }
            if (s >= e) return null
        }
        val old = t.subSequence(s, e).toString()
        val new = convertCase(old, to)
        if (old == new) return null
        // The selection that was there stays selected; a cursor stays where it was.
        val keepSel = minOf(a, b) != maxOf(a, b)
        return if (keepSel) TextEdit(s, e, new, s, s + new.length) else TextEdit(s, e, new, a.coerceIn(s, s + new.length))
    }

    /** An accent or vowel sign written after its letter. */
    private fun isMark(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isWordPart(cp: Int) = Character.isLetterOrDigit(cp) || cp == '_'.code || isMark(cp)

    // ------------------------------------------------------------------ transposing

    /**
     * Swaps the character before the cursor with the one after it; at the end of a line, the last two of the line.
     * An emoji or any other pair of UTF-16 units that belong together moves as one. Nothing happens with a selection,
     * at the start of a line, or on a line with fewer than two characters. The cursor ends after the swapped pair.
     */
    fun transpose(t: CharSequence, a: Int, b: Int): TextEdit? {
        if (a != b) return null
        var pos = a.coerceIn(0, t.length)
        // A cursor between the two characters of a CRLF break counts as the end of that line.
        if (pos > 0 && pos < t.length && t[pos - 1] == '\r' && t[pos] == '\n') pos--
        var ls = pos
        while (ls > 0 && t[ls - 1] != '\n' && t[ls - 1] != '\r') ls--
        var le = pos
        while (le < t.length && t[le] != '\n' && t[le] != '\r') le++
        if (pos == le) pos = prevUnit(t, pos, ls)
        if (pos <= ls) return null
        val left = prevUnit(t, pos, ls)
        val right = nextUnit(t, pos, le)
        if (left >= pos || right <= pos) return null
        val swapped = t.subSequence(pos, right).toString() + t.subSequence(left, pos).toString()
        return TextEdit(left, right, swapped, right)
    }

    private fun prevUnit(t: CharSequence, pos: Int, floor: Int): Int {
        if (pos <= floor) return pos
        var i = pos - 1
        if (i > floor && Character.isLowSurrogate(t[i]) && Character.isHighSurrogate(t[i - 1])) i--
        return i
    }

    private fun nextUnit(t: CharSequence, pos: Int, ceil: Int): Int {
        if (pos >= ceil) return pos
        var i = pos + 1
        if (i < ceil && Character.isHighSurrogate(t[i - 1]) && Character.isLowSurrogate(t[i])) i++
        return i
    }
}
