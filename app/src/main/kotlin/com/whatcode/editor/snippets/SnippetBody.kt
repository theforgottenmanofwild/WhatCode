package com.whatcode.editor.snippets

/**
 * Reads the body of a snippet. Plain text goes in as it is, and these are special:
 *
 *  - `$1`, `${1}` or `${1:text}` is a tab stop, numbered in the order Tab visits them; `text` is what stands there at
 *    first. A number used twice shows the same text in both places, and Tab visits the first one.
 *  - `$0` or `${0}` is where the cursor ends up; without it, that is the end of the snippet.
 *  - `\$`, `\\` and `\}` are a dollar sign, a backslash and a closing brace. A `$` that starts none of the above is
 *    just a dollar sign, and a `${` that is never closed is plain text.
 *  - A line break in the body starts the next line at the indentation of the line the snippet is put in, and a tab
 *    in the body is one level of indentation of the file.
 *
 * Plain logic, no Android.
 */
object SnippetBody {

    /** A tab stop: its [index] and where it stands in [Parsed.text], from [start] to [end]. */
    class Stop(val index: Int, val start: Int, val end: Int)

    /**
     * The text to put in, the tab stops from 1 up in the order of their numbers, and where the cursor ends up
     * ([finalOffset], the place of `$0` or else the end of the text).
     */
    class Parsed(val text: String, val stops: List<Stop>, val finalOffset: Int)

    private sealed class Token {
        class Text(val text: String) : Token()
        class Ref(val index: Int, val default: String?) : Token()
    }

    /** More digits than this are not a tab stop number. */
    private const val MAX_DIGITS = 3

    /** Reads [body]; its lines after the first start with [indent], and a tab in it becomes [unit]. */
    fun parse(body: String, indent: String = "", unit: String = "\t"): Parsed {
        val tokens = tokens(body)

        // The text that stands at a number: the first default given for it.
        val defaults = HashMap<Int, String>()
        for (t in tokens) if (t is Token.Ref && t.default != null && t.index !in defaults) defaults[t.index] = t.default

        val sb = StringBuilder()
        fun put(s: String) {
            for (c in s) {
                when (c) {
                    '\n' -> sb.append('\n').append(indent)
                    '\t' -> sb.append(unit)
                    '\r' -> {}
                    else -> sb.append(c)
                }
            }
        }

        val first = LinkedHashMap<Int, Stop>()
        for (t in tokens) {
            when (t) {
                is Token.Text -> put(t.text)
                is Token.Ref -> {
                    val start = sb.length
                    put(defaults[t.index] ?: "")
                    if (t.index !in first) first[t.index] = Stop(t.index, start, sb.length)
                }
            }
        }
        val final = first[0]
        val stops = first.values.filter { it.index != 0 }.sortedBy { it.index }
        return Parsed(sb.toString(), stops, final?.start ?: sb.length)
    }

    private fun tokens(body: String): List<Token> {
        val out = ArrayList<Token>()
        val lit = StringBuilder()
        fun flush() {
            if (lit.isNotEmpty()) {
                out.add(Token.Text(lit.toString()))
                lit.setLength(0)
            }
        }
        val n = body.length
        var i = 0
        while (i < n) {
            val c = body[i]
            if (c == '\\' && i + 1 < n && (body[i + 1] == '$' || body[i + 1] == '\\' || body[i + 1] == '}')) {
                lit.append(body[i + 1])
                i += 2
                continue
            }
            if (c != '$') {
                lit.append(c)
                i++
                continue
            }
            // A dollar sign: $12, ${12} or ${12:text}, or just a dollar sign.
            var j = i + 1
            val braced = j < n && body[j] == '{'
            if (braced) j++
            val digits = j
            while (j < n && body[j] in '0'..'9' && j - digits < MAX_DIGITS) j++
            if (j == digits) {
                lit.append('$')
                i++
                continue
            }
            val index = body.substring(digits, j).toInt()
            if (!braced) {
                flush()
                out.add(Token.Ref(index, null))
                i = j
            } else if (j < n && body[j] == '}') {
                flush()
                out.add(Token.Ref(index, null))
                i = j + 1
            } else if (j < n && body[j] == ':') {
                val text = StringBuilder()
                var k = j + 1
                var closed = false
                while (k < n) {
                    val d = body[k]
                    if (d == '\\' && k + 1 < n && (body[k + 1] == '$' || body[k + 1] == '\\' || body[k + 1] == '}')) {
                        text.append(body[k + 1])
                        k += 2
                    } else if (d == '}') {
                        closed = true
                        k++
                        break
                    } else {
                        text.append(d)
                        k++
                    }
                }
                if (closed) {
                    flush()
                    out.add(Token.Ref(index, text.toString()))
                    i = k
                } else {
                    lit.append('$')
                    i++
                }
            } else {
                lit.append('$')
                i++
            }
        }
        flush()
        return out
    }
}
