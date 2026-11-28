package com.whatcode.files

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import com.whatcode.ui.VectorIcon
import java.io.File
import java.text.DateFormat
import java.util.Date

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

        /** Opens the sidebar. */
        fun openMenu()

        /** [from] was renamed or moved to [to] (a folder, or a file): open tabs that were in it follow. */
        fun pathMoved(from: String, to: String)

        /** [path] was deleted (a folder, or a file): open tabs that were in it no longer have a file. */
        fun pathDeleted(path: String)
    }

    /** A file or folder waiting to be pasted: copied, or moved when [move]. */
    private class Clip(val file: File, val move: Boolean)

    /** One row of the list: a file or folder, the way up, or a line of text. */
    private class Item(val entry: ExplorerSort.Entry? = null, val up: String? = null, val text: String? = null)

    /** The explorer's screen. It fills the space it is given and is hidden while the editor shows. */
    val view = FrameLayout(activity)

    /** The path, the line about what waits to be pasted, and the list: everything but the add button. */
    private val page = LinearLayout(activity)

    /** The layout, the hidden files and the order of the list. The sidebar's options change them. */
    val options = ExplorerOptions(activity)
    val isShown: Boolean get() = view.visibility == View.VISIBLE

    /** The phone's own storage, where the explorer starts. */
    private val sharedRoot = ExplorerPaths.normalize(Environment.getExternalStorageDirectory().path)
    private val appFolders = ExplorerAccess.appFolders(sharedRoot, activity.packageName)

    /** The folder showing. */
    var path: String = remembered?.takeIf { File(it).isDirectory } ?: sharedRoot
        private set

    private val ui = Handler(Looper.getMainLooper())
    private val crumbs = LinearLayout(activity)
    private val crumbScroll = HorizontalScrollView(activity)
    private val note = LinearLayout(activity)
    private val noteText = TextView(activity)
    private val noteCancel = TextView(activity)
    private val noteAction = TextView(activity)
    private val listView = ListView(activity)
    private val gridView = GridView(activity)

    /** A line of text above the tiles (an empty folder), as a tile cannot hold one. */
    private val message = TextView(activity)
    private val addMenu = LinearLayout(activity)
    private val addButton = ImageView(activity)
    private var addOpen = false

    /** What the folder showing holds, as it was read; null when it could not be read. */
    private var loaded: List<ExplorerSort.Entry>? = null

    /** False until the first read of a folder is done. */
    private var ready = false
    private var items: List<Item> = emptyList()
    private var token = 0

    /** The folder that was asked for while the access to all files was not yet given; opened when it is. */
    private var waiting: String? = null

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
                setAddOpen(false)
                items.getOrNull(position)?.let { tap(it) }
            }
            list.setOnItemLongClickListener { _, row, position, _ ->
                val entry = items.getOrNull(position)?.entry
                if (entry != null) rowMenu(entry, row)
                entry != null
            }
        }
        val body = FrameLayout(activity)
        body.addView(listView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(gridView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        buildAdd()
    }

    // ------------------------------------------------------------------ the add button

    /** The round + at the bottom right. Open, it shows New folder and New file above it, and the + turns into an x. */
    private fun buildAdd() {
        addMenu.orientation = LinearLayout.VERTICAL
        addMenu.gravity = Gravity.END
        addMenu.visibility = View.GONE

        fun small(label: String, icon: Icon, action: () -> Unit) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    setAddOpen(false)
                    action()
                }
            }
            line.addView(
                TextView(activity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(palette.text)
                    background = AppTheme.rounded(activity, palette.surface, 8f, palette.outline)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    elevation = dp(2).toFloat()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
            )
            line.addView(
                ImageView(activity).apply {
                    setImageDrawable(VectorIcon(icon, dp(22)))
                    setColorFilter(palette.accent)
                    scaleType = ImageView.ScaleType.CENTER
                    background = AppTheme.pressable(activity, palette.surface, palette.ripple, 22f, palette.outline)
                    elevation = dp(4).toFloat()
                    contentDescription = label
                },
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            // The small buttons sit over the middle of the big one.
            addMenu.addView(
                line,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(12)
                    marginEnd = dp(6)
                }
            )
        }
        small("New folder", Icon.FOLDER) { newFolder() }
        small("New file", Icon.FILE) { newFile() }

        addButton.setImageDrawable(VectorIcon(Icon.PLUS, dp(26)))
        addButton.setColorFilter(palette.onAccent)
        addButton.scaleType = ImageView.ScaleType.CENTER
        addButton.background = AppTheme.pressable(activity, palette.accent, palette.ripple, 28f)
        addButton.elevation = dp(6).toFloat()
        addButton.contentDescription = "Add"
        addButton.setOnClickListener { setAddOpen(!addOpen) }

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        box.addView(addMenu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(addButton, LinearLayout.LayoutParams(dp(56), dp(56)))
        view.addView(
            box,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(16), dp(16))
            }
        )
    }

    private fun setAddOpen(open: Boolean) {
        addOpen = open
        addMenu.visibility = if (open) View.VISIBLE else View.GONE
        addButton.animate().rotation(if (open) 45f else 0f).setDuration(150).start()
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = items[position].text == null
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            if (options.mode == ExplorerSort.Mode.GRID) tile(items[position]) else row(items[position])
    }

    /** One tile of the grid: a big icon with the name under it. A long press opens the menu of the row. */
    private fun tile(item: Item): View {
        val entry = item.entry
        val name = entry?.name ?: ".."
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(8))
            minimumHeight = dp(92)
            background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 8f)
        }
        val image = ImageView(activity)
        if (entry == null || entry.isDir) {
            image.setImageDrawable(VectorIcon(Icon.FOLDER, dp(40)))
            image.setColorFilter(palette.accent)
        } else {
            image.setImageDrawable(FileIcons.drawable(entry.name, dp(40), palette.muted))
        }
        box.addView(image, LinearLayout.LayoutParams(dp(40), dp(40)))
        box.addView(
            TextView(activity).apply {
                text = name
                typeface = font
                setTextColor(palette.text)
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        )
        return box
    }

    private fun row(item: Item): View {
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
        }
        val text = item.text
        if (text != null) {
            line.addView(
                TextView(activity).apply {
                    this.text = text
                    setTextColor(palette.muted)
                    textSize = 13f
                    setPadding(0, dp(14), dp(8), dp(14))
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            return line
        }
        line.background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 0f)
        val compact = options.mode == ExplorerSort.Mode.COMPACT
        val entry = item.entry
        val name = entry?.name ?: ".."
        val image = ImageView(activity)
        if (entry == null || entry.isDir) {
            image.setImageDrawable(VectorIcon(Icon.FOLDER, dp(if (compact) 20 else 24)))
            image.setColorFilter(palette.accent)
        } else {
            image.setImageDrawable(FileIcons.drawable(entry.name, dp(if (compact) 20 else 24), palette.muted))
        }
        line.addView(image, LinearLayout.LayoutParams(dp(if (compact) 20 else 24), dp(if (compact) 20 else 24)).apply { marginEnd = dp(14) })
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            TextView(activity).apply {
                this.text = name
                typeface = font
                setTextColor(palette.text)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
        )
        if (entry != null && !compact) {
            val date = if (entry.modified > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modified)) else ""
            val detail = if (entry.isDir) date else listOf(ExplorerSort.sizeLabel(entry.size), date).filter { it.isNotEmpty() }.joinToString("  \u00B7  ")
            if (detail.isNotEmpty()) {
                column.addView(
                    TextView(activity).apply {
                        this.text = detail
                        setTextColor(palette.muted)
                        textSize = 11f
                        maxLines = 1
                    }
                )
            }
        }
        line.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (entry != null) {
            line.addView(
                TextView(activity).apply {
                    this.text = "\u22EE"
                    textSize = 20f
                    setTextColor(palette.muted)
                    gravity = Gravity.CENTER
                    contentDescription = "Menu for $name"
                    setOnClickListener { rowMenu(entry, this) }
                },
                LinearLayout.LayoutParams(dp(44), dp(if (compact) 40 else 44))
            )
        }
        line.minimumHeight = dp(if (compact) 40 else 54)
        return line
    }

    // ------------------------------------------------------------------ the path at the top

    private fun rootFor(folder: String): String = if (ExplorerPaths.isInside(folder, sharedRoot)) sharedRoot else "/"

    private fun renderCrumbs() {
        crumbs.removeAllViews()
        val root = rootFor(path)
        val steps = ExplorerPaths.crumbs(path, root, if (root == sharedRoot) "Phone storage" else "/")
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
                    typeface = if (last) Typeface.create(font, Typeface.BOLD) else font
                    setTextColor(if (last) palette.accent else palette.text)
                    textSize = 15f
                    maxLines = 1
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 8f)
                    setOnClickListener { if (!last) goTo(step.path) }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    // ------------------------------------------------------------------ the list

    private fun tap(item: Item) {
        val up = item.up
        val entry = item.entry
        when {
            up != null -> goTo(up)
            entry == null -> Unit
            entry.isDir -> goTo(entry.path)
            reachable(entry.path) -> host.openFile(File(entry.path))
        }
    }

    /** Shows the folder [target]. Does nothing, after asking about the access to all files, if that is needed. */
    fun goTo(target: String) {
        val folder = ExplorerPaths.normalize(target)
        if (!reachable(folder)) return
        setAddOpen(false)
        path = folder
        remembered = folder
        load()
    }

    /** Reads the folder again. */
    fun refresh() = load()

    private fun load() {
        val mine = ++token
        renderCrumbs()
        showNote()
        val dir = File(path)
        Thread {
            val kids = try {
                dir.listFiles()
            } catch (_: SecurityException) {
                null
            }
            val entries = kids?.map { ExplorerSort.Entry(it.name, it.path, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified()) }
            ui.post {
                if (mine != token || !alive()) return@post
                show(entries)
            }
        }.start()
    }

    private fun show(entries: List<ExplorerSort.Entry>?) {
        loaded = entries
        ready = true
        render()
        listView.setSelection(0)
        gridView.setSelection(0)
    }

    /** Lays the folder out as [options] say: shown or hidden files, the order, and rows or tiles. */
    private fun render() {
        val entries = loaded
        val list = ArrayList<Item>()
        ExplorerPaths.parent(path, rootFor(path))?.let { list.add(Item(up = it)) }
        when {
            entries == null -> list.add(
                Item(
                    text = if (StorageAccess.granted(activity)) "This folder can't be read."
                    else "This folder can't be read without access to all files."
                )
            )
            entries.isEmpty() -> list.add(Item(text = "(empty folder)"))
            else -> {
                val shown = ExplorerSort.arrange(entries, options.sort, options.descending, options.showHidden)
                if (shown.isEmpty()) list.add(Item(text = "(only hidden files here: turn on Hidden in the menu)"))
                shown.forEach { list.add(Item(entry = it)) }
            }
        }
        // A tile cannot hold a line of text, so in the grid it goes above the tiles.
        val grid = options.mode == ExplorerSort.Mode.GRID
        val line = list.firstOrNull { it.text != null }?.text
        items = if (grid) list.filter { it.text == null } else list
        message.text = if (grid) line.orEmpty() else ""
        message.visibility = if (grid && line != null) View.VISIBLE else View.GONE
        listView.visibility = if (grid) View.GONE else View.VISIBLE
        gridView.visibility = if (grid) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
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

    /** Asks for a name in a dialog. A name that can't be used is explained under the field, and the dialog stays. */
    private fun askName(title: String, initial: String, okLabel: String, onName: (String) -> Unit) {
        val input = EditText(activity).apply {
            setText(initial)
            typeface = font
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val dot = initial.lastIndexOf('.')
            setSelection(0, if (dot > 0) dot else initial.length)
        }
        val box = FrameLayout(activity).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppTheme.dialogBuilder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(okLabel, null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, activity)
        // The button must not close the dialog when the name is no good, so its click is set after show().
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            val problem = FileNames.problem(name)
            if (problem != null) {
                input.error = problem
            } else {
                dialog.dismiss()
                onName(name)
            }
        }
        input.requestFocus()
    }

    /** New file: asks for a name and makes an empty file in the folder showing. Called by the add button. */
    fun newFile() {
        askName("New file", "", "Create") { name -> work({ FileOps.createFile(File(path), name) }) }
    }

    /** New folder: asks for a name and makes a folder in the folder showing. Called by the add button. */
    fun newFolder() {
        askName("New folder", "", "Create") { name -> work({ FileOps.createFolder(File(path), name) }) }
    }

    private fun rowMenu(entry: ExplorerSort.Entry, anchor: View) {
        val popup = AppTheme.popup(activity, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Rename")
        popup.menu.add(0, 2, 1, "Copy")
        popup.menu.add(0, 3, 2, "Move")
        popup.menu.add(0, 4, 3, "Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> rename(entry)
                2 -> hold(entry, move = false)
                3 -> hold(entry, move = true)
                4 -> delete(entry)
            }
            true
        }
        popup.show()
    }

    private fun rename(entry: ExplorerSort.Entry) {
        askName("Rename", entry.name, "Rename") { name ->
            work({ FileOps.rename(File(entry.path), name) }) { renamed -> host.pathMoved(entry.path, renamed.path) }
        }
    }

    private fun delete(entry: ExplorerSort.Entry) {
        AppTheme.dialogBuilder(activity)
            .setTitle("Delete ${entry.name}?")
            .setMessage(if (entry.isDir) "The folder and everything in it will be deleted. This can't be undone." else "This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                work({ FileOps.delete(File(entry.path)) }) {
                    host.pathDeleted(entry.path)
                    if (clip?.file?.path?.let { ExplorerPaths.isInside(it, entry.path) } == true) clip = null
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    /** Copy or Move: the file or folder waits under the path, to be pasted into whichever folder is opened next. */
    private fun hold(entry: ExplorerSort.Entry, move: Boolean) {
        clip = Clip(File(entry.path), move)
        showNote()
    }

    private fun paste() {
        val held = clip ?: return
        val folder = File(path)
        work({ if (held.move) FileOps.move(held.file, folder) else FileOps.copy(held.file, folder) }) { result ->
            if (held.move) {
                if (result.path != held.file.path) host.pathMoved(held.file.path, result.path)
                clip = null
            }
        }
    }

    // ------------------------------------------------------------------ Find file

    /** Find file: a box to type part of a name in, and the files and folders below this one that match. */
    fun findFile() {
        val from = File(path)
        val input = EditText(activity).apply {
            hint = "Part of a file or folder name"
            typeface = font
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val status = TextView(activity).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val results = ListView(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(input)
            addView(status)
            addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.4).toInt()))
        }
        val dialog = AppTheme.dialogBuilder(activity)
            .setTitle("Find file in ${ExplorerPaths.name(path)}")
            .setView(box)
            .setNegativeButton("Close", null)
            .create()

        var shown: List<File> = emptyList()
        var run = 0

        fun showResults(found: List<File>) {
            shown = found
            results.adapter = object : ArrayAdapter<String>(
                activity, android.R.layout.simple_list_item_1,
                found.map { f ->
                    val parent = (f.parent ?: "").removePrefix(from.path).trim('/')
                    (if (f.isDirectory) f.name + "/" else f.name) + if (parent.isEmpty()) "" else "   \u00B7   $parent"
                }
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = super.getView(position, convertView, parent) as TextView
                    row.setTextColor(palette.text)
                    row.typeface = font
                    row.textSize = 14f
                    return row
                }
            }
        }

        val search = Runnable {
            val query = input.text.toString()
            val mine = ++run
            if (query.isBlank()) {
                showResults(emptyList())
                status.text = "Type part of a name. This folder and every folder below it are searched."
                return@Runnable
            }
            status.text = "Searching\u2026"
            Thread {
                val found = ExplorerFind.search(from, query, 200) { mine != run || !alive() }
                ui.post {
                    if (mine != run || !alive()) return@post
                    showResults(found.files)
                    status.text = when {
                        found.files.isEmpty() -> "Nothing found."
                        found.truncated -> "First ${found.files.size} found. Keep typing to narrow it down."
                        else -> "${found.files.size} found."
                    }
                }
            }.start()
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                ui.removeCallbacks(search)
                ui.postDelayed(search, 250)
            }
        })
        results.setOnItemClickListener { _, _, position, _ ->
            val file = shown.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            if (file.isDirectory) goTo(file.path) else if (reachable(file.path)) host.openFile(file)
        }
        dialog.setOnDismissListener {
            run++
            ui.removeCallbacks(search)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, activity)
        input.requestFocus()
        search.run()
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
        if (waitingToPaste != null) {
            note.visibility = View.VISIBLE
            noteText.text = (if (waitingToPaste.move) "Moving " else "Copying ") + waitingToPaste.file.name
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
            goTo(target)
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
        options.onChange = { if (ready) render() }
        load()
    }

    companion object {
        /** Where the explorer was, so it is still there when the screen is built again (a change of theme). */
        private var remembered: String? = null

        /** Whether the access to all files was asked about since the app started; it is not asked about again. */
        private var askedOnce = false

        /** What was copied or moved and waits to be pasted. */
        private var clip: Clip? = null
    }
}
