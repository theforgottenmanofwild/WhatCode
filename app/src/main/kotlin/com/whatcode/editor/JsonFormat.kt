package com.whatcode.editor

/** Why a text tool could not run. The message is shown to the person as it is. */
class TextToolFailure(message: String) : Exception(message)

/**
 * Pretty-prints and minifies JSON. It reads the text token by token and copies every string and number exactly as it
 * was written, so names keep their order, escapes stay escapes and a number such as 1.50 or 1E3 is not rewritten.
 * Anything that is not strict JSON (a comment, a trailing comma, single quotes) is refused with the place it was
 * found, and the text is left as it is. Plain logic, no Android.
 */
object JsonFormat {

    /** Containers nested deeper than this are refused, which also keeps the indentation of one line short. */
    const val MAX_DEPTH = 512

    /** What is wrong, and where: [offset] counts from the start of the text that was read. */
    class Problem(val offset: Int, val what: String)

    /** The formatted text, or the [problem] that stopped it. */
    class Result(val text: String?, val problem: Problem?)

    // What is expected next.
    private const val TOP = 0 // the one value of the text
    private const val OBJ_VALUE = 1 // the value after a name and a colon
    private const val ARR_FIRST = 2 // the first item of an array, or its end
    private const val ARR_NEXT = 3 // an item after a comma
    private const val KEY_FIRST = 4 // the first name of an object, or its end
    private const val KEY_NEXT = 5 // a name after a comma
    private const val COLON = 6
    private const val AFTER = 7 // a comma, the end of the container, or the end of the text

    private const val OBJECT = 1
    private const val ARRAY = 2

    /**
     * The one edit that formats the JSON in [t]: the selection from [a] to [b], or the whole text when nothing is
     * selected. [indentWidth] spaces make one level, or a tab when it is 0; [pretty] false removes all white space
     * instead. Only the part from the first to the last character of the JSON is replaced, so the white space around it
     * stays. Returns null if the text is already as asked, and throws [TextToolFailure] if it is not valid JSON.
     */
    fun edit(t: CharSequence, a: Int, b: Int, pretty: Boolean, indentWidth: Int): TextEdit? {
        var lo = minOf(a, b).coerceAtLeast(0)
        var hi = maxOf(a, b).coerceAtMost(t.length)
        val selected = lo != hi
        if (!selected) {
            lo = 0
            hi = t.length
        }
        while (lo < hi && isSpace(t[lo])) lo++
        while (hi > lo && isSpace(t[hi - 1])) hi--
        if (lo >= hi) throw TextToolFailure("There is no JSON here to format")

        // The lines of the result start with the white space the first line starts with.
        var lineStart = lo
        while (lineStart > 0 && t[lineStart - 1] != '\n' && t[lineStart - 1] != '\r') lineStart--
        var baseEnd = lineStart
        while (baseEnd < lo && (t[baseEnd] == ' ' || t[baseEnd] == '\t')) baseEnd++
        val base = t.subSequence(lineStart, baseEnd).toString()

        val unit = if (indentWidth <= 0) "\t" else " ".repeat(indentWidth.coerceAtMost(8))
        val result = run(t, lo, hi, if (pretty) unit else null, lineBreak(t, lo, hi), base)
        val problem = result.problem
        if (problem != null) throw TextToolFailure(describe(t, problem))
        val out = result.text!!
        if (sameAs(t, lo, hi, out)) return null
        return if (selected) TextEdit(lo, hi, out, lo, lo + out.length) else TextEdit(lo, hi, out, lo)
    }

