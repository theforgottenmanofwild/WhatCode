package com.whatcode.editor

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import com.whatcode.editor.highlight.CodeScanner
import com.whatcode.editor.highlight.CssScanner
import com.whatcode.editor.highlight.Languages
import com.whatcode.editor.highlight.ListScanner
import com.whatcode.editor.highlight.MarkupScanner
import com.whatcode.editor.highlight.isLiteralMarker
import java.util.concurrent.Executors

/**
 * Colours the text of the editor. This is the entry point; the languages are described in `highlight/Languages.kt`
 * and each family of language has its own scanner in the `highlight` package.
 */
object Highlighter {
    /** Files longer than this are shown without highlighting. */
    const val MAX_CHARS = 1_000_000

    /** Texts up to this long are coloured at once; longer ones are read on a background thread. */
    private const val SYNC_CHARS = 20_000

    /** True if [marker], the colour [tokenize] gave a piece, is that of a string, a comment or a regular expression. */
    fun isLiteral(marker: Int): Boolean = isLiteralMarker(marker)

    /** Every language the editor knows, by name, for lists such as the key layouts. Text comes last. */
    val languages: List<Lang> get() = Languages.languages

    fun languageFor(fileName: String?): Lang = Languages.languageFor(fileName)

    // ------------------------------------------------------------------ colouring the editor

    private val worker by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "highlighter").apply { isDaemon = true } }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Counts the requests, so that the answer to an old request is thrown away. Only the main thread changes it. */
    @Volatile
    private var generation = 0

    /**
     * Colours [e] for [lang] with the colours of [scheme]. Call it from the main thread. Short texts are coloured
     * at once; long ones are read on a background thread and coloured when that is done, unless the text changed
     * in the meantime. [painted] is called on the main thread each time the colours were put on the text, which
     * for a long text is later than this call returns.
     */
    fun apply(e: Editable, lang: Lang, scheme: SyntaxScheme, painted: (() -> Unit)? = null) {
        val gen = ++generation
        if (lang.family == Family.PLAIN || e.length > MAX_CHARS) {
            clear(e)
            painted?.invoke()
            return
        }
        val text = e.toString()
        if (text.length <= SYNC_CHARS) {
            paint(e, tokenize(text, lang), scheme)
            painted?.invoke()
            return
        }
        worker.execute {
            val tokens = tokenize(text, lang)
            mainHandler.post {
                if (gen == generation && e.length == text.length && e.toString() == text) {
                    paint(e, tokens, scheme)
                    painted?.invoke()
                }
            }
        }
    }

    /** Takes every color off [e], and drops any coloring that was still being worked out for it. */
    fun remove(e: Editable) {
        generation++
        clear(e)
    }

    private fun clear(e: Editable) {
        for (s in e.getSpans(0, e.length, SyntaxSpan::class.java)) e.removeSpan(s)
    }

    private fun paint(e: Editable, t: Tokens, scheme: SyntaxScheme) {
        clear(e)
        val len = e.length
        for (i in 0 until t.size) {
            val b = i * 3
            val end = minOf(t.data[b + 1], len)
            val color = scheme.resolve(t.data[b + 2])
            if (end > t.data[b]) e.setSpan(SyntaxSpan(color, isLiteral(t.data[b + 2])), t.data[b], end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Reads [text] and returns every piece that is to be coloured. Safe to call from any thread. */
    fun tokenize(text: String, lang: Lang): Tokens {
        val out = Tokens()
        try {
            scan(text, 0, text.length, lang, out)
        } catch (_: Exception) {
            // keep what was found so far
        } catch (_: StackOverflowError) {
            // a pattern met a text it could not handle: keep what was found so far
        }
        return out
    }

    private fun scan(text: String, from: Int, to: Int, lang: Lang, out: Tokens) {
        when (lang.family) {
            Family.PLAIN -> {}
            Family.CODE -> CodeScanner.scan(text, from, to, lang, out)
            Family.MARKUP -> MarkupScanner.scan(text, from, to, out)
            Family.CSS -> CssScanner.scan(text, from, to, lang, out)
            Family.YAML, Family.INI, Family.MARKDOWN -> ListScanner.scan(text, from, to, lang, out)
        }
    }
}
