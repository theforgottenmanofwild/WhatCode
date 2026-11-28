package com.whatcode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.whatcode.editor.CodeEditText
import com.whatcode.editor.EditorColouring
import com.whatcode.editor.FindBar
import com.whatcode.editor.Highlighter
import com.whatcode.editor.SyntaxScheme
import com.whatcode.editor.TrimWhitespace
import com.whatcode.editor.applyAppearance
import com.whatcode.editor.Lang
import com.whatcode.explorer.ExplorerAction
import com.whatcode.explorer.ExplorerOptions
import com.whatcode.explorer.FileExplorer
import com.whatcode.explorer.Sidebar
import com.whatcode.explorer.SidebarHost
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.files.DocumentIo
import com.whatcode.files.SessionStore
import com.whatcode.files.IoPlan
import com.whatcode.files.NotTextException
import com.whatcode.files.ReadCancelled
import com.whatcode.files.TextFile
import com.whatcode.files.Workspace
import com.whatcode.files.reason
import com.whatcode.keys.KeyActions
import com.whatcode.editor.TextTool
import com.whatcode.editor.snippets.SnippetSource
import com.whatcode.editor.snippets.UserSnippets
import com.whatcode.editor.TextToolRunner
import com.whatcode.bigfile.LargeFileConfig
import com.whatcode.bigfile.LargeFileText
import com.whatcode.bigfile.Plan
import com.whatcode.keys.KeyBar
import com.whatcode.settings.AutosaveSettings
import com.whatcode.settings.AppearanceSettings
import com.whatcode.settings.EditorSettings
import com.whatcode.settings.LargeFileActivity
import com.whatcode.settings.LargeFileSettings
import com.whatcode.settings.SettingsActivity
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import com.whatcode.ui.ThemeMode
import com.whatcode.update.UpdateController
import java.io.File

class MainActivity : Activity() {

    companion object {
        const val REQ_OPEN = 1
        const val REQ_SAVE_AS = 2
        const val REQ_FOLDER = 3
        private var pendingThemeSnapshot: Pair<List<Tab>, Int>? = null

        /** Which screen was showing when the screen is built again after a change of theme; see [explorerShowing]. */
        private var pendingExplorerShowing: Boolean? = null
    }

    private val palette by lazy { AppTheme.palette(this) }
    private val colorBg: Int get() = palette.background
    private val colorBar: Int get() = palette.surface
    private val colorKey: Int get() = palette.key
    private val colorText: Int get() = palette.text
    private val colorMuted: Int get() = palette.muted
    private val colorAccent: Int get() = palette.accent

    private val appearance by lazy { AppearanceSettings(this) }

    /** The syntax colours for the theme that is showing: the defaults plus the ones chosen in Settings > Appearance. */
    private val syntaxScheme by lazy { SyntaxScheme.of(palette.isLight, palette.editor, appearance.syntaxOverrides(palette.isLight)) }

    private lateinit var editor: CodeEditText
    private lateinit var colouring: EditorColouring
    private lateinit var titleView: TextView
    private lateinit var status: EditorStatus
    private lateinit var menuButton: Button
    private lateinit var updates: UpdateController
    private lateinit var workspace: Workspace
    private lateinit var sidebar: Sidebar
    private lateinit var explorer: FileExplorer
    private lateinit var editorScreen: LinearLayout
    private lateinit var findBar: FindBar
    private lateinit var keyBar: KeyBar
    private lateinit var autosave: AutosaveSettings
    private lateinit var largeFiles: LargeFileSettings
    private lateinit var editorSettings: EditorSettings
    private lateinit var sessionStore: SessionStore
    private lateinit var tabStrip: TabStrip
    private val documents by lazy { DocumentIo(this) }

    private val tabs = ArrayList<Tab>()
    private var activeTab = 0

    /**
     * True while the file explorer shows, false while the editor does. Both live in this one screen, so the open
     * tabs and their unsaved text stay as they are when the person goes from one to the other.
     */
    private var explorerShowing = true

    private var fileUri: Uri? = null
    private var fileName = "untitled.txt"
    private var modified = false
    private var loading = false

    /** The box shown while a big file is read or written in the background; at most one job at a time. */
    private var busy: BusyDialog? = null

    /** True while an autosave of a big text is being written on another thread. */
    private var writing = false

    /** Goes up with every change to the text, so a save that took a while can tell whether the text changed meanwhile. */
    private var editSerial = 0
    private var jobCancelled: java.util.concurrent.atomic.AtomicBoolean? = null

    /** Set when the person chose Discard while leaving, so [onPause] must not autosave what they just threw away. */
    private var discardingOnExit = false

    /** True from the start until the remembered files have been tried; [saveSession] must not run before that. */
    private var restorePending = false

    /** True while a remembered session has not been brought back, so a blank screen must not replace it. */
    private var sessionKept = false

    private var lang: Lang = Highlighter.languageFor(fileName)
        set(v) {
            field = v
            if (::editor.isInitialized) editor.lang = v
            if (::keyBar.isInitialized) keyBar.setLanguage(v)
        }

