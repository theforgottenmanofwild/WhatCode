package com.whatcode.files

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import com.whatcode.ui.VectorIcon
import java.util.Locale

/** What the sidebar needs from the editor screen it sits on. */
interface SidebarHost {
    /** Opens a file from the folder, asking first about unsaved changes. */
    fun openFile(uri: Uri)

    /** Opens a file and selects [length] characters at [col] on [line] (both counted from 1 and 0). */
    fun openFileAt(uri: Uri, line: Int, col: Int, length: Int)

    /** The file that is open now, if it has an address. */
    fun currentFile(): Uri?

    /** True while the file explorer is showing instead of the editor. */
    fun explorerShowing(): Boolean

    /** Shows the file explorer, keeping the open tabs. */
    fun showExplorer()

    /** Shows the editor, with the tabs as they were. */
    fun showEditor()

    /** Does what a row of the explorer's options asks for. */
    fun explorerAction(action: ExplorerAction)

    /** True while an update needs attention, so Settings is marked. */
    fun settingsNeedAttention(): Boolean
}

/** The rows of the options the sidebar shows in the file explorer. */
enum class ExplorerAction { NEW_FILE, NEW_FOLDER, OPEN, OPEN_FOLDER, FIND_FILE, SETTINGS }

/**
 * The sidebar, opened with the button at the top left. It slides over the editor with a narrow strip of
 * icons on its left edge and one panel beside it: Files (the opened folder as a tree) and Search (text in
 * every file of the opened folder). Tapping outside it, or Back, closes it.
 *
 * The strip can also show two options, File explorer and Code editor, which switch the screen: in the editor the
 * "<" at the top of the strip brings them in place of Files and Search. On the file explorer's screen the strip
 * shows them from the start, and the panel beside it holds the explorer's options.
 */
