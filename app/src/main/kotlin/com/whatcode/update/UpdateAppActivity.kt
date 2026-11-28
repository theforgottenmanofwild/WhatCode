package com.whatcode.update

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage

/**
 * The "Update App" page, opened from Settings. It shows the installed version and what the last check found,
 * the manual update buttons and the switch for the check when the app opens. The full-screen page for a found
 * update is [ReleaseActivity]; "View update" here opens it.
 */
class UpdateAppActivity : SettingsPage("Update App") {

    private lateinit var updates: UpdateController

    override fun onPrepare() {
        updates = UpdateController(this) { if (built) render() }
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
    }

    /** Redraws the page from the current update state. */
    override fun render() {
        content.removeAllViews()

        content.addView(TextView(this).apply {
            text = updates.status()
            setTextColor(colorText)
            textSize = 15f
            setLineSpacing(0f, 1.16f)
            background = AppTheme.card(this@UpdateAppActivity, palette, 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        for ((label, action) in updates.actions()) {
            actions.addView(
                button(label, primary = label == "Install" || label == "View update", onClick = action),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(0, 0, dp(8), 0) }
            )
        }
        content.addView(actions)

        content.addView(
            toggle("Check for updates when the app opens", updates.checkOnOpen) { updates.checkOnOpen = it },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(12), 0, dp(8))
            }
        )
        content.addView(muted("When this is on, the app looks for a newer build each time it opens and asks before downloading anything."))
    }
}
