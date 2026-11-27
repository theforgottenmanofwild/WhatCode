package com.whatcode.files

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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
 * tapping a file opens it in the editor, through [Host].
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
    }

    /** One row of the list: a file or folder, the way up, or a line of text. */
    private class Item(val entry: ExplorerSort.Entry? = null, val up: String? = null, val text: String? = null)

    /** The explorer's screen. It fills the space it is given and is hidden while the editor shows. */
    val view = LinearLayout(activity)
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
    private val noteAction = TextView(activity)
    private val listView = ListView(activity)
    private var items: List<Item> = emptyList()
    private var token = 0

    /** The folder that was asked for while the access to all files was not yet given; opened when it is. */
    private var waiting: String? = null

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ building

    private fun build() {
        view.orientation = LinearLayout.VERTICAL
        view.setBackgroundColor(palette.background)

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
        view.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // A thin line under the bar, as under the editor's.
        view.addView(View(activity).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        note.orientation = LinearLayout.HORIZONTAL
        note.gravity = Gravity.CENTER_VERTICAL
        note.setBackgroundColor(palette.accentSoft)
        note.setPadding(dp(14), dp(6), dp(6), dp(6))
        note.visibility = View.GONE
        noteText.setTextColor(palette.text)
        noteText.textSize = 12f
        note.addView(noteText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        noteAction.setTextColor(palette.accent)
        noteAction.textSize = 13f
        noteAction.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        noteAction.gravity = Gravity.CENTER
        noteAction.setPadding(dp(12), 0, dp(12), 0)
        note.addView(noteAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        view.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        listView.divider = null
        listView.dividerHeight = 0
        listView.selector = ColorDrawable(Color.TRANSPARENT)
        listView.overScrollMode = View.OVER_SCROLL_NEVER
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ -> items.getOrNull(position)?.let { tap(it) } }
        view.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = items[position].text == null
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = row(items[position])
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
        val entry = item.entry
        val name = entry?.name ?: ".."
        val image = ImageView(activity)
        if (entry == null || entry.isDir) {
            image.setImageDrawable(VectorIcon(Icon.FOLDER, dp(24)))
            image.setColorFilter(palette.accent)
        } else {
            image.setImageDrawable(FileIcons.drawable(entry.name, dp(24), palette.muted))
        }
        line.addView(image, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) })
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
        if (entry != null) {
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
        line.minimumHeight = dp(54)
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
            else -> ExplorerSort.sort(entries).forEach { list.add(Item(entry = it)) }
        }
        items = list
        adapter.notifyDataSetChanged()
        listView.setSelection(0)
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

    /** What the note under the path says while the access is off, and the explorer is somewhere that needs it. */
    private fun showNote() {
        val needed = !StorageAccess.granted(activity) &&
            ExplorerPaths.isInside(path, sharedRoot) && appFolders.none { ExplorerPaths.isInside(path, it) }
        note.visibility = if (needed) View.VISIBLE else View.GONE
        if (!needed) return
        noteText.text = "Access to all files is off, so some folders are not shown."
        noteAction.text = "Allow"
        noteAction.setOnClickListener { askAccess() }
    }

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
        load()
    }

    companion object {
        /** Where the explorer was, so it is still there when the screen is built again (a change of theme). */
        private var remembered: String? = null

        /** Whether the access to all files was asked about since the app started; it is not asked about again. */
        private var askedOnce = false
    }
}
