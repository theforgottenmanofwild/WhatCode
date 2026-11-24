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
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        const val REQ_OPEN = 1
        const val REQ_SAVE_AS = 2
        const val REQ_FOLDER = 3
    }

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorBar = 0xFF252526.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()

    private lateinit var editor: CodeEditText
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var menuButton: Button
    private lateinit var updates: UpdateController
    private lateinit var workspace: Workspace
    private lateinit var sidebar: Sidebar
    private lateinit var findBar: FindBar
    private lateinit var keyBar: KeyBar
    private lateinit var autosave: AutosaveSettings

    private var fileUri: Uri? = null
    private var fileName = "untitled.txt"
    private var modified = false
    private var loading = false
    private var lang = Highlighter.languageFor(fileName)

    private val handler = Handler(Looper.getMainLooper())
    private val highlightTask = Runnable { Highlighter.apply(editor.text, lang) }
    private val autosaveTask = Runnable { autosaveNow() }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = Workspace(this, REQ_FOLDER) { uri -> confirmDiscard { loadUri(uri) } }
        autosave = AutosaveSettings(this)
        updates = UpdateController(this) {
            // The menu button turns blue while an update needs attention.
            menuButton.setTextColor(if (updates.needsAttention()) colorAccent else colorText)
        }
        buildUi()
        val incoming = intent?.data
        if (incoming != null) loadUri(incoming) else refreshAll()
        updates.autoCheck()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
        updates.autoCheckIfAway()
        // The keys may have been changed in Settings > Code Editor > Key Editor.
        if (::keyBar.isInitialized) keyBar.reload()
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app (or opening another screen of it): save now if autosave is set to.
        if (autosave.enabled && autosave.saveOnLeave) autosaveNow()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sidebar.isOpen -> sidebar.close()
            findBar.isShown -> findBar.hide()
            else -> confirmDiscard { finish() }
        }
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
            REQ_FOLDER -> workspace.onFolderPicked(u)
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

        // Top bar: the sidebar button and the file name on the left, the three-dot menu on the right.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        bar.addView(
            Icons.button(this, Icon.MENU, "Sidebar", colorText) { sidebar.toggle() },
            LinearLayout.LayoutParams(dp(44), dp(40))
        )
        titleView = TextView(this).apply {
            setTextColor(colorAccent)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(dp(6), 0, 0, 0)
        }
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        menuButton = button("\u22EE") { showMenu() }.apply {
            textSize = 22f
            contentDescription = "Menu"
            setBackgroundColor(colorBar)
        }
        bar.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(40)))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
                    if (!loading) scheduleAutosave()
                    handler.removeCallbacks(highlightTask)
                    handler.postDelayed(highlightTask, 200)
                    refreshStatus()
                }
            })
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Find and replace bar, then the two scrollable rows of extra keys (symbols included).
        findBar = FindBar(this, editor, colorBar, colorBg, colorText, colorAccent)
        root.addView(findBar.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        keyBar = KeyBar(this, editor, colorBar, colorKey, colorText, colorAccent, object : KeyActions {
            override fun save() = this@MainActivity.save()
            override fun openFind() = findBar.show()
            override fun closeFind(): Boolean {
                if (!findBar.isShown) return false
                findBar.hide()
                return true
            }

            override fun commentSyntax(): Pair<String, String>? =
                if (lang.markup) "<!--" to "-->" else lang.lineComment?.let { it to "" }
        })
        editor.onModifiedChar = { keyBar.onTypedChar(it) }
        editor.onShortcut = { c, ctrl, alt, shift -> keyBar.shortcut(c, ctrl, alt, shift) }
        editor.onHistoryChanged = { keyBar.refreshStates() }
        root.addView(keyBar.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        statusView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF007ACC.toInt())
            textSize = 12f
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The sidebar slides over the editor, so both sit in one frame.
        sidebar = Sidebar(this, workspace, sidebarHost)
        workspace.onFolderChanged = { sidebar.onFolderChanged() }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(sidebar.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
    }

    /** What the sidebar asks of this screen: open a file, open it at a match, say which file is open. */
    private val sidebarHost = object : SidebarHost {
        override fun openFile(uri: Uri) {
            if (uri != fileUri) confirmDiscard { loadUri(uri) }
        }

        override fun openFileAt(uri: Uri, line: Int, col: Int, length: Int) {
            if (uri == fileUri) {
                selectRange(line, col, length)
            } else {
                confirmDiscard {
                    loadUri(uri)
                    if (fileUri == uri) selectRange(line, col, length)
                }
            }
        }

        override fun currentFile(): Uri? = fileUri
    }

    /** Selects [length] characters at [col] (from 0) on [line] (from 1) and scrolls them into view. */
    private fun selectRange(line: Int, col: Int, length: Int) {
        val s = editor.text.toString()
        var offset = 0
        var l = 1
        while (l < line) {
            val nl = s.indexOf('\n', offset)
            if (nl < 0) break
            offset = nl + 1
            l++
        }
        val start = (offset + col).coerceIn(0, s.length)
        val end = (start + length).coerceIn(start, s.length)
        editor.post {
            editor.requestFocus()
            editor.setSelection(start, end)
        }
    }

    private fun showMenu() {
        val popup = PopupMenu(this, menuButton, Gravity.END)
        popup.menu.add(0, 1, 0, "New")
        popup.menu.add(0, 2, 1, "Open")
        popup.menu.add(0, 3, 2, "Open folder")
        popup.menu.add(0, 4, 3, "Files")
        popup.menu.add(0, 5, 4, "Find file")
        popup.menu.add(0, 6, 5, "Save")
        popup.menu.add(0, 7, 6, "Save as")
        popup.menu.add(0, 8, 7, "Close file")
        popup.menu.add(0, 9, 8, "Edit keys")
        popup.menu.add(0, 10, 9, if (updates.needsAttention()) "Settings ●" else "Settings")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> confirmDiscard { newFile() }
                2 -> confirmDiscard { openPicker() }
                3 -> workspace.pickFolder()
                4 -> workspace.showFiles()
                5 -> workspace.showFind()
                6 -> save()
                7 -> saveAs()
                8 -> confirmDiscard { newFile() }
                9 -> keyBar.showEditor()
                10 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
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
        handler.removeCallbacks(autosaveTask)
        loading = true
        editor.setTextNoHistory("")
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
        handler.removeCallbacks(autosaveTask)
        try {
            val text = contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
            loading = true
            editor.setTextNoHistory(text)
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

    /** Restarts the wait before autosave. Only a file that already has a place to be saved to is autosaved. */
    private fun scheduleAutosave() {
        handler.removeCallbacks(autosaveTask)
        if (!autosave.enabled || fileUri == null) return
        handler.postDelayed(autosaveTask, autosave.delaySeconds * 1000L)
    }

    private fun autosaveNow() {
        handler.removeCallbacks(autosaveTask)
        val u = fileUri ?: return
        if (!autosave.enabled || !modified) return
        writeTo(u, quiet = true)
    }

    private fun writeTo(u: Uri, quiet: Boolean = false) {
        try {
            contentResolver.openOutputStream(u, "wt")!!.bufferedWriter().use { it.write(editor.text.toString()) }
            fileUri = u
            fileName = queryName(u) ?: fileName
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
            if (!quiet) toast("Saved")
        } catch (e: Exception) {
            toast((if (quiet) "Autosave failed: " else "Save failed: ") + e.message)
        }
    }
}
