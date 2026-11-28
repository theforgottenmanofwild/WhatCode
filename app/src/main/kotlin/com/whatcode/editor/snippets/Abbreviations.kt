package com.whatcode.editor.snippets

/**
 * Finding an HTML or CSS abbreviation (see [HtmlAbbreviation] and [CssAbbreviation]) right before the cursor, for Tab
 * to expand. Plain logic, no Android.
 */
object Abbreviations {

    /** How far back the start of a CSS block is looked for, so Tab stays quick in a very big file. */
    private const val CSS_LOOK_BACK = 20_000

    /** An abbreviation that starts at [start] and ends at the cursor, and the snippet [body] it turns into. */
    class Hit(val start: Int, val body: String)

    /** The languages abbreviations work in. */
    fun isHtml(langName: String) = langName == "HTML"
    fun isCss(langName: String) = langName == "CSS" || langName == "SCSS" || langName == "Less"

    /** The abbreviation before the cursor in a file of the language [langName], or null. Nothing may be selected. */
    fun trigger(t: CharSequence, selStart: Int, selEnd: Int, langName: String): Hit? {
        if (selStart != selEnd || selStart <= 0 || selStart > t.length) return null
        // The abbreviation must end at the cursor: the next character is not part of a word.
        if (selStart < t.length && (t[selStart].isLetterOrDigit() || t[selStart] == '_')) return null
        return when {
            isHtml(langName) -> html(t, selStart)
            isCss(langName) -> css(t, selStart)
            else -> null
        }
    }

    private fun html(t: CharSequence, cursor: Int): Hit? {
        // Back over the abbreviation. White space only ends it outside {text} and [attributes].
        var start = cursor
        var braces = 0
        var brackets = 0
        val limit = maxOf(0, cursor - HtmlAbbreviation.MAX_LENGTH)
        while (start > limit) {
            val c = t[start - 1]
            if (c == '\n' || c == '\r') break
            when (c) {
                '}' -> braces++
                '{' -> if (braces > 0) braces-- else return null
                ']' -> brackets++
                '[' -> if (brackets > 0) brackets-- else return null
            }
            if (braces == 0 && brackets == 0 && (c == ' ' || c == '\t' || c == '<')) break
            start--
        }
        if (start == cursor || braces != 0 || brackets != 0) return null
        var abbr = t.subSequence(start, cursor).toString()
        // Right after the end of a tag, such as <p>ul>li, the abbreviation starts after that tag.
        if (start > 0 && t[start - 1] == '<') {
            val gt = abbr.indexOf('>')
            if (gt < 0) return null
            start += gt + 1
            abbr = abbr.substring(gt + 1)
        } else {
            // The end of a tag that has attributes: <div class="a">ul
            val quoted = maxOf(abbr.lastIndexOf("\">"), abbr.lastIndexOf("'>"))
            if (quoted >= 0 && !abbr.startsWith("[") && abbr.indexOf('[').let { it < 0 || it > quoted }) {
                start += quoted + 2
                abbr = abbr.substring(quoted + 2)
            }
        }
        if (abbr.isEmpty() || insideTag(t, start)) return null
        val body = HtmlAbbreviation.expand(abbr) ?: return null
        return Hit(start, body)
    }

    /** True if [pos] is inside a tag, such as between the attributes of `<a href="x" |`. */
    private fun insideTag(t: CharSequence, pos: Int): Boolean {
        var i = pos - 1
        val limit = maxOf(0, pos - 2000)
        while (i >= limit) {
            when (t[i]) {
                '>' -> return false
                '<' -> return true
            }
            i--
        }
        return false
    }

    private fun css(t: CharSequence, cursor: Int): Hit? {
        var start = cursor
        while (start > 0 && start > cursor - 40) {
            val c = t[start - 1]
            if (c.isLetterOrDigit() || c == '-' || c == '.' || c == '#') start-- else break
        }
        if (start == cursor) return null
        // Only where a declaration can start: after the indentation, a { or a ;
        var b = start - 1
        while (b >= 0 && (t[b] == ' ' || t[b] == '\t')) b--
        if (b >= 0 && t[b] != '\n' && t[b] != '\r' && t[b] != '{' && t[b] != ';') return null
        if (!insideBlock(t, start)) return null
        val body = CssAbbreviation.expand(t.subSequence(start, cursor).toString()) ?: return null
        return Hit(start, body)
    }

    /** True if [pos] is inside a { } block, found by looking back no further than [CSS_LOOK_BACK] characters. */
    private fun insideBlock(t: CharSequence, pos: Int): Boolean {
        var depth = 0
        var i = pos - 1
        val limit = maxOf(0, pos - CSS_LOOK_BACK)
        while (i >= limit) {
            when (t[i]) {
                '}' -> depth++
                '{' -> if (depth == 0) return true else depth--
            }
            i--
        }
        return false
    }
}
