package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** EditText that reports cursor movement so the status bar can show Ln/Col. */
class CodeEditText(context: Context) : EditText(context) {
    var onCursorMoved: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCursorMoved?.invoke()
    }
}

class MainActivity : Activity() {

    companion object {
        const val REQ_OPEN = 1
        const val REQ_SAVE_AS = 2
    }

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorBar = 0xFF252526.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()

    private lateinit var editor: CodeEditText
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var updateButton: Button
    private lateinit var updates: UpdateController

    private var fileUri: Uri? = null
    private var fileName = "untitled.txt"
    private var modified = false
    private var loading = false
    private var lang = Highlighter.languageFor(fileName)

    private val handler = Handler(Looper.getMainLooper())
    private val highlightTask = Runnable { Highlighter.apply(editor.text, lang) }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateController(this) { updateButton.text = updates.buttonLabel() }
        buildUi()
        val incoming = intent?.data
        if (incoming != null) loadUri(incoming) else refreshAll()
        updates.autoCheck()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmDiscard { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data
        if (resultCode != RESULT_OK || u == null) return
        when (requestCode) {
            REQ_OPEN -> {
                try {
                    contentResolver.takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // not all providers allow persistable grants; editing still works this session
                }
                loadUri(u)
            }
            REQ_SAVE_AS -> writeTo(u)
        }
    }

    // ---------------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        setBackgroundColor(colorKey)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    private fun keyParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        // Edge-to-edge: keep content clear of system bars and the keyboard.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        // Top action bar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorBar)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        bar.addView(button("New") { confirmDiscard { newFile() } }, keyParams())
        bar.addView(button("Open") { confirmDiscard { openPicker() } }, keyParams())
        bar.addView(button("Save") { save() }, keyParams())
        bar.addView(button("Save as") { saveAs() }, keyParams())
        updateButton = button("Update") { updates.showPanel() }
        bar.addView(updateButton, keyParams())
        val barScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(colorBar)
            isHorizontalScrollBarEnabled = false
            addView(bar)
        }
        root.addView(barScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        titleView = TextView(this).apply {
            setTextColor(colorAccent)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        root.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        editor = CodeEditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(colorText)
            setBackgroundColor(colorBg)
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(10), dp(4), dp(10), dp(4))
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(false)
            // Auto-indent: a typed newline copies the current line's leading whitespace.
            filters = arrayOf(InputFilter { source, start, end, dest, dstart, _ ->
                if (end - start == 1 && source[start] == '\n') {
                    var lineStart = dstart
                    while (lineStart > 0 && dest[lineStart - 1] != '\n') lineStart--
                    val sb = StringBuilder("\n")
                    var i = lineStart
                    while (i < dstart && (dest[i] == ' ' || dest[i] == '\t')) {
                        sb.append(dest[i])
                        i++
                    }
                    sb.toString()
                } else {
                    null
                }
            })
            onCursorMoved = { refreshStatus() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!loading && !modified) {
                        modified = true
                        refreshTitle()
                    }
                    handler.removeCallbacks(highlightTask)
                    handler.postDelayed(highlightTask, 200)
                    refreshStatus()
                }
            })
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Symbol row: the keys a phone keyboard makes painful.
        val symbols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val keys = listOf(
            "Tab" to "    ", "{" to "{", "}" to "}", "(" to "(", ")" to ")", "[" to "[", "]" to "]",
            ";" to ";", ":" to ":", "\"" to "\"", "'" to "'", "<" to "<", ">" to ">",
            "/" to "/", "\\" to "\\", "=" to "=", "+" to "+", "-" to "-", "_" to "_",
            "*" to "*", "&" to "&", "|" to "|", "!" to "!", "#" to "#", "$" to "$"
        )
        for ((label, text) in keys) symbols.addView(button(label) { insertAtCursor(text) }, keyParams())
        val symbolScroll = HorizontalScrollView(this).apply {
            setBackgroundColor(colorBar)
            isHorizontalScrollBarEnabled = false
            addView(symbols)
        }
        root.addView(symbolScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF007ACC.toInt())
            textSize = 12f
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun insertAtCursor(s: String) {
        val a = editor.selectionStart.coerceAtLeast(0)
        val b = editor.selectionEnd.coerceAtLeast(0)
        editor.text.replace(minOf(a, b), maxOf(a, b), s)
    }

    private fun refreshTitle() {
        titleView.text = (if (modified) "● " else "") + fileName
    }

    private fun refreshStatus() {
        val text = editor.text
        val pos = editor.selectionStart.coerceIn(0, text.length)
        var line = 1
        var lastNl = -1
        for (i in 0 until pos) {
            if (text[i] == '\n') {
                line++
                lastNl = i
            }
        }
        statusView.text = "Ln $line, Col ${pos - lastNl}  ·  ${text.length} chars  ·  ${lang.name}"
    }

    private fun refreshAll() {
        refreshTitle()
        refreshStatus()
        handler.removeCallbacks(highlightTask)
        handler.postDelayed(highlightTask, 50)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // -------------------------------------------------------------------- files

    private fun confirmDiscard(then: () -> Unit) {
        if (!modified) {
            then()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Discard changes to $fileName?")
            .setPositiveButton("Discard") { _, _ -> then() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newFile() {
        loading = true
        editor.setText("")
        loading = false
        fileUri = null
        fileName = "untitled.txt"
        lang = Highlighter.languageFor(fileName)
        modified = false
        refreshAll()
    }

    private fun openPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(i, REQ_OPEN)
    }

    private fun queryName(u: Uri): String? = try {
        contentResolver.query(u, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun loadUri(u: Uri) {
        try {
            val text = contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
            loading = true
            editor.setText(text)
            loading = false
            fileUri = u
            fileName = queryName(u) ?: u.lastPathSegment ?: "file"
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
        } catch (e: Exception) {
            loading = false
            toast("Open failed: ${e.message}")
        }
    }

    private fun save() {
        val u = fileUri
        if (u == null) saveAs() else writeTo(u)
    }

    private fun saveAs() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(i, REQ_SAVE_AS)
    }

    private fun writeTo(u: Uri) {
        try {
            contentResolver.openOutputStream(u, "wt")!!.bufferedWriter().use { it.write(editor.text.toString()) }
            fileUri = u
            fileName = queryName(u) ?: fileName
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
            toast("Saved")
        } catch (e: Exception) {
            toast("Save failed: ${e.message}")
        }
    }
}
