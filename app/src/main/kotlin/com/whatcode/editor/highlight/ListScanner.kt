package com.whatcode.editor.highlight

import com.whatcode.editor.Family
import com.whatcode.editor.Lang
import com.whatcode.editor.Tokens

/** Reads the line-based formats: YAML, TOML and INI, and Markdown. */
internal object ListScanner {
    private fun listRules(lang: Lang): Compiled = ruleCache.getOrPut("list:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        when (lang.family) {
            Family.YAML -> {
                specs.add("""(?<![^\s])#[^\n]*""" to COMMENT)
                specs.add("""(?m:^(?:---|\.\.\.)(?=\s|\z))""" to KEYWORD)
                specs.add(
                    """(?m:^[ \t]*(?:-[ \t]+)*)(?:"[^"\n]*"|'[^'\n]*'|[^\s#:'"\-\[\]{},&*!|>%@`][^:#\n]*?)(?=[ \t]*:(?:[ \t]|\r|\n|\z))""" to S_KEYLEAD
                )
                specs.add(quoted('"') + "|" + quoted('\'') to S_QSTR)
                specs.add("""(?<![\w])[&*][A-Za-z0-9_.\-]+""" to TYPE)
                specs.add("""(?<![\w.\-])[+-]?(?:0x[0-9a-fA-F]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?)(?![\w.\-])""" to NUMBER)
                specs.add("""(?<![\w.\-])(?:[Tt]rue|[Ff]alse|TRUE|FALSE|[Nn]ull|NULL|[Yy]es|[Nn]o|YES|NO|[Oo]n|[Oo]ff|~)(?![\w.\-])""" to KEYWORD)
            }
            Family.INI -> {
                specs.add("""(?m:^[ \t]*[#;][^\n]*)""" to COMMENT)
                if (lang.trailingHash) specs.add("""(?<=\s)#[^\n]*""" to COMMENT)
                specs.add("""(?m:^[ \t]*)\[\[?[^\]\n]*\]\]?""" to S_TYPELEAD)
                specs.add("""(?m:^[ \t]*)[A-Za-z_][\w.\-]*(?=[ \t]*[=:])""" to S_KEYLEAD)
                if (lang.trailingHash) specs.add("\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)|'''[\\s\\S]*?(?:'''|\\z)" to STRING)
                specs.add(quoted('"') + "|" + """'[^'\n]*(?:'|(?=\n)|\z)""" to S_QSTR)
                specs.add("""(?<![\w.\-])[+-]?(?:0x[0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?)(?![\w.\-])""" to NUMBER)
                specs.add("""(?<![\w.\-])(?:true|false)(?![\w.\-])""" to KEYWORD)
            }
            else -> {
                specs.add("""(?m:^[ \t]*(?:```|~~~)[^\n]*\n)[\s\S]*?(?:(?m:^[ \t]*(?:```|~~~)[^\n]*$)|\z)""" to STRING)
                specs.add("""<!--[\s\S]*?(?:-->|\z)""" to COMMENT)
                specs.add("""(?m:^#{1,6}[ \t].*$)""" to KEYWORD)
                specs.add("""(?m:^[ \t]*(?:-{3,}|\*{3,}|_{3,})[ \t]*$)""" to CONTROL)
                specs.add("""(?m:^[ \t]*>[^\n]*)""" to COMMENT)
                specs.add("""(?m:^[ \t]*(?:[-*+]|\d+[.)])(?=[ \t]))""" to CONTROL)
                specs.add("""`[^`\n]+`""" to STRING)
                specs.add("""!?\[[^\]\n]*\](?:\([^)\n]*\)|\[[^\]\n]*\])?""" to CONSTANT)
                specs.add("""<https?://[^>\s]+>|https?://[^\s)>\]]+""" to CONSTANT)
                specs.add("""\*\*[^*\n]+\*\*|__[^_\n]+__""" to TYPE)
                specs.add("""(?<![*\w])\*[^*\s][^*\n]*\*(?![*\w])|(?<![_\w])_[^_\s][^_\n]*_(?![_\w])""" to TYPE)
            }
        }
        compile(specs)
    }

    /** Skips the spaces and the "- " of list items at the start of a key. */
    private fun skipLead(text: String, s: Int, e: Int): Int {
        var i = s
        while (i < e && (text[i] == ' ' || text[i] == '\t')) i++
        while (i + 1 < e && text[i] == '-' && (text[i + 1] == ' ' || text[i + 1] == '\t')) {
            i += 2
            while (i < e && (text[i] == ' ' || text[i] == '\t')) i++
        }
        return i
    }

    /** A quote starts a string only after one of : - [ , { = or at the start of a line, so a word like don't is left alone. */
    private fun stringStartOk(text: String, s: Int, from: Int): Boolean {
        var p = s - 1
        while (p >= from && (text[p] == ' ' || text[p] == '\t')) p--
        if (p < from) return true
        val c = text[p]
        return c == '\n' || c == '\r' || c == ':' || c == '-' || c == '[' || c == ',' || c == '{' || c == '='
    }

    /** See [CodeScanner.scan] for [origin], [marks] and [window]. */
    fun scan(text: String, from: Int, to: Int, lang: Lang, out: Tokens, origin: Int = from, marks: Marks? = null, window: Boolean = false) {
        val rules = listRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        if (window || from > origin) m.useTransparentBounds(true)
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            if (marks != null && !marks.before(s)) return
            marks?.after(e)
            when (val kind = rules.kinds[g - 1]) {
                S_KEYLEAD -> out.add(skipLead(text, s, e), e, PROPERTY)
                S_TYPELEAD -> out.add(skipLead(text, s, e), e, TYPE)
                S_QSTR -> if (stringStartOk(text, s, origin)) out.add(s, e, STRING) else { m.region(s + 1, to); marks?.after(s + 1) }
                else -> out.add(s, e, kind)
            }
        }
        marks?.finish(to)
    }
}
