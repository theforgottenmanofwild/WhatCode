package com.whatcodeeditor

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/** Marker subclass so we can remove only our own highlighting spans. */
class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

class Lang(
    val name: String,
    val keywords: Set<String> = emptySet(),
    val lineComment: String? = null,
    val blockComment: Boolean = false,
    val markup: Boolean = false,
    val singleQuotes: Boolean = true
)

object Highlighter {
    private val KEYWORD = Color.parseColor("#569CD6")
    private val NUMBER = Color.parseColor("#B5CEA8")
    private val STRING = Color.parseColor("#CE9178")
    private val COMMENT = Color.parseColor("#6A9955")

    private const val MAX_CHARS = 150_000

    private fun words(s: String): Set<String> = s.split(" ").filter { it.isNotEmpty() }.toSet()

    private val C_LIKE = words(
        "if else for while do switch case break continue return class struct enum interface " +
            "fun func function fn val var let const mut new null nil true false this self void int long " +
            "float double char bool boolean string import package use public private protected pub static " +
            "final abstract override try catch finally throw throws in is as when object extends " +
            "implements async await yield default typeof instanceof defer go chan range impl trait " +
            "match mod crate loop where lateinit open data sealed companion init"
    )
    private val PYTHON = words(
        "def class if elif else for while return import from as with try except finally raise " +
            "lambda None True False and or not in is pass break continue yield global nonlocal " +
            "async await del assert self"
    )
    private val SHELL = words(
        "if then else elif fi for while do done case esac function in echo export local return exit"
    )
    private val JSON = words("true false null")

    private val PLAIN = Lang("Text")

    private val byExt: Map<String, Lang> = HashMap<String, Lang>().apply {
        val cl = Lang("C-like", C_LIKE, "//", true)
        for (e in "kt kts java js jsx ts tsx c h cpp hpp cc cs go rs swift dart php scala".split(" ")) put(e, cl)
        val py = Lang("Python", PYTHON, "#")
        put("py", py)
        val sh = Lang("Shell/Config", SHELL, "#")
        for (e in "sh bash zsh rb yml yaml toml conf ini".split(" ")) put(e, sh)
        val mk = Lang("Markup", markup = true, singleQuotes = false)
        for (e in "html htm xml svg".split(" ")) put(e, mk)
        put("json", Lang("JSON", JSON, singleQuotes = false))
    }

    fun languageFor(fileName: String?): Lang {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        return byExt[ext] ?: PLAIN
    }

    private val NUM_RE = Regex("""\b\d+(?:\.\d+)?\b""")
    private val TAG_RE = Regex("""</?[A-Za-z][\w:-]*""")
    private val cache = HashMap<String, Regex>()

    private fun keywordRegex(l: Lang): Regex =
        cache.getOrPut("kw:" + l.name) { Regex("""\b(?:""" + l.keywords.joinToString("|") + """)\b""") }

    /** One combined regex so that comment/string boundaries resolve left-to-right. */
    private fun literalRegex(l: Lang): Regex = cache.getOrPut("lit:" + l.name) {
        val parts = ArrayList<String>()
        if (l.markup) parts.add("""<!--[\s\S]*?-->""")
        if (l.blockComment) parts.add("""/\*[\s\S]*?\*/""")
        if (l.lineComment != null) parts.add(Regex.escape(l.lineComment) + """[^\n]*""")
        parts.add(""""(?:\\.|[^"\\\n])*"""")
        if (l.singleQuotes) parts.add("""'(?:\\.|[^'\\\n])*'""")
        Regex(parts.joinToString("|"))
    }

    fun apply(e: Editable, lang: Lang) {
        for (s in e.getSpans(0, e.length, SyntaxSpan::class.java)) e.removeSpan(s)
        if (lang === PLAIN || e.length > MAX_CHARS) return

        val text = e.toString()
        val covered = BooleanArray(text.length)

        fun paint(start: Int, end: Int, color: Int) =
            e.setSpan(SyntaxSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 1) strings and comments (they win over keywords/numbers)
        for (m in literalRegex(lang).findAll(text)) {
            val first = text[m.range.first]
            val color = if (first == '"' || first == '\'') STRING else COMMENT
            paint(m.range.first, m.range.last + 1, color)
            for (i in m.range) covered[i] = true
        }
        // 2) keywords, numbers, tags — only outside strings/comments
        if (lang.keywords.isNotEmpty()) {
            for (m in keywordRegex(lang).findAll(text))
                if (!covered[m.range.first]) paint(m.range.first, m.range.last + 1, KEYWORD)
        }
        for (m in NUM_RE.findAll(text))
            if (!covered[m.range.first]) paint(m.range.first, m.range.last + 1, NUMBER)
        if (lang.markup) {
            for (m in TAG_RE.findAll(text))
                if (!covered[m.range.first]) paint(m.range.first, m.range.last + 1, KEYWORD)
        }
    }
}
