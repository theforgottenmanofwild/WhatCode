package com.whatcode.explorer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon
import java.text.DateFormat
import java.util.Date

/**
 * The views of the explorer's list: a row for the details and compact layouts, a tile for the grid. An [entry] of
 * null is the way up ("..").
 */
internal class ExplorerRows(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val font: Typeface,
) {
    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    /** One tile of the grid: a big icon with the name under it. A long press opens the menu of the row. */
    fun tile(entry: ExplorerSort.Entry?): View {
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

    fun row(entry: ExplorerSort.Entry?, text: String?, compact: Boolean, onMenu: (ExplorerSort.Entry, View) -> Unit): View {
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
        line.background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 0f)
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
                    setOnClickListener { onMenu(entry, this) }
                },
                LinearLayout.LayoutParams(dp(44), dp(if (compact) 40 else 44))
            )
        }
        line.minimumHeight = dp(if (compact) 40 else 54)
        return line
    }
}
