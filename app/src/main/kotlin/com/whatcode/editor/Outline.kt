package com.whatcode.editor

import java.util.concurrent.CancellationException
import java.util.regex.Pattern

/**
 * The outline of a text: its functions, classes and headings, for Go to symbol and for the current scope in the
 * status bar. It is found line by line with a pattern for each kind of language, not with a parser, so it is
 * quick and never fails, and it can miss a form that is rare (an arrow function in a variable, say). No Android here.
 */
object Outline {

    enum class Kind { FUNCTION, TYPE, HEADING }

    /**
     * One entry. [line] counts from 1, [offset] is where its name starts, [indent] is the width of the whitespace
     * before it (a tab is four), which also tells what contains it.
     */
    class Symbol(val line: Int, val offset: Int, val name: String, val kind: Kind, val indent: Int)

    /** At most this many entries are listed. */
    const val MAX_SYMBOLS = 5000

    /** A text with fewer characters than this is read at once; a longer one on another thread. */
    const val SYNC_BELOW = 200_000

    private const val TAB = 4

    private val MD_HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.*?)[ \\t#]*$")
    private val MD_FENCE = Pattern.compile("^[ ]{0,3}(```|~~~)")
    private val HTML_HEADING = Pattern.compile("<h([1-6])\\b[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE)
    private val CSS_RULE = Pattern.compile("^([^\\s@{}/;][^{};]*?|@[^{};]*?)[ \\t]*\\{[ \\t]*$")
    private val YAML_KEY = Pattern.compile("^([A-Za-z_][\\w.-]*):(?:\\s|$)")
    private val INI_SECTION = Pattern.compile("^\\[([^\\]]+)]")
    private val TAGS = Pattern.compile("<[^>]*>")

    private val patterns = HashMap<Lang, Pattern?>()

    /** The pattern of a code language: modifiers, a word that starts a definition, then its name. */
    private fun codePattern(lang: Lang): Pattern? = synchronized(patterns) {
        patterns.getOrPut(lang) {
            val words = (lang.defWords + lang.typeWords).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                null
            } else {
                val alt = words.joinToString("|") { Pattern.quote(it) }
                // Modifiers (public, static, async, export default) come first; a receiver or generics may follow the
                // word (Go: func (r *T) Name, Kotlin: fun <T> Name); the name may have dots (Kotlin: fun Type.name).
                Pattern.compile("^(?:[\\w@$]+[ \\t]+){0,6}?($alt)[ \\t]+(?:<[^>]*>[ \\t]*)?(?:\\([^)]*\\)[ \\t]*)?([\\p{L}_$][\\w$.]*)")
            }
        }
    }

    /**
     * The entries of [text] in order, at most [limit]. [stop] is asked between lines: when it says so the search ends
     * with a [CancellationException]. Lines end at `\n`, and a `\r` before it is not part of the line.
     */
    fun find(text: CharSequence, lang: Lang, stop: () -> Boolean = { false }, limit: Int = MAX_SYMBOLS): List<Symbol> {
        if (lang.family == Family.PLAIN) return emptyList()
        val code = if (lang.family == Family.CODE) codePattern(lang) else null
        if (lang.family == Family.CODE && code == null) return emptyList()
        val out = ArrayList<Symbol>()
        var lineNo = 0
        var start = 0
        var fenced = false
        val n = text.length
        while (start <= n && out.size < limit) {
            if ((lineNo and 0xFF) == 0 && stop()) throw CancellationException()
            var end = start
            while (end < n && text[end] != '\n') end++
            lineNo++
            var lineEnd = end
            if (lineEnd > start && text[lineEnd - 1] == '\r') lineEnd--
            if (lineEnd > start) {
                val line = text.subSequence(start, lineEnd).toString()
                when (lang.family) {
                    Family.CODE -> code(line, code!!, lang, lineNo, start, out)
                    Family.MARKDOWN -> {
                        if (MD_FENCE.matcher(line).find()) {
                            fenced = !fenced
                        } else if (!fenced) {
                            MD_HEADING.matcher(line).let { m ->
                                if (m.find() && m.group(2)!!.isNotEmpty()) {
                                    out.add(Symbol(lineNo, start + m.start(2), m.group(2)!!, Kind.HEADING, (m.group(1)!!.length - 1) * 2))
                                }
                            }
                        }
                    }
                    Family.MARKUP -> {
                        val m = HTML_HEADING.matcher(line)
                        if (m.find()) {
                            val title = TAGS.matcher(m.group(2)!!).replaceAll("").trim()
                            if (title.isNotEmpty()) out.add(Symbol(lineNo, start + m.start(), title, Kind.HEADING, (m.group(1)!![0] - '1') * 2))
                        }
                    }
                    Family.CSS -> {
                        val m = CSS_RULE.matcher(line.trimStart())
                        if (m.find() && line.length - line.trimStart().length == 0) {
                            out.add(Symbol(lineNo, start, m.group(1)!!.replace(Regex("\\s+"), " "), Kind.TYPE, 0))
                        }
                    }
                    Family.YAML -> {
                        val m = YAML_KEY.matcher(line)
                        if (m.find()) out.add(Symbol(lineNo, start, m.group(1)!!, Kind.HEADING, 0))
                    }
                    Family.INI -> {
                        val m = INI_SECTION.matcher(line)
                        if (m.find()) out.add(Symbol(lineNo, start + 1, m.group(1)!!, Kind.HEADING, 0))
                    }
                    else -> {}
                }
            }
            if (end >= n) break
            start = end + 1
        }
        return out
    }

    private fun code(line: String, p: Pattern, lang: Lang, lineNo: Int, lineStart: Int, out: MutableList<Symbol>) {
        val lead = line.indexOfFirst { it != ' ' && it != '\t' }
        if (lead < 0) return
        // A comment is not a definition. (Only the commonest markers; a comment in a string is rare enough.)
        for (c in lang.lineComments) if (c.isNotEmpty() && line.startsWith(c, lead)) return
        if (line.startsWith("*", lead) || line.startsWith("/*", lead)) return
        val m = p.matcher(line.substring(lead))
        if (!m.find()) return
        val word = m.group(1)!!
        val kind = if (word in lang.typeWords) Kind.TYPE else Kind.FUNCTION
        out.add(Symbol(lineNo, lineStart + lead + m.start(2), m.group(2)!!.trimEnd('.'), kind, width(line, lead)))
    }

    /** The width of the first [upTo] characters of [line], which are spaces and tabs. */
    private fun width(line: String, upTo: Int): Int {
        var w = 0
        for (i in 0 until upTo) w += if (line[i] == '\t') TAB else 1
        return w
    }

    /** The width of the whitespace at the start of the line that has [offset], and whether the line is blank. */
    private fun indentAt(text: CharSequence, lineStart: Int): Pair<Int, Boolean> {
        var w = 0
        var i = lineStart
        while (i < text.length) {
            when (text[i]) {
                ' ' -> w++
                '\t' -> w += TAB
                '\n', '\r' -> return w to true
                else -> return w to false
            }
            i++
        }
        return w to true
    }

    private fun lineStartOf(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    /**
     * What contains the cursor at [offset], outermost first: the entry on the cursor's line if there is one, and then
     * each earlier entry that is indented less than the one after it. A blank line counts as indented as the deeper
     * of the lines around it, so a gap inside a function is still inside it. [symbols] must be in order.
     */
    fun scopeAt(symbols: List<Symbol>, text: CharSequence, offset: Int): List<Symbol> {
        if (symbols.isEmpty() || text.isEmpty()) return emptyList()
        val at = offset.coerceIn(0, text.length)
        val start = lineStartOf(text, at)
        val end = lineEndOf(text, start)
        // The last entry that starts on this line or before it.
        var lo = 0
        var hi = symbols.size - 1
        var last = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (symbols[mid].offset <= end) {
                last = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (last < 0) return emptyList()
        val (own, blank) = indentAt(text, start)
        val chain = ArrayList<Symbol>()
        var limit: Int
        var i = last
        if (symbols[last].offset >= start || symbols[last].kind == Kind.HEADING) {
            // The entry on this line, or a heading above it: a heading holds everything up to the next one that is as big.
            chain.add(symbols[last])
            limit = symbols[last].indent
            i--
        } else {
            limit = if (blank) neighbourIndent(text, start) else own
        }
        while (i >= 0) {
            val s = symbols[i]
            if (s.indent < limit) {
                chain.add(s)
                limit = s.indent
                if (limit == 0) break
            }
            i--
        }
        chain.reverse()
        return chain
    }

    /** The separator between the names of a scope in the status bar. */
    const val SCOPE_JOIN = " \u203A "

    /**
     * The names of a [scope] for the status bar, outermost first, such as `Foo › run`. When they do not fit in [max]
     * characters the outer ones are dropped and a `…` takes their place, so the innermost name is always there
     * (cut short with a `…` if it alone is too long). An empty scope gives an empty string.
     */
    fun label(scope: List<Symbol>, max: Int = 40): String {
        if (scope.isEmpty()) return ""
        var from = 0
        fun join(i: Int) = (if (i > 0) "\u2026$SCOPE_JOIN" else "") + scope.subList(i, scope.size).joinToString(SCOPE_JOIN) { it.name }
        while (from < scope.size - 1 && join(from).length > max) from++
        val out = join(from)
        return if (out.length > max && max > 1) out.substring(0, max - 1) + "\u2026" else out
    }

    private fun lineEndOf(text: CharSequence, start: Int): Int {
        var i = start
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    /** For a blank line: the deeper of the nearest line with text above it and below it. */
    private fun neighbourIndent(text: CharSequence, start: Int): Int {
        var above = 0
        var i = start
        var steps = 0
        while (i > 0 && steps < 200) {
            val prev = lineStartOf(text, i - 1)
            val (w, blank) = indentAt(text, prev)
            if (!blank) {
                above = w
                break
            }
            i = prev
            steps++
        }
        var below = 0
        var j = lineEndOf(text, start)
        steps = 0
        while (j < text.length && steps < 200) {
            val next = j + 1
            if (next >= text.length) break
            val (w, blank) = indentAt(text, next)
            if (!blank) {
                below = w
                break
            }
            j = lineEndOf(text, next)
            steps++
        }
        return maxOf(above, below)
    }
}