class Sidebar(
    private val activity: Activity,
    private val workspace: Workspace,
    private val host: SidebarHost,
    private val palette: AppTheme.Palette,
) {
    enum class Panel { FILES, SEARCH, EXPLORER }

    private val colorBg get() = palette.background
    private val colorRail get() = palette.rail
    private val colorRailOn get() = palette.accentSoft
    private val colorKey get() = palette.key
    private val colorText get() = palette.text
    private val colorMuted get() = palette.muted
    private val colorAccent get() = palette.accent
    private val colorHit get() = palette.hit

    private val prefs = activity.getSharedPreferences("sidebar", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())

    /** The sidebar and the dimmed area around it. It fills the screen and is hidden until opened. */
    val view = FrameLayout(activity)
    val isOpen: Boolean get() = view.visibility == View.VISIBLE && !closing

    private val scrim = View(activity)
    private val drawer = LinearLayout(activity)
    private var drawerWidth = 0
    private var closing = false
    private var current = Panel.FILES
    private val railItems = HashMap<Panel, Pair<FrameLayout, ImageView>>()

    // The icon strip has two faces: the editor's panels, or the two options that switch the screen.
    private val railEditor = LinearLayout(activity)
    private val railSwitch = LinearLayout(activity)
    private val switchItems = HashMap<Boolean, Triple<FrameLayout, ImageView, TextView>>() // true: File explorer

    /** The editor's panel that was showing, so it comes back after a visit to the explorer. */
    private var editorPanel = Panel.FILES

    private val filesPanel = LinearLayout(activity)
    private val searchPanel = LinearLayout(activity)
    private val explorerPanel = LinearLayout(activity)
    private val explorerRows = LinearLayout(activity)

    // Files panel
    private val folderTitle = TextView(activity)
    private val treeBox = LinearLayout(activity)
    private val expanded = HashSet<String>()
    private val cache = HashMap<String, List<Workspace.Entry>>()
    private val loading = HashSet<String>()

    // Search panel
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var resultsBox: LinearLayout
    private lateinit var resultsScroll: ScrollView
    private val toggles = HashMap<String, TextView>()
    private var search: FileSearch? = null
    private lateinit var searchButton: Button
    private var searching = false

    init {
        view.visibility = View.GONE
        scrim.setBackgroundColor(0x99000000.toInt())
        scrim.setOnClickListener { close() }
        view.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        drawer.orientation = LinearLayout.HORIZONTAL
        drawer.setBackgroundColor(colorBg)
        drawer.isClickable = true // taps inside the sidebar must not reach the scrim
        drawer.applySystemBarInsets()
        view.addView(drawer, FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

        drawer.addView(buildRail(), LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT))
        val panels = FrameLayout(activity)
        buildFilesPanel()
        buildSearchPanel()
        buildExplorerPanel()
        panels.addView(filesPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panels.addView(searchPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panels.addView(explorerPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawer.addView(panels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        showPanel(Panel.FILES)
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    /** Keeps the content clear of the status bar, the navigation bar and the keyboard. */
    private fun View.applySystemBarInsets() {
        setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, 0, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
    }

    // ------------------------------------------------------------------ opening and closing

    fun toggle() {
        if (isOpen) close() else open()
    }

    fun open(panel: Panel? = null) {
        syncScreen()
        if (panel != null && !host.explorerShowing()) showPanel(panel)
        // The size is worked out each time, in case the screen was turned.
        val screen = activity.resources.displayMetrics.widthPixels
        drawerWidth = minOf((screen * 0.8f).toInt(), dp(380)).coerceAtLeast(minOf(screen, dp(280)))
        (drawer.layoutParams as FrameLayout.LayoutParams).width = drawerWidth
        drawer.requestLayout()
        closing = false
        view.visibility = View.VISIBLE
        drawer.translationX = -drawerWidth.toFloat()
        scrim.alpha = 0f
        drawer.animate().translationX(0f).setDuration(180).start()
        scrim.animate().alpha(1f).setDuration(180).start()
        if (current == Panel.FILES) renderTree()
    }

    fun close() {
        if (view.visibility != View.VISIBLE || closing) return
        closing = true
        hideKeyboard()
        drawer.animate().translationX(-drawerWidth.toFloat()).setDuration(160).start()
        scrim.animate().alpha(0f).setDuration(160).withEndAction {
            if (closing) view.visibility = View.GONE
            closing = false
        }.start()
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** Another folder was opened: forget the old tree and the old results. */
    fun onFolderChanged() {
        search?.cancel()
        setSearching(false)
        cache.clear()
        loading.clear()
        expanded.clear()
        resultsBox.removeAllViews()
        status.text = ""
        renderTree()
    }

    // ------------------------------------------------------------------ the icon strip

    private fun buildRail(): View {
        val rail = FrameLayout(activity).apply { setBackgroundColor(colorRail) }
        railEditor.orientation = LinearLayout.VERTICAL
        railEditor.setPadding(0, dp(8), 0, dp(8))
        railSwitch.orientation = LinearLayout.VERTICAL
        railSwitch.setPadding(0, dp(8), 0, dp(8))

        // The "<" brings the two options that switch the screen.
        railEditor.addView(
            Icons.button(activity, Icon.PREVIOUS, "File explorer and Code editor", colorMuted) { setSwitching(true) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        )
        fun item(panel: Panel, icon: Icon, description: String) {
            val img = Icons.button(activity, icon, description, colorMuted) { showPanel(panel) }
            val bar = View(activity).apply { setBackgroundColor(colorAccent) }
            val cell = FrameLayout(activity)
            cell.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            cell.addView(bar, FrameLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
            cell.tag = bar
            railItems[panel] = cell to img
            railEditor.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        item(Panel.FILES, Icon.FILES, "Files")
        item(Panel.SEARCH, Icon.FIND, "Search in files")

        fun option(explorer: Boolean, icon: Icon, description: String, caption: String) {
            val img = ImageView(activity).apply {
                setImageDrawable(VectorIcon(icon, dp(22)))
                setColorFilter(colorMuted)
            }
            val label = TextView(activity).apply {
                text = caption
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(colorMuted)
            }
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(img, LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, 0) })
            }
            val bar = View(activity).apply { setBackgroundColor(colorAccent) }
            val cell = FrameLayout(activity).apply {
                contentDescription = description
                setOnClickListener { switchScreen(explorer) }
            }
            cell.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            cell.addView(bar, FrameLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
            cell.tag = bar
            switchItems[explorer] = Triple(cell, img, label)
            railSwitch.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        }
        option(true, Icon.FOLDER, "File explorer", "File\nexplorer")
        option(false, Icon.CODE, "Code editor", "Code\neditor")

        rail.addView(railEditor, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rail.addView(railSwitch, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        railSwitch.visibility = View.GONE
        return rail
    }

    /** Shows the strip's two options (File explorer, Code editor) when [on], or the editor's own icons. */
    private fun setSwitching(on: Boolean) {
        railEditor.visibility = if (on) View.GONE else View.VISIBLE
        railSwitch.visibility = if (on) View.VISIBLE else View.GONE
        val inExplorer = host.explorerShowing()
        for ((explorer, item) in switchItems) {
            val here = explorer == inExplorer
            item.first.setBackgroundColor(if (here) colorRailOn else colorRail)
            (item.first.tag as View).visibility = if (here) View.VISIBLE else View.INVISIBLE
            item.second.setColorFilter(if (here) colorText else colorMuted)
            item.third.setTextColor(if (here) colorText else colorMuted)
        }
    }

    /**
     * File explorer or Code editor was tapped. The explorer while it shows does nothing. The editor from the
     * explorer shows the editor; from the editor it only brings back the editor's own icons.
     */
    private fun switchScreen(explorer: Boolean) {
        if (explorer && host.explorerShowing()) return
        if (explorer) host.showExplorer() else if (host.explorerShowing()) host.showEditor()
        syncScreen()
    }

    /** Makes the strip and the panel fit the screen that is showing: the explorer's, or the editor's. */
    fun syncScreen() {
        if (host.explorerShowing()) {
            setSwitching(true)
            if (current != Panel.EXPLORER) showPanel(Panel.EXPLORER) else renderExplorerRows()
        } else {
            setSwitching(false)
            if (current == Panel.EXPLORER) showPanel(editorPanel)
        }
    }

    private fun showPanel(panel: Panel) {
        current = panel
        if (panel != Panel.EXPLORER) editorPanel = panel
        filesPanel.visibility = if (panel == Panel.FILES) View.VISIBLE else View.GONE
        searchPanel.visibility = if (panel == Panel.SEARCH) View.VISIBLE else View.GONE
        explorerPanel.visibility = if (panel == Panel.EXPLORER) View.VISIBLE else View.GONE
        for ((p, item) in railItems) {
            val on = p == panel
            item.first.setBackgroundColor(if (on) colorRailOn else colorRail)
            (item.first.tag as View).visibility = if (on) View.VISIBLE else View.INVISIBLE
            item.second.setColorFilter(if (on) colorText else colorMuted)
        }
        if (panel == Panel.EXPLORER) {
            hideKeyboard()
            renderExplorerRows()
        } else if (panel == Panel.FILES) {
            hideKeyboard()
            if (isOpen) renderTree()
        } else {
            input.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.post { imm.showSoftInput(input, 0) }
        }
    }

    // ------------------------------------------------------------------ the explorer's options

    private fun buildExplorerPanel() {
        explorerPanel.orientation = LinearLayout.VERTICAL
        val title = TextView(activity).apply {
            text = "File explorer"
            setTextColor(colorText)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(palette.surface)
        }
        explorerPanel.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        explorerRows.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(activity)
        scroll.addView(explorerRows, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        explorerPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderExplorerRows() {
        explorerRows.removeAllViews()
        fun row(action: ExplorerAction, label: String) {
            explorerRows.addView(
                TextView(activity).apply {
                    text = label
                    setTextColor(colorText)
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), 0, dp(14), 0)
                    background = AppTheme.pressable(activity, android.graphics.Color.TRANSPARENT, palette.ripple, 0f)
                    setOnClickListener {
                        close()
                        host.explorerAction(action)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
        }
        row(ExplorerAction.NEW_FILE, "New file")
        row(ExplorerAction.NEW_FOLDER, "New folder")
        row(ExplorerAction.OPEN, "Open")
        row(ExplorerAction.OPEN_FOLDER, "Open folder")
        row(ExplorerAction.FIND_FILE, "Find file")
        row(ExplorerAction.SETTINGS, if (host.settingsNeedAttention()) "Settings \u25CF" else "Settings")
    }

    // ------------------------------------------------------------------ Files

    private fun buildFilesPanel() {
        filesPanel.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
            setBackgroundColor(palette.surface)
        }
        header.addView(
            ImageView(activity).apply {
                setImageDrawable(VectorIcon(Icon.FOLDER, dp(20)))
                setColorFilter(colorText)
            },
            LinearLayout.LayoutParams(dp(22), dp(22))
        )
        folderTitle.apply {
            setTextColor(colorText)
            textSize = 16f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(4), 0)
        }
        header.addView(folderTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            smallButton("Refresh") {
                cache.clear()
                loading.clear()
                renderTree()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
        )
        filesPanel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        treeBox.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(activity)
        scroll.addView(treeBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        filesPanel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(colorAccent)
        background = AppTheme.pressable(activity, palette.surface, palette.ripple, 10f)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { onClick() }
    }

    private fun note(text: String, depth: Int = 0) = TextView(activity).apply {
        this.text = text
        setTextColor(colorMuted)
        textSize = 13f
        setPadding(dp(14 + depth * 16), dp(10), dp(14), dp(10))
    }

    /** The message and button shown while no folder is open. */
    private fun noFolder(message: String): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(TextView(activity).apply {
            text = message
            setTextColor(colorMuted)
            textSize = 14f
        })
        addView(
            Button(activity).apply {
                text = "Open folder"
                isAllCaps = false
                textSize = 14f
                setTextColor(colorText)
                AppTheme.button(activity, this, palette)
                setOnClickListener { workspace.pickFolder() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, dp(12), 0, 0) }
        )
    }

    private fun renderTree() {
        treeBox.removeAllViews()
        val root = workspace.rootId
        if (!workspace.hasFolder || root == null) {
            folderTitle.text = "No folder open"
            treeBox.addView(noFolder("Open a folder to browse its files here."))
            return
        }
        folderTitle.text = workspace.folderName
        addChildren(root, 0)
    }

    private fun addChildren(parentId: String, depth: Int) {
        val list = cache[parentId]
        if (list == null) {
            load(parentId)
            treeBox.addView(note("Loading…", depth))
            return
        }
        if (list.isEmpty()) {
            treeBox.addView(note("(empty)", depth))
            return
        }
        val sorted = list.sortedWith(compareBy<Workspace.Entry>({ !it.isDir }, { it.name.lowercase(Locale.ROOT) }))
        val open = host.currentFile()
        for (e in sorted) {
            treeBox.addView(treeRow(e, depth, open))
            if (e.isDir && e.id in expanded) addChildren(e.id, depth + 1)
        }
    }

    private fun load(parentId: String) {
        if (!loading.add(parentId)) return
        Thread {
            val entries = try {
                workspace.listChildren(parentId)
            } catch (_: Exception) {
                emptyList()
            }
            ui.post {
                loading.remove(parentId)
                cache[parentId] = entries
                if (alive() && current == Panel.FILES) renderTree()
            }
        }.start()
    }

    private fun treeRow(e: Workspace.Entry, depth: Int, openFile: Uri?): View {
        val uri = workspace.uriFor(e.id)
        val isOpen = !e.isDir && uri != null && uri == openFile
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8 + depth * 16), 0, dp(10), 0)
            if (isOpen) setBackgroundColor(colorRailOn)
        }
        row.addView(
            TextView(activity).apply {
                text = if (!e.isDir) "" else if (e.id in expanded) "▾" else "▸"
                setTextColor(colorMuted)
                textSize = 12f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.MATCH_PARENT)
        )
        row.addView(
            ImageView(activity).apply {
                if (e.isDir) {
                    setImageDrawable(VectorIcon(Icon.FOLDER, dp(22)))
                    setColorFilter(colorAccent)
                } else {
                    setImageDrawable(FileIcons.drawable(e.name, dp(22), colorMuted))
                }
            },
            LinearLayout.LayoutParams(dp(22), dp(22)).apply { setMargins(0, 0, dp(8), 0) }
        )
        row.addView(
            TextView(activity).apply {
                text = e.name
                setTextColor(if (isOpen) colorAccent else colorText)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.setOnClickListener {
            if (e.isDir) {
                if (!expanded.add(e.id)) expanded.remove(e.id)
                renderTree()
            } else if (uri != null) {
                close()
                host.openFile(uri)
            }
        }
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        return row
    }

    // ------------------------------------------------------------------ Search

    private fun buildSearchPanel() {
        searchPanel.orientation = LinearLayout.VERTICAL

        val title = TextView(activity).apply {
            text = "Search in files"
            setTextColor(colorText)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(palette.surface)
        }
        searchPanel.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        // Options: match case, whole word, regular expression.
        val options = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), 0)
        }
        fun toggle(key: String, label: String, description: String) {
            val t = TextView(activity).apply {
                text = label
                textSize = 14f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                contentDescription = description
                setOnClickListener {
                    prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply()
                    paintToggles()
                }
            }
            toggles[key] = t
            options.addView(t, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        toggle("case", "aA", "Match case")
        toggle("word", "a-z", "Whole word")
        toggle("regex", ".*", "Regular expression")
        paintToggles()
        searchPanel.addView(options, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The text to look for, and the button that starts the search.
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        input = EditText(activity).apply {
            hint = "File names and text"
            setHintTextColor(colorMuted)
            setTextColor(colorText)
            background = AppTheme.rounded(activity, palette.background, 10f, palette.outline)
            backgroundTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(colorAccent, palette.outline)
            )
            textSize = 15f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch()
                    true
                } else {
                    false
                }
            }
        }
        line.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchButton = Button(activity).apply {
            text = "Search"
            isAllCaps = false
            textSize = 13f
            AppTheme.button(activity, this, palette, primary = true)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(14), 0, dp(14), 0)
            // While a search is going the button stops it.
            setOnClickListener { if (searching) stopSearch() else runSearch() }
        }
        line.addView(
            searchButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(dp(8), 0, 0, 0) }
        )
        searchPanel.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(activity).apply {
            setTextColor(colorMuted)
            textSize = 12f
            setPadding(dp(14), dp(4), dp(14), dp(6))
        }
        searchPanel.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        resultsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        resultsScroll = ScrollView(activity)
        resultsScroll.addView(resultsBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        searchPanel.addView(resultsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun paintToggles() {
        for ((key, t) in toggles) {
            val on = prefs.getBoolean(key, false)
            t.background = AppTheme.pressable(activity, if (on) colorAccent else colorKey, palette.ripple, 9f, palette.outline)
            t.setTextColor(if (on) palette.lockedText else colorText)
        }
    }

    private fun setSearching(on: Boolean) {
        searching = on
        searchButton.text = if (on) "Stop" else "Search"
    }

    /** Cancels the running search and keeps what it found so far. */
    private fun stopSearch() {
        search?.cancel()
        setSearching(false)
        status.text = "Stopped.  ${status.text.toString().removePrefix("Searching…").trim()}".trimEnd()
    }

    private fun runSearch() {
        search?.cancel()
        setSearching(false)
        resultsBox.removeAllViews()
        hideKeyboard()
        val q = input.text.toString()
        if (!workspace.hasFolder) {
            status.text = ""
            resultsBox.addView(noFolder("Open a folder first to search its files."))
            return
        }
        if (q.isEmpty()) {
            status.text = "Type what to look for."
            return
        }
        status.text = "Searching…"
        setSearching(true)
        val s = FileSearch(activity, workspace)
        search = s
        s.start(
            q,
            prefs.getBoolean("case", false),
            prefs.getBoolean("word", false),
            prefs.getBoolean("regex", false),
            object : FileSearch.Listener {
                override fun onFile(hit: FileHits, files: Int, matches: Int) {
                    if (search !== s) return
                    addResult(hit)
                    status.text = "Searching…  ${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}"
                }

                override fun onProgress(scanned: Int, files: Int, matches: Int) {
                    if (search !== s) return
                    status.text = "Searching…  $scanned files looked at  ·  ${plural(matches, "match", "matches")}"
                }

                override fun onDone(scanned: Int, files: Int, matches: Int, truncated: Boolean) {
                    if (search !== s) return
                    setSearching(false)
                    status.text = when {
                        matches == 0 -> "No matches in $scanned files."
                        truncated -> "First ${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}. Search for something more specific to see the rest."
                        else -> "${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}."
                    }
                }

                override fun onError(message: String) {
                    if (search !== s) return
                    setSearching(false)
                    status.text = message
                }
            }
        )
    }

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

    /** A file's name with its matching lines under it. Tapping the name folds or unfolds the lines. */
    private fun addResult(hit: FileHits) {
        val uri = workspace.uriFor(hit.id) ?: return
        val group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val lines = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val name = hit.path.substringAfterLast('/')
        val parent = hit.path.substringBeforeLast('/', "")
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(palette.surface)
        }
        header.addView(
            ImageView(activity).apply { setImageDrawable(FileIcons.drawable(name, dp(22), colorMuted)) },
            LinearLayout.LayoutParams(dp(22), dp(22)).apply { setMargins(0, 0, dp(8), 0) }
        )
        header.addView(
            TextView(activity).apply {
                text = if (parent.isEmpty()) name else "$name   $parent"
                val s = SpannableString(text)
                s.setSpan(android.text.style.ForegroundColorSpan(colorMuted), name.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
                setTextColor(colorText)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(TextView(activity).apply {
            text = when {
                hit.matches.isEmpty() -> "name"
                hit.nameMatch -> "name · ${hit.matches.size}"
                else -> hit.matches.size.toString()
            }
            setTextColor(colorAccent)
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
        })
        header.setOnClickListener {
            lines.visibility = if (lines.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        group.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(2), 0, 0) })

        if (hit.nameMatch) {
            // The name matched: a row that opens the file from its start.
            lines.addView(
                TextView(activity).apply {
                    text = "File name matches"
                    setTextColor(colorMuted)
                    textSize = 13f
                    setPadding(dp(14), dp(7), dp(12), dp(7))
                    setOnClickListener {
                        close()
                        host.openFile(uri)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        for (m in hit.matches) {
            val label = "${m.line}  "
            val s = SpannableString(label + m.text)
            s.setSpan(android.text.style.ForegroundColorSpan(colorMuted), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (m.textLen > 0) {
                s.setSpan(
                    BackgroundColorSpan(colorHit),
                    label.length + m.textCol,
                    label.length + m.textCol + m.textLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            lines.addView(
                TextView(activity).apply {
                    text = s
                    setTextColor(colorText)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(14), dp(7), dp(12), dp(7))
                    setOnClickListener {
                        close()
                        host.openFileAt(uri, m.line, m.col, m.length)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        group.addView(lines)
        resultsBox.addView(group)
    }
}
