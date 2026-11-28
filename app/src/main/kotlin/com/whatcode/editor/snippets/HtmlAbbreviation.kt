package com.whatcode.editor.snippets

/**
 * A small set of the abbreviations known from Emmet, for HTML: `ul>li.item$*3` turns into a list of three items.
 * [expand] turns an abbreviation into the body of a snippet (see [SnippetBody]), with a tab stop in each empty
 * element and each empty attribute, so Tab goes from one to the next. Plain logic, no Android.
 *
 *  - `tag`, `tag#id`, `tag.class.other`, `tag[attr=value other="two words"]`, `tag{text}`; with no tag name the tag is
 *    a div, or the child that suits the parent (li in a list, tr in a table, td in a row, option in a select).
 *  - `a>b` puts b in a, `a+b` puts b after a, and `a>b^c` climbs back up a level for c.
 *  - `li*3` makes three, and `$` in a name, a value or a text is the number of the copy (`$$` is 01, 02…).
 *  - `(a+b)*2` repeats a group, and `!` is a whole HTML page.
 *
 * Only tag names HTML has, and custom ones with a hyphen, are taken, so a word in a sentence is never turned into a tag.
 */
object HtmlAbbreviation {

    /** The longest abbreviation that is read, and the most elements it may make. */
    const val MAX_LENGTH = 300
    const val MAX_ELEMENTS = 500
    private const val MAX_REPEAT = 100

    private val TAGS = (
        "a abbr address area article aside audio b base bdi bdo blockquote body br button canvas caption cite code col " +
            "colgroup data datalist dd del details dfn dialog div dl dt em embed fieldset figcaption figure footer form h1 " +
            "h2 h3 h4 h5 h6 head header hgroup hr html i iframe img input ins kbd label legend li link main map mark menu " +
            "meta meter nav noscript object ol optgroup option output p param picture pre progress q rp rt ruby s samp " +
            "script search section select slot small source span strong style sub summary sup table tbody td template " +
            "textarea tfoot th thead time title tr track u ul var video wbr svg path circle rect g line polyline polygon"
        ).split(' ').toSet()

    private val VOID = "area base br col embed hr img input link meta param source track wbr".split(' ').toSet()

    /** Attributes an element gets when none of them is written. */
    private val DEFAULT_ATTRS = mapOf(
        "a" to listOf("href" to ""),
        "img" to listOf("src" to "", "alt" to ""),
        "input" to listOf("type" to "text"),
        "label" to listOf("for" to ""),
        "form" to listOf("action" to ""),
        "link" to listOf("rel" to "stylesheet", "href" to ""),
        "iframe" to listOf("src" to ""),
        "source" to listOf("src" to ""),
        "meta" to listOf("name" to "", "content" to ""),
    )

    /** The page `!` makes. */
    private const val PAGE = "<!DOCTYPE html>\n<html lang=\"\${1:en}\">\n<head>\n\t<meta charset=\"UTF-8\">\n" +
        "\t<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n\t<title>\${2:Document}</title>\n" +
        "</head>\n<body>\n\t\$0\n</body>\n</html>"

    private class Node(
        var tag: String?,
        val id: String?,
        val classes: List<String>,
        val attrs: List<Pair<String, String?>>,
        val text: String?,
    ) {
        val children = ArrayList<Node>()
        var index = 0

        fun copy(number: Int?): Node {
            fun n(s: String?) = if (number == null || s == null) s else numbered(s, number)
            val c = Node(tag?.let { n(it) }, n(id), classes.map { n(it)!! }, attrs.map { it.first to n(it.second) }, n(text))
            c.index = number ?: index
            for (k in children) c.children.add(k.copy(number))
            return c
        }
    }

    private class Fail : Exception()

    /** The snippet body for [abbr], or null if it is not an abbreviation this understands. */
    fun expand(abbr: String): String? {
        if (abbr.isEmpty() || abbr.length > MAX_LENGTH) return null
        if (abbr == "!") return PAGE
        return try {
            val p = Parser(abbr)
            val nodes = p.top()
            if (p.count > MAX_ELEMENTS || nodes.isEmpty()) return null
            render(nodes)
        } catch (_: Fail) {
            null
        }
    }

