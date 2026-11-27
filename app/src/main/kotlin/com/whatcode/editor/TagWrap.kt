package com.whatcode.editor

/**
 * The "Wrap in tag" key and its opposite, removing the tag pair around the cursor, in HTML, XML and SVG. Like
 * [MatchJump] it only deals in text and numbers; what it returns is applied as one undo step.
 */
object TagWrap {

    /** A tag as it was asked for: [open] is `<a href="x">` and [close] is `</a>`. */
    class Tag(val name: String, val open: String, val close: String)

    private val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
    )

    /**
     * The tag for what was typed: a name, with attributes if wanted, with or without the angle brackets. The first
     * word is the name of the closing tag. Null if that is not a tag name, or if the tag can hold nothing (such as
     * `<br>` in HTML, or one written with `/>`).
     */
    fun tag(input: String, lang: Lang): Tag? {
        var s = input.trim()
        if (s.startsWith("<")) s = s.substring(1)
        if (s.endsWith(">")) s = s.dropLast(1)
        s = s.trim()
        if (s.isEmpty() || s.endsWith("/") || s[0] == '/') return null
        val name = s.takeWhile { !it.isWhitespace() }
        if (!name[0].isLetter() || !name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' || it == '.' }) {
            return null
        }
        if (lang.name == "HTML" && name.lowercase() in VOID) return null
        // Outside a quoted value, an angle bracket would end the tag early.
        var quote: Char? = null
        for (c in s) {
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '<' || c == '>') {
                return null
            }
        }
        if (quote != null) return null
        return Tag(name, "<$s>", "</$name>")
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\r'

    private fun isWord(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

    private fun blank(text: CharSequence, from: Int, to: Int): Boolean {
        for (i in from until to) if (!isSpace(text[i])) return false
        return true
    }

    /** The line break the text uses near [pos]: that of the line [pos] is on, or of the one before it. */
    private fun lineBreak(text: CharSequence, pos: Int): String {
        var at = EditOps.lineEnd(text, pos)
        if (at >= text.length) at = EditOps.lineStart(text, pos) - 1
        return if (at > 0 && at < text.length && text[at - 1] == '\r') "\r\n" else "\n"
    }

    /** The end of the line [pos] is on, before its line break (LF or CRLF). */
    private fun contentEnd(text: CharSequence, pos: Int): Int {
        val end = EditOps.lineEnd(text, pos)
        return if (end < text.length && end > pos && text[end - 1] == '\r') end - 1 else end
    }

    /**
     * Puts [tag] round the selection, or round the word at the cursor, or leaves an empty pair with the cursor in it.
     * A selection that covers whole lines gets the tags on lines of their own, and its lines are indented by [unit].
     */
    fun wrap(text: CharSequence, selStart: Int, selEnd: Int, tag: Tag, unit: String): TextEdit {
        var a = minOf(selStart, selEnd).coerceIn(0, text.length)
        var b = maxOf(selStart, selEnd).coerceIn(0, text.length)
        if (a == b) {
            while (a > 0 && isWord(text[a - 1])) a--
            while (b < text.length && isWord(text[b])) b++
        } else {
            wholeLines(text, a, b, tag, unit)?.let { return it }
        }
        val inner = text.subSequence(a, b).toString()
        val from = a + tag.open.length
        return TextEdit(a, b, tag.open + inner + tag.close, from, from + inner.length)
    }

    private fun wholeLines(text: CharSequence, a: Int, b: Int, tag: Tag, unit: String): TextEdit? {
        // A selection that ends with a line break stops at the end of that line.
        var last = b
        if (text[last - 1] == '\n') last--
        if (last > a && text[last - 1] == '\r') last--
        val start = EditOps.lineStart(text, a)
        if (last <= a || !blank(text, start, a)) return null
        val end = contentEnd(text, last)
        if (!blank(text, last, end)) return null
        val lines = text.subSequence(start, end).toString().split('\n')
        val filled = lines.filter { l -> !l.all(::isSpace) }
        if (filled.isEmpty()) return null
        val indent = commonIndent(filled)
        val nl = lineBreak(text, start)
        val body = lines.joinToString("\n") { l -> if (l.all(::isSpace)) l else unit + l }
        val head = indent + tag.open + nl
        val insert = head + body + nl + indent + tag.close
        val from = start + head.length
        return TextEdit(start, end, insert, from, from + body.length)
    }

    /** The spaces and tabs that every one of [lines] starts with. */
    private fun commonIndent(lines: List<String>): String {
        var common = lines[0].takeWhile { it == ' ' || it == '\t' }
        for (l in lines) common = common.commonPrefixWith(l)
        return common
    }

    /**
     * Removes the innermost tag pair around the cursor (or around the selection) and keeps what is in it. Content on
     * lines of its own is moved back to the indentation of the tags. Null if there is no pair there.
     */
    fun unwrap(
        text: CharSequence,
        selStart: Int,
        selEnd: Int,
        lang: Lang,
        skipped: (Int) -> Boolean = { false },
    ): TextEdit? {
        val a = minOf(selStart, selEnd)
        val b = maxOf(selStart, selEnd)
        val e = MarkupElements.find(text, lang, skipped = skipped)
            .filter { if (a == b) it.start < a && a < it.end else it.start <= a && b <= it.end }
            .minByOrNull { it.end - it.start } ?: return null
        return ownLines(text, e, a) ?: run {
            val inner = text.subSequence(e.innerStart, e.innerEnd).toString()
            fun moved(p: Int) = e.start + (p.coerceIn(e.innerStart, e.innerEnd) - e.innerStart)
            TextEdit(e.start, e.end, inner, moved(a), moved(b))
        }
    }

    /** The removal when both tags have their lines to themselves, or null if they do not. */
    private fun ownLines(text: CharSequence, e: MarkupElements.Element, cursor: Int): TextEdit? {
        val start = EditOps.lineStart(text, e.start)
        val openEnd = EditOps.lineEnd(text, e.innerStart)
        val closeStart = EditOps.lineStart(text, e.innerEnd)
        val end = contentEnd(text, e.end)
        if (openEnd >= e.innerEnd || closeStart <= openEnd) return null
        if (!blank(text, start, e.start) || !blank(text, e.innerStart, openEnd)) return null
        if (!blank(text, closeStart, e.innerEnd) || !blank(text, e.end, end)) return null
        val first = openEnd + 1
        if (first == closeStart) {
            // Nothing between the tags: their lines go altogether.
            var after = EditOps.lineEnd(text, e.end)
            if (after < text.length) after++
            return TextEdit(start, after, "", start)
        }
        // The content, without the break that ends its last line.
        var last = closeStart - 1
        if (text[last - 1] == '\r') last--
        val lines = text.subSequence(first, maxOf(first, last)).toString().split('\n')
        val filled = lines.filter { l -> !l.all(::isSpace) }
        val indent = text.subSequence(start, e.start).toString()
        val common = if (filled.isEmpty()) "" else commonIndent(filled)
        val dedent = common.length > indent.length && common.startsWith(indent)

        var oldAt = first
        var newAt = start
        var caret = -1
        val out = lines.map { l ->
            val line = if (dedent && l.startsWith(common)) indent + l.substring(common.length) else l
            if (cursor >= oldAt && cursor <= oldAt + l.length) {
                val column = cursor - oldAt
                val removed = l.length - line.length
                val newColumn = when {
                    removed == 0 -> column
                    column >= common.length -> column - removed
                    else -> minOf(column, indent.length)
                }
                caret = newAt + newColumn.coerceIn(0, line.length)
            }
            oldAt += l.length + 1
            newAt += line.length + 1
            line
        }.joinToString("\n")
        if (caret < 0) caret = if (cursor < first) start else start + out.length
        // The line break that followed the closing tag now ends the last line of the content.
        return TextEdit(start, end, out, caret)
    }
}
