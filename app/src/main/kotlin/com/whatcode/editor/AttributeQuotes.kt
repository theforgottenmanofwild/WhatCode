package com.whatcode.editor

/**
 * Quoting attribute values in HTML, XML and SVG: typing `=` after an attribute name adds the pair of quotes, and
 * typing the quote that closes a value steps over it. Like [TagMatch] it only deals in text and numbers, so it can
 * be tested without a device.
 *
 * Whether the cursor is in an opening tag is found by reading the markup from the start of the text, so a comment,
 * a quoted value that holds a `>`, or the code of a script or a style is never mistaken for a tag.
 */
object AttributeQuotes {

    /** Where a position is inside an opening tag. */
    private class Place(
        /** Where the name of the tag ends. */
        val nameEnd: Int,
        /** True if the position is inside the value of an attribute, quoted or not. */
        val inValue: Boolean = false,
        /** The quote around that value, and where the closing one is (the length of the text if it is missing). */
        val quote: Char? = null,
        val close: Int = -1,
    )

    /** Elements whose text is another language. */
    private val RAW_TEXT = setOf("script", "style")

    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.'

    /** Anything that may be part of an attribute name, including forms such as `@click`, `[value]` and `v-on:x`. */
    private fun isAttributeChar(c: Char): Boolean = !c.isWhitespace() && c !in "\"'<>/="

    private fun isQuote(c: Char): Boolean = c == '"' || c == '\''

    /**
     * [c] was typed at [pos] with nothing selected. Returns the change to make instead of the plain typing, or null to
     * let the character be typed as usual.
     */
    fun typed(text: CharSequence, pos: Int, c: Char, lang: Lang): TextEdit? {
        if (lang.family != Family.MARKUP || pos <= 0 || pos > text.length) return null
        if (c == '=') {
            val place = placeAt(text, pos, lang) ?: return null
            if (place.inValue || !afterAttributeName(text, pos, place.nameEnd) || !freeForValue(text, pos)) return null
            return TextEdit(pos, pos, "=\"\"", pos + 2)
        }
        if (isQuote(c) && pos < text.length && text[pos] == c) {
            val place = placeAt(text, pos, lang) ?: return null
            if (place.quote == c && place.close == pos) return TextEdit(pos, pos, "", pos + 1)
        }
        return null
    }

    private fun afterAttributeName(text: CharSequence, pos: Int, tagNameEnd: Int): Boolean {
        var end = pos
        while (end > 0 && text[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && isAttributeChar(text[start - 1])) start--
        return start < end && start > tagNameEnd && text[start - 1].isWhitespace()
    }

    /** True if nothing after [pos] is a value, a quote or another `=` that the new quotes would get in the way of. */
    private fun freeForValue(text: CharSequence, pos: Int): Boolean {
        if (pos >= text.length) return true
        val next = text[pos]
        val tagEnds = next == '>' || (next == '/' && pos + 1 < text.length && text[pos + 1] == '>')
        if (!next.isWhitespace() && !tagEnds) return false
        var i = pos
        while (i < text.length && text[i].isWhitespace()) i++
        return i >= text.length || (!isQuote(text[i]) && text[i] != '=')
    }

    /** The position right after the next [end] from [from] on; past the end of the text if there is none. */
    private fun after(text: CharSequence, end: String, from: Int): Int {
        val at = text.indexOf(end, from)
        return if (at < 0) text.length + 1 else at + end.length
    }

    /** What [pos] is inside the opening tag it is in, or null if it is in text, a comment, a script and so on. */
    private fun placeAt(text: CharSequence, pos: Int, lang: Lang): Place? {
        val n = text.length
        var i = 0
        while (i < pos) {
            if (text[i] != '<' || i + 1 >= n) {
                i++
                continue
            }
            val next = text[i + 1]
            // Comments, CDATA, doctypes, processing instructions and closing tags hold no attributes to quote.
            val end = when {
                text.startsWith("<!--", i) -> after(text, "-->", i + 4)
                text.startsWith("<![CDATA[", i) -> after(text, "]]>", i + 9)
                next == '!' || next == '?' -> after(text, ">", i + 2)
                next == '/' && i + 2 < n && text[i + 2].isLetter() -> {
                    var j = i + 2
                    while (j < n && text[j] != '>' && text[j] != '<') j++
                    if (j < n && text[j] == '<') j else j + 1
                }
                else -> -1
            }
            if (end >= 0) {
                if (pos < end) return null
                i = end
                continue
            }
            if (!next.isLetter()) {
                i++
                continue
            }
            var nameEnd = i + 1
            while (nameEnd < n && isNameChar(text[nameEnd])) nameEnd++
            var j = nameEnd
            while (j < pos && text[j] != '>' && text[j] != '<') {
                if (text[j] != '=') {
                    j++
                    continue
                }
                var v = j + 1
                while (v < n && text[v].isWhitespace()) v++
                if (v < n && isQuote(text[v])) {
                    val found = text.indexOf(text[v], v + 1)
                    val close = if (found < 0) n else found
                    if (pos > v && pos <= close) return Place(nameEnd, true, text[v], close)
                    j = if (pos <= v) pos else close + 1
                } else {
                    var e = v
                    while (e < n && !text[e].isWhitespace() && text[e] != '>' && text[e] != '<') e++
                    if (pos > v && pos <= e) return Place(nameEnd, true)
                    j = if (e > v) e else j + 1
                }
            }
            if (j >= pos) return Place(nameEnd)
            if (text[j] == '<') { // a tag that was never ended; the next one starts here
                i = j
                continue
            }
            val name = text.substring(i + 1, nameEnd)
            i = j + 1
            if (lang.name != "XML" && name.lowercase() in RAW_TEXT && text[j - 1] != '/') {
                val closing = text.indexOf("</$name", i, ignoreCase = true)
                if (closing < 0 || pos <= closing) return null
                i = closing
            }
        }
        return null
    }
}
