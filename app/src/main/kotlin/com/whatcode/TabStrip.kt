package com.whatcode

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.ui.AppTheme

/**
 * The tab bar under the top bar: one tab per open file, the showing one highlighted, a dot on those with unsaved
 * changes. It only shows while two or more files are open. Taps are reported by index through [onSelect], [onClose]
 * and (long press) [onMenu].
 */
internal class TabStrip(
    private val context: Context,
    private val palette: AppTheme.Palette,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onMenu: (Int, View) -> Unit,
) {
    /** What one tab shows. */
    class Label(val name: String, val modified: Boolean)

    private val colorBg get() = palette.background
    private val colorBar get() = palette.surface
    private val colorText get() = palette.text
    private val colorMuted get() = palette.muted
    private val colorAccent get() = palette.accent

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    val view = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setBackgroundColor(colorBar)
        visibility = View.GONE
        addView(row)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** Draws the bar for [labels], with the tab at [active] highlighted. */
    fun render(labels: List<Label>, active: Int) {
        if (labels.size < 2) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        row.removeAllViews()
        labels.forEachIndexed { i, label ->
            val isActive = i == active
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (isActive) colorBg else colorBar)
            }
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(
                TextView(context).apply {
                    text = (if (label.modified) "● " else "") + label.name
                    setTextColor(if (isActive) colorAccent else colorText)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    maxLines = 1
                    maxWidth = dp(170)
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), 0, dp(4), 0)
                    setOnClickListener { onSelect(i) }
                    setOnLongClickListener {
                        onMenu(i, this)
                        true
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            line.addView(
                TextView(context).apply {
                    text = "×"
                    textSize = 18f
                    setTextColor(colorMuted)
                    gravity = Gravity.CENTER
                    contentDescription = "Close ${label.name}"
                    setOnClickListener { onClose(i) }
                },
                LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.MATCH_PARENT)
            )
            cell.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f))
            cell.addView(
                View(context).apply { setBackgroundColor(if (isActive) colorAccent else colorBar) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
            )
            row.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        view.post {
            val v = row.getChildAt(active) ?: return@post
            view.smoothScrollTo(maxOf(0, v.left - dp(48)), 0)
        }
    }
}
