package com.whatcode.explorer

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.files.Workspace
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import com.whatcode.ui.VectorIcon

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

    /** How the explorer shows its list: the layout, hidden files and order the options let the person choose. */
    fun explorerOptions(): ExplorerOptions

    /** True while an update needs attention, so Settings is marked. */
    fun settingsNeedAttention(): Boolean
}

/** The rows of the options the sidebar shows in the file explorer. New file and New folder are on its + button. */
enum class ExplorerAction { OPEN, OPEN_FOLDER, FIND_FILE, SETTINGS }

/**
 * The sidebar, opened with the button at the top left. It slides over the editor with a narrow strip of
 * icons on its left edge and one panel beside it: Files (the opened folder as a tree) and Search (text in
 * every file of the opened folder). Tapping outside it, or Back, closes it.
 *
 * The strip can also show two options, File explorer and Code editor, which switch the screen: in the editor the
 * "<" at the top of the strip brings them in place of Files and Search. On the file explorer's screen the strip
 * shows them from the start, and the panel beside it holds the explorer's options: how the list looks (details,
 * compact or tiles, hidden files), the order it is in, and Open, Open folder, Find file and Settings.
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

    private val files = FilesPanel(activity, workspace, host, palette, close = { close() }, isShowing = { current == Panel.FILES })
    private val searchPanel = SearchPanel(activity, workspace, host, palette, prefs, close = { close() }, hideKeyboard = { hideKeyboard() })
    private val explorerPanel = ExplorerOptionsPanel(activity, palette, host) { close() }

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
        panels.addView(files.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panels.addView(searchPanel.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panels.addView(explorerPanel.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
        if (current == Panel.FILES) files.render()
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
        searchPanel.reset()
        files.reset()
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
            if (current != Panel.EXPLORER) showPanel(Panel.EXPLORER) else explorerPanel.render()
        } else {
            setSwitching(false)
            if (current == Panel.EXPLORER) showPanel(editorPanel)
        }
    }

    private fun showPanel(panel: Panel) {
        current = panel
        if (panel != Panel.EXPLORER) editorPanel = panel
        files.view.visibility = if (panel == Panel.FILES) View.VISIBLE else View.GONE
        searchPanel.view.visibility = if (panel == Panel.SEARCH) View.VISIBLE else View.GONE
        explorerPanel.view.visibility = if (panel == Panel.EXPLORER) View.VISIBLE else View.GONE
        for ((p, item) in railItems) {
            val on = p == panel
            item.first.setBackgroundColor(if (on) colorRailOn else colorRail)
            (item.first.tag as View).visibility = if (on) View.VISIBLE else View.INVISIBLE
            item.second.setColorFilter(if (on) colorText else colorMuted)
        }
        if (panel == Panel.EXPLORER) {
            hideKeyboard()
            explorerPanel.render()
        } else if (panel == Panel.FILES) {
            hideKeyboard()
            if (isOpen) files.render()
        } else {
            searchPanel.focusInput()
        }
    }
}