    /**
     * Formats the JSON value that fills [t] from [from] to [to], with no white space at either end. [indent] is one
     * level of indentation, or null to remove all white space. Lines end with [eol] and, but for the first, start with
     * [base].
     */
    fun run(t: CharSequence, from: Int, to: Int, indent: String?, eol: String, base: String = ""): Result {
        val pretty = indent != null
        val unit = indent ?: ""
        val sb = StringBuilder((to - from) + (to - from) / 4 + 16)
        var kinds = ByteArray(64) // what each open container is
        var depth = 0
        var expect = TOP
        var i = from

        fun fail(at: Int, what: String) = Result(null, Problem(at, what))

        fun newLine(levels: Int) {
            sb.append(eol).append(base)
            repeat(levels) { sb.append(unit) }
        }

        while (i < to) {
            val c = t[i]
            if (isSpace(c)) {
                i++
                continue
            }
            when (expect) {
                TOP, OBJ_VALUE, ARR_FIRST, ARR_NEXT -> {
                    if (c == ']' && expect == ARR_FIRST) {
                        sb.append(']')
                        depth--
                        expect = AFTER
                        i++
                        continue
                    }
                    if (pretty && (expect == ARR_FIRST || expect == ARR_NEXT)) newLine(depth)
                    when {
                        c == '{' || c == '[' -> {
                            if (depth >= MAX_DEPTH) return fail(i, "It is nested more than $MAX_DEPTH levels deep")
                            if (depth == kinds.size) kinds = kinds.copyOf(depth * 2)
                            kinds[depth++] = (if (c == '{') OBJECT else ARRAY).toByte()
                            sb.append(c)
                            expect = if (c == '{') KEY_FIRST else ARR_FIRST
                            i++
                            continue
                        }
                        c == '"' -> {
                            val end = stringEnd(t, i, to)
                            if (end < 0) return fail(-end - 1, STRING_PROBLEM)
                            sb.append(t, i, end)
                            i = end
                        }
                        c == '-' || (c in '0'..'9') -> {
                            val end = numberEnd(t, i, to)
                            if (end < 0) return fail(-end - 1, "This is not a valid number")
                            sb.append(t, i, end)
                            i = end
                        }
                        c == 't' || c == 'f' || c == 'n' -> {
                            val word = if (c == 't') "true" else if (c == 'f') "false" else "null"
                            if (!t.hasWordAt(word, i, to)) return fail(i, "Unexpected '$c'")
                            sb.append(word)
                            i += word.length
                        }
                        else -> return fail(i, unexpected(c))
                    }
                    expect = AFTER
                }
                KEY_FIRST, KEY_NEXT -> {
                    if (c == '}' && expect == KEY_FIRST) {
                        sb.append('}')
                        depth--
                        expect = AFTER
                        i++
                        continue
                    }
                    if (c != '"') return fail(i, if (c == '}') "Unexpected '}'" else "Expected a name in double quotes")
                    val end = stringEnd(t, i, to)
                    if (end < 0) return fail(-end - 1, STRING_PROBLEM)
                    if (pretty) newLine(depth)
                    sb.append(t, i, end)
                    i = end
                    expect = COLON
                }
                COLON -> {
                    if (c != ':') return fail(i, "Expected ':' after the name")
                    sb.append(':')
                    if (pretty) sb.append(' ')
                    expect = OBJ_VALUE
                    i++
                }
                else -> { // AFTER
                    if (depth == 0) return fail(i, "There is more text after the JSON")
                    val inObject = kinds[depth - 1].toInt() == OBJECT
                    when {
                        c == ',' -> {
                            sb.append(',')
                            expect = if (inObject) KEY_NEXT else ARR_NEXT
                        }
                        (c == '}' && inObject) || (c == ']' && !inObject) -> {
                            if (pretty) newLine(depth - 1)
                            sb.append(c)
                            depth--
                        }
                        else -> return fail(i, if (inObject) "Expected ',' or '}'" else "Expected ',' or ']'")
                    }
                    i++
                }
            }
        }
        if (depth != 0 || expect != AFTER) return fail(to, "The JSON ends too soon")
        return Result(sb.toString(), null)
    }

