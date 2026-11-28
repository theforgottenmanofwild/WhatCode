package com.whatcode.settings

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.editor.snippets.Snippet
import com.whatcode.editor.snippets.SnippetFile
import com.whatcode.ui.AppTheme

/**
 * The dialog to write a snippet, or change or delete one: its word, its name, its language and its code. A snippet
 * that fails a check of [SnippetFile.problem] is explained under the field, and the dialog stays.
 *
 * [others] are the person's other snippets. [onSave] gets the snippet to keep; [onDelete] is offered when an existing
 * snippet is changed.
 */
internal fun editSnippet(
    activity: Activity,
    snippet: Snippet?,
    others: List<Snippet>,
    onSave: (Snippet) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
    val palette = AppTheme.palette(activity)
    val mono = AppearanceSettings(activity).font.typeface()
    var lang: String? = snippet?.lang

    fun label(text: String) = TextView(activity).apply {
        this.text = text
        setTextColor(palette.muted)
        textSize = 12f
        setPadding(0, dp(10), 0, 0)
    }

    val prefix = EditText(activity).apply {
        setText(snippet?.prefix ?: "")
        typeface = mono
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        hint = "todo"
    }
    val name = EditText(activity).apply {
        setText(snippet?.name ?: "")
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        hint = "A note to do later"
    }
    val langButton = Button(activity).apply {
        isAllCaps = false
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        AppTheme.button(activity, this, palette, false)
        setTextColor(palette.text)
    }
    fun showLang() {
        langButton.text = "${lang ?: "Every language"}  ▾"
    }
    showLang()
    langButton.setOnClickListener {
        val names = listOf("Every language") + SnippetFile.languages
        AppTheme.dialogBuilder(activity)
            .setTitle("Language")
            .setItems(names.toTypedArray()) { _, i ->
                lang = if (i == 0) null else names[i]
                showLang()
            }
            .show().also { AppTheme.styleDialog(it, activity) }
    }
    val body = EditText(activity).apply {
        setText(snippet?.body ?: "")
        typeface = mono
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(false)
        minLines = 5
        gravity = Gravity.TOP or Gravity.START
        hint = "// TODO: \${1:what}"
    }

    val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(4), dp(20), 0)
        val wide = { LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        addView(label("Word that Tab turns into the snippet"))
        addView(prefix, wide())
        addView(label("Name, shown in the Snippets list"))
        addView(name, wide())
        addView(label("Language"))
        addView(langButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        addView(label("Code. \$1, \$2… are the places Tab goes to, \${1:text} starts with text, and \$0 is where it ends."))
        addView(body, wide())
    }
    val scroll = ScrollView(activity).apply { addView(column) }

    val builder = AppTheme.dialogBuilder(activity)
        .setTitle(if (snippet == null) "New snippet" else "Change snippet")
        .setView(scroll)
        .setPositiveButton("Save", null)
        .setNegativeButton("Cancel", null)
    if (onDelete != null) builder.setNeutralButton("Delete", null)
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    dialog.show()
    AppTheme.styleDialog(dialog, activity)

    // The buttons must not close the dialog when the snippet is no good, so their clicks are set after show().
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val p = prefix.text.toString().trim()
        val n = name.text.toString().trim()
        // A body keeps its own spaces and line breaks; only the line endings are made plain.
        val b = body.text.toString().replace("\r\n", "\n").replace('\r', '\n')
        val problem = SnippetFile.problem(p, n, b, lang, others)
        if (problem == null) {
            dialog.dismiss()
            onSave(Snippet(p, n, b, lang))
            return@setOnClickListener
        }
        val field = when {
            p.isEmpty() || p.length > SnippetFile.MAX_PREFIX || !p.all { it == '_' || it.isLetterOrDigit() } -> prefix
            n.length > SnippetFile.MAX_NAME -> name
            others.any { it.prefix == p && it.lang == lang } -> prefix
            else -> body
        }
        field.error = problem
        field.requestFocus()
    }
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
        AppTheme.dialogBuilder(activity)
            .setTitle("Delete ${snippet?.prefix}?")
            .setMessage("The snippet is deleted. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                dialog.dismiss()
                onDelete?.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }
    if (snippet == null) prefix.requestFocus()
}
