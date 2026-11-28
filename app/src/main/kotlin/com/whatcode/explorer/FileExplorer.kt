package com.whatcode.explorer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.text.InputType
import android.text.format.DateFormat
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.EditText
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.apk.ApkDialogs
import com.whatcode.apk.ApkNames
import com.whatcode.apk.ArchiveBrowser
import com.whatcode.apk.BinaryXml
import com.whatcode.apk.HexViewerActivity
import com.whatcode.bigfile.LargeFileViewerActivity
import com.whatcode.explorer.logic.ExplorerAccess
import com.whatcode.explorer.logic.ExplorerBookmarks
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.explorer.logic.ExplorerTrash
import com.whatcode.explorer.logic.FileTemplates
import com.whatcode.explorer.logic.RowMenu
import com.whatcode.files.FileOpException
import com.whatcode.files.FileNames
import com.whatcode.files.FileOps
import com.whatcode.files.NotTextException
import com.whatcode.files.SharedFiles
import com.whatcode.files.TextFile
import com.whatcode.files.StorageAccess
import com.whatcode.settings.ApkSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The file explorer: the screen the app starts on. It lists the folder it is in, folders first, with the path at the
 * top (tap a step of it to go there) and a button on the left for the sidebar. Tapping a folder goes into it and
 * tapping a file opens it in the editor, through [Host]. The menu at the end of a row (or a long press) renames,
 * copies, moves and deletes it; a copy or a move waits in a line under the path until it is pasted in another
 * folder. The round **+** button at the bottom right opens two smaller ones, New folder and New file. How the list
 * looks (details, compact or tiles, hidden files, the order) is in [options], which the sidebar changes, and [findFile]
 * is called from the sidebar's options.
 *
 * The phone's storage needs the access to all files (see [ExplorerAccess]). The explorer never asks for it when it
 * starts: the first time a folder is opened that needs it, it says why in one line and offers the system settings
 * page. Without it the explorer still shows what the app can reach.
 */
