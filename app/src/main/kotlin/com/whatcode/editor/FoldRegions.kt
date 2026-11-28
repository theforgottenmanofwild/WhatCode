package com.whatcode.editor

/** What folding reads to find the blocks that can be folded: the brackets, the indentation, or both. */
enum class FoldBy(val id: Int, val key: String, val label: String) {
    BOTH(0, "both", "Brackets and indentation"),
    INDENTATION(1, "indentation", "Indentation"),
    BRACKETS(2, "brackets", "Brackets");

    companion object {
        /** The choice saved as [id], or [BOTH] for a number that is not one. */
        fun of(id: Int): FoldBy = entries.firstOrNull { it.id == id } ?: BOTH

        /** The choice saved as [key], or [BOTH] for a key that is not one. */
        fun fromKey(key: String?): FoldBy = entries.firstOrNull { it.key == key } ?: BOTH
    }
}

/**
 * A block of lines that can be folded. [header] is where the line that stays on show starts. The lines that go out of
 * sight run from [start], the start of the line after the header, up to [end], the start of the line after the last
 * hidden one, so both [start] and [end] follow a line break and the hidden text always ends with one.
 */
data class FoldRegion(val header: Int, val start: Int, val end: Int)

/**
 * Finds the blocks of a text that can be folded. A block is either what lies between a bracket that opens on one line
 * and its partner on a later line (the line with the closing bracket stays on show), or the lines under a line that
 * are indented more than it is. Nothing here needs Android, so it is tested without a device.
 */
object FoldRegions {

    /** How deep the indentation is followed; what is deeper is left out. */
    const val MAX_DEPTH = 200

    /** The fewest hidden lines a block can be asked to have. */
    const val MIN_LINES_LOWEST = 1

    /**
     * Every block of [text], in the order of its header, one for each header (when a line could fold two ways, the
     * one that hides the most). A block hides at least [minLines] lines. [step] is the width of one level of
     * indentation (see [IndentGuides.stepOf]). [skipped] says whether the character at a position is in a string, a
     * comment or a regular expression, so a bracket there is not counted (see [BracketMatch.skipFrom]).
     */
    fun find(
        text: CharSequence,
        by: FoldBy,
        step: Int,
        minLines: Int,
        skipped: (Int) -> Boolean = NOT_SKIPPED,
    ): List<FoldRegion> {
        val min = maxOf(minLines, MIN_LINES_LOWEST)
        val starts = lineStarts(text)
        if (starts.size < 3) return emptyList()
        val found = HashMap<Int, FoldRegion>()
        if (by != FoldBy.INDENTATION) byBrackets(text, starts, min, skipped, found)
        if (by != FoldBy.BRACKETS) byIndentation(text, starts, maxOf(step, 1), min, found)
        return found.values.sortedBy { it.header }
    }

    private val NOT_SKIPPED: (Int) -> Boolean = { false }

    /** Where every line of [text] starts. An empty text has one empty line, and a final line break starts another. */
    fun lineStarts(text: CharSequence): IntArray {
        var n = 1
        for (i in 0 until text.length) if (text[i] == '\n') n++
        val out = IntArray(n)
        var line = 1
        for (i in 0 until text.length) if (text[i] == '\n') out[line++] = i + 1
        return out
    }

    private fun byBrackets(text: CharSequence, starts: IntArray, min: Int, skipped: (Int) -> Boolean, found: HashMap<Int, FoldRegion>) {
        var chars = CharArray(64)
        var lines = IntArray(64)
        var depth = 0
        var line = 0
        for (i in 0 until text.length) {
            val c = text[i]
            when (c) {
                '\n' -> line++
                '{', '[', '(' -> if (depth < MAX_BRACKET_DEPTH && !skipped(i)) {
                    if (depth == chars.size) {
                        chars = chars.copyOf(depth * 2)
                        lines = lines.copyOf(depth * 2)
                    }
                    chars[depth] = c
                    lines[depth] = line
                    depth++
                }
                '}', ']', ')' -> if (depth > 0 && chars[depth - 1] == opener(c) && !skipped(i)) {
                    depth--
                    val open = lines[depth]
                    // The lines between the two brackets, not counting the line of either one.
                    if (line - open - 1 >= min) {
                        val region = FoldRegion(starts[open], starts[open + 1], starts[line])
                        val old = found[open]
                        if (old == null || old.end < region.end) found[open] = region
                    }
                }
            }
        }
    }

