package com.whatcode.explorer

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.files.Workspace
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon
import java.util.Locale

/**
 * The sidebar's Files panel: the opened folder as a tree. Folders open and fold in place; tapping a file opens it
 * and closes the sidebar through [close]. [isShowing] says whether this is the panel on show, so that a folder read
 * that finishes late does not draw into a panel nobody sees.
 */
internal class FilesPanel(
    private val activity: Activity,
    private val workspace: Workspace,
    private val host: SidebarHost,
    private val palette: AppTheme.Palette,
    private val close: () -> Unit,
    private val isShowing: () -> Boolean,
) {
    val view = LinearLayout(activity)
    private val folderTitle = TextView(activity)
    private val treeBox = LinearLayout(activity)
    private val expanded = HashSet<String>()
    private val cache = HashMap<String, List<Workspace.Entry>>()
    private val loading = HashSet<String>()
    private val ui = Handler(Looper.getMainLooper())

    private val colorRailOn get() = palette.accentSoft
    private val colorText get() = palette.text
    private val colorMuted get() = palette.muted
    private val colorAccent get() = palette.accent

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    init {
        view.orientation = LinearLayout.VERTICAL

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
                render()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
        )
        view.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        treeBox.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(activity)
        scroll.addView(treeBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

    fun render() {
        treeBox.removeAllViews()
        val root = workspace.rootId
        if (!workspace.hasFolder || root == null) {
            folderTitle.text = "No folder open"
            treeBox.addView(noFolderView(activity, palette, workspace, "Open a folder to browse its files here."))
            return
        }
        folderTitle.text = workspace.folderName
        addChildren(root, 0)
    }

    /** Another folder was opened: forget what was read of the old one. */
    fun reset() {
        cache.clear()
        loading.clear()
        expanded.clear()
        render()
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
                if (alive() && isShowing()) render()
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
                render()
            } else if (uri != null) {
                close()
                host.openFile(uri)
            }
        }
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        return row
    }
}

/** The message and button shown while no folder is open. */
internal fun noFolderView(activity: Activity, palette: AppTheme.Palette, workspace: Workspace, message: String): View {
    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        addView(TextView(activity).apply {
            text = message
            setTextColor(palette.muted)
            textSize = 14f
        })
        addView(
            Button(activity).apply {
                text = "Open folder"
                isAllCaps = false
                textSize = 14f
                setTextColor(palette.text)
                AppTheme.button(activity, this, palette)
                setOnClickListener { workspace.pickFolder() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, dp(12), 0, 0) }
        )
    }
}
