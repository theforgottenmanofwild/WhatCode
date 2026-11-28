package com.whatcode.apk

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.bigfile.PagedFile
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared read-only Hex viewer (C8). Reads files on storage through [PagedFile] so multi-gigabyte binaries open
 * without loading into memory, and also supports bounded in-memory binary entries from archives (C5).
 */
class HexViewerActivity : Activity() {

    private lateinit var palette: AppTheme.Palette
    private lateinit var listView: ListView
    private lateinit var statusView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var copyButton: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findStatus: TextView
    private lateinit var modeBtn: TextView
    private lateinit var searchToggleBtn: TextView

    private val ui = Handler(Looper.getMainLooper())
    private var pagedFile: PagedFile? = null
    private var source: HexViewLogic.ByteSource? = null
    private var offsetDigits = 8
    private var rowCount = 0
    private var selectedRow = -1

    private var hexMode = true
    private var searching = false
    private var searchCancel: AtomicBoolean? = null
    private var searchHits: List<Long> = emptyList()
    private var searchCursor = -1

    private val rowBuf = ByteArray(HexViewLogic.BYTES_PER_ROW)
    private lateinit var adapter: HexRowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        palette = AppTheme.palette(this)

        val filePath = intent.getStringExtra(EXTRA_PATH)
        val archivePath = intent.getStringExtra(EXTRA_ARCHIVE_PATH)
        val entryPath = intent.getStringExtra(EXTRA_ENTRY_PATH)
        val displayTitle = intent.getStringExtra(EXTRA_TITLE)
            ?: entryPath?.substringAfterLast('/')
            ?: filePath?.let(::File)?.name
            ?: "Hex viewer"

        if (filePath == null && (archivePath == null || entryPath == null)) {
            toast("Could not open file in Hex viewer.")
            finish()
            return
        }

