package com.whatcode.explorer

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon

/**
 * The panel beside the sidebar's strip on the explorer's screen: how the list looks (details, compact or tiles,
 * hidden files), the order it is in, and Open, Open folder, Find file and Settings. [close] closes the sidebar.
 */
internal class ExplorerOptionsPanel(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val host: SidebarHost,
    private val close: () -> Unit,
) {
    val view = LinearLayout(activity)
    private val rows = LinearLayout(activity)

    private val colorText get() = palette.text
    private val colorMuted get() = palette.muted
    private val colorAccent get() = palette.accent

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    init {
        view.orientation = LinearLayout.VERTICAL
        val title = TextView(activity).apply {
            text = "File explorer"
            setTextColor(colorText)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(palette.surface)
        }
        view.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        rows.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(activity)
        scroll.addView(rows, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** One choice in a row of the explorer's options: [on] when it is the one in use. */
    private class Choice(val icon: Icon, val label: String, val on: Boolean, val tap: () -> Unit)

    fun render() {
        rows.removeAllViews()
        val options = host.explorerOptions()

        fun heading(text: String) {
            rows.addView(
                TextView(activity).apply {
                    this.text = text
                    setTextColor(colorMuted)
                    textSize = 12f
                    setPadding(dp(18), dp(14), dp(14), dp(2))
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        // Equal cells side by side: an icon, its name, and a bar under the one that is on. A tap changes the
        // option, and the cells are drawn again so the bar moves.
        fun choices(list: List<Choice>) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), 0, dp(4), 0)
            }
            for (c in list) {
                val tint = if (c.on) colorAccent else colorMuted
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(10), 0, 0)
                    contentDescription = c.label
                    background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 8f)
                    setOnClickListener {
                        c.tap()
                        render()
                    }
                }
                cell.addView(
                    ImageView(activity).apply {
                        setImageDrawable(VectorIcon(c.icon, dp(24)))
                        setColorFilter(tint)
                    },
                    LinearLayout.LayoutParams(dp(24), dp(24))
                )
                cell.addView(
                    TextView(activity).apply {
                        text = c.label
                        textSize = 11f
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setTextColor(if (c.on) colorText else colorMuted)
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
                )
                cell.addView(
                    View(activity).apply {
                        setBackgroundColor(colorAccent)
                        visibility = if (c.on) View.VISIBLE else View.INVISIBLE
                    },
                    LinearLayout.LayoutParams(dp(28), dp(3)).apply { topMargin = dp(6) }
                )
                line.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
            }
            rows.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        heading("View")
        choices(
            listOf(
                Choice(Icon.VIEW_DETAILED, "Detailed", options.mode == ExplorerSort.Mode.DETAILED) { options.setMode(ExplorerSort.Mode.DETAILED) },
                Choice(Icon.VIEW_COMPACT, "Compact", options.mode == ExplorerSort.Mode.COMPACT) { options.setMode(ExplorerSort.Mode.COMPACT) },
                Choice(Icon.VIEW_GRID, "Grid", options.mode == ExplorerSort.Mode.GRID) { options.setMode(ExplorerSort.Mode.GRID) },
                Choice(Icon.HIDDEN, "Hidden", options.showHidden) { options.toggleHidden() },
            )
        )
        heading("Sort")
        choices(
            listOf(
                Choice(Icon.SORT_NAME, "Name", options.sort == ExplorerSort.Key.NAME) { options.setSort(ExplorerSort.Key.NAME) },
                Choice(Icon.SORT_SIZE, "Size", options.sort == ExplorerSort.Key.SIZE) { options.setSort(ExplorerSort.Key.SIZE) },
                Choice(Icon.SORT_DATE, "Date", options.sort == ExplorerSort.Key.DATE) { options.setSort(ExplorerSort.Key.DATE) },
                Choice(Icon.FILE, "Type", options.sort == ExplorerSort.Key.TYPE) { options.setSort(ExplorerSort.Key.TYPE) },
                Choice(Icon.SORT_DESC, "Desc", options.descending) { options.toggleDescending() },
            )
        )

        rows.addView(
            View(activity).apply { setBackgroundColor(palette.outline) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, dp(14), 0, dp(4)) }
        )

        fun row(action: ExplorerAction, label: String) {
            rows.addView(
                TextView(activity).apply {
                    text = label
                    setTextColor(colorText)
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18), 0, dp(14), 0)
                    background = AppTheme.pressable(activity, Color.TRANSPARENT, palette.ripple, 0f)
                    setOnClickListener {
                        close()
                        host.explorerAction(action)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
        }
        row(ExplorerAction.OPEN, "Open")
        row(ExplorerAction.OPEN_FOLDER, "Open folder")
        row(ExplorerAction.FIND_FILE, "Find file")
        row(ExplorerAction.SETTINGS, if (host.settingsNeedAttention()) "Settings \u25CF" else "Settings")
    }
}
