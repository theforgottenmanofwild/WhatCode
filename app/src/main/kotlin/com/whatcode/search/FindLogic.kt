package com.whatcode.search

import java.util.concurrent.CancellationException
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * The rules of Find and Replace in the open file that need no device, kept apart from the bar that shows them so
 * they can be unit tested and run on another thread. Every search asks `stop` while it reads, and ends with a
 * [CancellationException] when it says so, and tells `progress` how far through the text it is (as a position).
 */
object FindLogic {

    /** At most this many matches are listed. A text with more says so ([Found.capped]). */
    const val MAX_HITS = 100_000

    /** A text with fewer characters than this is searched at once; a longer one on another thread. */
    const val SYNC_BELOW = 200_000

    /** A search at once that has taken this long moves to another thread instead. */
    const val SYNC_BUDGET_MS = 200L

    class Span(val start: Int, val end: Int)

    class Found(val hits: List<Span>, val capped: Boolean)

    /** The new text for [from] until [to] of the old one, and how many matches it replaces. */
    class Replaced(val from: Int, val to: Int, val text: CharSequence, val count: Int)

    /** The pattern for what was typed, or null when it is empty or not a valid regular expression. */
    fun buildPattern(query: String, caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean): Pattern? {
        if (query.isEmpty()) return null
        return try {
            val core = if (regex) query else Pattern.quote(query)
            val full = if (wholeWord) "(?<!\\w)(?:$core)(?!\\w)" else core
            var flags = Pattern.MULTILINE
            if (!caseSensitive) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            Pattern.compile(full, flags)
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    /** Every match in [text], in order, without empty ones (they have nothing to mark), at most [cap]. */
    fun findAll(
        text: CharSequence,
        pattern: Pattern,
        stop: () -> Boolean = { false },
        progress: (Int) -> Unit = {},
        cap: Int = MAX_HITS,
    ): Found {
        val out = ArrayList<Span>()
        val m = pattern.matcher(StoppableText(text, stop, progress))
        var capped = false
        while (m.find()) {
            if (m.end() == m.start()) continue
            if (out.size >= cap) {
                capped = true
                break
            }
            out.add(Span(m.start(), m.end()))
        }
        return Found(out, capped)
    }

    /**
     * Every match in [text] replaced, as one piece that runs from the first match to the end of the last, so the
     * text before and after is not touched or copied. Null when nothing matches. With [regex], `$1` groups and `\n`
     * and `\t` in [replacement] are expanded (see [expand]).
     */
    fun replaceAll(
        text: CharSequence,
        pattern: Pattern,
        replacement: String,
        regex: Boolean,
        stop: () -> Boolean = { false },
        progress: (Int) -> Unit = {},
    ): Replaced? {
        val m = pattern.matcher(StoppableText(text, stop, progress))
        val sb = StringBuilder()
        var from = -1
        var last = 0
        var n = 0
        while (m.find()) {
            if (m.end() == m.start()) continue
            if (from < 0) {
                from = m.start()
                last = from
            }
            sb.append(text, last, m.start()).append(if (regex) expand(m, replacement) else replacement)
            last = m.end()
            n++
        }
        if (n == 0) return null
        return Replaced(from, last, sb, n)
    }

    /** Expands `$1`-style groups and `\n`, `\t` in a regex replacement; `\x` is `x` for any other character. */
    fun expand(m: Matcher, rep: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < rep.length) {
            val c = rep[i]
            if (c == '\\' && i + 1 < rep.length) {
                when (val n = rep[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> sb.append(n)
                }
                i += 2
            } else if (c == '$' && i + 1 < rep.length && rep[i + 1].isDigit()) {
                var g = rep[i + 1] - '0'
                i += 2
                while (i < rep.length && rep[i].isDigit() && g * 10 + (rep[i] - '0') <= m.groupCount()) {
                    g = g * 10 + (rep[i] - '0')
                    i++
                }
                if (g <= m.groupCount()) sb.append(m.group(g) ?: "")
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
