package com.whatcode.settings.explorer

import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.whatcode.explorer.ExplorerSettings
import com.whatcode.explorer.logic.RowMenu
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > Row menu: which actions the menu of a row shows, and in what order. */
class ExplorerRowMenuActivity : SettingsPage("Row menu") {

    private val settings by lazy { ExplorerSettings(this) }

    override fun render() {
        content.removeAllViews()
        val items = settings.rowMenu
        val lastShown = items.count { it.shown } <= 1

        content.addView(sectionTitle("ACTIONS"))
        content.addView(muted("Turn an action off to hide it, and use the arrows to move it."))
        items.forEachIndexed { index, item ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            // The last action that shows can't be turned off, so the menu never ends up empty.
            val label = item.action.label + when {
                item.action.apkOnly -> "  (APKs only)"
                !item.action.forFolders -> "  (files only)"
                else -> ""
            }
            line.addView(
                toggle(label, item.shown, enabled = !(item.shown && lastShown)) { on ->
                    settings.rowMenu = items.toMutableList().also { it[index] = RowMenu.Item(item.action, on) }
                    render()
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            line.addView(
                arrow("\u25B2", "Move ${item.action.label} up", index > 0) { move(items, index, -1) },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) }
            )
            line.addView(
                arrow("\u25BC", "Move ${item.action.label} down", index < items.lastIndex) { move(items, index, 1) },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) }
            )
            content.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) })
        }
        content.addView(muted("Selecting several things offers Copy, Move, Delete and Share, in this order."))

        defaultsSection(
            question = "Show every action, in the usual order?",
            done = "Row menu returned to default",
        ) { settings.resetRowMenu() }
    }

    private fun arrow(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) = button(glyph) {
        if (enabled) onClick()
    }.apply {
        contentDescription = description
        alpha = if (enabled) 1f else 0.3f
        setPadding(0, 0, 0, 0)
    }

    private fun move(items: List<RowMenu.Item>, index: Int, by: Int) {
        settings.rowMenu = RowMenu.moved(items, index, by)
        render()
    }
}
