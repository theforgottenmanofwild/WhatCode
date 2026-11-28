package com.whatcode.explorer

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.VectorIcon

/**
 * The round + at the bottom right of the explorer. Open, it shows New folder and New file above it, and the + turns
 * into an x. [view] is what goes into the explorer's screen.
 */
internal class ExplorerAddButton(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val onNewFolder: () -> Unit,
    private val onNewFile: () -> Unit,
) {
    val view = LinearLayout(activity)
    private val addMenu = LinearLayout(activity)
    private val addButton = ImageView(activity)
    private var addOpen = false

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    init {
        addMenu.orientation = LinearLayout.VERTICAL
        addMenu.gravity = Gravity.END
        addMenu.visibility = View.GONE

        fun small(label: String, icon: Icon, action: () -> Unit) {
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    setOpen(false)
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
        small("New folder", Icon.FOLDER) { onNewFolder() }
        small("New file", Icon.FILE) { onNewFile() }

        addButton.setImageDrawable(VectorIcon(Icon.PLUS, dp(26)))
        addButton.setColorFilter(palette.onAccent)
        addButton.scaleType = ImageView.ScaleType.CENTER
        addButton.background = AppTheme.pressable(activity, palette.accent, palette.ripple, 28f)
        addButton.elevation = dp(6).toFloat()
        addButton.contentDescription = "Add"
        addButton.setOnClickListener { setOpen(!addOpen) }


        view.orientation = LinearLayout.VERTICAL
        view.gravity = Gravity.END
        view.addView(addMenu, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(addButton, LinearLayout.LayoutParams(dp(56), dp(56)))
    }

    fun setOpen(open: Boolean) {
        addOpen = open
        addMenu.visibility = if (open) View.VISIBLE else View.GONE
        addButton.animate().rotation(if (open) 45f else 0f).setDuration(150).start()
    }
}
