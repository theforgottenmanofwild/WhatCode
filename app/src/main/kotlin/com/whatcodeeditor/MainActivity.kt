package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.hardware.input.InputManager
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
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
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
    private val colorMuted = 0xFF9DA5B0.toInt()
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
    private lateinit var editorSettings: EditorSettings
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var tabRow: LinearLayout

    /**
     * One open file. The tab that is showing keeps its text in the editor and its state in the fields
     * [fileUri], [fileName] and [modified] below; every other tab keeps its text, cursor and undo history here
     * (see [storeActive] and [showTab]).
     */
    private class Tab(var uri: Uri?, var name: String) {
        var text: String = ""
        var modified = false
        var selStart = 0
        var selEnd = 0
        var scrollY = 0
        var history: CodeEditText.History? = null
    }

    private val tabs = ArrayList<Tab>()
    private var activeTab = 0

    private var fileUri: Uri? = null
    private var fileName = "untitled.txt"
    private var modified = false
    private var loading = false
    private var lang: Lang = Highlighter.languageFor(fileName)
        set(v) {
            field = v
            if (::editor.isInitialized) editor.lang = v
            if (::keyBar.isInitialized) keyBar.setLanguage(v)
        }

    private val handler = Handler(Looper.getMainLooper())
    private val highlightTask = Runnable { Highlighter.apply(editor.text, lang) }
    private val autosaveTask = Runnable { autosaveNow() }

    /** Plugging in or removing a physical keyboard shows or hides the key rows (if that is turned on). */
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = applyKeyBarVisibility()
        override fun onInputDeviceRemoved(deviceId: Int) = applyKeyBarVisibility()
        override fun onInputDeviceChanged(deviceId: Int) = applyKeyBarVisibility()
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspace = Workspace(this, REQ_FOLDER) { uri -> openUri(uri) }
        autosave = AutosaveSettings(this)
        editorSettings = EditorSettings(this)
        updates = UpdateController(this) {
            // The menu button turns blue while an update needs attention.
            menuButton.setTextColor(if (updates.needsAttention()) colorAccent else colorText)
        }
        tabs.add(Tab(null, fileName))
        buildUi()
        applySettings()
        (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.registerInputDeviceListener(deviceListener, handler)
        val incoming = intent?.data
        if (incoming != null) openUri(incoming) else refreshAll()
        updates.autoCheck()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
        updates.autoCheckIfAway()
        // Typing, keys and tabs may have been changed in Settings > Code Editor.
        if (::keyBar.isInitialized) applySettings()
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app (or opening another screen of it): save now if autosave is set to.
        if (autosave.enabled && autosave.saveOnLeave) autosaveAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(deviceListener)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sidebar.isOpen -> sidebar.close()
            findBar.isShown -> findBar.hide()
            else -> confirmExit()
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
                openUri(u)
            }
            REQ_SAVE_AS -> writeTo(u)
            REQ_FOLDER -> workspace.onFolderPicked(u)
        }
    }

    /** Hands the typing options, the key rows and the key rows' visibility their current settings. */
    private fun applySettings() {
        editor.autoClose = editorSettings.autoClose
        editor.smartIndent = editorSettings.smartIndent
        keyBar.reload()
        applyKeyBarVisibility()
    }

    private fun hardwareKeyboardConnected(): Boolean {
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (!d.isVirtual && d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                (d.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
            ) return true
        }
        return false
    }

    private fun applyKeyBarVisibility() {
        if (!::keyBar.isInitialized) return
        val hide = editorSettings.hideKeysWithKeyboard && hardwareKeyboardConnected()
        keyBar.view.visibility = if (hide) View.GONE else View.VISIBLE
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

        // The tab bar, under the top bar. It only shows while two or more files are open.
        tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(colorBar)
            visibility = View.GONE
            addView(tabRow)
        }
        root.addView(tabScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

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
            // Auto-indent: a typed newline copies the current line's leading whitespace. (With smart indentation
            // on, CodeEditText handles Enter itself and this only sees the Enter that it lets through.)
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

            override fun commentSyntax(): Pair<String, String>? = lang.commentPair
            override fun closeTab() = closeTabs(listOf(tabs[activeTab]))
            override fun switchTab(step: Int) = this@MainActivity.switchTab(step)
        })
        editor.onModifiedChar = { keyBar.onTypedChar(it) }
        editor.onShortcut = { c, ctrl, alt, shift -> keyBar.shortcut(c, ctrl, alt, shift) }
        editor.onHistoryChanged = { keyBar.refreshStates() }
        editor.lang = lang
        keyBar.setLanguage(lang)
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
            if (uri != fileUri) openUri(uri)
        }

        override fun openFileAt(uri: Uri, line: Int, col: Int, length: Int) {
            openUri(uri) { if (fileUri == uri) selectRange(line, col, length) }
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
                1 -> newFile()
                2 -> openPicker()
                3 -> workspace.pickFolder()
                4 -> workspace.showFiles()
                5 -> workspace.showFind()
                6 -> save()
                7 -> saveAs()
                8 -> closeTabs(listOf(tabs[activeTab]))
                9 -> keyBar.showEditor()
                10 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun refreshTitle() {
        titleView.text = (if (modified) "● " else "") + fileName
        refreshTabs()
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

    // --------------------------------------------------------------------- tabs

    private fun isActive(t: Tab): Boolean = tabs.getOrNull(activeTab) === t
    private fun isModified(t: Tab): Boolean = if (isActive(t)) modified else t.modified
    private fun nameOf(t: Tab): String = if (isActive(t)) fileName else t.name
    private fun uriOf(t: Tab): Uri? = if (isActive(t)) fileUri else t.uri

    /** Draws the tab bar: one tab per open file, the showing one highlighted, a dot on those with unsaved changes. */
    private fun refreshTabs() {
        if (!::tabScroll.isInitialized) return
        if (tabs.size < 2) {
            tabScroll.visibility = View.GONE
            return
        }
        tabScroll.visibility = View.VISIBLE
        tabRow.removeAllViews()
        tabs.forEachIndexed { i, t ->
            val active = i == activeTab
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (active) colorBg else colorBar)
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(
                TextView(this).apply {
                    text = (if (isModified(t)) "● " else "") + nameOf(t)
                    setTextColor(if (active) colorAccent else colorText)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    maxLines = 1
                    maxWidth = dp(170)
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), 0, dp(4), 0)
                    setOnClickListener { switchTo(i) }
                    setOnLongClickListener {
                        tabMenu(t, this)
                        true
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            line.addView(
                TextView(this).apply {
                    text = "×"
                    textSize = 18f
                    setTextColor(colorMuted)
                    gravity = Gravity.CENTER
                    contentDescription = "Close ${nameOf(t)}"
                    setOnClickListener { closeTabs(listOf(t)) }
                },
                LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT)
            )
            cell.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f))
            cell.addView(
                View(this).apply { setBackgroundColor(if (active) colorAccent else colorBar) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
            )
            tabRow.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        tabScroll.post {
            val v = tabRow.getChildAt(activeTab) ?: return@post
            tabScroll.smoothScrollTo(maxOf(0, v.left - dp(48)), 0)
        }
    }

    private fun tabMenu(t: Tab, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Close")
        if (tabs.size > 1) popup.menu.add(0, 2, 1, "Close others")
        popup.menu.add(0, 3, 2, "Close all")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> closeTabs(listOf(t))
                2 -> closeTabs(tabs.filter { it !== t })
                3 -> closeTabs(tabs.toList())
            }
            true
        }
        popup.show()
    }

    /** Puts the showing file's state into its tab, so another tab can take the editor. */
    private fun storeActive() {
        val t = tabs.getOrNull(activeTab) ?: return
        t.uri = fileUri
        t.name = fileName
        t.modified = modified
        t.text = editor.text.toString()
        t.selStart = editor.selectionStart
        t.selEnd = editor.selectionEnd
        t.scrollY = editor.scrollY
        t.history = editor.saveHistory()
    }

    /** Shows tab [i] in the editor. The tab that was showing must have been put away with [storeActive] first. */
    private fun showTab(i: Int) {
        handler.removeCallbacks(autosaveTask)
        activeTab = i
        val t = tabs[i]
        loading = true
        editor.setTextNoHistory(t.text)
        editor.loadHistory(t.history)
        loading = false
        fileUri = t.uri
        fileName = t.name
        modified = t.modified
        lang = Highlighter.languageFor(fileName)
        t.text = ""
        t.history = null
        refreshAll()
        editor.post {
            val n = editor.text.length
            editor.setSelection(t.selStart.coerceIn(0, n), t.selEnd.coerceIn(0, n))
            editor.scrollTo(0, t.scrollY)
        }
    }

    private fun switchTo(i: Int) {
        if (i == activeTab || i !in tabs.indices) return
        storeActive()
        showTab(i)
    }

    private fun switchTab(step: Int) {
        if (tabs.size < 2) return
        switchTo(((activeTab + step) % tabs.size + tabs.size) % tabs.size)
    }

    /** Closes these tabs, asking first if any has unsaved changes. The last tab to close leaves a blank file. */
    private fun closeTabs(victims: List<Tab>) {
        if (victims.isEmpty()) return
        val dirty = victims.filter { isModified(it) }
        if (dirty.isEmpty()) {
            removeTabs(victims)
            return
        }
        val dialog = AlertDialog.Builder(this).setTitle("Unsaved changes")
        val only = victims.singleOrNull()
        if (only != null && uriOf(only) != null) {
            dialog.setMessage("Save changes to ${nameOf(only)} before closing it?")
                .setPositiveButton("Save") { _, _ -> if (saveTab(only)) removeTabs(victims) }
                .setNegativeButton("Discard") { _, _ -> removeTabs(victims) }
                .setNeutralButton("Cancel", null)
        } else {
            dialog.setMessage("Discard changes to ${dirty.joinToString(", ") { nameOf(it) }}?")
                .setPositiveButton("Discard") { _, _ -> removeTabs(victims) }
                .setNegativeButton("Cancel", null)
        }
        dialog.show()
    }

    private fun removeTabs(victims: List<Tab>) {
        storeActive()
        val current = tabs[activeTab]
        tabs.removeAll(victims.toSet())
        if (tabs.isEmpty()) {
            tabs.add(Tab(null, "untitled.txt"))
            showTab(0)
            return
        }
        val kept = tabs.indexOf(current)
        if (kept >= 0) {
            // The showing tab stays; only the bar changes.
            activeTab = kept
            refreshTabs()
        } else {
            showTab(minOf(activeTab, tabs.size - 1))
        }
    }

    private fun saveTab(t: Tab): Boolean {
        val u = uriOf(t) ?: return false
        return if (isActive(t)) writeTo(u, quiet = true) else writeTabText(t, u)
    }

    private fun writeTabText(t: Tab, u: Uri): Boolean = try {
        contentResolver.openOutputStream(u, "wt")!!.bufferedWriter().use { it.write(t.text) }
        t.modified = false
        true
    } catch (e: Exception) {
        toast("Save failed: ${e.message}")
        false
    }

    // -------------------------------------------------------------------- files

    /** Asks before the file in the editor is thrown away (used when files replace each other instead of opening in tabs). */
    private fun confirmDiscardActive(then: () -> Unit) {
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

    /** Leaving the app: asks if any open file has unsaved changes. */
    private fun confirmExit() {
        val dirty = tabs.filter { isModified(it) }
        if (dirty.isEmpty()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Unsaved changes")
            .setMessage("Discard changes to ${dirty.joinToString(", ") { nameOf(it) }}?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** New: a blank file in a new tab, or in place of the open file when tabs are off. */
    private fun newFile() {
        if (!editorSettings.openInTabs) {
            confirmDiscardActive { resetActive() }
            return
        }
        storeActive()
        val cur = tabs[activeTab]
        if (cur.uri == null && !cur.modified && cur.text.isEmpty()) return // already a blank file
        tabs.add(Tab(null, "untitled.txt"))
        showTab(tabs.size - 1)
    }

    private fun resetActive() {
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

    private fun readText(u: Uri): String? = try {
        contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        toast("Open failed: ${e.message}")
        null
    }

    /**
     * Opens a file. A file that is already open is just switched to. Otherwise it gets a new tab (a blank, untouched
     * file is replaced instead), or, with tabs turned off, it replaces the open file after asking about unsaved
     * changes. [then] runs once the file is showing.
     */
    private fun openUri(u: Uri, then: () -> Unit = {}) {
        if (u == fileUri) {
            then()
            return
        }
        storeActive()
        val existing = tabs.indexOfFirst { it.uri == u }
        if (existing >= 0) {
            showTab(existing)
            then()
            return
        }
        if (!editorSettings.openInTabs) {
            confirmDiscardActive {
                if (replaceActive(u)) then()
            }
            return
        }
        val text = readText(u) ?: return
        val tab = Tab(u, queryName(u) ?: u.lastPathSegment ?: "file")
        tab.text = text
        val cur = tabs[activeTab]
        if (cur.uri == null && !cur.modified && cur.text.isEmpty()) {
            tabs[activeTab] = tab
        } else {
            tabs.add(tab)
            activeTab = tabs.size - 1
        }
        showTab(activeTab)
        then()
    }

    /** Loads a file into the editor in place of the open one. */
    private fun replaceActive(u: Uri): Boolean {
        val text = readText(u) ?: return false
        handler.removeCallbacks(autosaveTask)
        loading = true
        editor.setTextNoHistory(text)
        loading = false
        fileUri = u
        fileName = queryName(u) ?: u.lastPathSegment ?: "file"
        lang = Highlighter.languageFor(fileName)
        modified = false
        refreshAll()
        return true
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

    /** Autosave for leaving the app: the showing file, and every other tab that has unsaved changes and a file. */
    private fun autosaveAll() {
        autosaveNow()
        val current = tabs.getOrNull(activeTab)
        var changed = false
        for (t in tabs) {
            val u = t.uri
            if (t !== current && t.modified && u != null && writeTabText(t, u)) changed = true
        }
        if (changed) refreshTabs()
    }

    private fun writeTo(u: Uri, quiet: Boolean = false): Boolean {
        return try {
            contentResolver.openOutputStream(u, "wt")!!.bufferedWriter().use { it.write(editor.text.toString()) }
            fileUri = u
            fileName = queryName(u) ?: fileName
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
            if (!quiet) toast("Saved")
            true
        } catch (e: Exception) {
            toast((if (quiet) "Autosave failed: " else "Save failed: ") + e.message)
            false
        }
    }
}
