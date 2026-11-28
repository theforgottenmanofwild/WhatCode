package com.whatcode.editor.highlight

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

internal class Compiled(val pattern: Pattern, val kinds: IntArray)

internal val ruleCache = ConcurrentHashMap<String, Compiled>()

internal fun compile(specs: List<Pair<String, Int>>): Compiled =
    Compiled(Pattern.compile(specs.joinToString("|") { "(${it.first})" }), IntArray(specs.size) { specs[it].second })

/**
 * A quoted string that stops at the quote, at the end of the line, or at the end of the text.
 * The repeats are possessive (`*+`): nothing they give back could ever let the end match, so the result is the
 * same, but Java no longer recurses once per escape, which overflowed a phone's thread stack on long lines.
 */
internal fun quoted(q: Char): String = """$q[^$q\\\n]*+(?:\\[\s\S][^$q\\\n]*+)*+(?:$q|(?=\n)|\z)"""

internal fun nextChar(text: String, from: Int, to: Int): Char {
    var k = from
    while (k < to && (text[k] == ' ' || text[k] == '\t')) k++
    return if (k < to) text[k] else '\u0000'
}