    private val handler = Handler(Looper.getMainLooper())
    // The pair of brackets at the cursor is looked for again once the colours are on, as it skips strings and comments.
    private val highlightTask = Runnable {
        colouring.configure(largeFiles.longLineLimit, largeFiles.highlightMargin)
        if (appearance.syntaxHighlighting) {
            colouring.apply(lang, syntaxScheme) { editor.refreshBracketMatch() }
        } else {
            colouring.remove()
            editor.refreshBracketMatch()
        }
    }
    private val autosaveTask = Runnable { autosaveNow() }

    /** Plugging in or removing a physical keyboard shows or hides the key rows (if that is turned on). */
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = applyKeyBarVisibility()
        override fun onInputDeviceRemoved(deviceId: Int) = applyKeyBarVisibility()
        override fun onInputDeviceChanged(deviceId: Int) = applyKeyBarVisibility()
    }

    // ---------------------------------------------------------------- lifecycle

    private var themeAtCreate: ThemeMode = ThemeMode.SYSTEM
    private var systemLightAtCreate = false

    /** What the saved appearance looked like when this screen was built; see [AppearanceSettings.signature]. */
    private var appearanceAtCreate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        themeAtCreate = AppTheme.mode(this)
        systemLightAtCreate = AppTheme.resolvedLight(this, ThemeMode.SYSTEM)
        appearanceAtCreate = AppearanceSettings.signature(this)
        AppTheme.applyWindow(this)
        workspace = Workspace(this, REQ_FOLDER) { uri -> openUri(uri) }
        autosave = AutosaveSettings(this)
        largeFiles = LargeFileSettings(this)
        largeConfig = largeFiles.config()
        editorSettings = EditorSettings(this)
        sessionStore = SessionStore(this)
        updates = UpdateController(this) {
            // The menu button turns blue while an update needs attention.
            menuButton.setTextColor(if (updates.needsAttention()) colorAccent else colorText)
        }
        val snapshot = pendingThemeSnapshot
        pendingThemeSnapshot = null
        val screenSnapshot = pendingExplorerShowing
        pendingExplorerShowing = null
        if (snapshot == null) {
            tabs.add(Tab(null, fileName))
        } else {
            tabs.addAll(snapshot.first.map { source ->
                Tab(source.uri, source.name).apply {
                    text = source.text
                    modified = source.modified
                    selStart = source.selStart
                    selEnd = source.selEnd
                    scrollY = source.scrollY
                    history = source.history
                    navigation = source.navigation
                }
            })
            activeTab = snapshot.second.coerceIn(tabs.indices)
            val current = tabs[activeTab]
            fileUri = current.uri
            fileName = current.name
            modified = current.modified
        }
        buildUi()
        if (snapshot != null) {
            val current = tabs[activeTab]
            loading = true
            try {
                editor.setTextNoHistory(current.text)
                editor.loadHistory(current.history)
                editor.loadNavigation(current.navigation)
            } finally {
                loading = false
            }
            current.text = ""
            current.history = null
            current.navigation = null
            lang = Highlighter.languageFor(fileName)
            editor.post {
                val length = editor.text.length
                editor.setSelection(current.selStart.coerceIn(0, length), current.selEnd.coerceIn(0, length))
                editor.scrollTo(0, current.scrollY)
            }
            refreshAll()
        }
        applySettings()
        (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.registerInputDeviceListener(deviceListener, handler)
        val incoming = intent?.data
        if (snapshot == null) {
            // The app always starts on the file explorer. Only a file it was opened with (Open with, Share) takes it
            // straight to the editor. Files that were open the last time are brought back below, in the editor's
            // tabs, and wait there behind the explorer.
            setScreen(explorerOn = incoming == null)
            // Bring the files back once the screen is up, not in the middle of onCreate, when the editor, the tab
            // bar and the key rows have not been laid out yet. Until then the saved list must be left alone.
            restorePending = true
            editor.post {
                val restored = restoreSession()
                if (incoming != null) {
                    openUri(incoming) { showEditorScreen() }
                } else if (!restored) {
                    // Nothing could be reopened, so there is no file to show: the explorer is where to start.
                    refreshAll()
                    showExplorerScreen()
                }
            }
        } else {
            setScreen(explorerOn = screenSnapshot ?: false)
        }
        updates.autoCheck()
    }

    override fun onResume() {
        super.onResume()
        // Light / Dark / System, the accent, fonts, spacing and syntax colours are all read when the screen is built.
        if (themeAtCreate != AppTheme.mode(this) || appearanceAtCreate != AppearanceSettings.signature(this)) {
            preserveTabsForThemeRecreation()
            recreate()
            return
        }
        if (themeAtCreate == ThemeMode.SYSTEM) AppTheme.applyWindow(this)
        discardingOnExit = false
        updates.refresh()
        updates.autoCheckIfAway()
        // Typing, keys and tabs may have been changed in Settings > Code Editor.
        if (::keyBar.isInitialized) applySettings()
        // Files may have changed while the app was away, and the access to all files may have been given.
        if (explorerShowing) explorer.onResume()
    }

    private fun preserveTabsForThemeRecreation() {
        if (!::editor.isInitialized || tabs.isEmpty()) return
        storeActive()
        pendingExplorerShowing = explorerShowing
        val snapshot = tabs.map { source ->
            Tab(source.uri, source.name).apply {
                text = if (source === tabs.getOrNull(activeTab)) editor.text.toString() else source.text
                modified = if (source === tabs.getOrNull(activeTab)) this@MainActivity.modified else source.modified
                selStart = if (source === tabs.getOrNull(activeTab)) editor.selectionStart else source.selStart
                selEnd = if (source === tabs.getOrNull(activeTab)) editor.selectionEnd else source.selEnd
                scrollY = if (source === tabs.getOrNull(activeTab)) editor.scrollY else source.scrollY
                history = if (source === tabs.getOrNull(activeTab)) editor.saveHistory() else source.history
                navigation = source.navigation
            }
        }
        pendingThemeSnapshot = snapshot to activeTab
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (AppTheme.mode(this) == ThemeMode.SYSTEM) {
            val systemLightNow = AppTheme.resolvedLight(this, ThemeMode.SYSTEM)
            if (systemLightNow != systemLightAtCreate) {
                preserveTabsForThemeRecreation()
                recreate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app (or opening another screen of it): save now if autosave is set to. Not after the person
        // chose Discard in the "Unsaved changes" question, or the changes they threw away would be written anyway.
        if (autosave.enabled && autosave.saveOnLeave && !discardingOnExit) autosaveAll()
        // Getting here means the app did start; see SessionStore.markStarting.
        sessionStore.markStarted()
        saveSession()
    }

    /** Remembers the open files (and the cursor in each) so the next start can bring them back. */
    private fun saveSession() {
        if (!::editor.isInitialized || tabs.isEmpty()) return
        // The remembered files have not been tried yet: what is on screen is only the blank start-up file.
        if (restorePending) return
        val entries = ArrayList<SessionStore.Entry>()
        var active = 0
        tabs.forEachIndexed { i, tab ->
            val showing = i == activeTab
            val uri = (if (showing) fileUri else tab.uri) ?: return@forEachIndexed
            if (showing) active = entries.size
            entries.add(
                if (showing) SessionStore.Entry(uri.toString(), editor.selectionStart, editor.selectionEnd, editor.scrollY)
                else SessionStore.Entry(uri.toString(), tab.selStart, tab.selEnd, tab.scrollY)
            )
        }
        // The last reopen failed and the person has not opened anything since: keep the remembered files instead of
        // replacing them with an empty list, so the next start can try again.
        if (entries.isEmpty() && sessionKept) return
        sessionStore.save(entries, active)
    }

    /** A file read back from the remembered session, before it gets a tab. */
    private class SavedFile(val uri: Uri, val name: String, val text: String, val entry: SessionStore.Entry)

    private fun toTab(f: SavedFile): Tab = Tab(f.uri, f.name).apply {
        text = f.text
        selStart = f.entry.selStart
        selEnd = f.entry.selEnd
        scrollY = f.entry.scrollY
    }

    /** Runs a step that only redraws something. If it fails, the failure is logged and the rest carries on. */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            ErrorLog.log(this, what, t)
        }
    }

    /**
     * Reopens the files that were open when the app was last left. Files that can no longer be read are skipped,
     * with a message naming them. Returns true when at least one file is showing again.
     */
    private fun restoreSession(): Boolean {
        try {
            return restoreSessionNow()
        } finally {
            restorePending = false
        }
    }

    private fun restoreSessionNow(): Boolean {
        if (!editorSettings.reopenLastFiles) return false
        // The last start died while reopening the files: don't try again, or the app would crash on every launch.
        if (sessionStore.startFailed()) {
            sessionStore.clear()
            sessionStore.markStarted()
            toast("The last start crashed, so the open files were not reopened")
            return false
        }
        val session = sessionStore.load() ?: return false
        sessionStore.markStarting()
        // If the app is still running a few seconds later, the start worked.
        handler.postDelayed({ sessionStore.markStarted() }, 4000)
        // Until the files are showing again, the saved list must not be replaced by an empty one.
        sessionKept = true

        val files = ArrayList<SavedFile>()
        val failures = ArrayList<String>()
        var active = -1
        session.entries.forEachIndexed { i, entry ->
            var shortName = "a file"
            try {
                val uri = Uri.parse(entry.uri)
                shortName = uri.lastPathSegment ?: shortName
                val text = documents.read(uri)
                if (i == session.active) active = files.size
                files.add(SavedFile(uri, documents.displayName(uri) ?: shortName, text, entry))
            } catch (t: Throwable) {
                // One file that can't be read (access lost, too large) must not stop the others.
                ErrorLog.log(this, "read $shortName", t)
                failures.add("$shortName (${ErrorLog.describe(t)})")
            }
        }
        if (files.isEmpty()) {
            toast("Couldn't reopen the last files: ${failures.joinToString(", ")}")
            return false
        }
        return try {
            tabs.clear()
            tabs.addAll(files.map { toTab(it) })
            showTab(if (active >= 0) active else 0)
            sessionKept = false
            if (failures.isNotEmpty()) toast("Couldn't reopen: ${failures.joinToString(", ")}")
            true
        } catch (t: Throwable) {
            // Start with a blank file. The saved files are kept (see sessionKept), so the next start can try again;
            // a real crash loop is stopped by the mark set in markStarting.
            ErrorLog.log(this, "reopen", t)
            toast("Couldn't reopen the last files: ${ErrorLog.describe(t)}")
            loading = false
            tabs.clear()
            tabs.add(Tab(null, "untitled.txt"))
            activeTab = 0
            runCatching { showTab(0) }
            false
        }
    }

    override fun onDestroy() {
        // Nothing queued (highlighting, autosave) may run against a screen that is gone.
        handler.removeCallbacksAndMessages(null)
        jobCancelled?.set(true)
        busy?.end()
        super.onDestroy()
        (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.unregisterInputDeviceListener(deviceListener)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sidebar.isOpen -> sidebar.close()
            // Back goes from the editor to the explorer. In the explorer it goes up a folder (when that is on in
            // Settings > File explorer), and leaves the app from the top.
            explorerShowing -> if (!explorer.back()) confirmExit()
            findBar.isShown -> findBar.hide()
            else -> showExplorerScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val u = data?.data
        if (resultCode != RESULT_OK || u == null) return
        when (requestCode) {
            REQ_OPEN -> {
                keepAccess(u)
                openUri(u) { showEditorScreen() }
            }
            REQ_SAVE_AS -> {
                // A file created with Save as must stay reachable after a restart, or it can't be reopened.
                keepAccess(u)
                saveByHand(u)
            }
            REQ_FOLDER -> {
                workspace.onFolderPicked(u)
                // In the explorer, a folder on the phone's own storage is also where the explorer goes.
                if (explorerShowing) {
                    val id = try {
                        DocumentsContract.getTreeDocumentId(u)
                    } catch (_: Exception) {
                        null
                    }
                    if (id == null || !explorer.openPicked(id)) {
                        toast("That folder is not on the phone's own storage, so only the editor's sidebar shows it")
                    }
                    askOpenEditor()
                }
            }
        }
    }

    /**
     * A folder was opened for the code editor while the explorer shows: asks whether to go to the editor now.
     * "Not now" leaves the explorer showing; the folder stays open in the editor's sidebar for when it is visited.
     */
    private fun askOpenEditor() {
        if (isFinishing || isDestroyed) return
        AppTheme.dialogBuilder(this)
            .setTitle("Open the code editor?")
            .setMessage("\"${workspace.folderName}\" is open in the code editor. Open the code editor now?")
            .setPositiveButton("Open code editor") { _, _ -> showEditorScreen() }
            .setNegativeButton("Not now", null)
            .show().also { AppTheme.styleDialog(it, this) }
    }

    /** Asks Android to keep access to [u] across restarts, so the file can be reopened next time. */
    private fun keepAccess(u: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // not all providers allow persistable grants; editing still works this session
        }
    }

    private var largeConfig = LargeFileConfig()
    private var largePlan = Plan.NONE

    /**
     * Works out what the size of the text turns off (Settings > Large files) and tells the editor and the status bar
     * when that changes. It costs a comparison of numbers, so it runs after every edit.
     */
    private fun applyLargePlan() {
        if (!::status.isInitialized) return
        val plan = largeConfig.planFor(editor.text.length)
        if (plan == largePlan) return
        largePlan = plan
        editor.largeFile = plan
        status.largeMode = plan.active
    }

    /** Saves a change to the guides or the ruler made from a key, and shows it without building the screen again. */
    private fun changeGuides(change: () -> Unit) {
        change()
        editor.showIndentGuides = appearance.indentGuides
        editor.rulerColumn = if (appearance.ruler) appearance.rulerColumn else 0
        appearanceAtCreate = AppearanceSettings.signature(this)
    }

    /** Tells the person what large file mode has turned off for the text on the screen, and offers its settings. */
    private fun showLargeFileInfo() {
        val chars = editor.text.length
        AppTheme.dialogBuilder(this)
            .setTitle(LargeFileText.STATUS)
            .setMessage(LargeFileText.explain(largeConfig.planFor(chars), chars, largeConfig))
            .setPositiveButton("OK", null)
            .setNeutralButton("Settings") { _, _ -> startActivity(Intent(this, LargeFileActivity::class.java)) }
            .show().also { AppTheme.styleDialog(it, this) }
    }

    /** Hands the colouring the limit for a long line and the margin of lines it colours (Settings > Large files). */
    private fun applyLargeFileSettings() {
        largeConfig = largeFiles.config()
        applyLargePlan()
        if (!::colouring.isInitialized) return
        if (colouring.configure(largeFiles.longLineLimit, largeFiles.highlightMargin)) {
            handler.removeCallbacks(highlightTask)
            handler.post(highlightTask)
        }
    }

    /** Hands the typing options, tags, clipboard, shortcuts, word wrap and the key rows their current settings. */
    private fun applySettings() {
        applyLargeFileSettings()
        editor.autoClose = editorSettings.autoClose
        editor.smartIndent = editorSettings.smartIndent
        editor.indentDefault = editorSettings.indentDefault
        editor.closeTags = editorSettings.closeTags
        editor.splitTags = editorSettings.splitTags
        editor.renameTags = editorSettings.renameTags
        editor.quoteAttributes = editorSettings.quoteAttributes
        editor.matchHighlight = editorSettings.matchHighlight
        editor.wholeLineClipboard = editorSettings.wholeLineClipboard
        editor.plainPaste = editorSettings.plainPaste
        editor.hardwareShortcuts = editorSettings.hardwareShortcuts
        editor.wordWrap = editorSettings.wordWrap
        status.view.visibility = if (editorSettings.showStatusBar) View.VISIBLE else View.GONE
        status.showScope = editorSettings.showScope
        editor.snippetsOn = editorSettings.snippets
        editor.snippetsTab = editorSettings.snippetsTab
        editor.abbreviationsOn = editorSettings.abbreviations
        editor.snippetSource = SnippetSource(builtIn = editorSettings.snippetsBuiltIn, user = UserSnippets.load(this))
        editor.completion.enabled = editorSettings.completion
        editor.completion.minLetters = editorSettings.completionMinLetters
        editor.completion.delayMs = editorSettings.completionDelay
        editor.completion.otherFiles = editorSettings.completionOtherFiles
        editor.completion.acceptWithEnter = editorSettings.completionEnter
        editor.completion.others = { tabs.filterIndexed { i, _ -> i != activeTab }.map { it.text } }
        editor.multi.enabled = editorSettings.multiCursor
        editor.multi.max = editorSettings.multiCursorMax
        editor.multi.matchCase = editorSettings.multiCursorMatchCase
        editor.multi.wholeWord = editorSettings.multiCursorWholeWord
        editor.multi.pasteLines = editorSettings.multiCursorPasteLines
        editor.multi.onMessage = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        editor.bookmarksOn = editorSettings.bookmarks
        editor.jumpHistoryOn = editorSettings.jumpHistory
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
        val hide = !editorSettings.showKeyRows || (editorSettings.hideKeysWithKeyboard && hardwareKeyboardConnected())
        keyBar.view.visibility = if (hide) View.GONE else View.VISIBLE
    }

    // ---------------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        AppTheme.button(this@MainActivity, this, palette)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    /** Edge-to-edge: keeps the content of [v] clear of the system bars and the keyboard. */
    private fun fitInsets(v: View) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        fitInsets(root)

        // Top bar: the sidebar button and the file name on the left, the three-dot menu on the right.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        bar.addView(
            Icons.button(this, Icon.MENU, "Sidebar", colorText) { sidebar.toggle() },
            LinearLayout.LayoutParams(dp(44), dp(40))
        )
        titleView = TextView(this).apply {
            setTextColor(colorAccent)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setPadding(dp(6), 0, 0, 0)
        }
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        menuButton = button("\u22EE") { showMenu() }.apply {
            textSize = 22f
            contentDescription = "Menu"
            background = AppTheme.pressable(this@MainActivity, colorBar, palette.ripple, 12f)
        }
        bar.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(40)))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // A thin line under the bar, matching the cards on the Settings screens.
        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        // The tab bar, under the top bar. It only shows while two or more files are open.
        tabStrip = TabStrip(
            this, palette,
            onSelect = { switchTo(it) },
            onClose = { closeTabs(listOf(tabs[it])) },
            onMenu = { i, anchor -> tabMenu(tabs[i], anchor) },
        )
        root.addView(tabStrip.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        status = EditorStatus(this, palette, handler, { editor }, { lang })

        editor = CodeEditText(this).apply {
            // Font, size, line height, margins, colours, cursor and selection; see Settings > Appearance.
            applyAppearance(appearance, palette)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            wordWrap = editorSettings.wordWrap
            filters = arrayOf<InputFilter>(AutoIndentFilter)
            onCursorMoved = { refreshStatus() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    status.beforeEdit(s, a, b)
                }

                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    status.onEdit(s, a, c)
                }

                override fun afterTextChanged(s: Editable?) {
                    editSerial++
                    if (!loading && !modified) {
                        modified = true
                        refreshTitle()
                    }
                    if (!loading) scheduleAutosave()
                    handler.removeCallbacks(highlightTask)
                    // Colouring a long text reads only the lines on the screen, so the wait is the same for any length.
                    handler.postDelayed(highlightTask, largeFiles.highlightDelayMs.toLong())
                    applyLargePlan()
                    status.afterEdit(loading)
                }
            })
        }
        colouring = EditorColouring(editor)
        colouring.onLongLines = { long ->
            editor.longLines = long
            status.longLines = long
            handler.post { status.refresh() }
        }
        colouring.configure(largeFiles.longLineLimit, largeFiles.highlightMargin)
        status.onTap = { if (largePlan.active) showLargeFileInfo() }
        status.onLineEndingTap = {
            if (editorSettings.textTools) {
                TextToolRunner.menu(
                    this, editor, editorSettings.textToolOptions(), "Line endings", listOf(TextTool.TO_LF, TextTool.TO_CRLF),
                )
            }
            editorSettings.textTools
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Find and replace bar, then the two scrollable rows of extra keys (symbols included).
        findBar = FindBar(this, editor, colorBar, colorBg, colorText, colorAccent)
        root.addView(findBar.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        keyBar = KeyBar(this, editor, colorBar, colorKey, colorText, colorAccent, object : KeyActions {
            override fun toggleIndentGuides() = changeGuides { appearance.indentGuides = !appearance.indentGuides }

            override fun toggleRuler() = changeGuides { appearance.ruler = !appearance.ruler }

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

        root.addView(status.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The explorer and the editor share the screen, and the sidebar slides over either, so all sit in one frame.
        editorScreen = root
        explorer = FileExplorer(this, palette, appearance.font.typeface(), explorerHost)
        fitInsets(explorer.view)
        sidebar = Sidebar(this, workspace, sidebarHost, palette)
        workspace.onFolderChanged = { sidebar.onFolderChanged() }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(explorer.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(sidebar.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        setScreen(explorerShowing)
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

        override fun explorerShowing(): Boolean = this@MainActivity.explorerShowing

        override fun showExplorer() = showExplorerScreen()

        override fun showEditor() = showEditorScreen()

        override fun explorerAction(action: ExplorerAction) {
            when (action) {
                ExplorerAction.OPEN -> openPicker()
                ExplorerAction.OPEN_FOLDER -> workspace.pickFolder()
                ExplorerAction.FIND_FILE -> explorer.findFile()
                ExplorerAction.SETTINGS -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        override fun explorerOptions(): ExplorerOptions = explorer.options

        override fun settingsNeedAttention(): Boolean = updates.needsAttention()
    }

    /** What the file explorer asks of this screen: open a file in the editor, open the sidebar. */
    private val explorerHost = object : FileExplorer.Host {
        override fun openFile(file: File) {
            openUri(Uri.fromFile(file)) { showEditorScreen() }
        }

        override fun openMenu() = sidebar.toggle()

        override fun pathMoved(from: String, to: String) = followPath(from, to)

        override fun pathDeleted(path: String) = forgetPath(path)
    }

    /** The address a file in an open tab has after [from] was renamed or moved to [to], or null if it did not change. */
    private fun movedUri(uri: Uri?, from: String, to: String): Uri? {
        if (uri?.scheme != "file") return null
        val moved = ExplorerPaths.rebase(uri.path ?: return null, from, to) ?: return null
        return Uri.fromFile(File(moved))
    }

    /** A file or folder was renamed or moved in the explorer: the tabs that were open in it keep their files. */
    private fun followPath(from: String, to: String) {
        var changed = false
        tabs.forEachIndexed { i, tab ->
            val showing = i == activeTab
            val moved = movedUri(if (showing) fileUri else tab.uri, from, to) ?: return@forEachIndexed
            val name = moved.lastPathSegment ?: return@forEachIndexed
            if (showing) {
                fileUri = moved
                fileName = name
                lang = Highlighter.languageFor(name)
            } else {
                tab.uri = moved
                tab.name = name
            }
            changed = true
        }
        if (changed) {
            refreshTitle()
            guard("session") { saveSession() }
        }
    }

    /**
     * A file or folder was deleted in the explorer. The tabs that were open in it stay, with their text, as changes
     * not saved yet and no file: Save asks where to put them, and closing asks first.
     */
    private fun forgetPath(path: String) {
        var changed = false
        tabs.forEachIndexed { i, tab ->
            val showing = i == activeTab
            val uri = if (showing) fileUri else tab.uri
            val where = if (uri?.scheme == "file") uri.path else null
            if (where == null || !ExplorerPaths.isInside(where, path)) return@forEachIndexed
            if (showing) {
                fileUri = null
                modified = true
            } else {
                tab.uri = null
                tab.modified = true
            }
            changed = true
        }
        if (changed) {
            handler.removeCallbacks(autosaveTask)
            refreshTitle()
            guard("session") { saveSession() }
        }
    }

    /** Shows the explorer or the editor. Nothing is closed or saved: the tabs stay as they are. */
    private fun setScreen(explorerOn: Boolean) {
        explorerShowing = explorerOn
        if (::explorer.isInitialized) explorer.view.visibility = if (explorerOn) View.VISIBLE else View.GONE
        if (::editorScreen.isInitialized) editorScreen.visibility = if (explorerOn) View.GONE else View.VISIBLE
        if (explorerOn && ::editor.isInitialized) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
        }
        if (::sidebar.isInitialized) sidebar.syncScreen()
    }

    private fun showExplorerScreen() {
        if (explorerShowing) return
        setScreen(true)
        explorer.refresh()
    }

    private fun showEditorScreen() {
        if (!explorerShowing) return
        setScreen(false)
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
        val popup = AppTheme.popup(this, menuButton, Gravity.END)
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

    private fun refreshStatus() = status.refresh()

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

    /** Draws the tab bar again from the open files; see [TabStrip]. */
    private fun refreshTabs() {
        if (!::tabStrip.isInitialized) return
        tabStrip.render(tabs.map { TabStrip.Label(nameOf(it), isModified(it)) }, activeTab)
    }

    private fun tabMenu(t: Tab, anchor: View) {
        val popup = AppTheme.popup(this, anchor)
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
        t.navigation = editor.saveNavigation()
    }

    /** Shows tab [i] in the editor. The tab that was showing must have been put away with [storeActive] first. */
    private fun showTab(i: Int) {
        handler.removeCallbacks(autosaveTask)
        activeTab = i
        val t = tabs[i]
        loading = true
        try {
            editor.setTextNoHistory(t.text)
            editor.loadHistory(t.history)
            editor.loadNavigation(t.navigation)
        } finally {
            // Left set, it would stop every later edit from counting as a change (and from being autosaved).
            loading = false
        }
        fileUri = t.uri
        fileName = t.name
        modified = t.modified
        val selStart = t.selStart
        val selEnd = t.selEnd
        val scrollY = t.scrollY
        t.text = ""
        t.history = null
        t.navigation = null
        // Only redrawing is left. If one of these steps fails the file is still open, so go on and log it.
        guard("language") { lang = Highlighter.languageFor(fileName) }
        guard("refresh") { refreshAll() }
        editor.post {
            guard("cursor") {
                val n = editor.text.length
                editor.setSelection(selStart.coerceIn(0, n), selEnd.coerceIn(0, n))
                editor.scrollTo(0, scrollY)
            }
        }
        // Remember the new set of files now, not only when the app is left, so a crash can't lose it.
        guard("session") { saveSession() }
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
        val dialog = AppTheme.dialogBuilder(this).setTitle("Unsaved changes")
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
        dialog.show().also { AppTheme.styleDialog(it, this) }
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
            guard("session") { saveSession() }
        } else {
            showTab(minOf(activeTab, tabs.size - 1))
        }
    }

    private fun saveTab(t: Tab): Boolean {
        val u = uriOf(t) ?: return false
        return if (isActive(t)) writeTo(u, quiet = true, byHand = true) else writeTabText(t, u, byHand = true)
    }

    /** True if saving the file called [name] trims it first: only a save the person asked for, never autosave. */
    private fun trimsOnSave(name: String, byHand: Boolean): Boolean =
        byHand && editorSettings.trimOnSave && TrimWhitespace.appliesTo(Highlighter.languageFor(name))

    private fun writeTabText(t: Tab, u: Uri, byHand: Boolean = false): Boolean = try {
        val text = if (trimsOnSave(t.name, byHand)) TrimWhitespace.trim(t.text) else t.text
        TextFile.write(documents.openForWrite(u), text)
        if (text != t.text) {
            t.text = text
            t.history = null // its steps describe the text as it was
        }
        t.modified = false
        true
    } catch (e: Exception) {
        toast("Save failed: ${reason(e)}")
        false
    }

    // -------------------------------------------------------------------- files

    /** Asks before the file in the editor is thrown away (used when files replace each other instead of opening in tabs). */
    private fun confirmDiscardActive(then: () -> Unit) {
        if (!modified) {
            then()
            return
        }
        AppTheme.dialogBuilder(this)
            .setTitle("Unsaved changes")
            .setMessage("Discard changes to $fileName?")
            .setPositiveButton("Discard") { _, _ -> then() }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, this) }
    }

    /** Leaving the app: asks if any open file has unsaved changes. */
    private fun confirmExit() {
        val dirty = tabs.filter { isModified(it) }
        if (dirty.isEmpty()) {
            finish()
            return
        }
        AppTheme.dialogBuilder(this)
            .setTitle("Unsaved changes")
            .setMessage("Discard changes to ${dirty.joinToString(", ") { nameOf(it) }}?")
            .setPositiveButton("Discard") { _, _ ->
                discardingOnExit = true
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, this) }
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
        try {
            editor.setTextNoHistory("")
        } finally {
            loading = false
        }
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

    private fun readText(u: Uri, quiet: Boolean = false): String? = try {
        documents.read(u)
    } catch (e: OutOfMemoryError) {
        // A huge file is not an Exception, so without this it would take the whole app down.
        if (!quiet) toast("Open failed: the file is too large to open")
        null
    } catch (e: Exception) {
        if (!quiet) toast("Open failed: ${reason(e)}")
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
            confirmDiscardActive { replaceActive(u, then) }
            return
        }
        readAsync(u) { text ->
            val tab = Tab(u, documents.displayName(u) ?: u.lastPathSegment ?: "file")
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
    }

    /** Loads a file into the editor in place of the open one, then runs [then]. */
    private fun replaceActive(u: Uri, then: () -> Unit) {
        readAsync(u) { text ->
            handler.removeCallbacks(autosaveTask)
            loading = true
            try {
                editor.setTextNoHistory(text)
            } finally {
                loading = false
            }
            fileUri = u
            fileName = documents.displayName(u) ?: u.lastPathSegment ?: "file"
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
            then()
        }
    }

    /**
     * Reads [u] and gives the text to [done] on the main thread. A small file is read at once. A big one, or one of
     * unknown size, is read on another thread with a box that shows the progress and has Cancel. A file that
     * fails, is not text or is cancelled says so, and [done] is not called.
     */
    private fun readAsync(u: Uri, done: (String) -> Unit) {
        if (busy != null) return // one job at a time; the box covers the screen, so this is a stray second tap
        val size = documents.size(u)
        if (!IoPlan.readInBackground(size)) {
            readText(u)?.let(done)
            return
        }
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val box = BusyDialog(this, handler, "Opening ${documents.displayName(u) ?: "file"}") { stop.set(true) }
        busy = box
        jobCancelled = stop
        box.start()
        Thread {
            var text: String? = null
            var failure: Throwable? = null
            try {
                text = documents.read(u, { n -> handler.post { if (busy === box) box.update(n, size) } }, { stop.get() })
            } catch (t: Throwable) {
                failure = t
            }
            handler.post {
                if (busy === box) {
                    busy = null
                    jobCancelled = null
                }
                box.end()
                if (isFinishing || isDestroyed) return@post
                when {
                    text != null -> done(text)
                    failure is ReadCancelled -> toast("Cancelled")
                    failure is OutOfMemoryError -> toast("Open failed: the file is too large to open")
                    failure is NotTextException -> toast("Open failed: ${reason(failure)}")
                    failure != null -> {
                        ErrorLog.log(this, "read", failure)
                        toast("Open failed: ${reason(failure)}")
                    }
                }
            }
        }.start()
    }

    private fun save() {
        val u = fileUri
        if (u == null) saveAs() else saveByHand(u)
    }

    /**
     * A save the person asked for. A big text is written on another thread with a box that shows the progress. It has
     * no Cancel: the file is emptied when writing starts, so stopping halfway would leave it cut short.
     */
    private fun saveByHand(u: Uri) {
        if (busy != null) return
        if (writing) {
            toast("Still saving, try again in a moment")
            return
        }
        if (!IoPlan.writeInBackground(editor.text?.length ?: 0)) {
            writeTo(u, byHand = true)
            return
        }
        if (trimsOnSave(documents.displayName(u) ?: fileName, true)) editor.trimTrailingWhitespace()
        val text = editor.text.toString()
        val serial = editSerial
        val total = text.length.toLong()
        val box = BusyDialog(this, handler, "Saving ${documents.displayName(u) ?: fileName}", null)
        busy = box
        box.start()
        Thread {
            var failure: Throwable? = null
            try {
                TextFile.write(documents.openForWrite(u), text, { n -> handler.post { if (busy === box) box.update(n.toLong(), total) } })
            } catch (t: Throwable) {
                failure = t
            }
            handler.post {
                if (busy === box) busy = null
                box.end()
                if (isFinishing || isDestroyed) return@post
                if (failure != null) {
                    ErrorLog.log(this, "save", failure)
                    toast("Save failed: ${reason(failure)}")
                    return@post
                }
                // Typing while the box was not showing yet leaves the file changed, as it was before the save.
                if (editSerial == serial) modified = false
                fileUri = u
                fileName = documents.displayName(u) ?: fileName
                lang = Highlighter.languageFor(fileName)
                refreshAll()
                toast("Saved")
            }
        }.start()
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

    /**
     * Saves the showing file if it has unsaved changes. A big text is written on another thread (no box, the person
     * keeps typing), unless [inBackground] is false, as when the app is being left and must finish writing now.
     */
    private fun autosaveNow(inBackground: Boolean = true) {
        handler.removeCallbacks(autosaveTask)
        val u = fileUri ?: return
        if (!autosave.enabled || !modified) return
        if (writing || busy != null) return // the file is being written already
        if (inBackground && IoPlan.writeInBackground(editor.text?.length ?: 0)) {
            autosaveInBackground(u)
            return
        }
        writeTo(u, quiet = true)
    }

    private fun autosaveInBackground(u: Uri) {
        writing = true
        val text = editor.text.toString()
        val serial = editSerial
        Thread {
            var failure: Throwable? = null
            try {
                TextFile.write(documents.openForWrite(u), text)
            } catch (t: Throwable) {
                failure = t
            }
            handler.post {
                writing = false
                if (isFinishing || isDestroyed) return@post
                when {
                    failure != null -> {
                        ErrorLog.log(this, "autosave", failure)
                        toast("Autosave failed: ${reason(failure)}")
                    }
                    fileUri == u && editSerial == serial -> {
                        modified = false
                        refreshAll()
                    }
                    else -> scheduleAutosave() // typed meanwhile: what was written is already behind
                }
            }
        }.start()
    }

    /** Autosave for leaving the app: the showing file, and every other tab that has unsaved changes and a file. */
    private fun autosaveAll() {
        autosaveNow(inBackground = false)
        val current = tabs.getOrNull(activeTab)
        var changed = false
        for (t in tabs) {
            val u = t.uri
            if (t !== current && t.modified && u != null && writeTabText(t, u)) changed = true
        }
        if (changed) refreshTabs()
    }

    private fun writeTo(u: Uri, quiet: Boolean = false, byHand: Boolean = false): Boolean {
        if (writing) {
            if (!quiet) toast("Still saving, try again in a moment")
            return false
        }
        return try {
            if (trimsOnSave(documents.displayName(u) ?: fileName, byHand)) editor.trimTrailingWhitespace()
            TextFile.write(documents.openForWrite(u), editor.text.toString())
            fileUri = u
            fileName = documents.displayName(u) ?: fileName
            lang = Highlighter.languageFor(fileName)
            modified = false
            refreshAll()
            if (!quiet) toast("Saved")
            true
        } catch (e: Exception) {
            toast((if (quiet) "Autosave failed: " else "Save failed: ") + reason(e))
            false
        }
    }
}
