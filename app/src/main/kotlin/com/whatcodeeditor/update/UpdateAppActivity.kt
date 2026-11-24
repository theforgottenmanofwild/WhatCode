package com.whatcodeeditor.update

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.whatcodeeditor.ui.AppTheme

/**
 * The "Update App" page, opened from Settings. It shows the installed version and what the last check found,
 * the manual update buttons and the switch for the check when the app opens. The full-screen page for a found
 * update is [ReleaseActivity]; "View update" here opens it.
 */
class UpdateAppActivity : Activity() {

    private val palette by lazy { AppTheme.palette(this) }
    private val colorBg: Int get() = palette.background
    private val colorBar: Int get() = palette.surface
    private val colorKey: Int get() = palette.key
    private val colorText: Int get() = palette.text
    private val colorMuted: Int get() = palette.muted
    private val colorAccent: Int get() = palette.accent

    private lateinit var updates: UpdateController
    private lateinit var updateStatus: TextView
    private lateinit var updateActions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        updates = UpdateController(this) { renderUpdates() }
        buildUi()
        renderUpdates()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        AppTheme.button(this@UpdateAppActivity, this, palette, primary)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        // Same edge-to-edge handling as the editor screen.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        // Top bar: back arrow and the page title.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(4), dp(4), dp(10), dp(4))
        }
        val back = button("‹") { finish() }.apply {
            textSize = 28f
            contentDescription = "Back"
            background = AppTheme.pressable(this@UpdateAppActivity, colorBar, palette.ripple, 12f)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(44)))
        bar.addView(TextView(this).apply {
            text = "Update App"
            setTextColor(colorText)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }

        updateStatus = TextView(this).apply {
            setTextColor(colorText)
            textSize = 15f
            setLineSpacing(0f, 1.16f)
            background = AppTheme.card(this@UpdateAppActivity, palette, 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(updateStatus)

        updateActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(updateActions)

        content.addView(Switch(this).apply {
            thumbTintList = AppTheme.switchTint(palette)
            trackTintList = AppTheme.switchTint(palette)
            text = "Check for updates when the app opens"
            setTextColor(colorText)
            textSize = 15f
            isChecked = updates.checkOnOpen
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, on -> updates.checkOnOpen = on }
        })
        content.addView(TextView(this).apply {
            text = "When this is on, the app looks for a newer build each time it opens and asks before downloading anything."
            setTextColor(colorMuted)
            textSize = 13f
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    /** Redraws the page from the current update state. */
    private fun renderUpdates() {
        if (!::updateActions.isInitialized) return
        updateStatus.text = updates.status()
        updateActions.removeAllViews()
        for ((label, action) in updates.actions()) {
            updateActions.addView(
                button(label, primary = label == "Install" || label == "View update", onClick = action),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(0, 0, dp(8), 0) }
            )
        }
    }
}