    /** "Not valid JSON: Unexpected ',' at line 3, column 9", counting from the start of [t]. */
    fun describe(t: CharSequence, p: Problem): String {
        var line = 1
        var lineStart = 0
        var i = 0
        val to = p.offset.coerceIn(0, t.length)
        while (i < to) {
            val c = t[i]
            if (c == '\n' || (c == '\r' && !(i + 1 < t.length && t[i + 1] == '\n'))) {
                line++
                lineStart = i + 1
            }
            i++
        }
        return "Not valid JSON: ${p.what}, at line $line, column ${to - lineStart + 1}"
    }

    // ------------------------------------------------------------------ pieces

    private const val STRING_PROBLEM = "The string is not closed, or holds a bad escape or a raw line break"

    private fun unexpected(c: Char) = if (c == '\'') "Unexpected ' (JSON strings use double quotes)" else "Unexpected '$c'"

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /**
     * The index after the string that starts with the quote at [at], or `-(index + 1)` of what is wrong with it.
     * A string may not hold a character below a space, and a backslash must be followed by one of `"\/bfnrt` or `u`
     * and four hex digits.
     */
    private fun stringEnd(t: CharSequence, at: Int, to: Int): Int {
        var i = at + 1
        while (i < to) {
            val c = t[i]
            when {
                c == '"' -> return i + 1
                c < ' ' -> return -(i + 1)
                c == '\\' -> {
                    if (i + 1 >= to) return -(i + 1)
                    when (t[i + 1]) {
                        '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> i += 2
                        'u' -> {
                            if (i + 5 >= to) return -(i + 1)
                            for (k in 2..5) if (!isHex(t[i + k])) return -(i + 1)
                            i += 6
                        }
                        else -> return -(i + 1)
                    }
                }
                else -> i++
            }
        }
        return -(at + 1)
    }

    /** The index after the number that starts at [at], or `-(index + 1)` of where it goes wrong. */
    private fun numberEnd(t: CharSequence, at: Int, to: Int): Int {
        var i = at
        if (t[i] == '-') i++
        if (i >= to) return -(at + 1)
        if (t[i] == '0') {
            i++
        } else if (t[i] in '1'..'9') {
            while (i < to && t[i] in '0'..'9') i++
        } else {
            return -(i + 1)
        }
        if (i < to && t[i] == '.') {
            val digits = i + 1
            i = digits
            while (i < to && t[i] in '0'..'9') i++
            if (i == digits) return -(digits + 1)
        }
        if (i < to && (t[i] == 'e' || t[i] == 'E')) {
            i++
            if (i < to && (t[i] == '+' || t[i] == '-')) i++
            val digits = i
            while (i < to && t[i] in '0'..'9') i++
            if (i == digits) return -(digits + 1)
        }
        // A letter or a digit right after the number (01, 1x) makes it something else.
        if (i < to && (t[i] in '0'..'9' || t[i].isLetter() || t[i] == '.')) return -(i + 1)
        return i
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun CharSequence.hasWordAt(word: String, at: Int, to: Int): Boolean {
        if (at + word.length > to) return false
        for (k in word.indices) if (this[at + k] != word[k]) return false
        return true
    }

    /** The line break to put in the result: the first one in the JSON itself, else the first of the text, else LF. */
    private fun lineBreak(t: CharSequence, lo: Int, hi: Int): String {
        fun first(from: Int, to: Int): String? {
            var i = from
            while (i < to) {
                if (t[i] == '\n') return "\n"
                if (t[i] == '\r') return if (i + 1 < t.length && t[i + 1] == '\n') "\r\n" else "\r"
                i++
            }
            return null
        }
        return first(lo, hi) ?: first(0, t.length) ?: "\n"
    }

    private fun sameAs(t: CharSequence, from: Int, to: Int, s: String): Boolean {
        if (to - from != s.length) return false
        for (k in s.indices) if (t[from + k] != s[k]) return false
        return true
    }
}
