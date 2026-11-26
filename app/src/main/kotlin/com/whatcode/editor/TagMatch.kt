package com.whatcode.editor

/**
 * Tag logic for HTML, XML and SVG: finds the closing tag that goes with the opening tag at the cursor (and the other
 * way round), and tells whether a tag that was just opened still needs its closing tag. Like [BracketMatch] it only
 * deals in text and numbers, so it can be tested without a device.
 *
 * A tag that the highlighter coloured as a string or a comment is not a tag, `<br>`-like void elements (HTML only)
 * and self-closing tags (`<path/>`) have no partner, and the search stops [MAX_SCAN] characters away.
 */
object TagMatch {

    /** How far from a tag its partner is looked for, in characters. */
    const val MAX_SCAN = BracketMatch.MAX_SCAN

    /**
     * The two tags of a pair. The opening tag is marked from its `<` to the end of its name (`<div`), the closing tag
     * is marked whole (`</div>`). Each range is from (included) up to (not included).
     */
    class TagPair(val openStart: Int, val openEnd: Int, val closeStart: Int, val closeEnd: Int)

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    /** Elements whose text is another language. */
    private val RAW_TEXT = arrayOf("script", "style")

    private val NOT_SKIPPED: (Int) -> Boolean = { false }

    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-' || c == '_' || c == ':' || c == '.'

