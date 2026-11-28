package com.whatcode.editor.highlight

import com.whatcode.editor.Tokens
import java.util.Locale

/** Reads HTML, XML and SVG. The text of a script or style element is handed to the code and CSS scanners. */
internal object MarkupScanner {
    private val markupRules: Compiled by lazy {
        compile(
            listOf(
                """<!--[\s\S]*?(?:-->|\z)""" to COMMENT,
                """<!\[CDATA\[[\s\S]*?(?:\]\]>|\z)""" to STRING,
                """<![A-Za-z][^>]*>|<\?[\s\S]*?(?:\?>|\z)""" to KEYWORD,
                """</?[A-Za-z][\w:.-]*""" to S_TAGOPEN,
                """/?>""" to S_TAGEND,
                """&(?:[A-Za-z][A-Za-z0-9]*|#[0-9]+|#[xX][0-9a-fA-F]+);""" to S_ENTITY,
                "\"[^\"]*\"|'[^']*'" to S_TSTR,
                """[A-Za-z_:@][\w:.@\-\[\]()*#]*""" to S_ATTR,
            )
        )
    }

    fun scan(text: String, from: Int, to: Int, out: Tokens) {
        val rules = markupRules
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        var inTag = false
        var raw: String? = null
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_TAGOPEN -> {
                    out.add(s, e, KEYWORD)
                    inTag = true
                    val closing = text[s + 1] == '/'
                    val name = text.substring(if (closing) s + 2 else s + 1, e).lowercase(Locale.ROOT)
                    raw = if (!closing && (name == "script" || name == "style")) name else null
                }
                S_TAGEND -> if (inTag) {
                    out.add(s, e, KEYWORD)
                    inTag = false
                    val rt = raw
                    raw = null
                    if (rt != null && text[s] != '/') {
                        // The text of a script or style element is another language, up to its closing tag.
                        val close = text.indexOf("</$rt", e, ignoreCase = true)
                        val end = if (close < 0) to else minOf(close, to)
                        if (end > e) {
                            if (rt == "script") CodeScanner.scan(text, e, end, Languages.JS, out) else CssScanner.scan(text, e, end, Languages.CSS, out)
                        }
                        m.region(end, to)
                    }
                }
                S_TSTR -> if (inTag) out.add(s, e, STRING) else m.region(s + 1, to)
                S_ATTR -> if (inTag) out.add(s, e, PROPERTY)
                S_ENTITY -> if (!inTag) out.add(s, e, CONSTANT)
                else -> out.add(s, e, kind)
            }
        }
    }
}
