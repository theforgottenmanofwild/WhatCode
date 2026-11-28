package com.whatcode.editor

import android.text.style.ForegroundColorSpan

/**
 * Marker subclass so we can remove only our own highlighting spans. [literal] is true for a piece that was coloured
 * as a string, a comment or a regular expression; the bracket matching does not pair brackets inside those.
 */
class SyntaxSpan(color: Int, val literal: Boolean = false) : ForegroundColorSpan(color)

/** How a language is read: the family decides which scanner is used. */
enum class Family { PLAIN, CODE, MARKUP, CSS, YAML, INI, MARKDOWN }

/**
 * One language. [name] is what the status bar shows. The word sets say which words are keywords, control
 * keywords (if, return), constants (true, null), types and so on; the other fields describe comments and strings.
 */
class Lang(
    val name: String,
    val family: Family = Family.CODE,
    val keywords: Set<String> = emptySet(),
    val controls: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    /** Words that are keywords only where one is expected, such as `from` in `import x from "y"`. */
    val soft: Set<String> = emptySet(),
    /** Words after which comes the name of a function (`fun`, `def`). */
    val defWords: Set<String> = emptySet(),
    /** Words after which comes the name of a type (`class`, `struct`). */
    val typeWords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComments: Boolean = false,
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    /** Python style prefixes such as r"" and f"". */
    val prefixedStrings: Boolean = false,
    val regexLiterals: Boolean = false,
    /** Capitalised words are types and ALL_CAPS words are constants. */
    val capitalizedTypes: Boolean = false,
    val caseInsensitive: Boolean = false,
    /** A word followed by ( is a function. */
    val calls: Boolean = true,
    /** A string followed by a colon is a key (JSON). */
    val keys: Boolean = false,
    /** Extra patterns with their colours, tried before plain words. */
    val extras: List<Pair<String, Int>> = emptyList(),
    /** Comment markers for languages that have no line comment, such as HTML. */
    val block: Pair<String, String>? = null,
    /** TOML allows a comment after a value. */
    val trailingHash: Boolean = false,
    /** A word followed by a colon, after a { or a comma, is the key of an object (`{ id: 1 }`). */
    val objectKeys: Boolean = false,
) {
    val lineComment: String? get() = lineComments.firstOrNull()

    /** What the comment key puts round a line: the opening and the closing marker (empty for a line comment). */
    val commentPair: Pair<String, String>? get() = lineComment?.let { it to "" } ?: block
}

/** The quote characters that are typed in pairs in this language; none in plain text and Markdown. */
val Lang.pairQuotes: String get() = if (family == Family.PLAIN || family == Family.MARKDOWN) "" else quotes

/** The coloured pieces found in a text: start, end and colour of each, in order and not overlapping. */
class Tokens(private val discard: Boolean = false) {
    var data = IntArray(if (discard) 0 else 3 * 2048)
    var size = 0

    /** Takes a piece. A [discard]ing list takes none: it is for a scan that only wants to know where its pieces fall. */
    fun add(start: Int, end: Int, color: Int) {
        if (discard || end <= start) return
        if (size * 3 + 3 > data.size) data = data.copyOf(data.size * 2)
        data[size * 3] = start
        data[size * 3 + 1] = end
        data[size * 3 + 2] = color
        size++
    }
}
