package com.whatcode.editor.completion

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.Toast
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.TextEdit
import com.whatcode.settings.EditorSettings
import java.util.concurrent.Executors

/**
 * Word completion for one editor: waits a moment after a letter is typed, looks for words on another thread (see
 * [WordCompletion.suggest]), shows them in a [CompletionPopup] and puts the chosen one in as one undo step. Only a
 * stretch of the text around the cursor is copied, so it takes the same time in a small file and in a huge one.
 */
internal class CompletionController(private val editor: CodeEditText) {

    var enabled = EditorSettings.DEFAULT_COMPLETION
    var minLetters = EditorSettings.DEFAULT_COMPLETION_MIN
    var delayMs = EditorSettings.DEFAULT_COMPLETION_DELAY
    var otherFiles = EditorSettings.DEFAULT_COMPLETION_OTHER
    var acceptWithEnter = EditorSettings.DEFAULT_COMPLETION_ENTER

    /** The texts of the other open tabs, asked for when suggestions are looked for. */
    var others: () -> List<String> = { emptyList() }

    private val handler = Handler(Looper.getMainLooper())
    private val popup = CompletionPopup(editor) { accept(it) }

    // Changes whenever the text changes or the work is dropped, so a result that comes back late can be told from a fresh one.
    @Volatile private var generation = 0
    private var shownStart = -1
    private var manual = false

    private val look = Runnable { start() }

    val showing: Boolean get() = popup.showing

    /** An edit was made. [typedLetter] is true for one letter, digit or underscore typed (or deleted next to a word). */
    fun changed(typedLetter: Boolean) {
        generation++
        handler.removeCallbacks(look)
        if (!enabled || !typedLetter) {
            popup.dismiss()
            return
        }
        manual = false
        handler.postDelayed(look, delayMs.toLong())
    }

    /** Drops what is waiting and hides the list. */
    fun cancel() {
        generation++
        handler.removeCallbacks(look)
        popup.dismiss()
    }

    /** The cursor moved: the list goes unless the cursor is still in the word it was made for. */
    fun cursorMoved() {
        if (!popup.showing) return
        val a = editor.selectionStart
        val b = editor.selectionEnd
        if (a != b || WordCompletion.prefixStart(editor.text, a) != shownStart) cancel()
    }

    /** Asks for suggestions now, whatever [minLetters] says (the Complete key and Ctrl+Space). */
    fun trigger() {
        if (!enabled) {
            Toast.makeText(editor.context, "Word completion is turned off in Settings > Completion", Toast.LENGTH_SHORT).show()
            return
        }
        generation++
        handler.removeCallbacks(look)
        manual = true
        start()
    }

    fun move(by: Int) = popup.move(by)

    /** The text scrolled, so the list would be in the wrong place: it goes, and the next letter brings it back. */
    fun scrolled() = popup.dismiss()

    /** Puts [word], or the chosen one, in place of the letters typed so far. Returns false when there is nothing to put in. */
    fun accept(word: String? = null): Boolean {
        val chosen = word ?: popup.selectedWord ?: return false
        val cursor = editor.selectionStart
        val from = if (cursor == editor.selectionEnd) WordCompletion.prefixStart(editor.text, cursor) else -1
        cancel()
        if (from < 0) return false
        editor.applyStep(TextEdit(from, cursor, chosen, from + chosen.length))
        return true
    }

    private fun start() {
        val text = editor.text
        val cursor = editor.selectionStart
        if (cursor != editor.selectionEnd || cursor < 0) return popup.dismiss()
        val from = WordCompletion.prefixStart(text, cursor)
        if (from < 0) return popup.dismiss()
        val length = cursor - from
        if (!manual && length < minLetters) return popup.dismiss()

        val a = maxOf(0, cursor - WordCompletion.AROUND)
        val b = minOf(text.length, cursor + WordCompletion.AROUND)
        val window = TextUtils.substring(text, a, b)
        val prefix = window.substring(from - a, cursor - a)
        val rest = if (otherFiles) others() else emptyList()
        val mine = ++generation
        val wasManual = manual
        pool.execute {
            val found = try {
                WordCompletion.suggest(window, cursor - a, prefix, rest, stop = { mine != generation })
            } catch (_: WordCompletion.Stopped) {
                return@execute
            }
            handler.post {
                if (mine != generation || editor.selectionStart != cursor || editor.selectionEnd != cursor) return@post
                shownStart = from
                if (found.isEmpty() && wasManual) {
                    Toast.makeText(editor.context, "No words to suggest", Toast.LENGTH_SHORT).show()
                }
                popup.show(found, length, cursor)
            }
        }
    }

    private companion object {
        val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "word-completion").apply { isDaemon = true } }
    }
}