class FileExplorer(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val font: Typeface,
    private val host: Host,
) {
    /** What the explorer asks of the screen it sits on. */
    interface Host {
        /** Opens [file] in the editor. */
        fun openFile(file: File)

        /** Opens read-only text (e.g. a decoded archive XML or text entry) in the editor. */
        fun openReadOnlyText(name: String, content: String)

        /** Opens the sidebar. */
        fun openMenu()

        /** [from] was renamed or moved to [to] (a folder, or a file): open tabs that were in it follow. */
        fun pathMoved(from: String, to: String)

        /** [path] was deleted (a folder, or a file): open tabs that were in it no longer have a file. */
        fun pathDeleted(path: String)
    }

    /** Files and folders waiting to be pasted: copied, or moved when [move]. */
    private class Clip(val files: List<File>, val move: Boolean)

    /** One undoable delete/move batch kept for the Undo banner. */
    private sealed class UndoBatch(val summary: String) {
        class Trashed(val items: List<ExplorerTrash.TrashItem>) :
            UndoBatch(if (items.size == 1) "Moved ${items[0].name} to Trash" else "Moved ${items.size} items to Trash")
        class Moved(val moves: List<Pair<File, File>>) :
            UndoBatch(if (moves.size == 1) "Moved ${moves[0].second.name}" else "Moved ${moves.size} items")
    }

    private var lastUndo: UndoBatch? = null

    private fun trashRoot(): File = File(activity.filesDir, "trash")

    /** One row of the list: a file or folder, the way up, or a line of text. */
    private class Item(val entry: ExplorerSort.Entry? = null, val up: String? = null, val text: String? = null)

    /** The explorer's screen. It fills the space it is given and is hidden while the editor shows. */
    val view = FrameLayout(activity)

    /** The path, the line about what waits to be pasted, and the list: everything but the add button. */
    private val page = LinearLayout(activity)

    /** The layout, the hidden files and the order of the list. The sidebar's options and the settings change them. */
    val options = ExplorerOptions(activity)

    /** Where the explorer starts, how it moves and what taps do: Settings > File explorer. */
    private val settings = ExplorerSettings(activity)
    private val apkSettings = ApkSettings(activity)
    val isShown: Boolean get() = view.visibility == View.VISIBLE

    /** The phone's own storage, where the explorer starts. */
    private val sharedRoot = ExplorerPaths.normalize(Environment.getExternalStorageDirectory().path)
    private val appFolders = ExplorerAccess.appFolders(sharedRoot, activity.packageName)

    /** The folder showing. */
    var path: String = remembered?.takeIf { File(it).isDirectory } ?: startFolder()
        private set

    /** Non-null when browsing inside a `.apk`, `.zip`, `.jar` or `.aar` archive (read-only). */
    private var archiveFile: File? = null
    private var archivePath: String = ""

    private val ui = Handler(Looper.getMainLooper())
    private val crumbs = LinearLayout(activity)
    private val crumbScroll = HorizontalScrollView(activity)

    /** The box that takes the place of the path while a folder is typed in it (see [ExplorerSettings.pathBox]). */
    private val pathField = EditText(activity)

    /** The line at the top of the list while it is pulled down to be read again. */
    private val pullNote = TextView(activity)
    private val pull = PullToRefresh(activity) { state -> showPull(state) }
    private val note = LinearLayout(activity)
    private val noteText = TextView(activity)
    private val noteCancel = TextView(activity)
    private val noteAction = TextView(activity)
    private val listView = ListView(activity)
    private val gridView = GridView(activity)

    /** A line of text above the tiles (an empty folder), as a tile cannot hold one. */
    private val message = TextView(activity)

    /** What the folder showing holds, as it was read; null when it could not be read. */
    private var loaded: List<ExplorerSort.Entry>? = null

    /** False until the first read of a folder is done. */
    private var ready = false
    private var items: List<Item> = emptyList()
    private var token = 0

    /** What is picked in the selection, by path, in the order it was picked. Empty when nothing is selected. */
    private val selected = LinkedHashMap<String, ExplorerSort.Entry>()

    /** The folder that was asked for while the access to all files was not yet given; opened when it is. */
    private var waiting: String? = null

    private val rows = ExplorerRows(activity, palette, font)
    private val add = ExplorerAddButton(activity, palette, { newFolder() }, { newFile() })

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ building

    private fun build() {
        view.setBackgroundColor(palette.background)
        page.orientation = LinearLayout.VERTICAL
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        bar.addView(
            Icons.button(activity, Icon.MENU, "Menu", palette.text) { host.openMenu() },
            LinearLayout.LayoutParams(dp(44), dp(40))
        )
        crumbs.orientation = LinearLayout.HORIZONTAL
        crumbs.gravity = Gravity.CENTER_VERTICAL
        crumbScroll.isHorizontalScrollBarEnabled = false
        crumbScroll.overScrollMode = View.OVER_SCROLL_NEVER
        crumbScroll.addView(crumbs, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bar.addView(crumbScroll, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
        buildPathField()
        bar.addView(pathField, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        page.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // A thin line under the bar, as under the editor's.
        page.addView(View(activity).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        note.orientation = LinearLayout.HORIZONTAL
        note.gravity = Gravity.CENTER_VERTICAL
        note.setBackgroundColor(palette.accentSoft)
        note.setPadding(dp(14), dp(6), dp(6), dp(6))
        note.visibility = View.GONE
        noteText.setTextColor(palette.text)
        noteText.textSize = 12f
        note.addView(noteText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        for ((action, color) in listOf(noteCancel to palette.muted, noteAction to palette.accent)) {
            action.setTextColor(color)
            action.textSize = 13f
            action.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            action.gravity = Gravity.CENTER
            action.setPadding(dp(12), 0, dp(12), 0)
            note.addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        }
        noteCancel.visibility = View.GONE
        page.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        pullNote.setTextColor(palette.accent)
        pullNote.textSize = 13f
        pullNote.gravity = Gravity.CENTER
        pullNote.setPadding(0, dp(10), 0, dp(10))
        pullNote.visibility = View.GONE
        page.addView(pullNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        message.setTextColor(palette.muted)
        message.textSize = 13f
        message.setPadding(dp(14), dp(14), dp(14), dp(6))
        message.visibility = View.GONE
        page.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        listView.divider = null
        listView.dividerHeight = 0
        // Room under the last row, so the add button never covers it.
        listView.setPadding(0, 0, 0, dp(88))
        listView.clipToPadding = false
        gridView.numColumns = GridView.AUTO_FIT
        gridView.columnWidth = dp(92)
        gridView.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        gridView.horizontalSpacing = dp(2)
        gridView.verticalSpacing = dp(2)
        gridView.setPadding(dp(8), dp(8), dp(8), dp(88))
        gridView.clipToPadding = false
        gridView.visibility = View.GONE
        for (list in listOf<AbsListView>(listView, gridView)) {
            list.selector = ColorDrawable(Color.TRANSPARENT)
            list.overScrollMode = View.OVER_SCROLL_NEVER
            list.adapter = adapter
            list.setOnItemClickListener { _, _, position, _ ->
                add.setOpen(false)
                items.getOrNull(position)?.let { tap(it) }
            }
            list.setOnItemLongClickListener { _, row, position, _ ->
                val entry = items.getOrNull(position)?.entry
                if (entry != null) {
                    if (settings.longPress == ExplorerSettings.LongPress.SELECT) toggle(entry) else rowMenu(entry, row)
                }
                entry != null
            }
            list.setOnTouchListener { _, event ->
                if (settings.pullToRefresh) pull.onTouch(list, event)
                false
            }
        }
        val body = FrameLayout(activity)
        body.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(gridView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        view.addView(
            add.view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(16))
            }
        )
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = items[position].text == null
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            items[position].let { item ->
                if (options.mode == ExplorerSort.Mode.GRID) rows.tile(item.entry, look)
                else rows.row(item.entry, item.text, look) { entry, anchor -> rowMenu(entry, anchor) }
            }
    }

    // ------------------------------------------------------------------ the path at the top

    private fun volumeFor(folder: String): StorageRoots.Root? {
        val norm = ExplorerPaths.normalize(folder)
        return StorageRoots.list(activity)
            .filter { ExplorerPaths.isInside(norm, it.path) }
            .maxByOrNull { it.path.length }
    }

    private fun rootFor(folder: String): String = volumeFor(folder)?.path ?: "/"

    private fun renderCrumbs() {
        crumbs.removeAllViews()
        val vol = volumeFor(path)
        val root = vol?.path ?: "/"
        val rootLabel = vol?.label ?: "/"
        val steps = ExplorerPaths.crumbs(path, root, rootLabel)
        steps.forEachIndexed { i, step ->
            val last = i == steps.lastIndex
            if (i > 0) {
                crumbs.addView(
                    TextView(activity).apply {
                        text = "\u203A"
                        setTextColor(palette.muted)
                        textSize = 16f
                    }
                )
            }
            crumbs.addView(
                TextView(activity).apply {
                    text = step.label
                    val inArch = archiveFile != null
                    typeface = if (last && !inArch) Typeface.create(font, Typeface.BOLD) else font
                    setTextColor(if (last && !inArch) palette.accent else palette.text)
                    textSize = 15f
                    maxLines = 1
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 8f)
                    setOnClickListener {
                        if (inArch || !last) goTo(step.path) else if (settings.pathBox) editPath()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        val arch = archiveFile
        if (arch != null) {
            val parts = archivePath.trim('/').split('/').filter { it.isNotEmpty() }
            fun addArchStep(label: String, sub: String, bold: Boolean) {
                crumbs.addView(
                    TextView(activity).apply {
                        text = "\u203A"
                        setTextColor(palette.muted)
                        textSize = 16f
                    },
                )
                crumbs.addView(
                    TextView(activity).apply {
                        text = label
                        typeface = if (bold) Typeface.create(font, Typeface.BOLD) else font
                        setTextColor(if (bold) palette.accent else palette.text)
                        textSize = 15f
                        maxLines = 1
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), 0, dp(8), 0)
                        background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 8f)
                        setOnClickListener { if (!bold) goToArchive(arch, sub) }
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
            addArchStep(arch.name, "", parts.isEmpty())
            val built = StringBuilder()
            parts.forEachIndexed { idx, part ->
                built.append(part).append('/')
                val sub = built.toString()
                addArchStep(part, sub, idx == parts.lastIndex)
            }
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    // ------------------------------------------------------------------ the list

    private fun tap(item: Item) {
        val arch = archiveFile
        val up = item.up
        val entry = item.entry
        if (arch != null) {
            when {
                up != null -> if (up.isEmpty()) {
                    if (archivePath.isEmpty()) exitArchive() else goToArchive(arch, "")
                } else {
                    goToArchive(arch, up)
                }
                entry == null -> Unit
                entry.isDir -> goToArchive(arch, entry.path)
                else -> openArchiveEntry(arch, entry)
            }
            return
        }
        when {
            up != null -> goTo(up)
            entry == null -> Unit
            selected.isNotEmpty() -> toggle(entry)
            entry.isDir -> goTo(entry.path)
            reachable(entry.path) -> openTapped(File(entry.path))
        }
    }

    /**
     * Does what Settings > File explorer > Taps says for a tapped file. When a text file goes to the editor and a file
     * that isn't text only gets the editor's message, there is nothing to find out first. Otherwise the start of the
     * file is read off the main thread to tell which kind it is, and the answer is dropped if the folder changed.
     */
    private fun openTapped(file: File) {
        if (ApkNames.isApkFile(file.name)) {
            ApkDialogs.onTap(
                activity = activity,
                file = file,
                settings = apkSettings,
                palette = palette,
                font = font,
                onShare = { share(listOf(it)) },
                onBrowse = { browseArchive(it) },
                onExtract = { extractArchive(it) },
                onRefresh = { refresh() },
            )
            return
        }
        if (ArchiveBrowser.isArchive(file.name)) {
            ApkDialogs.showArchiveSheet(
                activity = activity,
                file = file,
                onBrowse = { browseArchive(it) },
                onExtract = { extractArchive(it) },
                onHex = { HexViewerActivity.launch(activity, it) },
                onOpenWith = { openWith(it) },
                onShare = { share(listOf(it)) },
            )
            return
        }
        val textTap = settings.textTap
        val otherTap = settings.otherTap
        if (textTap == ExplorerSettings.TextTap.EDITOR && otherTap == ExplorerSettings.OtherTap.MESSAGE) {
            recordOpened(file)
            host.openFile(file)
            return
        }
        val mine = token
        Thread {
            val text = try {
                TextFile.isText(file.inputStream())
            } catch (_: Exception) {
                null
            }
            ui.post {
                if (mine != token || !alive()) return@post
                when {
                    // It could not be read: the editor says why.
                    text == null -> host.openFile(file)
                    !text && otherTap == ExplorerSettings.OtherTap.OPEN_WITH -> openWith(file)
                    !text -> toast(NotTextException().message.orEmpty())
                    textTap == ExplorerSettings.TextTap.ASK -> askHowToOpen(file)
                    else -> {
                        recordOpened(file)
                        host.openFile(file)
                    }
                }
            }
        }.start()
    }

    private fun openWith(file: File) {
        if (!SharedFiles.openWith(activity, file)) toast("No app on the phone can open ${file.name}.")
    }

    private fun openInViewer(file: File) {
        val mine = token
        Thread {
            val ok = try {
                TextFile.isText(file.inputStream())
            } catch (_: Exception) {
                null
            }
            ui.post {
                if (mine != token || !alive()) return@post
                when (ok) {
                    null -> toast("Could not read ${file.name}.")
                    false -> AppTheme.dialogBuilder(activity)
                        .setTitle("Not a text file")
                        .setMessage("${file.name} isn't text. Open it in the read-only Hex viewer?")
                        .setPositiveButton("Hex viewer") { _, _ -> HexViewerActivity.launch(activity, file) }
                        .setNegativeButton("Cancel", null)
                        .show().also { AppTheme.styleDialog(it, activity) }
                    true -> LargeFileViewerActivity.launch(activity, file)
                }
            }
        }.start()
    }

    private fun askHowToOpen(file: File) {
        AppTheme.dialogBuilder(activity)
            .setTitle("Open ${file.name}")
            .setItems(arrayOf("In the code editor", "With another app")) { _, which ->
                if (which == 0) {
                    recordOpened(file)
                    host.openFile(file)
                } else {
                    openWith(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    private fun recordOpened(file: File) {
        settings.recentFiles = ExplorerBookmarks.recordRecent(settings.recentFiles, file.absolutePath, file.name)
    }

    /** Shows the folder [target]. Does nothing, after asking about the access to all files, if that is needed. */
    fun goTo(target: String) {
        val folder = ExplorerPaths.normalize(target)
        if (!reachable(folder)) return
        add.setOpen(false)
        closePath()
        archiveFile = null
        archivePath = ""
        if (folder != path) selected.clear()
        path = folder
        remembered = folder
        settings.lastFolder = folder
        options.applyFolderSort(folder)
        load()
    }

    private fun browseArchive(file: File) {
        if (!file.isFile || !file.canRead()) {
            toast("Could not open ${file.name}.")
            return
        }
        selected.clear()
        goToArchive(file, "")
    }

    private fun goToArchive(file: File, subPath: String) {
        add.setOpen(false)
        closePath()
        archiveFile = file
        archivePath = subPath
        load()
    }

    private fun exitArchive() {
        archiveFile = null
        archivePath = ""
        load()
    }

    private fun extractArchive(file: File, onlySubPath: String? = null) {
        ApkDialogs.extractArchive(
            activity = activity,
            archive = file,
            currentFolder = File(path),
            settings = apkSettings,
            onlySubPath = onlySubPath,
            onRefresh = { if (archiveFile == null) load() },
        )
    }

    private fun openArchiveEntry(archive: File, entry: ExplorerSort.Entry) {
        val mine = token
        Thread {
            var decodedText: String? = null
            var tooBigOrError: String? = null
            try {
                val bytes = ArchiveBrowser.readEntryBytes(archive, entry.path)
                if (entry.name.lowercase(Locale.ROOT).endsWith(".xml") && BinaryXml.isBinaryXml(bytes)) {
                    val arsc = try {
                        val arscBytes = ArchiveBrowser.readEntryBytes(archive, "resources.arsc", 16L * 1024L * 1024L)
                        BinaryXml.ArscStrings.parse(arscBytes)
                    } catch (_: Exception) {
                        BinaryXml.ArscStrings.EMPTY
                    }
                    decodedText = BinaryXml.decode(bytes, arsc)
                } else if (TextFile.isText(ByteArrayInputStream(bytes))) {
                    decodedText = String(bytes, Charsets.UTF_8)
                }
            } catch (e: SecurityException) {
                tooBigOrError = e.message
            } catch (e: Exception) {
                tooBigOrError = e.message ?: "Could not read entry."
            }
            ui.post {
                if (mine != token || !alive()) return@post
                when {
                    decodedText != null -> host.openReadOnlyText("${entry.name} (${archive.name})", decodedText!!)
                    tooBigOrError != null -> toast(tooBigOrError!!)
                    else -> HexViewerActivity.launchArchiveEntry(activity, archive, entry.path)
                }
            }
        }.start()
    }

    /** Reads the folder again. */
    fun refresh() = load()

    private fun load() {
        val mine = ++token
        renderCrumbs()
        showNote()
        val arch = archiveFile
        add.view.visibility = if (arch == null) View.VISIBLE else View.GONE
        if (arch != null) {
            val sub = archivePath
            Thread {
                val entries = try {
                    ArchiveBrowser.listFolder(arch, sub)
                } catch (_: Exception) {
                    null
                }
                ui.post {
                    if (mine != token || !alive()) return@post
                    pull.done()
                    show(entries)
                }
            }.start()
            return
        }
        val dir = File(path)
        Thread {
            val kids = try {
                dir.listFiles()
            } catch (_: SecurityException) {
                null
            }
            val counts = options.folderCounts
            val entries = kids?.map {
                val dir = it.isDirectory
                // A folder's items are counted only when asked for, and not through a link to a folder elsewhere.
                val count = if (counts && dir && mine == token) (it.list()?.size ?: -1) else -1
                ExplorerSort.Entry(it.name, it.path, dir, if (it.isFile) it.length() else 0L, it.lastModified(), count)
            }
            ui.post {
                if (mine != token || !alive()) return@post
                pull.done()
                show(entries)
            }
        }.start()
    }

    private fun show(entries: List<ExplorerSort.Entry>?) {
        loaded = entries
        // What was picked and is no longer there (deleted, moved) is dropped from the selection.
        if (entries == null) selected.clear() else selected.keys.retainAll(entries.map { it.path }.toSet())
        ready = true
        render()
        listView.setSelection(0)
        gridView.setSelection(0)
    }

    /** Lays the folder out as [options] say: shown or hidden files, the order, and rows or tiles. */
    private fun render() {
        val entries = loaded
        val list = ArrayList<Item>()
        val arch = archiveFile
        if (arch != null) {
            val up = if (archivePath.isEmpty()) "" else archivePath.trimEnd('/').substringBeforeLast('/', "").let {
                if (it.isEmpty()) "" else "$it/"
            }
            list.add(Item(up = up))
        } else {
            ExplorerPaths.parent(path, rootFor(path))?.let { list.add(Item(up = it)) }
        }
        when {
            entries == null -> list.add(
                Item(
                    text = if (arch != null) "Could not read ${arch.name}."
                    else if (StorageAccess.granted(activity)) "This folder can't be read."
                    else "This folder can't be read without access to all files."
                )
            )
            entries.isEmpty() -> list.add(Item(text = "(empty folder)"))
            else -> {
                val shown = ExplorerSort.arrange(entries, options.sort, options.descending, options.showHidden, options.order)
                if (shown.isEmpty()) list.add(Item(text = "(only hidden files here: turn on Hidden in the menu)"))
                shown.forEach { list.add(Item(entry = it)) }
            }
        }
        look = makeLook()
        // A tile cannot hold a line of text, so in the grid it goes above the tiles.
        val grid = options.mode == ExplorerSort.Mode.GRID
        gridView.columnWidth = dp(look.tileDp)
        val line = list.firstOrNull { it.text != null }?.text
        items = if (grid) list.filter { it.text == null } else list
        message.text = if (grid) line.orEmpty() else ""
        message.visibility = if (grid && line != null) View.VISIBLE else View.GONE
        listView.visibility = if (grid) View.GONE else View.VISIBLE
        gridView.visibility = if (grid) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    /** What the rows show, read from the settings each time the list is laid out. */
    private var look = makeLook()

    private var apkRefreshPending = false
    private val apkRefreshTask = Runnable {
        apkRefreshPending = false
        if (ready && alive()) adapter.notifyDataSetChanged()
    }

    private fun scheduleApkRowRefresh() {
        if (apkRefreshPending) return
        apkRefreshPending = true
        ui.postDelayed(apkRefreshTask, 40)
    }

    private fun makeLook(): ExplorerRows.Look {
        val clock = options.clock
        return ExplorerRows.Look(
            compact = options.mode == ExplorerSort.Mode.COMPACT,
            density = options.density,
            tileDp = options.tileSize.dp,
            showSize = options.showSize,
            showDate = options.showDate,
            dateStyle = options.dateStyle,
            hour24 = if (clock == ExplorerOptions.Clock.SYSTEM) DateFormat.is24HourFormat(activity) else clock == ExplorerOptions.Clock.H24,
            decimalSizes = options.decimalSizes,
            showExtensions = options.showExtensions,
            fileIcons = options.fileIcons,
            menuButton = settings.menuButton,
            now = System.currentTimeMillis(),
            selected = selected.keys.toSet(),
            apkRowMeta = apkSettings.showRowAppInfo,
            apkCacheMb = apkSettings.iconCacheMb,
            inArchive = archiveFile != null,
            onApkMetaLoaded = ::scheduleApkRowRefresh,
        )
    }

    // ------------------------------------------------------------------ typing a path, pulling to refresh, Back

    /** The folder to start in, as Settings > File explorer > Start and navigation says; Phone storage if it is gone. */
    private fun startFolder(): String {
        val wanted = when (settings.start) {
            ExplorerSettings.Start.PHONE -> null
            ExplorerSettings.Start.LAST -> settings.lastFolder
            ExplorerSettings.Start.FOLDER -> settings.startFolder
        }
        return wanted?.takeIf { it.startsWith("/") && File(it).isDirectory }?.let { ExplorerPaths.normalize(it) } ?: sharedRoot
    }

    private fun buildPathField() {
        pathField.visibility = View.GONE
        pathField.typeface = font
        pathField.textSize = 14f
        pathField.setTextColor(palette.text)
        pathField.setSingleLine()
        pathField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
        pathField.imeOptions = EditorInfo.IME_ACTION_GO
        pathField.background = AppTheme.card(activity, palette, 10f)
        pathField.setPadding(dp(10), 0, dp(10), 0)
        pathField.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                goToTyped(pathField.text.toString())
                true
            } else {
                false
            }
        }
        pathField.setOnFocusChangeListener { _, focused -> if (!focused) closePath() }
    }

    /** Turns the path into a box with the folder's path in it, to type another one. */
    private fun editPath() {
        pathField.setText(path)
        crumbScroll.visibility = View.GONE
        pathField.visibility = View.VISIBLE
        pathField.requestFocus()
        pathField.setSelection(pathField.text.length)
        keyboard().showSoftInput(pathField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closePath() {
        if (pathField.visibility != View.VISIBLE) return
        pathField.visibility = View.GONE
        crumbScroll.visibility = View.VISIBLE
        keyboard().hideSoftInputFromWindow(pathField.windowToken, 0)
    }

    private fun keyboard() = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /** Goes to the folder typed in the path box, or opens the file typed there; says so when there is neither. */
    private fun goToTyped(typed: String) {
        val text = typed.trim()
        // A path that does not start with / is read from the folder showing; ".." and "." work as in a terminal.
        val target = ExplorerPaths.normalize(File(if (text.startsWith("/")) text else "$path/$text").normalize().path)
        val file = File(target)
        when {
            text.isEmpty() -> closePath()
            file.isDirectory -> goTo(target)
            file.isFile -> {
                closePath()
                if (reachable(target)) openTapped(file)
            }
            !StorageAccess.granted(activity) && ExplorerAccess.needsAllFiles(target, sharedRoot, appFolders) -> goTo(target)
            else -> pathField.error = "There is no folder or file at this path."
        }
    }

    private fun showPull(state: PullToRefresh.State) {
        pullNote.visibility = if (state == PullToRefresh.State.IDLE) View.GONE else View.VISIBLE
        pullNote.text = when (state) {
            PullToRefresh.State.PULLING -> "Pull down to refresh"
            PullToRefresh.State.READY -> "Release to refresh"
            PullToRefresh.State.REFRESHING -> "Refreshing\u2026"
            PullToRefresh.State.IDLE -> ""
        }
        if (state == PullToRefresh.State.REFRESHING) load()
    }

    /**
     * Back in the explorer: closes the path box if it is open, or goes up a folder when Settings > File explorer says
     * Back does. Returns false when Back should leave the app instead.
     */
    fun back(): Boolean {
        if (pathField.visibility == View.VISIBLE) {
            closePath()
            return true
        }
        if (selected.isNotEmpty()) {
            clearSelection()
            return true
        }
        val arch = archiveFile
        if (arch != null) {
            if (archivePath.isEmpty()) {
                exitArchive()
            } else {
                val parentSub = archivePath.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                goToArchive(arch, parentSub)
            }
            return true
        }
        if (!settings.backGoesUp) return false
        val up = ExplorerPaths.parent(path, rootFor(path)) ?: return false
        goTo(up)
        return true
    }

    // ------------------------------------------------------------------ file operations

    /**
     * Runs [job] off the main thread, then [done] with what it returned. A failure is shown as the message it came
     * with. The folder is read again afterwards either way, as a failed copy may have left something behind.
     */
    private fun <T> work(job: () -> T, done: (T) -> Unit = {}) {
        Thread {
            var value: T? = null
            var error: String? = null
            try {
                value = job()
            } catch (e: FileOpException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            ui.post {
                if (!alive()) return@post
                if (error != null) {
                    val hint = if (!StorageAccess.granted(activity) && needsAccessHere()) " Access to all files is off." else ""
                    toast(error + hint)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    done(value as T)
                }
                load()
            }
        }.start()
    }

    /** New file: picks a template and name and creates the file in the folder showing. Called by the add button. */
    fun newFile() {
        val templates = FileTemplates.ALL
        var chosenIdx = 0
        val input = EditText(activity).apply {
            hint = templates[0].defaultName
            typeface = font
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val tplBtn = TextView(activity).apply {
            text = "Template: ${templates[0].label}  \u25BE"
            textSize = 13f
            setTextColor(palette.accent)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        tplBtn.setOnClickListener {
            val labels = templates.map { it.label }.toTypedArray()
            AppTheme.dialogBuilder(activity)
                .setTitle("Choose template")
                .setItems(labels) { _, which ->
                    chosenIdx = which
                    val tpl = templates[which]
                    tplBtn.text = "Template: ${tpl.label}  \u25BE"
                    if (input.text.isNullOrBlank() || templates.any { it.defaultName == input.text.toString().trim() }) {
                        input.setText(tpl.defaultName)
                        val dot = tpl.defaultName.lastIndexOf('.')
                        input.setSelection(0, if (dot > 0) dot else tpl.defaultName.length)
                    }
                    input.hint = tpl.defaultName
                }
                .show().also { AppTheme.styleDialog(it, activity) }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(tplBtn)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppTheme.dialogBuilder(activity)
            .setTitle("New file")
            .setView(box)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, activity)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val raw = input.text.toString().trim()
            val tpl = templates[chosenIdx]
            val name = raw.ifEmpty { if (tpl.id != "empty") tpl.defaultName else "" }
            val problem = FileNames.problem(name)
            if (problem != null) {
                input.error = problem
            } else {
                dialog.dismiss()
                work({
                    val created = FileOps.createFile(File(path), name)
                    val body = FileTemplates.render(tpl.id, created.name)
                    if (body.isNotEmpty()) created.writeText(body, Charsets.UTF_8)
                    created
                })
            }
        }
        input.requestFocus()
    }

    /** New folder: asks for a name and makes a folder in the folder showing. Called by the add button. */
    fun newFolder() {
        askName(activity, font, "New folder", "", "Create") { name -> work({ FileOps.createFolder(File(path), name) }) }
    }

    // ------------------------------------------------------------------ the selection

    /** Picks [entry] for the selection, or drops it if it is picked already. The first pick starts the selection. */
    private fun toggle(entry: ExplorerSort.Entry) {
        if (selected.remove(entry.path) == null) selected[entry.path] = entry
        selectionChanged()
    }

    private fun clearSelection() {
        if (selected.isEmpty()) return
        selected.clear()
        selectionChanged()
    }

    private fun selectAll() {
        for (entry in items.mapNotNull { it.entry }) selected[entry.path] = entry
        selectionChanged()
    }

    private fun selectionChanged() {
        look = makeLook()
        adapter.notifyDataSetChanged()
        showNote()
    }

    /** The menu of the selection: Select all, then the actions of the row menu that work on many things. */
    private fun selectionMenu(anchor: View) {
        val chosen = selected.values.toList()
        val popup = AppTheme.popup(activity, anchor, Gravity.END)
        popup.menu.add(0, SELECT_ALL, 0, "Select all")
        RowMenu.actionsFor(settings.rowMenu, chosen.size, chosen.count { it.isDir }).forEachIndexed { i, action ->
            popup.menu.add(0, action.ordinal + 1, i + 1, action.label)
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == SELECT_ALL) selectAll() else act(RowMenu.Action.values()[item.itemId - 1], chosen)
            true
        }
        popup.show()
    }

    // ------------------------------------------------------------------ the row menu and what it does

    /** The menu of one row, with the actions that Settings > File explorer > Row menu shows, in its order. */
    private fun rowMenu(entry: ExplorerSort.Entry, anchor: View) {
        val arch = archiveFile
        if (arch != null) {
            val popup = AppTheme.popup(activity, anchor, Gravity.END)
            popup.menu.add(0, 1, 0, "Extract")
            if (!entry.isDir) popup.menu.add(0, 2, 1, "Hex viewer")
            popup.menu.add(0, 3, 2, "Copy entry path")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> extractArchive(arch, entry.path)
                    2 -> HexViewerActivity.launchArchiveEntry(activity, arch, entry.path)
                    else -> copyText(entry.path)
                }
                true
            }
            popup.show()
            return
        }
        val isApk = !entry.isDir && ApkNames.isApkFile(entry.name)
        val isArchive = !entry.isDir && ArchiveBrowser.isArchive(entry.name)
        val actions = RowMenu.actionsFor(settings.rowMenu, 1, if (entry.isDir) 1 else 0, isApk, isArchive)
        if (actions.isEmpty()) {
            toast("Nothing in the row menu fits this one. Change it in Settings > File explorer > Row menu.")
            return
        }
        val popup = AppTheme.popup(activity, anchor, Gravity.END)
        actions.forEachIndexed { i, action -> popup.menu.add(0, action.ordinal, i, action.label) }
        popup.setOnMenuItemClickListener { item ->
            act(RowMenu.Action.values()[item.itemId], listOf(entry))
            true
        }
        popup.show()
    }

    /** Does [action] on [chosen], from the row menu (one thing) or the selection (one or more). */
    private fun act(action: RowMenu.Action, chosen: List<ExplorerSort.Entry>) {
        val files = chosen.map { File(it.path) }
        val one = chosen.singleOrNull()
        clearSelection()
        when (action) {
            RowMenu.Action.RENAME -> one?.let { rename(it) }
            RowMenu.Action.COPY -> hold(files, move = false)
            RowMenu.Action.MOVE -> hold(files, move = true)
            RowMenu.Action.DELETE -> delete(chosen)
            RowMenu.Action.SHARE -> share(files)
            RowMenu.Action.PROPERTIES -> one?.let { PropertiesDialog.show(activity, File(it.path), options.decimalSizes) { p -> copyText(p) } }
            RowMenu.Action.COPY_PATH -> copyText(files.joinToString("\n") { it.absolutePath })
            RowMenu.Action.OPEN_WITH -> one?.let { openWith(File(it.path)) }
            RowMenu.Action.OPEN_VIEWER -> one?.let { openInViewer(File(it.path)) }
            RowMenu.Action.HEX_VIEWER -> one?.let { HexViewerActivity.launch(activity, File(it.path)) }
            RowMenu.Action.BOOKMARK -> one?.let { toggleBookmark(it.path, it.name) }
            RowMenu.Action.COMPRESS -> compressFiles(files)
            RowMenu.Action.APK_INFO -> one?.let { ApkDialogs.showInfo(activity, File(it.path), apkSettings, palette, font, { f -> share(listOf(f)) }, { refresh() }) }
            RowMenu.Action.APK_INSTALL -> one?.let { ApkDialogs.startInstall(activity, File(it.path), apkSettings, palette, font, { f -> share(listOf(f)) }, { refresh() }) }
            RowMenu.Action.BROWSE_CONTENTS -> one?.let { browseArchive(File(it.path)) }
            RowMenu.Action.EXTRACT -> one?.let { extractArchive(File(it.path)) }
        }
    }

    private fun copyText(text: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", text))
        toast(if (text.contains('\n')) "Paths copied" else "Path copied")
    }

    private fun share(files: List<File>) {
        val shareable = files.filter { it.isFile }
        if (shareable.isEmpty()) {
            toast("A folder can't be shared.")
            return
        }
        val sent = try {
            SharedFiles.share(activity, shareable)
        } catch (_: Exception) {
            false
        }
        if (!sent) toast("No app on the phone can take ${if (shareable.size == 1) shareable[0].name else "these files"}.")
    }

    private fun rename(entry: ExplorerSort.Entry) {
        askName(activity, font, "Rename", entry.name, "Rename") { name ->
            work({ FileOps.rename(File(entry.path), name) }) { renamed ->
                host.pathMoved(entry.path, renamed.path)
                settings.bookmarks = ExplorerBookmarks.rebaseBookmarks(settings.bookmarks, entry.path, renamed.path)
                settings.recentFiles = settings.recentFiles.map { r ->
                    val moved = ExplorerPaths.rebase(r.path, entry.path, renamed.path)
                    if (moved != null) ExplorerBookmarks.RecentFile(moved, File(moved).name, r.openedAt) else r
                }
                // Copies and moves that wait to be pasted follow the file or folder that was renamed.
                clip = clip?.let { held ->
                    Clip(held.files.map { f -> ExplorerPaths.rebase(f.path, entry.path, renamed.path)?.let { File(it) } ?: f }, held.move)
                }
            }
        }
    }

    private fun toggleBookmark(path: String, label: String) {
        val (updated, added) = ExplorerBookmarks.toggleBookmark(settings.bookmarks, path, label)
        settings.bookmarks = updated
        toast(if (added) "Bookmarked $label" else "Removed bookmark")
    }

    /** Bookmarks or unbookmarks the folder currently open. */
    fun toggleBookmarkCurrent() {
        toggleBookmark(path, ExplorerPaths.name(path))
    }

    /** Opens a path picked from the sidebar (storage root, bookmarked folder/file, or recent file). */
    fun openPathFromSidebar(target: String) {
        val file = File(target)
        if (!reachable(target)) return
        when {
            file.isDirectory -> goTo(target)
            file.isFile -> {
                if (ArchiveBrowser.isArchive(file.name)) {
                    openTapped(file)
                } else {
                    recordOpened(file)
                    host.openFile(file)
                }
            }
            else -> toast("That path is no longer available.")
        }
    }

    private fun compressFiles(files: List<File>) {
        if (files.isEmpty()) return
        val stem = if (files.size == 1) files[0].nameWithoutExtension.ifEmpty { files[0].name } else ExplorerPaths.name(path).ifEmpty { "archive" }
        val initial = "$stem.zip"
        askName(activity, font, "Compress to ZIP", initial, "Compress") { rawName ->
            val zipName = if (rawName.lowercase(Locale.ROOT).endsWith(".zip")) rawName else "$rawName.zip"
            val destZip = File(path, FileNames.unique(zipName, folder = false) { File(path, it).exists() })
            val stop = AtomicBoolean(false)
            val box = BusyDialog(activity, ui, "Compressing $zipName…") { stop.set(true) }
            box.start()
            Thread {
                var summary: ArchiveBrowser.ExtractSummary? = null
                var error: String? = null
                try {
                    summary = ArchiveBrowser.compress(files, destZip, cancelled = { stop.get() }) { n, bytes ->
                        ui.post { box.update(bytes, 0L.coerceAtLeast(bytes)) }
                    }
                } catch (e: Exception) {
                    error = e.message ?: "Compression failed."
                }
                ui.post {
                    box.end()
                    if (!alive()) return@post
                    when {
                        error != null -> toast(error!!)
                        stop.get() -> toast("Compression cancelled")
                        summary != null -> toast("Created ${destZip.name} (${summary!!.filesExtracted} files)")
                    }
                    load()
                }
            }.start()
        }
    }

    private fun <T> eachOf(
        files: List<File>,
        cancelled: () -> Boolean = { false },
        onItem: (index: Int, file: File) -> Unit = { _, _ -> },
        step: (File) -> T,
    ): Pair<List<Pair<File, T>>, String?> {
        val done = ArrayList<Pair<File, T>>()
        for ((index, file) in files.withIndex()) {
            if (cancelled()) return done to "Cancelled"
            onItem(index, file)
            try {
                done.add(file to step(file))
            } catch (e: FileOpException) {
                return done to e.message
            } catch (e: Exception) {
                return done to (e.message ?: e.javaClass.simpleName)
            }
        }
        return done to null
    }

    private fun delete(chosen: List<ExplorerSort.Entry>) {
        val useTrash = settings.useTrash
        val targets = chosen.map { File(it.path) }
        val run = {
            val stop = AtomicBoolean(false)
            val showBusy = targets.size > 1 || chosen.any { it.isDir }
            val box = if (showBusy) BusyDialog(activity, ui, if (useTrash) "Moving to Trash…" else "Deleting…") { stop.set(true) } else null
            box?.start()
            Thread {
                val (done, error) = if (useTrash) {
                    val root = trashRoot()
                    eachOf(targets, cancelled = { stop.get() }, onItem = { idx, _ ->
                        if (box != null) ui.post { box.update((idx + 1).toLong(), targets.size.toLong()) }
                    }) { file ->
                        ExplorerTrash.moveToTrash(file, root)
                    }
                } else {
                    eachOf(targets, cancelled = { stop.get() }, onItem = { idx, _ ->
                        if (box != null) ui.post { box.update((idx + 1).toLong(), targets.size.toLong()) }
                    }) { file ->
                        FileOps.delete(file, cancelled = { stop.get() })
                    }
                }
                ui.post {
                    box?.end()
                    if (!alive()) return@post
                    for ((file, _) in done) {
                        host.pathDeleted(file.path)
                        settings.recentFiles = ExplorerBookmarks.removeRecent(settings.recentFiles, file.path)
                    }
                    clip = clip?.let { held ->
                        val left = held.files.filter { f -> done.none { ExplorerPaths.isInside(f.path, it.first.path) } }
                        if (left.isEmpty()) null else Clip(left, held.move)
                    }
                    if (useTrash && done.isNotEmpty()) {
                        val items = done.mapNotNull { it.second as? ExplorerTrash.TrashItem }
                        if (items.isNotEmpty()) lastUndo = UndoBatch.Trashed(items)
                    }
                    error?.let { toast(it) }
                    load()
                }
            }.start()
        }
        if (!settings.confirmDelete) {
            run()
            return
        }
        val one = chosen.singleOrNull()
        val folders = chosen.any { it.isDir }
        AppTheme.dialogBuilder(activity)
            .setTitle(if (one != null) (if (useTrash) "Move ${one.name} to Trash?" else "Delete ${one.name}?") else (if (useTrash) "Move ${chosen.size} items to Trash?" else "Delete ${chosen.size} items?"))
            .setMessage(
                when {
                    useTrash -> "You can restore it later from Trash or tap Undo."
                    one != null && !one.isDir -> "This can't be undone."
                    folders -> "Folders and everything in them will be deleted permanently."
                    else -> "This can't be undone."
                }
            )
            .setPositiveButton(if (useTrash) "Move to Trash" else "Delete") { _, _ -> run() }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    /** Copy or Move: the files and folders wait under the path, to be pasted into whichever folder is opened next. */
    private fun hold(files: List<File>, move: Boolean) {
        clip = Clip(files, move)
        showNote()
    }

    /**
     * Pastes what is held into the folder showing. When a name is taken there, Settings > File explorer > Safety says
     * what to do; if it says to ask, one question is asked for the whole paste. The names are looked at off the main
     * thread, and the answer is dropped if the folder changed meanwhile.
     */
    private fun paste() {
        val held = clip ?: return
        val folder = File(path)
        val fixed = settings.clash.ops
        if (fixed != null) {
            pasteWith(held, folder, fixed)
            return
        }
        val mine = token
        Thread {
            val taken = held.files.count { FileOps.clashes(it, folder) }
            ui.post {
                if (mine != token || !alive()) return@post
                if (taken == 0) pasteWith(held, folder, FileOps.Clash.KEEP_BOTH) else askClash(held, folder, taken)
            }
        }.start()
    }

    private fun askClash(held: Clip, folder: File, taken: Int) {
        val message = if (held.files.size == 1) "${held.files[0].name} is already in this folder." else "$taken of ${held.files.size} are already in this folder."
        AppTheme.dialogBuilder(activity)
            .setTitle("Name already taken")
            .setMessage(message)
            .setPositiveButton("Keep both") { _, _ -> pasteWith(held, folder, FileOps.Clash.KEEP_BOTH) }
            .setNeutralButton("Replace") { _, _ -> pasteWith(held, folder, FileOps.Clash.REPLACE) }
            .setNegativeButton("Skip") { _, _ -> pasteWith(held, folder, FileOps.Clash.SKIP) }
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    private fun pasteWith(held: Clip, folder: File, clash: FileOps.Clash) {
        val stop = AtomicBoolean(false)
        val showBusy = held.files.size > 1 || held.files.any { it.isDirectory }
        val box = if (showBusy) BusyDialog(activity, ui, if (held.move) "Moving…" else "Copying…") { stop.set(true) } else null
        box?.start()
        Thread {
            val (done, error) = eachOf(
                held.files,
                cancelled = { stop.get() },
                onItem = { idx, _ -> if (box != null) ui.post { box.update((idx + 1).toLong(), held.files.size.toLong()) } },
            ) {
                if (held.move) FileOps.move(it, folder, clash, cancelled = { stop.get() })
                else FileOps.copy(it, folder, clash, cancelled = { stop.get() })
            }
            ui.post {
                box?.end()
                if (!alive()) return@post
                if (held.move) {
                    val movedPairs = ArrayList<Pair<File, File>>()
                    for ((from, to) in done) {
                        if (to != null && to.path != from.path) {
                            host.pathMoved(from.path, to.path)
                            settings.bookmarks = ExplorerBookmarks.rebaseBookmarks(settings.bookmarks, from.path, to.path)
                            movedPairs.add(from to to)
                        }
                    }
                    if (movedPairs.isNotEmpty()) lastUndo = UndoBatch.Moved(movedPairs)
                    val finished = done.map { it.first.path }.toSet()
                    val left = held.files.filter { it.path !in finished }
                    if (clip === held) clip = if (left.isEmpty()) null else Clip(left, true)
                }
                error?.let { toast(it) }
                load()
            }
        }.start()
    }

    /** Undoes the last Trash delete or Move batch from the Undo banner. */
    private fun undoLastBatch() {
        val undo = lastUndo ?: return
        lastUndo = null
        when (undo) {
            is UndoBatch.Trashed -> work({
                val root = trashRoot()
                for (item in undo.items) ExplorerTrash.restore(item, root)
            }) { toast("Restored from Trash") }
            is UndoBatch.Moved -> work({
                for ((orig, current) in undo.moves) {
                    val origParent = orig.parentFile ?: continue
                    if (!origParent.exists()) origParent.mkdirs()
                    FileOps.move(current, origParent, FileOps.Clash.KEEP_BOTH)
                    host.pathMoved(current.path, orig.path)
                }
            }) { toast("Move undone") }
        }
    }

    /** Opens the Trash viewer dialog with Restore and Empty Trash actions. */
    fun openTrash() {
        val root = trashRoot()
        val trashed = ExplorerTrash.list(root)
        if (trashed.isEmpty()) {
            toast("Trash is empty.")
            return
        }
        val labels = trashed.map {
            "${it.name}  ·  ${ExplorerSort.sizeLabel(it.size, options.decimalSizes)}\nfrom ${it.originalPath.substringBeforeLast('/')}"
        }.toTypedArray()
        AppTheme.dialogBuilder(activity)
            .setTitle("Trash (${trashed.size})")
            .setItems(labels) { _, which ->
                val item = trashed[which]
                AppTheme.dialogBuilder(activity)
                    .setTitle(item.name)
                    .setMessage("Original folder: ${item.originalPath.substringBeforeLast('/')}")
                    .setPositiveButton("Restore") { _, _ ->
                        work({ ExplorerTrash.restore(item, root) }) { restored ->
                            toast("Restored ${restored.name}")
                        }
                    }
                    .setNeutralButton("Delete permanently") { _, _ ->
                        work({ ExplorerTrash.deleteFromTrash(item, root) }) { toast("Deleted permanently") }
                    }
                    .setNegativeButton("Cancel", null)
                    .show().also { AppTheme.styleDialog(it, activity) }
            }
            .setNeutralButton("Empty Trash") { _, _ ->
                AppTheme.dialogBuilder(activity)
                    .setTitle("Empty Trash?")
                    .setMessage("Permanently remove ${trashed.size} items from Trash?")
                    .setPositiveButton("Empty") { _, _ ->
                        work({ ExplorerTrash.empty(root) }) { count -> toast("Removed $count items") }
                    }
                    .setNegativeButton("Cancel", null)
                    .show().also { AppTheme.styleDialog(it, activity) }
            }
            .setNegativeButton("Close", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    // ------------------------------------------------------------------ Find file

    /** Find file: a box to type part of a name in, and the files and folders below this one that match. */
    fun findFile() {
        FindFileDialog(activity, palette, font).show(File(path)) { file ->
            if (file.isDirectory) goTo(file.path) else if (reachable(file.path)) {
                recordOpened(file)
                host.openFile(file)
            }
        }
    }

    // ------------------------------------------------------------------ access to all files

    /** True if [target] can be opened now. If not, the person is asked about the access to all files. */
    private fun reachable(target: String): Boolean {
        if (!ExplorerAccess.needsAllFiles(target, sharedRoot, appFolders) || StorageAccess.granted(activity)) return true
        waiting = target
        if (askedOnce) {
            toast("Turn on access to all files to open this folder.")
        } else {
            askedOnce = true
            askAccess()
        }
        return false
    }

    private fun askAccess() {
        AppTheme.dialogBuilder(activity)
            .setTitle("Allow access to all files")
            .setMessage("WhatCode needs access to all files to show the folders on your phone's storage.")
            .setPositiveButton("Open settings") { _, _ ->
                if (!StorageAccess.openSettings(activity)) toast("Couldn't open the settings. Look for WhatCode under Apps.")
            }
            .setNegativeButton("Not now") { _, _ -> waiting = null }
            .setOnCancelListener { waiting = null }
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    /**
     * The line under the path: what is waiting to be pasted, or else that the access to all files is off while the
     * explorer is somewhere that needs it.
     */
    private fun showNote() {
        val waitingToPaste = clip
        noteCancel.visibility = View.GONE
        val arch = archiveFile
        if (arch != null) {
            note.visibility = View.VISIBLE
            noteText.text = "Inside ${arch.name} (read-only)"
            noteAction.text = "Extract all"
            noteAction.setOnClickListener { extractArchive(arch, archivePath.ifEmpty { null }) }
            noteCancel.text = "Close"
            noteCancel.visibility = View.VISIBLE
            noteCancel.setOnClickListener { exitArchive() }
            return
        }
        if (selected.isNotEmpty()) {
            note.visibility = View.VISIBLE
            noteText.text = "${selected.size} selected"
            noteAction.text = "Actions"
            noteAction.setOnClickListener { selectionMenu(it) }
            noteCancel.text = "Cancel"
            noteCancel.visibility = View.VISIBLE
            noteCancel.setOnClickListener { clearSelection() }
            return
        }
        if (waitingToPaste != null) {
            note.visibility = View.VISIBLE
            val what = if (waitingToPaste.files.size == 1) waitingToPaste.files[0].name else "${waitingToPaste.files.size} items"
            noteText.text = (if (waitingToPaste.move) "Moving " else "Copying ") + what
            noteAction.text = "Paste here"
            noteAction.setOnClickListener { paste() }
            noteCancel.text = "Cancel"
            noteCancel.visibility = View.VISIBLE
            noteCancel.setOnClickListener {
                clip = null
                showNote()
            }
            return
        }
        val undo = lastUndo
        if (undo != null) {
            note.visibility = View.VISIBLE
            noteText.text = undo.summary
            noteAction.text = "Undo"
            noteAction.setOnClickListener { undoLastBatch() }
            noteCancel.text = "Dismiss"
            noteCancel.visibility = View.VISIBLE
            noteCancel.setOnClickListener {
                lastUndo = null
                showNote()
            }
            return
        }
        val needed = !StorageAccess.granted(activity) && needsAccessHere()
        note.visibility = if (needed) View.VISIBLE else View.GONE
        if (!needed) return
        noteText.text = "Access to all files is off, so some folders are not shown."
        noteAction.text = "Allow"
        noteAction.setOnClickListener { askAccess() }
    }

    /** True when the folder showing is on the phone's storage and is not one of the app's own folders. */
    private fun needsAccessHere(): Boolean =
        ExplorerPaths.isInside(path, sharedRoot) && appFolders.none { ExplorerPaths.isInside(path, it) }

    /** Call when the screen comes back: picks up a grant made in the settings, and reads the folder again. */
    fun onResume() {
        val target = waiting
        waiting = null
        if (target != null && StorageAccess.granted(activity)) {
            // What was asked for may have been a file (a tap, or a Find file result): open it, don't show it as a folder.
            if (File(target).isFile) host.openFile(File(target)) else goTo(target)
        } else {
            load()
        }
    }

    /** The folder picked in Android's picker, if it is on the phone's own storage; false when it is not. */
    fun openPicked(treeId: String): Boolean {
        val folder = ExplorerPaths.fromTreeId(treeId, sharedRoot) ?: return false
        goTo(folder)
        return true
    }

    init {
        build()
        options.applyFolderSort(path)
        options.onChange = {
            options.recordFolderSort(path)
            if (ready) render()
        }
        load()
    }

    companion object {
        /** Where the explorer was, so it is still there when the screen is built again (a change of theme). */
        private var remembered: String? = null

        /** Whether the access to all files was asked about since the app started; it is not asked about again. */
        private var askedOnce = false

        /** What was copied or moved and waits to be pasted. */
        private var clip: Clip? = null

        /** The id of Select all in the menu of the selection; the actions use their position in [RowMenu.Action] plus one. */
        private const val SELECT_ALL = 0
    }
}
