package com.whatcode.explorer

import android.app.Activity
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.apk.ApkDialogs
import com.whatcode.apk.BuildOutputLocator
import com.whatcode.files.Workspace
import com.whatcode.settings.ApkSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon
import java.io.File
import java.io.FileOutputStream
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
    private var latestBuild: BuildOutputLocator.Candidate? = null
    private var latestBuildUri: Uri? = null
    private var latestScanGen = 0
    private var latestScanned = false

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
                latestBuild = null
                latestBuildUri = null
                latestScanned = false
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
        val apkSettings = ApkSettings(activity)
        if (apkSettings.latestBuildShortcut) {
            if (!latestScanned) scanLatestBuild(root)
            latestBuild?.let { cand -> treeBox.addView(latestBuildCard(cand, apkSettings)) }
        }
        addChildren(root, 0)
    }

    /** Another folder was opened: forget what was read of the old one. */
    fun reset() {
        cache.clear()
        loading.clear()
        expanded.clear()
        latestScanGen++
        latestBuild = null
        latestBuildUri = null
        latestScanned = false
        render()
    }

    private fun scanLatestBuild(rootId: String) {
        val mine = ++latestScanGen
        latestScanned = true
        Thread {
            val found = ArrayList<Pair<BuildOutputLocator.Candidate, Uri>>()
            try {
                val top = workspace.listChildren(rootId)
                val apkDirs = ArrayList<String>()
                for (e in top) {
                    if (!e.isDir) continue
                    val low = e.name.lowercase(Locale.ROOT)
                    if (low == "app") {
                        workspace.listChildren(e.id).firstOrNull { it.isDir && it.name.equals("build", true) }
                            ?.let { b -> findOutputsApkDir(b.id)?.let { apkDirs.add(it) } }
                    } else if (low == "build") {
                        findOutputsApkDir(e.id)?.let { apkDirs.add(it) }
                    }
                }
                for (dirId in apkDirs) collectBuildApks(dirId, 0, found)
            } catch (_: Exception) {
            }
            val best = BuildOutputLocator.pickLatest(found.map { it.first })
            val bestUri = best?.let { b -> found.firstOrNull { it.first === b }?.second }
            ui.post {
                if (mine == latestScanGen && alive() && (best != latestBuild || bestUri != latestBuildUri)) {
                    latestBuild = best
                    latestBuildUri = bestUri
                    if (isShowing()) render()
                }
            }
        }.start()
    }

    private fun findOutputsApkDir(buildDirId: String): String? {
        val outputs = workspace.listChildren(buildDirId).firstOrNull { it.isDir && it.name.equals("outputs", true) }
            ?: return null
        return workspace.listChildren(outputs.id).firstOrNull { it.isDir && it.name.equals("apk", true) }?.id
    }

    private fun collectBuildApks(
        dirId: String,
        depth: Int,
        out: MutableList<Pair<BuildOutputLocator.Candidate, Uri>>,
    ) {
        if (depth > 3) return
        for (e in workspace.listChildren(dirId)) {
            if (e.isDir) {
                collectBuildApks(e.id, depth + 1, out)
            } else if (e.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                val uri = workspace.uriFor(e.id) ?: continue
                var mtime = 0L
                try {
                    activity.contentResolver.query(
                        uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) mtime = c.getLong(0)
                    }
                } catch (_: Exception) {
                }
                out.add(BuildOutputLocator.Candidate(e.id, e.name, mtime, maxOf(0L, e.size)) to uri)
            }
        }
    }

    private fun latestBuildCard(cand: BuildOutputLocator.Candidate, apkSettings: ApkSettings): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = AppTheme.pressable(activity, colorRailOn, palette.ripple, 8f)
            setOnClickListener { openLatestBuild(cand, apkSettings) }
        }
        val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        col.addView(
            TextView(activity).apply {
                text = "Latest build"
                setTextColor(colorAccent)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            },
        )
        col.addView(
            TextView(activity).apply {
                text = cand.name
                setTextColor(colorText)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
        )
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            TextView(activity).apply {
                text = "Install"
                setTextColor(colorAccent)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
        )
        val wrap = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return wrap
    }

    private fun openLatestBuild(cand: BuildOutputLocator.Candidate, apkSettings: ApkSettings) {
        val uri = latestBuildUri ?: return
        val busy = BusyDialog(activity, ui, "Loading ${cand.name}…", null)
        busy.start()
        Thread {
            val outDir = File(activity.cacheDir, "latest_build").apply { mkdirs() }
            val safeName = cand.name.replace('/', '_')
            val tempApk = File(outDir, safeName)
            var ok = false
            try {
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempApk).use { output -> input.copyTo(output) }
                    ok = tempApk.isFile && tempApk.length() > 0L
                }
            } catch (_: Exception) {
            }
            ui.post {
                busy.end()
                if (!alive()) return@post
                if (!ok) {
                    Toast.makeText(activity, "Could not read ${cand.name}.", Toast.LENGTH_SHORT).show()
                    return@post
                }
                ApkDialogs.showTapSheet(
                    activity = activity,
                    file = tempApk,
                    settings = apkSettings,
                    palette = palette,
                    font = Typeface.DEFAULT,
                    onShare = { com.whatcode.files.SharedFiles.share(activity, listOf(it)) },
                    onBrowse = {},
                    onExtract = {},
                    onRefresh = {},
                )
            }
        }.start()
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