        buildUi(displayTitle)
        if (filePath != null) {
            openFileAsync(File(filePath))
        } else {
            openArchiveEntryAsync(File(archivePath!!), entryPath!!)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun buildUi(title: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }

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
                text = title
                setTextColor(palette.text)
                textSize = 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
        )
        subtitleView = TextView(this).apply {
            text = "Opening…  ·  Hex"
            setTextColor(palette.muted)
            textSize = 11f
            maxLines = 1
        }
        titleCol.addView(subtitleView)
        topBar.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        val toolsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        copyButton = actionChip("Copy") { copySelectedRow() }.apply { visibility = View.GONE }
        tools.addView(copyButton)
        tools.addView(actionChip("Find") { toggleFindBar() })
        tools.addView(actionChip("Jump") { askJumpOffset() })
        toolsScroll.addView(tools)
        topBar.addView(toolsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        findBar = buildFindBar()
        findBar.visibility = View.GONE
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        adapter = HexRowAdapter()
        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            isFastScrollEnabled = true
            setBackgroundColor(palette.editor)
            adapter = this@HexViewerActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                selectedRow = if (selectedRow == position) -1 else position
                copyButton.visibility = if (selectedRow >= 0) View.VISIBLE else View.GONE
                this@HexViewerActivity.adapter.notifyDataSetChanged()
                updateStatus()
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
                override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                    updateStatus()
                }
            })
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        statusView = TextView(this).apply {
            setBackgroundColor(palette.surface)
            setTextColor(palette.muted)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(12), dp(6), dp(12), dp(6))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    private fun actionChip(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = AppTheme.pressable(this@HexViewerActivity, Color.TRANSPARENT, palette.ripple, 8f)
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
            hint = "Hex bytes (e.g. 50 4B 03 04)"
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
        searchToggleBtn = actionChip("Go") {
            if (searching) cancelSearch() else runSearch()
        }
        row1.addView(searchToggleBtn)
        row1.addView(actionChip("<") { stepHit(-1) })
        row1.addView(actionChip(">") { stepHit(1) })
        row1.addView(actionChip("×") { toggleFindBar() })
        bar.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modeBtn = actionChip("Mode: Hex") {
            hexMode = !hexMode
            modeBtn.text = if (hexMode) "Mode: Hex" else "Mode: Text"
            findInput.hint = if (hexMode) "Hex bytes (e.g. 50 4B 03 04)" else "ASCII / UTF-8 text"
            modeBtn.setTextColor(palette.accent)
        }.apply { setTextColor(palette.accent) }
        row2.addView(modeBtn)
        findStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(palette.muted)
            gravity = Gravity.END
        }
        row2.addView(findStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row2)
        return bar
    }

    private fun openFileAsync(file: File) {
        statusView.text = "Opening…"
        Thread {
            val pf = try {
                if (file.isFile && file.canRead()) PagedFile(file) else null
            } catch (_: Exception) {
                null
            }
            ui.post {
                if (isFinishing || isDestroyed) {
                    pf?.close()
                    return@post
                }
                if (pf == null) {
                    toast("Could not open that file.")
                    finish()
                    return@post
                }
                pagedFile = pf
                bindSource(HexViewLogic.PagedByteSource(pf))
            }
        }.start()
    }

    private fun openArchiveEntryAsync(archive: File, entryPath: String) {
        statusView.text = "Reading archive entry…"
        Thread {
            val bytes = try {
                if (archive.isFile && archive.canRead()) ArchiveBrowser.readEntryBytes(archive, entryPath, MAX_ARCHIVE_BYTES) else null
            } catch (_: Exception) {
                null
            }
            ui.post {
                if (isFinishing || isDestroyed) return@post
                if (bytes == null) {
                    toast("Entry is larger than ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB or could not be read. Extract it first to inspect.")
                    finish()
                    return@post
                }
                bindSource(HexViewLogic.MemoryByteSource(bytes))
            }
        }.start()
    }

    private fun bindSource(byteSource: HexViewLogic.ByteSource) {
        source = byteSource
        offsetDigits = HexViewLogic.offsetDigitsFor(byteSource.length)
        val rowsLong = if (byteSource.length == 0L) 1L else (byteSource.length + HexViewLogic.BYTES_PER_ROW - 1L) / HexViewLogic.BYTES_PER_ROW
        rowCount = rowsLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        subtitleView.text = "${ExplorerSort.sizeLabel(byteSource.length, decimal = false)}  ·  Hex"
        adapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun updateStatus() {
        val src = source ?: return
        val activeRow = if (selectedRow >= 0) selectedRow else listView.firstVisiblePosition.coerceAtLeast(0)
        val offset = activeRow.toLong() * HexViewLogic.BYTES_PER_ROW
        val offStr = "0x" + java.lang.Long.toHexString(offset).uppercase(Locale.US).padStart(offsetDigits, '0')
        statusView.text = "Offset $offStr  ·  ${String.format(Locale.US, "%,d", src.length)} bytes"
    }

    private fun copySelectedRow() {
        val src = source ?: return
        if (selectedRow < 0) return
        val offset = selectedRow.toLong() * HexViewLogic.BYTES_PER_ROW
        val n = src.read(offset, HexViewLogic.BYTES_PER_ROW, rowBuf)
        val line = HexViewLogic.formatRow(offset, rowBuf, n, offsetDigits)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("hex", line))
        toast("Row copied")
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
        searchToggleBtn.text = "Go"
    }

    private fun runSearch() {
        val src = source ?: return
        val raw = findInput.text.toString()
        val needle = if (hexMode) HexViewLogic.parseHexBytes(raw) else raw.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
        if (needle == null || needle.isEmpty()) {
            findStatus.text = if (hexMode) "Enter pairs of hex digits" else "Type text to find"
            return
        }
        cancelSearch()
        val stop = AtomicBoolean(false)
        searchCancel = stop
        searching = true
        searchToggleBtn.text = "Stop"
        searchHits = emptyList()
        searchCursor = -1
        findStatus.text = "Searching…"
        Thread {
            val hits = HexViewLogic.findAll(src, needle, cancelled = { stop.get() }) { found, scanned ->
                ui.post {
                    if (searchCancel === stop && !isFinishing && !isDestroyed) {
                        val pct = if (src.length > 0) ((scanned * 100L) / src.length).toInt().coerceIn(0, 100) else 100
                        findStatus.text = "$found matches ($pct%)"
                    }
                }
            }
            ui.post {
                if (searchCancel === stop && !isFinishing && !isDestroyed) {
                    searching = false
                    searchCancel = null
                    searchToggleBtn.text = "Go"
                    searchHits = hits
                    if (hits.isEmpty()) {
                        findStatus.text = if (stop.get()) "Stopped (0 matches)" else "0 matches"
                    } else {
                        searchCursor = 0
                        jumpToOffset(hits[0])
                        updateFindCounter()
                    }
                }
            }
        }.start()
    }

    private fun stepHit(dir: Int) {
        if (searchHits.isEmpty()) return
        searchCursor = if (searchCursor < 0) 0 else (searchCursor + dir + searchHits.size) % searchHits.size
        jumpToOffset(searchHits[searchCursor])
        updateFindCounter()
    }

    private fun updateFindCounter() {
        val capped = if (searchHits.size >= HexViewLogic.DEFAULT_SEARCH_CAP) "+" else ""
        findStatus.text = "${searchCursor + 1} of ${searchHits.size}$capped"
    }

    private fun jumpToOffset(offset: Long) {
        val row = (offset / HexViewLogic.BYTES_PER_ROW).coerceIn(0L, maxOf(0, rowCount - 1).toLong()).toInt()
        selectedRow = row
        copyButton.visibility = View.VISIBLE
        adapter.notifyDataSetChanged()
        listView.setSelectionFromTop(row, dp(24))
        updateStatus()
    }

    private fun askJumpOffset() {
        val src = source ?: return
        val input = EditText(this).apply {
            hint = "Hex offset (e.g. 0x1A0 or 1A0)"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppTheme.dialogBuilder(this)
            .setTitle("Jump to offset")
            .setView(box)
            .setPositiveButton("Jump") { _, _ ->
                val off = HexViewLogic.parseOffset(input.text.toString(), maxOf(0L, src.length - 1L))
                if (off != null) jumpToOffset(off) else toast("Invalid hex offset")
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, this)
    }

    override fun onDestroy() {
        searchCancel?.set(true)
        pagedFile?.close()
        super.onDestroy()
    }

    private inner class HexRowAdapter : BaseAdapter() {
        override fun getCount(): Int = if (source == null) 0 else rowCount
        override fun getItem(position: Int): Long = position.toLong() * HexViewLogic.BYTES_PER_ROW
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(this@HexViewerActivity).apply {
                typeface = Typeface.MONOSPACE
                textSize = 11.5f
                setPadding(dp(8), dp(2), dp(8), dp(2))
                setHorizontallyScrolling(true)
            }
            val src = source ?: return tv
            val offset = position.toLong() * HexViewLogic.BYTES_PER_ROW
            val count = src.read(offset, HexViewLogic.BYTES_PER_ROW, rowBuf)
            tv.text = HexViewLogic.formatRow(offset, rowBuf, count, offsetDigits)
            val selected = position == selectedRow
            tv.setTextColor(if (selected) palette.accent else palette.text)
            tv.setBackgroundColor(if (selected) palette.surface else Color.TRANSPARENT)
            return tv
        }
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ARCHIVE_PATH = "archive_path"
        private const val EXTRA_ENTRY_PATH = "entry_path"
        private const val EXTRA_TITLE = "title"
        private const val MAX_ARCHIVE_BYTES = 4L * 1024L * 1024L

        fun launch(context: Context, file: File) {
            context.startActivity(
                Intent(context, HexViewerActivity::class.java)
                    .putExtra(EXTRA_PATH, file.absolutePath)
                    .putExtra(EXTRA_TITLE, file.name),
            )
        }

        fun launchArchiveEntry(context: Context, archive: File, entryPath: String) {
            context.startActivity(
                Intent(context, HexViewerActivity::class.java)
                    .putExtra(EXTRA_ARCHIVE_PATH, archive.absolutePath)
                    .putExtra(EXTRA_ENTRY_PATH, entryPath)
                    .putExtra(EXTRA_TITLE, "${entryPath.substringAfterLast('/')} (${archive.name})"),
            )
        }
    }
}
