package com.whatcode.editor.snippets

import android.content.Context

/** The snippets the person wrote, saved on the device (see [SnippetFile] for the checks and the format). */
object UserSnippets {

    private fun prefs(context: Context) = context.getSharedPreferences(SnippetFile.PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Snippet> = SnippetFile.decodeSaved(prefs(context).getString(SnippetFile.KEY_USER, null))

    fun save(context: Context, list: List<Snippet>) {
        prefs(context).edit().putString(SnippetFile.KEY_USER, SnippetFile.encodeSaved(list)).apply()
    }
}
