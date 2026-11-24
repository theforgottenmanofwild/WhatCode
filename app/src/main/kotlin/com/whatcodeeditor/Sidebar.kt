package com.whatcodeeditor

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
import java.util.Locale

/** What the sidebar needs from the editor screen it sits on. */
interface SidebarHost {
    /** Opens a file from the folder, asking first about unsaved changes. */
    fun openFile(uri: Uri)

    /** Opens a file and selects [length] characters at [col] on [line] (both counted from 1 and 0). */
    fun openFileAt(uri: Uri, line: Int, col: Int, length: Int)

    /** The file that is open now, if it has an address. */
    fun currentFile(): Uri?
}

/**
 * The sidebar, opened with the button at the top left. It slides over the editor with a narrow strip of
 * icons on its left edge and one panel beside it: Files (the opened folder as a tree) and Search (text in
 * every file of the opened folder). Tapping outside it, or Back, closes it.
 */
class Sidebar(
    private val activity: Activity,
    private val workspace: Workspace,
    private val host: SidebarHost,
) {
    enum class Panel { FILES, SEARCH }

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorRail = 0xFF181818.toInt()
    private val colorRailOn = 0xFF2A2A2C.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorMuted = 0xFF9DA5B0.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()
    private val colorHit = 0x559CDCFE

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

    private val filesPanel = LinearLayout(activity)
    private val searchPanel = LinearLayout(activity)

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
        panels.addView(filesPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panels.addView(searchPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
        if (panel != null) showPanel(panel)
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
        cache.clear()
        loading.clear()
        expanded.clear()
        resultsBox.removeAllViews()
        status.text = ""
        renderTree()
    }

    // ------------------------------------------------------------------ the icon strip

    private fun buildRail(): View {
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorRail)
            setPadding(0, dp(8), 0, dp(8))
        }
        fun item(panel: Panel, icon: Icon, description: String) {
            val img = Icons.button(activity, icon, description, colorMuted) { showPanel(panel) }
            val bar = View(activity).apply { setBackgroundColor(colorAccent) }
            val cell = FrameLayout(activity)
            cell.addView(img, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            cell.addView(bar, FrameLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
            cell.tag = bar
            railItems[panel] = cell to img
            rail.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        item(Panel.FILES, Icon.FILES, "Files")
        item(Panel.SEARCH, Icon.FIND, "Search in files")
        return rail
    }

    private fun showPanel(panel: Panel) {
        current = panel
        filesPanel.visibility = if (panel == Panel.FILES) View.VISIBLE else View.GONE
        searchPanel.visibility = if (panel == Panel.SEARCH) View.VISIBLE else View.GONE
        for ((p, item) in railItems) {
            val on = p == panel
            item.first.setBackgroundColor(if (on) colorRailOn else colorRail)
            (item.first.tag as View).visibility = if (on) View.VISIBLE else View.INVISIBLE
            item.second.setColorFilter(if (on) colorText else colorMuted)
        }
        if (panel == Panel.FILES) {
            hideKeyboard()
            if (isOpen) renderTree()
        } else {
            input.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.post { imm.showSoftInput(input, 0) }
        }
    }

    // ------------------------------------------------------------------ Files

    private fun buildFilesPanel() {
        filesPanel.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
            setBackgroundColor(0xFF252526.toInt())
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
        setBackgroundColor(0x00000000)
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
                setBackgroundColor(colorKey)
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
                    setImageDrawable(FileIcons.drawable(e.name, dp(22)))
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
            setBackgroundColor(0xFF252526.toInt())
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
            hint = "Search"
            setHintTextColor(colorMuted)
            setTextColor(colorText)
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
        line.addView(
            Button(activity).apply {
                text = "Search"
                isAllCaps = false
                textSize = 13f
                setTextColor(colorAccent)
                setBackgroundColor(colorKey)
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { runSearch() }
            },
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
            t.setBackgroundColor(if (on) colorAccent else colorKey)
            t.setTextColor(if (on) 0xFF1E1E1E.toInt() else colorText)
        }
    }

    private fun runSearch() {
        search?.cancel()
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
                    status.text = when {
                        matches == 0 -> "No matches in $scanned files."
                        truncated -> "First ${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}. Search for something more specific to see the rest."
                        else -> "${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}."
                    }
                }

                override fun onError(message: String) {
                    if (search !== s) return
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
            setBackgroundColor(0xFF252526.toInt())
        }
        header.addView(
            ImageView(activity).apply { setImageDrawable(FileIcons.drawable(name, dp(22))) },
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
            text = hit.matches.size.toString()
            setTextColor(colorAccent)
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
        })
        header.setOnClickListener {
            lines.visibility = if (lines.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        group.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(2), 0, 0) })

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
