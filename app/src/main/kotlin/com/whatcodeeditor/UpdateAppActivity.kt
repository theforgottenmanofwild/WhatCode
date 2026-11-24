package com.whatcodeeditor

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

/**
 * The "Update App" page, opened from Settings. It shows the installed version and what the last check found,
 * the manual update buttons and the switch for the check when the app opens. The full-screen page for a found
 * update is [ReleaseActivity]; "View update" here opens it.
 */
class UpdateAppActivity : Activity() {

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorBar = 0xFF252526.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorMuted = 0xFF9DA5B0.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()

    private lateinit var updates: UpdateController
    private lateinit var updateStatus: TextView
    private lateinit var updateActions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateController(this) { renderUpdates() }
        buildUi()
        renderUpdates()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        setBackgroundColor(colorKey)
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
            textSize = 26f
            contentDescription = "Back"
            setBackgroundColor(colorBar)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(40)))
        bar.addView(TextView(this).apply {
            text = "Update App"
            setTextColor(colorAccent)
            textSize = 16f
            typeface = Typeface.MONOSPACE
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }

        updateStatus = TextView(this).apply {
            setTextColor(colorText)
            textSize = 15f
        }
        content.addView(updateStatus)

        updateActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        content.addView(updateActions)

        content.addView(Switch(this).apply {
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
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
                button(label, action),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(0, 0, dp(8), 0) }
            )
        }
    }
}