    private fun nameEnd(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && isNameChar(text[i])) i++
        return i
    }

    /** HTML does not care about the case of tag names, XML does. */
    private fun caseless(lang: Lang) = lang.name == "HTML"

    private fun isVoid(name: String, lang: Lang) = lang.name == "HTML" && name.lowercase() in VOID

    /** True if the `<` at [i] starts an opening or a closing tag (not a comment, a doctype or plain text). */
    private fun startsTag(text: CharSequence, i: Int): Boolean {
        if (i + 1 >= text.length) return false
        val n = text[i + 1]
        return n.isLetter() || (n == '/' && i + 2 < text.length && text[i + 2].isLetter())
    }

    /** The position of the `>` that ends the tag starting at [lt], or -1. */
    private fun tagEnd(text: CharSequence, lt: Int, skipped: (Int) -> Boolean): Int {
        val end = minOf(text.length, lt + 1 + MAX_SCAN)
        var i = lt + 1
        while (i < end) {
            val c = text[i]
            if (c == '>' && !skipped(i)) return i
            if (c == '<' && !skipped(i)) return -1
            i++
        }
        return -1
    }

    /** The `<` and the `>` of the tag the cursor is in, at its start or right after its end, or null. */
    private fun tagAround(text: CharSequence, cursor: Int, skipped: (Int) -> Boolean): IntArray? {
        if (cursor < text.length && text[cursor] == '<' && startsTag(text, cursor) && !skipped(cursor)) {
            val gt = tagEnd(text, cursor, skipped)
            return if (gt < 0) null else intArrayOf(cursor, gt)
        }
        val stop = maxOf(0, cursor - MAX_SCAN)
        var justAfter = -1
        var k = cursor - 1
        while (k >= stop) {
            val c = text[k]
            if ((c == '<' || c == '>') && !skipped(k)) {
                if (c == '>') {
                    if (k == cursor - 1 && justAfter < 0) {
                        justAfter = k // the cursor is right after a tag; look for where it starts
                        k--
                        continue
                    }
                    return null
                }
                if (!startsTag(text, k)) return null
                if (justAfter >= 0) return intArrayOf(k, justAfter)
                val gt = tagEnd(text, k, skipped)
                return if (gt < cursor) null else intArrayOf(k, gt)
            }
            k--
        }
        return null
    }

    /**
     * The tag pair that the tag at [cursor] belongs to, or null. The cursor counts as being in a tag anywhere from its
     * `<` to right after its `>`.
     */
    fun find(text: CharSequence, cursor: Int, lang: Lang, skipped: (Int) -> Boolean = NOT_SKIPPED): TagPair? {
        if (lang.family != Family.MARKUP || cursor < 0 || cursor > text.length) return null
        val t = tagAround(text, cursor, skipped) ?: return null
        val lt = t[0]
        val gt = t[1]
        val closing = text[lt + 1] == '/'
        val ns = if (closing) lt + 2 else lt + 1
        val ne = nameEnd(text, ns)
        if (ne == ns) return null
        if (!closing && text[gt - 1] == '/') return null // <path/>
        val name = text.subSequence(ns, ne).toString()
        if (!closing && isVoid(name, lang)) return null
        val ic = caseless(lang)
        return if (closing) {
            val open = backward(text, lt, name, ic, skipped)
            if (open < 0) null else TagPair(open, nameEnd(text, open + 1), lt, gt + 1)
        } else {
            val close = forward(text, gt + 1, name, ic, skipped) ?: return null
            TagPair(lt, ne, close[0], close[1] + 1)
        }
    }

    /** Where the closing tag for [name] is (its `<` and its `>`), looking forward from [from]. */
    private fun forward(text: CharSequence, from: Int, name: String, ic: Boolean, skipped: (Int) -> Boolean): IntArray? {
        val end = minOf(text.length, from + MAX_SCAN)
        var depth = 1
        var i = from
        while (i < end) {
            if (text[i] == '<' && startsTag(text, i) && !skipped(i)) {
                val closing = text[i + 1] == '/'
                val ns = if (closing) i + 2 else i + 1
                if (sameName(text, ns, name, ic)) {
                    val gt = tagEnd(text, i, skipped)
                    if (gt >= 0) {
                        if (closing) {
                            if (--depth == 0) return intArrayOf(i, gt)
                        } else if (text[gt - 1] != '/') {
                            depth++
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    /** Where the opening tag for [name] starts, looking back from the closing tag at [from]; -1 if there is none. */
    private fun backward(text: CharSequence, from: Int, name: String, ic: Boolean, skipped: (Int) -> Boolean): Int {
        val stop = maxOf(0, from - MAX_SCAN)
        var depth = 1
        var i = from - 1
        while (i >= stop) {
            if (text[i] == '<' && startsTag(text, i) && !skipped(i)) {
                val closing = text[i + 1] == '/'
                val ns = if (closing) i + 2 else i + 1
                if (sameName(text, ns, name, ic)) {
                    if (closing) {
                        depth++
                    } else {
                        val gt = tagEnd(text, i, skipped)
                        if (gt >= 0 && text[gt - 1] != '/' && --depth == 0) return i
                    }
                }
            }
            i--
        }
        return -1
    }

    /** True if exactly [name] is written at [at]: the name must end there, so `p` does not match `pre`. */
    private fun sameName(text: CharSequence, at: Int, name: String, ic: Boolean): Boolean {
        if (at + name.length > text.length) return false
        if (!text.regionMatches(at, name, 0, name.length, ic)) return false
        return at + name.length >= text.length || !isNameChar(text[at + name.length])
    }

    // ---------------------------------------------------------------- closing a tag that was just opened

    /**
     * The name of the tag that typing `>` at [pos] ends, if that tag needs a closing tag: it must be an opening tag
     * (not `</x`, `<!`, `<?` or a self-closing `<x/`) and not a void element such as `<br>`. Null otherwise.
     */
    fun openingNameAt(text: CharSequence, pos: Int, lang: Lang, skipped: (Int) -> Boolean = NOT_SKIPPED): String? {
        if (lang.family != Family.MARKUP || pos <= 0 || pos > text.length) return null
        if (text[pos - 1] == '/') return null
        val stop = maxOf(0, pos - MAX_SCAN)
        var k = pos - 1
        while (k >= stop) {
            val c = text[k]
            if ((c == '<' || c == '>') && !skipped(k)) {
                if (c == '>' || k + 1 >= pos || !text[k + 1].isLetter()) return null
                val ne = minOf(nameEnd(text, k + 1), pos)
                if (inQuotes(text, ne, pos) || inRawText(text, k, lang, skipped)) return null
                val name = text.subSequence(k + 1, ne).toString()
                return if (isVoid(name, lang)) null else name
            }
            k--
        }
        return null
    }

    /** True if the `<` at [lt] is in the text of a script or style element, which is code and not markup. */
    private fun inRawText(text: CharSequence, lt: Int, lang: Lang, skipped: (Int) -> Boolean): Boolean {
        if (lang.name == "XML") return false
        for (raw in RAW_TEXT) {
            var at = text.lastIndexOf("<$raw", lt - 1, ignoreCase = true)
            while (at >= 0 && (skipped(at) || !sameName(text, at + 1, raw, true))) {
                at = text.lastIndexOf("<$raw", at - 1, ignoreCase = true)
            }
            if (at >= 0 && text.lastIndexOf("</$raw", lt, ignoreCase = true) < at) return true
        }
        return false
    }

    /** True if a quoted attribute value is still open between [from] and [to]. */
    private fun inQuotes(text: CharSequence, from: Int, to: Int): Boolean {
        var quote: Char? = null
        for (i in from until to) {
            val c = text[i]
            if (quote == null) {
                if (c == '"' || c == '\'') quote = c
            } else if (c == quote) {
                quote = null
            }
        }
        return quote != null
    }

    /**
     * True if the tag [name], being ended at [pos], already has a closing tag of its own after [pos]. A closing tag
     * that belongs to an element around it does not count: in `<div><div</div>` the inner tag still needs one.
     */
    fun isClosed(text: CharSequence, pos: Int, name: String, lang: Lang, skipped: (Int) -> Boolean = NOT_SKIPPED): Boolean {
        val ic = caseless(lang)
        var start = pos - 1
        while (start >= 0 && !(text[start] == '<' && !skipped(start))) start--
        if (start < 0) return false
        // Elements of this name around the new tag, and closing tags after it that nothing after it opened.
        var waiting = 0
        var spare = 0
        var inner = 0
        var i = 0
        while (i < text.length) {
            if (i != start && text[i] == '<' && startsTag(text, i)) {
                val closing = text[i + 1] == '/'
                if (sameName(text, if (closing) i + 2 else i + 1, name, ic) && !skipped(i)) {
                    val gt = tagEnd(text, i, skipped)
                    if (gt >= 0 && (closing || text[gt - 1] != '/')) {
                        if (i < start) {
                            if (!closing) waiting++ else if (waiting > 0) waiting--
                        } else {
                            if (!closing) inner++ else if (inner > 0) inner-- else spare++
                        }
                    }
                }
            }
            i++
        }
        return spare > waiting
    }
}
