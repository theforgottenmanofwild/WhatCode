package com.whatcode.editor.snippets

/**
 * A small set of the CSS abbreviations known from Emmet: `m10` is `margin: 10px;`, `p10-20` is `padding: 10px 20px;`,
 * `w50p` is `width: 50%;`, `c#f` is `color: #ffffff;` and `df` is `display: flex;`. [expand] turns one into the body
 * of a snippet (see [SnippetBody]). Plain logic, no Android.
 *
 * A number takes px, or the unit its letters stand for (p %, e em, r rem, x ex, v vh, w vw); a property that takes a
 * plain number (font-weight, line-height, opacity, z-index, flex) gets none. Values are joined with `-`, and a `-`
 * right after the name is a minus sign. A property with no value is written with a tab stop for it, when its name has
 * two letters or more.
 */
object CssAbbreviation {

    private val PROPERTIES = mapOf(
        "m" to "margin", "mt" to "margin-top", "mr" to "margin-right", "mb" to "margin-bottom", "ml" to "margin-left",
        "p" to "padding", "pt" to "padding-top", "pr" to "padding-right", "pb" to "padding-bottom", "pl" to "padding-left",
        "w" to "width", "h" to "height", "maw" to "max-width", "mah" to "max-height", "miw" to "min-width", "mih" to "min-height",
        "fz" to "font-size", "fw" to "font-weight", "ff" to "font-family", "lh" to "line-height", "c" to "color",
        "bg" to "background", "bgc" to "background-color", "bgi" to "background-image", "bd" to "border",
        "bdrs" to "border-radius", "op" to "opacity", "zi" to "z-index", "t" to "top", "r" to "right", "b" to "bottom",
        "l" to "left", "g" to "gap", "fx" to "flex", "ta" to "text-align", "d" to "display", "pos" to "position",
        "ov" to "overflow", "cur" to "cursor", "trs" to "transition", "trf" to "transform", "bxsh" to "box-shadow",
        "td" to "text-decoration", "tt" to "text-transform", "ls" to "letter-spacing", "va" to "vertical-align",
        "jc" to "justify-content", "ai" to "align-items", "fd" to "flex-direction", "fxw" to "flex-wrap",
        "gtc" to "grid-template-columns", "gtr" to "grid-template-rows", "ct" to "content", "ol" to "outline",
    )

    /** Properties that take a plain number, with no unit. */
    private val PLAIN_NUMBER = setOf("font-weight", "line-height", "opacity", "z-index", "flex")

    /** Whole declarations, written in full. */
    private val KEYWORDS = mapOf(
        "df" to "display: flex;", "dif" to "display: inline-flex;", "dg" to "display: grid;", "db" to "display: block;",
        "dn" to "display: none;", "di" to "display: inline;", "dib" to "display: inline-block;",
        "posa" to "position: absolute;", "posr" to "position: relative;", "posf" to "position: fixed;", "poss" to "position: sticky;",
        "tac" to "text-align: center;", "tal" to "text-align: left;", "tar" to "text-align: right;", "taj" to "text-align: justify;",
        "fwb" to "font-weight: bold;", "fwn" to "font-weight: normal;", "fsi" to "font-style: italic;",
        "jcc" to "justify-content: center;", "jcsb" to "justify-content: space-between;", "jcsa" to "justify-content: space-around;",
        "jcfs" to "justify-content: flex-start;", "jcfe" to "justify-content: flex-end;",
        "aic" to "align-items: center;", "aifs" to "align-items: flex-start;", "aife" to "align-items: flex-end;",
        "fdc" to "flex-direction: column;", "fdr" to "flex-direction: row;", "fxww" to "flex-wrap: wrap;",
        "curp" to "cursor: pointer;", "ovh" to "overflow: hidden;", "ova" to "overflow: auto;",
        "bxzb" to "box-sizing: border-box;", "tdn" to "text-decoration: none;", "ttu" to "text-transform: uppercase;",
        "bdn" to "border: none;", "lsn" to "list-style: none;",
    )

    private val UNITS = mapOf('p' to "%", 'e' to "em", 'r' to "rem", 'x' to "ex", 'v' to "vh", 'w' to "vw")

    /** The snippet body for [abbr], or null if it is not an abbreviation this knows. */
    fun expand(abbr: String): String? {
        if (abbr.isEmpty() || abbr.length > 40) return null
        KEYWORDS[abbr]?.let { return it }
        // The name is the longest run of letters that is a known property.
        var nameEnd = 0
        while (nameEnd < abbr.length && abbr[nameEnd].isLetter() && abbr[nameEnd].isLowerCase()) nameEnd++
        var name = abbr.substring(0, nameEnd)
        while (name.isNotEmpty() && name !in PROPERTIES) name = name.dropLast(1)
        if (name.isEmpty()) return null
        val property = PROPERTIES.getValue(name)
        val rest = abbr.substring(name.length)
        if (rest.isEmpty()) return if (name.length >= 2) "$property: \${1};" else null
        // A value starts with a number, a minus sign or a colour; a word is only taken after another value.
        if (rest[0].isLetter()) return null
        val values = values(rest, property) ?: return null
        return "$property: $values;"
    }

    /** The values written by [rest] for [property], joined by spaces, or null. */
    private fun values(rest: String, property: String): String? {
        if (rest.startsWith('#')) return color(rest)
        val out = ArrayList<String>()
        var i = 0
        while (i < rest.length) {
            // A '-' between values joins them; a '-' at the start of a value, or a second one, is a minus sign.
            if (out.isNotEmpty()) {
                if (rest[i] != '-') return null
                i++
            }
            val neg = i < rest.length && rest[i] == '-'
            if (neg) i++
            val start = i
            while (i < rest.length && (rest[i].isDigit() || rest[i] == '.')) i++
            if (i == start) {
                // A word, such as auto.
                while (i < rest.length && rest[i].isLetter()) i++
                if (i == start || neg) return null
                out.add(rest.substring(start, i))
                continue
            }
            val number = rest.substring(start, i)
            if (number.count { it == '.' } > 1 || number.startsWith('.') || number.endsWith('.')) return null
            val unit = if (i < rest.length && rest[i] in UNITS) UNITS.getValue(rest[i++]) else null
            val sign = if (neg) "-" else ""
            out.add(
                when {
                    unit != null -> "$sign$number$unit"
                    property in PLAIN_NUMBER || number.toDouble() == 0.0 -> "$sign$number"
                    else -> "$sign${number}px"
                }
            )
            if (out.size > 4) return null
        }
        return out.joinToString(" ")
    }

    /** `#f` is `#ffffff`, `#e0` is `#e0e0e0`; three and six digits stay as they are. */
    private fun color(rest: String): String? {
        val hex = rest.substring(1)
        if (hex.isEmpty() || hex.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
        return "#" + when (hex.length) {
            1 -> hex.repeat(6)
            2 -> hex.repeat(3)
            3, 6 -> hex
            else -> return null
        }
    }
}
