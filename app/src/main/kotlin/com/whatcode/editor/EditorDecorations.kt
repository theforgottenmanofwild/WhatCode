package com.whatcode.editor

/**
 * Scans visible text ranges for inline hex color codes (`#RGB`, `#RRGGBB`, `#AARRGGBB`) and clickable `http`/`https`
 * URLs. Pure Kotlin (unit tested).
 */
object EditorDecorations {

    data class ColorHit(val start: Int, val end: Int, val argb: Int)
    data class UrlHit(val start: Int, val end: Int, val url: String)

    private val HEX_COLOR = Regex("""(?:^|(?<=[^A-Za-z0-9_]))#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})(?![0-9A-Za-z_])""")
    private val URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""")

    fun findHexColors(text: CharSequence, from: Int = 0, to: Int = text.length): List<ColorHit> {
        val start = from.coerceIn(0, text.length)
        val end = to.coerceIn(start, text.length)
        if (end <= start) return emptyList()
        val slice = text.subSequence(start, end)
        val hits = ArrayList<ColorHit>()
        for (m in HEX_COLOR.findAll(slice)) {
            val digits = m.groupValues[1]
            val argb = parseHexArgb(digits) ?: continue
            hits.add(ColorHit(start + m.range.first, start + m.range.last + 1, argb))
        }
        return hits
    }

    fun parseHexArgb(digits: String): Int? = when (digits.length) {
        3 -> {
            val r = Character.digit(digits[0], 16)
            val g = Character.digit(digits[1], 16)
            val b = Character.digit(digits[2], 16)
            if (r < 0 || g < 0 || b < 0) null
            else (0xFF shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
        }
        6 -> {
            val v = digits.toLongOrNull(16) ?: return null
            (0xFF000000L or v).toInt()
        }
        8 -> {
            val v = digits.toLongOrNull(16) ?: return null
            v.toInt()
        }
        else -> null
    }

    fun findUrls(text: CharSequence, from: Int = 0, to: Int = text.length): List<UrlHit> {
        val start = from.coerceIn(0, text.length)
        val end = to.coerceIn(start, text.length)
        if (end <= start) return emptyList()
        val slice = text.subSequence(start, end)
        val hits = ArrayList<UrlHit>()
        for (m in URL_REGEX.findAll(slice)) {
            var urlEnd = m.range.last + 1
            while (urlEnd > m.range.first && slice[urlEnd - 1] in charArrayOf('.', ',', ';', ':')) {
                urlEnd--
            }
            if (urlEnd > m.range.first + 8) {
                hits.add(UrlHit(start + m.range.first, start + urlEnd, slice.subSequence(m.range.first, urlEnd).toString()))
            }
        }
        return hits
    }

    /** Returns the URL surrounding [offset] in [text], or null if cursor/tap is not inside one. */
    fun urlAt(text: CharSequence, offset: Int): String? {
        val from = (offset - 2048).coerceAtLeast(0)
        val to = (offset + 2048).coerceAtMost(text.length)
        return findUrls(text, from, to).firstOrNull { offset in it.start..it.end }?.url
    }
}
