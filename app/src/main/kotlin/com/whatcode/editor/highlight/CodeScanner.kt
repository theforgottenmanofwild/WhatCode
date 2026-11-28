package com.whatcode.editor.highlight

import com.whatcode.editor.Lang
import com.whatcode.editor.Tokens
import java.util.Locale
import java.util.regex.Pattern

/** Reads source code (everything of the CODE family): comments, strings, numbers, keywords, types and calls. */
internal object CodeScanner {
    private const val NUM =
        """0[xX][0-9a-fA-F_]+[lLuUnN]*|0[bB][01_]+[lLuUnN]*|\d[\d_]*(?:\.\d[\d_]*)?(?:[eE][+-]?\d+)?[fFdDlLuUn]*"""

    private const val WORD = """[A-Za-z_$\u0080-\uFFFF][\w$\u0080-\uFFFF]*"""

    private fun codeRules(lang: Lang): Compiled = ruleCache.getOrPut("code:" + lang.name) {
        val specs = ArrayList<Pair<String, Int>>()
        if (lang.lineComments.isNotEmpty()) {
            specs.add("(?:" + lang.lineComments.joinToString("|") { Pattern.quote(it) } + ")[^\\n]*" to COMMENT)
        }
        if (lang.blockComments) specs.add("""/\*[\s\S]*?(?:\*/|\z)""" to COMMENT)

        val strings = ArrayList<String>()
        if (lang.tripleQuotes) {
            for (q in listOf('"', '\'')) {
                if (q in lang.quotes) {
                    val t = q.toString().repeat(3)
                    strings.add("$t[\\s\\S]*?(?:$t|\\z)")
                }
            }
        }
        for (q in lang.quotes) {
            strings.add(if (q == '`') """`[^`\\]*+(?:\\[\s\S][^`\\]*+)*+(?:`|\z)""" else quoted(q))
        }
        if (strings.isNotEmpty()) {
            val all = strings.joinToString("|")
            specs.add((if (lang.prefixedStrings) "(?:[rRbBfFuU]{1,2})?(?:$all)" else all) to S_STRING)
        }
        if (lang.regexLiterals) {
            specs.add("""/(?![*/])(?:\\.|\[(?:\\.|[^\]\\\n])*\]|[^/\\\n\[])+/[a-z]*""" to S_REGEX)
        }
        specs.add(NUM to NUMBER)
        specs.addAll(lang.extras)
        specs.add(WORD to S_WORD)
        compile(specs)
    }

    private val REGEX_PRE_WORDS = words("return typeof instanceof case do else yield await void delete throw in of new")

    /** True if a / at [s] can start a regular expression, going by what comes before it. */
    private fun regexAllowed(text: String, s: Int, from: Int): Boolean {
        var p = s - 1
        while (p >= from && text[p].isWhitespace()) p--
        if (p < from) return true
        val c = text[p]
        if (c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '`') return false
        if (c.isLetterOrDigit() || c == '_' || c == '$') {
            var b = p
            while (b > from && (text[b - 1].isLetterOrDigit() || text[b - 1] == '_' || text[b - 1] == '$')) b--
            return text.substring(b, p + 1) in REGEX_PRE_WORDS
        }
        if (c == '+' || c == '-') return p > from && text[p - 1] != c
        return true
    }

    /** The last character before [s] that is not white space, or a NUL if there is none. */
    private fun prevSignificant(text: String, s: Int, from: Int): Char {
        var p = s - 1
        while (p >= from && text[p].isWhitespace()) p--
        return if (p >= from) text[p] else '\u0000'
    }

    private fun onlySpaces(text: String, a: Int, b: Int): Boolean {
        for (i in a until b) if (!text[i].isWhitespace()) return false
        return true
    }

    private fun softOk(c: Char): Boolean = c.isLetter() || c == '_' || c == '"' || c == '\'' || c == '`' || c == '{' || c == '*'

    private fun isConstantName(w: String): Boolean {
        if (w.length < 2) return false
        var letter = false
        for (ch in w) {
            if (ch.isLetter()) {
                if (!ch.isUpperCase()) return false
                letter = true
            } else if (!ch.isDigit() && ch != '_') {
                return false
            }
        }
        return letter
    }

    fun scan(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        val rules = codeRules(lang)
        val n = rules.kinds.size
        val m = rules.pattern.matcher(text)
        m.region(from, to)
        var prevWord = ""
        var prevEnd = -1
        while (m.find()) {
            var g = 1
            while (g <= n && m.start(g) < 0) g++
            if (g > n) continue
            val s = m.start()
            val e = m.end()
            when (val kind = rules.kinds[g - 1]) {
                S_STRING -> {
                    val color = if (lang.keys && nextChar(text, e, to) == ':') PROPERTY else STRING
                    out.add(s, e, color)
                }
                S_REGEX -> {
                    if (regexAllowed(text, s, from)) {
                        out.add(s, e, REGEX)
                    } else {
                        m.region(s + 1, to) // it was a division sign
                    }
                }
                S_WORD -> {
                    val word = text.substring(s, e)
                    val key = if (lang.caseInsensitive) word.lowercase(Locale.ROOT) else word
                    val afterDot = s > from && text[s - 1] == '.' && !(s >= from + 3 && text[s - 2] == '.' && text[s - 3] == '.')
                    val next = nextChar(text, e, to)
                    val prev = if (prevEnd >= 0 && onlySpaces(text, prevEnd, s)) prevWord else ""
                    var color = 0
                    if (!afterDot) {
                        color = when {
                            key in lang.controls -> CONTROL
                            key in lang.keywords || key in lang.constants -> KEYWORD
                            key in lang.soft && softOk(next) -> KEYWORD
                            else -> 0
                        }
                    }
                    if (color == 0 && key in lang.types) color = TYPE
                    if (color == 0) {
                        val cap = lang.capitalizedTypes && word[0].isUpperCase()
                        color = when {
                            lang.objectKeys && !afterDot && next == ':' && prevSignificant(text, s, from).let { it == '{' || it == ',' } -> PROPERTY
                            prev in lang.defWords && (next == '(' || next == '<' || next == '[') -> FUNCTION
                            prev in lang.typeWords -> TYPE
                            afterDot && lang.calls && next == '(' -> FUNCTION
                            lang.capitalizedTypes && prev == "new" -> TYPE
                            cap -> if (isConstantName(word)) CONSTANT else TYPE
                            lang.capitalizedTypes && !afterDot && isConstantName(word) -> CONSTANT
                            lang.calls && next == '(' -> FUNCTION
                            else -> 0
                        }
                    }
                    if (color != 0) out.add(s, e, color)
                    prevWord = key
                    prevEnd = e
                }
                else -> out.add(s, e, kind)
            }
        }
    }
}