    private const val MAX_BRACKET_DEPTH = 10_000

    private fun opener(close: Char): Char = when (close) {
        '}' -> '{'
        ']' -> '['
        else -> '('
    }

    private fun byIndentation(text: CharSequence, starts: IntArray, step: Int, min: Int, found: HashMap<Int, FoldRegion>) {
        val lines = starts.size
        val stackLine = IntArray(MAX_DEPTH)
        val stackIndent = IntArray(MAX_DEPTH)
        val stackLast = IntArray(MAX_DEPTH)
        var top = -1
        // The last line of the text has no line break after it when the text does not end with one.
        val unterminated = text.isNotEmpty() && text[text.length - 1] != '\n'

        fun close(k: Int) {
            val last = stackLast[k]
            if (last < 0) return
            val header = stackLine[k]
            var endLine = last
            // A hidden text must end with a line break, so a body that reaches the unfinished last line stops short of it.
            if (endLine == lines - 1 && unterminated) endLine--
            if (endLine - header >= min && !found.containsKey(header)) {
                found[header] = FoldRegion(starts[header], starts[header + 1], starts[endLine + 1])
            }
        }

        for (i in 0 until lines) {
            val d = IndentGuides.indent(text, starts[i], step)
            if (d < 0) continue
            while (top >= 0 && stackIndent[top] >= d) {
                close(top)
                top--
            }
            for (k in 0..top) stackLast[k] = i
            if (top + 1 < MAX_DEPTH) {
                top++
                stackLine[top] = i
                stackIndent[top] = d
                stackLast[top] = -1
            }
        }
        while (top >= 0) {
            close(top)
            top--
        }
    }

    /** The block whose header is the line that starts at [lineStart], or null. [regions] must be in order of header. */
    fun startingAt(regions: List<FoldRegion>, lineStart: Int): FoldRegion? {
        var lo = 0
        var hi = regions.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val h = regions[mid].header
            when {
                h < lineStart -> lo = mid + 1
                h > lineStart -> hi = mid - 1
                else -> return regions[mid]
            }
        }
        return null
    }

    /**
     * The block that the line with [offset] is the header of, or else the innermost block that holds that line, or
     * null if there is none. [regions] must be in order of header.
     */
    fun around(regions: List<FoldRegion>, text: CharSequence, offset: Int): FoldRegion? {
        val at = lineStartOf(text, offset)
        startingAt(regions, at)?.let { return it }
        // The nearest header before the line comes last, so going back from there finds the innermost block first.
        var lo = 0
        var hi = regions.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (regions[mid].header <= at) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        var i = idx
        while (i >= 0) {
            if (regions[i].end > at) return regions[i]
            i--
        }
        return null
    }

    /** The start of the line that holds [offset]. */
    fun lineStartOf(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    /**
     * [regions] after [removed] characters at [start] were replaced by [added]. A block that lies after the change moves
     * with it, one that lies before it stays, and one that holds the change keeps its place and grows or shrinks by
     * as much, so the markers do not jump while a new count is made. A block the change cuts across is left out.
     */
    fun shifted(regions: List<FoldRegion>, start: Int, removed: Int, added: Int): List<FoldRegion> {
        if (regions.isEmpty()) return regions
        val end = start + removed
        val delta = added - removed
        val out = ArrayList<FoldRegion>(regions.size)
        for (r in regions) {
            when {
                end <= r.header -> out.add(FoldRegion(r.header + delta, r.start + delta, r.end + delta))
                start >= r.end -> out.add(r)
                start >= r.header && end <= r.end -> {
                    // Inside the block, or in its header line; an edit that goes from the header line into the hidden part leaves it out.
                    if (start >= r.start) out.add(FoldRegion(r.header, r.start, r.end + delta))
                    else if (end <= r.start) out.add(FoldRegion(r.header, r.start + delta, r.end + delta))
                }
            }
        }
        return out
    }
}
