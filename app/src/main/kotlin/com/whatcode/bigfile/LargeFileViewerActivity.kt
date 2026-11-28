package com.whatcode.bigfile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.whatcode.editor.Highlighter
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.search.FindLogic
import com.whatcode.settings.AppearanceSettings
import com.whatcode.settings.LargeFileSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated read-only large-file viewer (Part D2). Streams through [PagedFile] and [SparseLineIndex] so gigabyte-scale
 * files open immediately, shows `counting…` while indexing lines in the background, and provides streaming search,
 * Go to line/percent, tail-following for growing files, word-wrap toggle, selection copy and pinch zoom.
 */
class LargeFileViewerActivity : Activity() {

    private lateinit var palette: AppTheme.Palette
    private lateinit var appearance: AppearanceSettings
    private lateinit var largeSettings: LargeFileSettings
    private lateinit var viewer: LargeFileView
    private lateinit var statusView: TextView
    private lateinit var copyButton: TextView
    private lateinit var tailButton: TextView
    private lateinit var wrapButton: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findStatus: TextView
    private lateinit var caseBtn: TextView
    private lateinit var wordBtn: TextView
    private lateinit var regexBtn: TextView
    private lateinit var searchToggleBtn: TextView

    private val ui = Handler(Looper.getMainLooper())
    private var paged: PagedFile? = null
    private var lineIndex: SparseLineIndex? = null
    private var file: File? = null
    private var followEnd = false
    private var indexCancel: AtomicBoolean? = null

    private var searchCancel: AtomicBoolean? = null
    private var searchHits: List<ViewerSearch.Hit> = emptyList()
    private var searchTotal = 0L
    private var searchCapped = false
    private var searchCursor = -1
    private var caseSensitive = false
    private var wholeWord = false
    private var useRegex = false
    private var searching = false

