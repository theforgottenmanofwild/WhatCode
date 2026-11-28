package com.whatcode.editor

import android.text.Editable
import android.text.Spanned
import com.whatcode.editor.highlight.Languages
import com.whatcode.editor.highlight.ScanStates
import com.whatcode.editor.highlight.scanSkippingLong
import com.whatcode.editor.highlight.scanText
import com.whatcode.editor.highlight.isLiteralMarker

/**
 * Colours the text of the editor. This is the entry point; the languages are described in `highlight/Languages.kt`
 * and each family of language has its own scanner in the `highlight` package.
 */
object Highlighter {
    /** Files longer than this are shown without highlighting. */
    const val MAX_CHARS = 8_000_000

    /** True if [marker], the colour [tokenize] gave a piece, is that of a string, a comment or a regular expression. */
    fun isLiteral(marker: Int): Boolean = isLiteralMarker(marker)

    /** Every language the editor knows, by name, for lists such as the key layouts. Text comes last. */
    val languages: List<Lang> get() = Languages.languages

    fun languageFor(fileName: String?): Lang = Languages.languageFor(fileName)

    // ------------------------------------------------------------------ colouring the editor

    /** Takes every colour off [e]. */
    fun clear(e: Editable) {
        for (sp in e.getSpans(0, e.length, SyntaxSpan::class.java)) e.removeSpan(sp)
    }

    /** Puts the colours of [t] on [e], in place of the ones there. */
    fun paint(e: Editable, t: Tokens, scheme: SyntaxScheme) {
        clear(e)
        val len = e.length
        for (i in 0 until t.size) {
            val b = i * 3
            val end = minOf(t.data[b + 1], len)
            val color = scheme.resolve(t.data[b + 2])
            if (end > t.data[b]) e.setSpan(SyntaxSpan(color, isLiteral(t.data[b + 2])), t.data[b], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Reads [text] and returns every piece that is to be coloured. A line longer than [longLine] characters is not
     * read (see [LongLines]). Safe to call from any thread.
     */
    fun tokenize(text: String, lang: Lang, longLine: Int = Int.MAX_VALUE): Tokens {
        val out = Tokens()
        try {
            if (longLine == Int.MAX_VALUE || !LongLines.exists(text, longLine)) {
                scanText(text, 0, text.length, lang, out)
            } else {
                scanSkippingLong(text, 0, text.length, lang, out, 0, null, false, { pos -> LongLines.nextStart(text, pos, longLine) })
            }
        } catch (_: Exception) {
            // keep what was found so far
        } catch (_: StackOverflowError) {
            // a pattern met a text it could not handle: keep what was found so far
        }
        return out
    }

    /**
     * The pieces of lines [firstLine] to [lastLine] (counted from 0) only, the same ones [tokenize] finds for them in
     * the whole text, found by reading from the nearest place [states] says a scan may begin. A piece that goes on
     * past the last line is cut there. Safe to call from any thread while nothing changes [text], [index] or [states].
     */
    fun tokenizeWindow(text: String, lang: Lang, index: LineIndex, states: ScanStates, firstLine: Int, lastLine: Int): Tokens {
        val first = firstLine.coerceIn(0, index.lineCount - 1)
        val last = lastLine.coerceIn(first, index.lineCount - 1)
        val begin = index.startOf(states.resyncLine(first))
        val from = index.startOf(first)
        val to = if (last + 1 < index.lineCount) index.startOf(last + 1) else text.length
        val all = Tokens()
        try {
            scanSkippingLong(text, begin, to, lang, all, 0, null, true, { pos -> states.nextLongStart(index, pos) })
        } catch (_: Exception) {
            // keep what was found so far
        } catch (_: StackOverflowError) {
            // a pattern met a text it could not handle: keep what was found so far
        }
        if (begin == from) return all
        val out = Tokens()
        for (i in 0 until all.size) {
            val b = i * 3
            if (all.data[b + 1] > from) out.add(all.data[b], all.data[b + 1], all.data[b + 2])
        }
        return out
    }
}
