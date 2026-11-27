package com.whatcode.editor

/**
 * The elements of a stretch of HTML, XML or SVG: where each opening tag and its closing tag are. It reads the text
 * once from front to back, so every element it finds nests properly. Used by [SelectionExpander] and [TagWrap].
 * Like [TagMatch] it only deals in text and numbers.
 *
 * Comments, CDATA, self-closing tags, HTML tags that have no closing tag, the code of a script or a style, and a
 * `<` that the highlighter coloured as a string or a comment are not elements. A tag without a partner is left out.
 */
object MarkupElements {

    /** An opening tag from [start] up to [innerStart] and its closing tag from [innerEnd] up to [end]. */
    class Element(val start: Int, val innerStart: Int, val innerEnd: Int, val end: Int)

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    private class Open(val name: String, val start: Int, val innerStart: Int)

    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.'

    /** The elements that open and close between [from] and [to]. */
    fun find(
        text: CharSequence,
        lang: Lang,
        from: Int = 0,
        to: Int = text.length,
        skipped: (Int) -> Boolean = { false },
    ): List<Element> {
        if (lang.family != Family.MARKUP) return emptyList()
        val html = lang.name == "HTML"
        val end = minOf(to, text.length)
        val out = ArrayList<Element>()
        val open = ArrayList<Open>()
        var i = maxOf(from, 0)
        while (i < end) {
            if (text[i] != '<' || i + 1 >= end) {
                i++
                continue
            }
            if (text.startsWith("<!--", i)) {
                i = after(text, "-->", i + 4)
                continue
            }
            if (text.startsWith("<![CDATA[", i)) {
                i = after(text, "]]>", i + 9)
                continue
            }
            val closing = text[i + 1] == '/'
            val nameStart = if (closing) i + 2 else i + 1
            if (nameStart >= end || !text[nameStart].isLetter() || skipped(i)) {
                i++
                continue
            }
            var nameEnd = nameStart
            while (nameEnd < end && isNameChar(text[nameEnd])) nameEnd++
            val gt = tagEnd(text, nameEnd, end)
            if (gt < 0) {
                i = nameEnd
                continue
            }
            val name = text.subSequence(nameStart, nameEnd).toString()
            i = gt + 1
            if (closing) {
                val at = open.indexOfLast { it.name.equals(name, ignoreCase = html) }
                if (at >= 0) {
                    out.add(Element(open[at].start, open[at].innerStart, nameStart - 2, gt + 1))
                    // Tags opened inside it and never closed end with it.
                    while (open.size > at) open.removeAt(open.size - 1)
                }
            } else if (text[gt - 1] != '/' && !(html && name.lowercase() in VOID)) {
                open.add(Open(name, nameStart - 1, gt + 1))
                if (lang.name != "XML" && (name.equals("script", true) || name.equals("style", true))) {
                    val close = text.indexOf("</$name", gt + 1, ignoreCase = true)
                    i = if (close < 0) end else close
                }
            }
        }
        return out
    }

    /** The position of the `>` that ends a tag, looking from [from]; -1 if another `<` or [end] comes first. */
    private fun tagEnd(text: CharSequence, from: Int, end: Int): Int {
        var i = from
        var last = ' '
        while (i < end) {
            val c = text[i]
            if (c == '>') return i
            if (c == '<') return -1
            if ((c == '"' || c == '\'') && last == '=') {
                val close = text.indexOf(c, i + 1)
                if (close < 0 || close >= end) return -1
                i = close
            }
            if (!c.isWhitespace()) last = c
            i++
        }
        return -1
    }

    private fun after(text: CharSequence, end: String, from: Int): Int {
        val at = text.indexOf(end, from)
        return if (at < 0) text.length else at + end.length
    }
}
