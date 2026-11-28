package com.whatcode.editor.snippets

import android.app.Activity
import android.widget.Toast
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.TypingLogic
import com.whatcode.ui.AppTheme

/** Putting snippets into the editor, as one undo step each, and the Snippets menu. */
internal object SnippetRunner {

    /**
     * Tab after the prefix of a snippet: puts the snippet in place of the prefix. Returns false if the word before
     * the cursor is not the prefix of a snippet of the open language, so Tab can do its usual work.
     */
    fun expandAtCursor(editor: CodeEditText): Boolean {
        val hit = Snippets.trigger(editor.text, editor.selectionStart, editor.selectionEnd, editor.lang.name, editor.snippetSource)
            ?: return false
        insert(editor, hit.snippet, hit.start, editor.selectionStart)
        return true
    }

    /**
     * Tab after an HTML or CSS abbreviation: puts what it stands for in its place. Returns false if there is none
     * before the cursor, so Tab can do its usual work.
     */
    fun expandAbbreviation(editor: CodeEditText): Boolean {
        val hit = Abbreviations.trigger(editor.text, editor.selectionStart, editor.selectionEnd, editor.lang.name) ?: return false
        insert(editor, Snippet("", "", hit.body), hit.start, editor.selectionStart)
        return true
    }

    /** Puts [snippet] in place of the text from [from] to [to], and starts on its first tab stop. */
    fun insert(editor: CodeEditText, snippet: Snippet, from: Int, to: Int) {
        val unit = TypingLogic.indentUnit(editor.text, editor.lang, editor.indentDefault.unit)
        val expansion = Snippets.expansion(editor.text, from, to, snippet, unit)
        editor.applyStep(expansion.edit)
        editor.snippetSession.begin(expansion.stops, expansion.finalOffset)
        editor.bringPointIntoView(editor.selectionEnd.coerceAtLeast(0))
    }

    /** Lists the snippets of the open language and puts in the one that is tapped, in place of the selection. */
    fun menu(activity: Activity, editor: CodeEditText) {
        val list = Snippets.available(editor.lang.name, editor.snippetSource)
        if (list.isEmpty()) {
            Toast.makeText(activity, "There are no snippets for ${editor.lang.name}", Toast.LENGTH_SHORT).show()
            return
        }
        AppTheme.dialogBuilder(activity)
            .setTitle("Snippets for ${editor.lang.name}")
            .setItems(list.map { "${it.prefix}   ${it.name}" }.toTypedArray()) { _, i ->
                val a = editor.selectionStart.coerceAtLeast(0)
                val b = editor.selectionEnd.coerceAtLeast(0)
                insert(editor, list[i], minOf(a, b), maxOf(a, b))
                editor.requestFocus()
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }
}
