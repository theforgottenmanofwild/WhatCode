package com.whatcode.editor

/**
 * Finds the bracket that goes with the one next to the cursor, so the editor can highlight the pair. It only deals
 * in text and numbers, so it can be tested without a device; [CodeEditText] shows what it finds.
 *
 * Round, square and curly brackets are paired in every language. Angle brackets are paired only where they mean
 * something: as the two ends of a tag in HTML, XML and SVG, and as the brackets of generics (`List<String>`) in the
 * languages that have them, where the comparison and arrow operators (`a < b`, `->`, `=>`, `>=`) are left alone.
 *
 * A bracket that the highlighter coloured as a string, a comment or a regular expression is not paired, and is not
 * counted while looking for a partner, so `f("(", x)` pairs the real brackets. The search stops [MAX_SCAN]
 * characters away from the bracket, so a big file or a single very long line never slows the cursor down.
 */
object BracketMatch {

    /** How far from a bracket its partner is looked for, in characters. */
    const val MAX_SCAN = 10_000

    /** The two brackets of a pair: where the opening one is and where the closing one is. */
    class BracketPair(val open: Int, val close: Int)

    private const val OPENERS = "([{<"
    private const val CLOSERS = ")]}>"

    /** What `<` and `>` mean in a language: nothing, the ends of a tag, or the brackets of generics. */
    private enum class Angles { NONE, TAGS, GENERICS }

    private val GENERIC_LANGUAGES = setOf("TypeScript", "Kotlin", "Java", "C#", "C++", "Rust", "Swift", "Dart", "Scala")

    private val NOT_SKIPPED: (Int) -> Boolean = { false }

    private fun anglesFor(lang: Lang): Angles = when {
        lang.family == Family.MARKUP -> Angles.TAGS
        lang.name in GENERIC_LANGUAGES -> Angles.GENERICS
        else -> Angles.NONE
    }

    /**
     * The pair that the bracket next to [cursor] belongs to, or null if there is none (no bracket there, no
     * partner within [MAX_SCAN] characters, or the bracket is inside a string or a comment). The character before the
     * cursor is tried first, then the one after it. [skipped] says whether the character at a position is in a string,
     * a comment or a regular expression; see [skipFrom].
     */
    fun find(text: CharSequence, cursor: Int, lang: Lang, skipped: (Int) -> Boolean = NOT_SKIPPED): BracketPair? {
        if (cursor < 0 || cursor > text.length) return null
        val angles = anglesFor(lang)
        if (cursor > 0) pairOf(text, cursor - 1, angles, skipped)?.let { return it }
        if (cursor < text.length) pairOf(text, cursor, angles, skipped)?.let { return it }
        return null
    }

    /**
     * A function for [find] that says which positions the highlighter coloured as a string, a comment or a regular
     * expression, made from the pieces [Highlighter.tokenize] found. The pieces are in order and do not overlap.
     */
    fun skipFrom(tokens: Tokens): (Int) -> Boolean = { pos ->
        var lo = 0
        var hi = tokens.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (tokens.data[mid * 3] <= pos) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        found >= 0 && pos < tokens.data[found * 3 + 1] && Highlighter.isLiteral(tokens.data[found * 3 + 2])
    }

    private fun pairOf(text: CharSequence, pos: Int, angles: Angles, skipped: (Int) -> Boolean): BracketPair? {
        val c = text[pos]
        val o = OPENERS.indexOf(c)
        val kind = if (o >= 0) o else CLOSERS.indexOf(c)
        if (kind < 0) return null
        val opening = o >= 0
        if (!isBracket(text, pos, c, angles, skipped)) return null
        if (skipped(pos)) return null
        val open = OPENERS[kind]
        val close = CLOSERS[kind]
        val partner = if (opening) forward(text, pos, open, close, angles, skipped) else backward(text, pos, open, close, angles, skipped)
        if (partner < 0) return null
        return if (opening) BracketPair(pos, partner) else BracketPair(partner, pos)
    }

    private fun forward(text: CharSequence, from: Int, open: Char, close: Char, angles: Angles, skipped: (Int) -> Boolean): Int {
        val end = minOf(text.length, from + 1 + MAX_SCAN)
        val generics = open == '<' && angles == Angles.GENERICS
        var depth = 1
        var i = from + 1
        while (i < end) {
            val c = text[i]
            if (c == open) {
                if (isBracket(text, i, c, angles, skipped) && !skipped(i)) depth++
            } else if (c == close) {
                if (isBracket(text, i, c, angles, skipped) && !skipped(i) && --depth == 0) return i
            } else if (generics && endsGenerics(text, i) && !skipped(i)) {
                return -1
            }
            i++
        }
        return -1
    }