    private val tailTask = object : Runnable {
        override fun run() {
            if (!followEnd || isFinishing || isDestroyed) return
            val pf = paged ?: return
            val idx = lineIndex ?: return
            Thread {
                val grew = idx.refreshTail(pf)
                ui.post {
                    if (!isFinishing && !isDestroyed && grew) {
                        viewer.notifyIndexUpdated()
                        viewer.scrollToEnd()
                        updateStatus()
                    }
                }
            }.start()
            ui.postDelayed(this, TAIL_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        palette = AppTheme.palette(this)
        appearance = AppearanceSettings(this)
        largeSettings = LargeFileSettings(this)
        followEnd = largeSettings.viewerFollowEnd

        val rawPath = intent.getStringExtra(EXTRA_PATH)
        val f = rawPath?.let(::File)
        if (f == null || !f.isFile || !f.canRead()) {
            toast("Could not open that file.")
            finish()
            return
        }
        file = f
        buildUi(f)
        openFileAsync(f)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buildUi(f: File) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

        // Top toolbar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        topBar.addView(
            Icons.button(this, Icon.PREVIOUS, "Back", palette.text) { finish() },
            LinearLayout.LayoutParams(dp(42), dp(42)),
        )
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(
            TextView(this).apply {
                text = f.name
                setTextColor(palette.text)
                textSize = 15f
                typeface = Typeface.create(appearance.font.typeface(), Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
        )
        titleCol.addView(
            TextView(this).apply {
                text = ExplorerSort.sizeLabel(f.length(), decimal = false) + "  ·  Read-only"
                setTextColor(palette.muted)
                textSize = 11f
                maxLines = 1
            },
        )
        topBar.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        val toolsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copyButton = actionChip("Copy") { viewer.copySelection() }.apply { visibility = View.GONE }
        tools.addView(copyButton)
        tools.addView(actionChip("Find") { toggleFindBar() })
        tools.addView(actionChip("Go to") { askGoTo() })
        wrapButton = actionChip("Wrap") {
            viewer.wrapLines = !viewer.wrapLines
            refreshToggleTints()
        }
        tools.addView(wrapButton)
        tailButton = actionChip("Tail") {
            followEnd = !followEnd
            refreshToggleTints()
            ui.removeCallbacks(tailTask)
            if (followEnd) {
                viewer.scrollToEnd()
                ui.postDelayed(tailTask, TAIL_POLL_MS)
            }
            updateStatus()
        }
        tools.addView(tailButton)
        toolsScroll.addView(tools)
        topBar.addView(toolsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        // Collapsible streaming search bar
        findBar = buildFindBar()
        findBar.visibility = View.GONE
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Main custom canvas viewer
        viewer = LargeFileView(this).apply {
            wrapLines = largeSettings.viewerWrap
            onStatusChanged = { updateStatus() }
            onSelectionChanged = { hasSel -> copyButton.visibility = if (hasSel) View.VISIBLE else View.GONE }
            onMessage = { msg -> toast(msg) }
        }
        root.addView(viewer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Status bar
        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        statusView = TextView(this).apply {
            setBackgroundColor(palette.surface)
            setTextColor(palette.muted)
            textSize = 12f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        refreshToggleTints()
    }

    private fun actionChip(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = AppTheme.pressable(this@LargeFileViewerActivity, Color.TRANSPARENT, palette.ripple, 8f)
        setOnClickListener { onClick() }
    }

    private fun buildFindBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        findInput = EditText(this).apply {
            hint = "Search large file…"
            setHintTextColor(palette.muted)
            setTextColor(palette.text)
            textSize = 13f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                    runSearch()
                    true
                } else {
                    false
                }
            }
        }
        row1.addView(findInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        caseBtn = actionChip("Aa") {
            caseSensitive = !caseSensitive
            refreshToggleTints()
        }
        wordBtn = actionChip("W") {
            wholeWord = !wholeWord
            refreshToggleTints()
        }
        regexBtn = actionChip(".*") {
            useRegex = !useRegex
            refreshToggleTints()
        }
        searchToggleBtn = actionChip("Go") {
            if (searching) cancelSearch() else runSearch()
        }
        row1.addView(caseBtn)
        row1.addView(wordBtn)
        row1.addView(regexBtn)
        row1.addView(searchToggleBtn)
        bar.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        findStatus = TextView(this).apply {
            setTextColor(palette.muted)
            textSize = 12f
            text = "Ready"
        }
        row2.addView(findStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(actionChip("<") { stepHit(-1) })
        row2.addView(actionChip(">") { stepHit(1) })
        row2.addView(actionChip("Close") { toggleFindBar() })
        bar.addView(row2)
        bar.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        return bar
    }

    private fun refreshToggleTints() {
        wrapButton.setTextColor(if (viewer.wrapLines) palette.accent else palette.text)
        tailButton.setTextColor(if (followEnd) palette.accent else palette.text)
        caseBtn.setTextColor(if (caseSensitive) palette.accent else palette.muted)
        wordBtn.setTextColor(if (wholeWord) palette.accent else palette.muted)
        regexBtn.setTextColor(if (useRegex) palette.accent else palette.muted)
        searchToggleBtn.text = if (searching) "Stop" else "Go"
    }

    private fun openFileAsync(f: File) {
        statusView.text = "Opening…"
        Thread {
            val opened = try {
                PagedFile(f)
            } catch (_: Exception) {
                null
            }
            if (opened == null) {
                ui.post {
                    if (!isFinishing && !isDestroyed) {
                        toast("Open failed")
                        finish()
                    }
                }
                return@Thread
            }
            if (!opened.isText()) {
                opened.close()
                ui.post {
                    if (!isFinishing && !isDestroyed) {
                        toast("That file isn't text, so it can't be opened here.")
                        finish()
                    }
                }
                return@Thread
            }
            val idx = SparseLineIndex(chunkLines = largeSettings.viewerChunkLines)
            val stop = AtomicBoolean(false)
            indexCancel = stop
            paged = opened
            lineIndex = idx
            val lang = Highlighter.languageFor(f.name)
            val longLimit = largeSettings.longLineLimit
            ui.post {
                if (isFinishing || isDestroyed) return@post
                viewer.bind(opened, idx, lang, longLimit)
                if (followEnd) ui.postDelayed(tailTask, TAIL_POLL_MS)
            }
            var lastUi = 0L
            idx.build(opened, cancelled = { stop.get() }) { _, _, done ->
                val now = System.currentTimeMillis()
                if (done || now - lastUi >= 250L) {
                    lastUi = now
                    ui.post {
                        if (!isFinishing && !isDestroyed) {
                            viewer.notifyIndexUpdated()
                            if (done && followEnd) viewer.scrollToEnd()
                            updateStatus()
                        }
                    }
                }
            }
        }.start()
    }

    private fun updateStatus() {
        val idx = lineIndex
        if (idx == null) {
            statusView.text = "Opening…"
            return
        }
        val top = viewer.topLine + 1
        val total = String.format(Locale.US, "%,d", idx.lineCount)
        val counting = if (idx.completed) "" else " (counting…)"
        val tail = if (followEnd) "  ·  Following end" else ""
        statusView.text = "Line ${String.format(Locale.US, "%,d", top)} / $total$counting$tail"
    }

    private fun toggleFindBar() {
        if (findBar.visibility == View.VISIBLE) {
            cancelSearch()
            findBar.visibility = View.GONE
        } else {
            findBar.visibility = View.VISIBLE
            findInput.requestFocus()
        }
    }

    private fun cancelSearch() {
        searchCancel?.set(true)
        searchCancel = null
        searching = false
        refreshToggleTints()
    }

    private fun runSearch() {
        val q = findInput.text.toString()
        val pat = FindLogic.buildPattern(q, caseSensitive, wholeWord, useRegex)
        if (pat == null) {
            findStatus.text = if (q.isEmpty()) "Type text to find" else "Invalid regular expression"
            return
        }
        val pf = paged ?: return
        cancelSearch()
        val stop = AtomicBoolean(false)
        searchCancel = stop
        searching = true
        searchHits = emptyList()
        searchTotal = 0L
        searchCapped = false
        searchCursor = -1
        findStatus.text = "Searching…"
        refreshToggleTints()
        val cap = largeSettings.viewerSearchCap
        Thread {
            val res = ViewerSearch.search(pf, pat, cap = cap, cancelled = { stop.get() }) { hits, total, capped, bytes ->
                ui.post {
                    if (searchCancel === stop && !isFinishing && !isDestroyed) {
                        searchHits = hits
                        searchTotal = total
                        searchCapped = capped
                        val pct = if (pf.length > 0) ((bytes * 100) / pf.length).toInt().coerceIn(0, 100) else 100
                        val plus = if (capped) "+" else ""
                        findStatus.text = "${hits.size}$plus matches ($pct%)"
                        if (searchCursor < 0 && hits.isNotEmpty()) {
                            searchCursor = 0
                            viewer.jumpToLine(hits[0].line, hits[0])
                        }
                    }
                }
            }
            ui.post {
                if (searchCancel === stop && !isFinishing && !isDestroyed) {
                    searching = false
                    searchCancel = null
                    searchHits = res.hits
                    searchTotal = res.totalMatched
                    searchCapped = res.capped
                    refreshToggleTints()
                    if (res.hits.isEmpty()) {
                        findStatus.text = if (stop.get()) "Stopped (0 matches)" else "0 matches"
                    } else {
                        if (searchCursor < 0) {
                            searchCursor = 0
                            viewer.jumpToLine(res.hits[0].line, res.hits[0])
                        }
                        updateSearchCounter()
                    }
                }
            }
        }.start()
    }

    private fun stepHit(dir: Int) {
        if (searchHits.isEmpty()) return
        searchCursor = if (searchCursor < 0) 0 else (searchCursor + dir + searchHits.size) % searchHits.size
        val hit = searchHits[searchCursor]
        viewer.jumpToLine(hit.line, hit)
        updateSearchCounter()
    }

    private fun updateSearchCounter() {
        val totalStr = if (searchCapped) "${searchHits.size}+ (${String.format(Locale.US, "%,d", searchTotal)} total)" else "${searchHits.size}"
        findStatus.text = "${searchCursor + 1} of $totalStr"
    }

    private fun askGoTo() {
        val idx = lineIndex ?: return
        val pf = paged ?: return
        val input = EditText(this).apply {
            hint = "Line (1..${idx.lineCount}) or 50%"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppTheme.dialogBuilder(this)
            .setTitle("Go to line or %")
            .setView(box)
            .setPositiveButton("Go") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.endsWith("%")) {
                    val pct = raw.dropLast(1).trim().toFloatOrNull()
                    if (pct != null) viewer.jumpToLine(idx.lineForPercent(pf, pct / 100f))
                } else {
                    val n = raw.toLongOrNull()
                    if (n != null && n >= 1L) viewer.jumpToLine((n - 1L).coerceIn(0L, maxOf(0L, idx.lineCount - 1L)))
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, this)
    }

    override fun onDestroy() {
        followEnd = false
        ui.removeCallbacks(tailTask)
        indexCancel?.set(true)
        searchCancel?.set(true)
        paged?.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val TAIL_POLL_MS = 700L

        fun launch(context: Context, file: File) {
            context.startActivity(Intent(context, LargeFileViewerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath))
        }
    }
}
