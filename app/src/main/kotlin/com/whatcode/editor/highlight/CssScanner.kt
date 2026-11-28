package com.whatcode.editor.highlight

import com.whatcode.editor.Lang
import com.whatcode.editor.Tokens

/** Reads CSS, SCSS and Less. */
internal object CssScanner {
    private fun cssRules(lang: Lang): Compiled = ruleCache.getOrPut("css:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        specs.add("""/\*[\s\S]*?(?:\*/|\z)""" to COMMENT)
        if (lang.lineComments.isNotEmpty()) specs.add("""//[^\n]*""" to COMMENT)
        specs.add("""url\((?:"[^"\n]*"|'[^'\n]*'|[^)\n]*)\)""" to S_URL)
        specs.add(quoted('"') + "|" + quoted('\'') to STRING)
        specs.add("""@[A-Za-z-]+""" to S_AT)
        specs.add("""-{0,2}[A-Za-z][\w-]*(?=\s*:\s*[^\s{][^;{}]*[;}])""" to S_PROP)
        specs.add("""--[\w-]+""" to PROPERTY)
        specs.add("""[$][\w-]+""" to PROPERTY)
        specs.add("""#[\w-]+""" to S_HASH)
        specs.add("""(?<![\w-])[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?(?:%|[A-Za-z]+)?""" to NUMBER)
        specs.add("""\.[A-Za-z_-][\w-]*""" to S_CLASS)
        specs.add("""::?[A-Za-z-]+(?:\([^)\n]*\))?""" to S_PSEUDO)
        specs.add("""!\s*important""" to KEYWORD)
        specs.add("""[{};]""" to S_PUNCT)
        specs.add("""-?[A-Za-z_][\w-]*""" to S_IDENT)
        compile(specs)
    }

    private fun isHexColor(text: String, a: Int, b: Int): Boolean {
        val len = b - a
        if (len != 3 && len != 4 && len != 6 && len != 8) return false
        for (i in a until b) {
            val c = text[i]
            if (!(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F')) return false
        }
        return true
    }

    /** See [CodeScanner.scan] for [origin], [marks] and [window]. */
    fun scan(text: String, from: Int, to: Int, lang: Lang, out: Tokens, origin: Int = from, marks: Marks? = null, window: Boolean = false) {
        val rules = cssRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        if (window || from > origin) m.useTransparentBounds(true)
        var inDecl = false // after a property name, up to the ; or }
        var inAt = false // after an @rule, up to the { or ;
        marks?.ok = { !inDecl && !inAt }
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            if (marks != null && !marks.before(s)) return
            marks?.after(e)
            when (val kind = rules.kinds[g - 1]) {
                S_PROP -> {
                    out.add(s, e, PROPERTY)
                    inDecl = true
                }
                S_AT -> {
                    out.add(s, e, CONTROL)
                    inAt = true
                }
                S_URL -> {
                    out.add(s, s + 3, FUNCTION)
                    out.add(s + 4, e - 1, STRING)
                }
                S_HASH -> {
                    if (!inDecl) out.add(s, e, SELECTOR) else if (isHexColor(text, s + 1, e)) out.add(s, e, NUMBER)
                }
                S_CLASS -> if (!inDecl) out.add(s, e, SELECTOR)
                S_PSEUDO -> if (!inDecl && !inAt) out.add(s, e, SELECTOR)
                S_PUNCT -> {
                    inDecl = false
                    inAt = false
                }
                S_IDENT -> {
                    if (nextChar(text, e, to) == '(') out.add(s, e, FUNCTION) else if (!inDecl && !inAt) out.add(s, e, SELECTOR)
                }
                else -> out.add(s, e, kind)
            }
        }
        marks?.finish(to)
    }
}