    private fun backward(text: CharSequence, from: Int, open: Char, close: Char, angles: Angles, skipped: (Int) -> Boolean): Int {
        val end = maxOf(0, from - MAX_SCAN)
        val generics = open == '<' && angles == Angles.GENERICS
        var depth = 1
        var i = from - 1
        while (i >= end) {
            val c = text[i]
            if (c == close) {
                if (isBracket(text, i, c, angles, skipped) && !skipped(i)) depth++
            } else if (c == open) {
                if (isBracket(text, i, c, angles, skipped) && !skipped(i) && --depth == 0) return i
            } else if (generics && endsGenerics(text, i) && !skipped(i)) {
                return -1
            }
            i--
        }
        return -1
    }

    /** True if the character at [i] is a bracket in this language; only `<` and `>` can be something else. */
    private fun isBracket(text: CharSequence, i: Int, c: Char, angles: Angles, skipped: (Int) -> Boolean): Boolean = when (c) {
        '<' -> (angles == Angles.TAGS && isTagOpen(text, i)) || (angles == Angles.GENERICS && isGenericOpen(text, i))
        '>' -> (angles == Angles.TAGS && isTagClose(text, i, skipped)) || (angles == Angles.GENERICS && isGenericClose(text, i))
        else -> true
    }

    /** In markup `<` starts a tag only when a name, `/`, `!` or `?` follows it, so `1 < 2` is just text. */
    private fun isTagOpen(text: CharSequence, i: Int): Boolean {
        if (i + 1 >= text.length) return false
        val n = text[i + 1]
        return n.isLetter() || n == '/' || n == '!' || n == '?'
    }

    /** In markup `>` ends a tag only when the nearest `<` or `>` before it is the `<` that starts a tag. */
    private fun isTagClose(text: CharSequence, i: Int, skipped: (Int) -> Boolean): Boolean {
        val end = maxOf(0, i - MAX_SCAN)
        var k = i - 1
        while (k >= end) {
            val c = text[k]
            if ((c == '<' || c == '>') && !skipped(k)) return c == '<' && isTagOpen(text, k)
            k--
        }
        return false
    }

    /** `<` opens generics when it touches the name before it (`List<`, `foo::<`, `template <`) and not what follows. */
    private fun isGenericOpen(text: CharSequence, i: Int): Boolean {
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        if (next == '=' || next == '<' || next.isWhitespace()) return false // a <= b, a << b, a < b
        if (i == 0) return false
        val prev = text[i - 1]
        if (prev.isLetterOrDigit() || prev == '_') return true
        if (prev == ':' && i >= 2 && text[i - 2] == ':') return true // Rust: foo::<T>()
        if (prev == ' ') return endsWithWord(text, i - 1, "template") // C++: template <typename T>
        return false
    }

    /** `>` closes generics unless it is the end of an arrow (`->`, `=>`), a comparison (`>=`) or has a space before it. */
    private fun isGenericClose(text: CharSequence, i: Int): Boolean {
        if (i == 0) return false
        val prev = text[i - 1]
        if (prev == '-' || prev == '=' || prev.isWhitespace()) return false
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        return next != '='
    }

    /** Generics never contain these, so meeting one while looking for a partner means there is none. */
    private fun endsGenerics(text: CharSequence, i: Int): Boolean {
        val c = text[i]
        if (c == ';' || c == '{' || c == '}') return true
        if (c == '&' || c == '|') return (i + 1 < text.length && text[i + 1] == c) || (i > 0 && text[i - 1] == c)
        return false
    }

    /** True if the word [word] ends right before [end], as a whole word. */
    private fun endsWithWord(text: CharSequence, end: Int, word: String): Boolean {
        var e = end
        while (e > 0 && text[e - 1] == ' ') e--
        val start = e - word.length
        if (start < 0) return false
        for (k in word.indices) if (text[start + k] != word[k]) return false
        return start == 0 || !(text[start - 1].isLetterOrDigit() || text[start - 1] == '_')
    }
}