    /** Puts [n] in place of each run of `$` in [s], with zeros in front up to the length of the run. */
    private fun numbered(s: String, n: Int): String {
        if ('$' !in s) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] != '$') {
                sb.append(s[i++])
                continue
            }
            var j = i
            while (j < s.length && s[j] == '$') j++
            sb.append(n.toString().padStart(j - i, '0'))
            i = j
        }
        return sb.toString()
    }

    private class Parser(val s: String) {
        var pos = 0
        var count = 0

        fun top(): List<Node> {
            val out = ArrayList<Node>()
            var (nodes, climb) = seq()
            out.addAll(nodes)
            // Climbing past the top goes on at the top.
            while (climb > 0 && pos < s.length) {
                val more = seq()
                out.addAll(more.first)
                climb = more.second
            }
            if (pos != s.length) throw Fail()
            return out
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null

        /** A run of terms joined by `+`, `>` and `^`. Returns the nodes and how many levels are still to climb. */
        private fun seq(): Pair<List<Node>, Int> {
            val out = ArrayList<Node>()
            var last = term()
            out.addAll(last)
            while (true) {
                when (peek()) {
                    '+' -> {
                        pos++
                        last = term()
                        out.addAll(last)
                    }
                    '>' -> {
                        pos++
                        val (kids, climb) = seq()
                        for (parent in last) attach(parent, kids)
                        if (climb > 1) return out to climb - 1
                        if (climb == 1) {
                            last = term()
                            out.addAll(last)
                        }
                    }
                    '^' -> {
                        var k = 0
                        while (peek() == '^') {
                            pos++
                            k++
                        }
                        return out to k
                    }
                    else -> return out to 0
                }
            }
        }

        /** Puts copies of [kids] into [parent]; a `$` left in them is the number of the parent's copy. */
        private fun attach(parent: Node, kids: List<Node>) {
            for (k in kids) {
                count += size(k)
                if (count > MAX_ELEMENTS) throw Fail()
                parent.children.add(k.copy(if (parent.index > 0) parent.index else null))
            }
        }

        private fun size(n: Node): Int = 1 + n.children.sumOf { size(it) }

        /** One element or a group in brackets, repeated if `*N` follows. */
        private fun term(): List<Node> {
            val base: List<Node> = if (peek() == '(') {
                pos++
                val (inner, _) = seq()
                if (peek() != ')') throw Fail()
                pos++
                inner
            } else {
                listOf(element())
            }
            if (peek() != '*') return base
            pos++
            val start = pos
            while (peek()?.isDigit() == true) pos++
            if (pos == start || pos - start > 3) throw Fail()
            val times = s.substring(start, pos).toInt()
            if (times < 1 || times > MAX_REPEAT) throw Fail()
            val out = ArrayList<Node>()
            for (i in 1..times) for (b in base) out.add(b.copy(i))
            count += out.sumOf { size(it) }
            if (count > MAX_ELEMENTS) throw Fail()
            return out
        }

        private fun element(): Node {
            val tag = name()
            if (tag != null && tag !in TAGS && '-' !in tag) throw Fail()
            var id: String? = null
            val classes = ArrayList<String>()
            val attrs = ArrayList<Pair<String, String?>>()
            var text: String? = null
            loop@ while (true) {
                when (peek()) {
                    '#' -> {
                        pos++
                        id = name() ?: throw Fail()
                    }
                    '.' -> {
                        pos++
                        classes.add(name() ?: throw Fail())
                    }
                    '[' -> {
                        pos++
                        attrs.addAll(attributes())
                    }
                    '{' -> {
                        if (text != null) throw Fail()
                        pos++
                        text = braced()
                    }
                    else -> break@loop
                }
            }
            if (tag == null && id == null && classes.isEmpty() && attrs.isEmpty() && text == null) throw Fail()
            count++
            return Node(tag, id, classes, attrs, text)
        }

        /** A tag, id or class name, with the `$` signs of numbering. */
        private fun name(): String? {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] in "-_:$@")) pos++
            return if (pos == start) null else s.substring(start, pos)
        }

        private fun braced(): String {
            val sb = StringBuilder()
            while (pos < s.length && s[pos] != '}') sb.append(s[pos++])
            if (pos >= s.length) throw Fail()
            pos++
            return sb.toString()
        }

        private fun attributes(): List<Pair<String, String?>> {
            val out = ArrayList<Pair<String, String?>>()
            while (true) {
                while (peek() == ' ') pos++
                if (peek() == ']') {
                    pos++
                    return out
                }
                val key = name() ?: throw Fail()
                if (peek() != '=') {
                    out.add(key to null)
                    continue
                }
                pos++
                val q = peek()
                val value = if (q == '"' || q == '\'') {
                    pos++
                    val start = pos
                    while (pos < s.length && s[pos] != q) pos++
                    if (pos >= s.length) throw Fail()
                    s.substring(start, pos).also { pos++ }
                } else {
                    val start = pos
                    while (pos < s.length && s[pos] != ' ' && s[pos] != ']') pos++
                    s.substring(start, pos)
                }
                out.add(key to value)
            }
        }
    }

    // ------------------------------------------------------------------ writing it out

    private fun render(nodes: List<Node>): String {
        var stop = 0
        fun next() = "\${${++stop}}"
        fun esc(v: String) = v.replace("\\", "\\\\").replace("$", "\\$").replace("}", "\\}")

        fun open(n: Node, tag: String): String {
            val sb = StringBuilder("<").append(tag)
            if (n.id != null) sb.append(" id=\"").append(esc(n.id)).append('"')
            if (n.classes.isNotEmpty()) sb.append(" class=\"").append(esc(n.classes.joinToString(" "))).append('"')
            val written = if (n.attrs.isEmpty()) DEFAULT_ATTRS[tag] ?: emptyList() else n.attrs.map { it.first to (it.second ?: "") }
            for ((k, v) in written) {
                sb.append(' ').append(k).append("=\"").append(if (v.isEmpty()) next() else esc(v)).append('"')
            }
            return sb.append('>').toString()
        }

        fun write(n: Node, parentTag: String?, sb: StringBuilder, indent: String) {
            val tag = n.tag ?: when (parentTag) {
                "ul", "ol", "menu" -> "li"
                "table", "tbody", "thead", "tfoot" -> "tr"
                "tr" -> "td"
                "select", "datalist", "optgroup" -> "option"
                else -> "div"
            }
            sb.append(indent).append(open(n, tag))
            if (tag in VOID && n.children.isEmpty() && n.text == null) return
            if (n.children.isEmpty()) {
                sb.append(if (n.text != null) esc(n.text) else next())
            } else {
                if (n.text != null) sb.append('\n').append(indent).append('\t').append(esc(n.text))
                for (c in n.children) {
                    sb.append('\n')
                    write(c, tag, sb, "$indent\t")
                }
                sb.append('\n').append(indent)
            }
            sb.append("</").append(tag).append('>')
        }

        val sb = StringBuilder()
        for ((i, n) in nodes.withIndex()) {
            if (i > 0) sb.append('\n')
            write(n, null, sb, "")
        }
        return sb.toString()
    }
}
