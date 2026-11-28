package com.whatcode.explorer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.apk.ApkNames
import com.whatcode.apk.ApkRowCache
import com.whatcode.explorer.logic.ExplorerFormat
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon

/**
 * The views of the explorer's list: a row for the details and compact layouts, a tile for the grid. An [entry] of
 * null is the way up (".."). What the rows show comes from a [Look], made once each time the list is laid out.
 */
internal class ExplorerRows(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val font: Typeface,
) {
    /** What the rows and tiles show, from the View and Taps settings, and the time the dates are counted from. */
    class Look(
        val compact: Boolean,
        val density: ExplorerOptions.Density,
        val tileDp: Int,
        val showSize: Boolean,
        val showDate: Boolean,
        val dateStyle: ExplorerFormat.DateStyle,
        val hour24: Boolean,
        val decimalSizes: Boolean,
        val showExtensions: Boolean,
        val fileIcons: Boolean,
        val menuButton: Boolean,
        val now: Long,
        /** The paths picked in the selection, shown with a highlight. */
        val selected: Set<String> = emptySet(),
        val apkRowMeta: Boolean = false,
        val apkCacheMb: Int = 4,
        val inArchive: Boolean = false,
        val onApkMetaLoaded: (() -> Unit)? = null,
    )

    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun apkMeta(entry: ExplorerSort.Entry, look: Look): ApkRowCache.Meta? {
        if (look.inArchive || !look.apkRowMeta || entry.isDir || !ApkNames.isApkFile(entry.name)) return null
        val cached = ApkRowCache.peek(entry.path, entry.modified, entry.size)
        if (cached == null && look.onApkMetaLoaded != null) {
            ApkRowCache.request(activity, entry.path, entry.modified, entry.size, look.apkCacheMb, ui, look.onApkMetaLoaded)
        }
        return cached
    }

    private fun icon(entry: ExplorerSort.Entry?, size: Int, look: Look): ImageView = ImageView(activity).apply {
        val apkIcon = entry?.let { apkMeta(it, look)?.icon }
        when {
            entry == null || entry.isDir -> {
                setImageDrawable(VectorIcon(Icon.FOLDER, size))
                setColorFilter(palette.accent)
            }
            apkIcon != null -> setImageBitmap(apkIcon)
            look.fileIcons -> setImageDrawable(FileIcons.drawable(entry.name, size, palette.muted))
            else -> {
                setImageDrawable(VectorIcon(Icon.FILE, size))
                setColorFilter(palette.muted)
            }
        }
    }

    /** The colour behind a row or tile: the soft accent when it is picked in the selection. */
    private fun backdrop(entry: ExplorerSort.Entry?, look: Look): Int =
        if (entry != null && entry.path in look.selected) palette.accentSoft else Color.TRANSPARENT

    private fun label(entry: ExplorerSort.Entry?, look: Look): String =
        entry?.let { ExplorerFormat.name(it.name, it.isDir, look.showExtensions) } ?: ".."

    /** The line under a name: an APK app name (if cached), a file's size and date, or a folder's item count and date. */
    private fun detail(entry: ExplorerSort.Entry, look: Look): String {
        val apkLabel = if (!look.inArchive && !entry.isDir && look.apkRowMeta && ApkNames.isApkFile(entry.name)) {
            ApkRowCache.peek(entry.path, entry.modified, entry.size)?.label
        } else {
            null
        }
        val date = if (look.showDate) ExplorerFormat.date(entry.modified, look.now, look.dateStyle, look.hour24) else ""
        val first = when {
            entry.isDir -> ExplorerFormat.count(entry.count)
            look.showSize -> ExplorerSort.sizeLabel(entry.size, look.decimalSizes)
            else -> ""
        }
        return listOfNotNull(apkLabel, first.ifEmpty { null }, date.ifEmpty { null }).joinToString("  \u00B7  ")
    }

    /** One tile of the grid: a big icon with the name under it. A long press opens the menu of the row. */
    fun tile(entry: ExplorerSort.Entry?, look: Look): View {
        val name = label(entry, look)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(10), dp(4), dp(8))
            minimumHeight = dp(look.tileDp)
            background = AppTheme.pressable(activity, backdrop(entry, look), palette.ripple, 8f)
        }
        val size = dp(look.tileDp * 40 / 92)
        box.addView(icon(entry, size, look), LinearLayout.LayoutParams(size, size))
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

    fun row(entry: ExplorerSort.Entry?, text: String?, look: Look, onMenu: (ExplorerSort.Entry, View) -> Unit): View {
        val compact = look.compact
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(6), 0)
        }
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
        line.background = AppTheme.pressable(activity, backdrop(entry, look), palette.ripple, 0f)
        val name = label(entry, look)
        val iconSize = dp(if (compact) 20 else 24)
        line.addView(icon(entry, iconSize, look), LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(14) })
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
            val detail = detail(entry, look)
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
        if (entry != null && look.menuButton) {
            line.addView(
                TextView(activity).apply {
                    this.text = "\u22EE"
                    textSize = 20f
                    setTextColor(palette.muted)
                    gravity = Gravity.CENTER
                    contentDescription = "Menu for $name"
                    setOnClickListener { onMenu(entry, this) }
                },
                LinearLayout.LayoutParams(dp(44), dp(if (compact) 40 else 44))
            )
        }
        if (entry != null && !look.menuButton) line.setPadding(dp(14), 0, dp(14), 0)
        val extra = when (look.density) {
            ExplorerOptions.Density.ROOMY -> 10
            ExplorerOptions.Density.NORMAL -> 0
            ExplorerOptions.Density.DENSE -> -8
        }
        line.minimumHeight = dp((if (compact) 40 else 54) + if (compact) extra * 3 / 4 else extra)
        return line
    }
}
