package com.whatcode.editor.snippets

import android.text.Editable
import android.text.TextWatcher
import com.whatcode.editor.CodeEditText

/**
 * The tab stops of the snippet that was last put into [editor]: Tab goes to the next one, Shift+Tab to the one before.
 * It ends after the last stop, on ESC, or when the cursor is put somewhere outside the snippet.
 */
internal class SnippetSession(private val editor: CodeEditText) {

    private var tracker: SnippetTracker? = null
    private var at = 0

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            tracker?.changed(start, count, after)
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {}
    }

    val active: Boolean get() = tracker != null

    /**
     * Starts on the first stop of a snippet that was just put in: [stops] and [finalAt] come from
     * [Snippets.Expansion]. A snippet with no stop only moves the cursor.
     */
    fun begin(stops: IntArray, finalAt: Int) {
        end()
        if (stops.isEmpty()) {
            editor.setSelection(finalAt.coerceIn(0, editor.text.length))
            return
        }
        tracker = SnippetTracker(stops, finalAt)
        at = 0
        editor.addTextChangedListener(watcher)
        select()
    }

    fun end() {
        if (tracker == null) return
        tracker = null
        editor.removeTextChangedListener(watcher)
    }

    /**
     * Tab (or Shift+Tab with [back]) while a snippet is being filled in: goes to the next stop, or to the one before.
     * Returns false if there is no snippet, or the cursor has left it, so the key does its usual work.
     */
    fun tab(back: Boolean): Boolean {
        val tr = tracker ?: return false
        val a = editor.selectionStart
        val b = editor.selectionEnd
        if (a < 0 || !tr.contains(a) || !tr.contains(b)) {
            end()
            return false
        }
        if (back) {
            if (at > 0) at--
            select()
            return true
        }
        at++
        if (at >= tr.count) {
            val to = tr.finalAt.coerceIn(0, editor.text.length)
            end()
            editor.setSelection(to)
            editor.bringPointIntoView(to)
            return true
        }
        select()
        return true
    }

    private fun select() {
        val tr = tracker ?: return
        val n = editor.text.length
        val s = tr.start(at).coerceIn(0, n)
        val e = tr.end(at).coerceIn(s, n)
        editor.setSelection(s, e)
        editor.bringPointIntoView(e)
    }
}
