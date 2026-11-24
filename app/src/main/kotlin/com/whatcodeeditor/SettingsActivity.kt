package com.whatcodeeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The settings page, opened from the editor's menu. It is a list of sections, each a title with buttons that
 * open a page and nothing else. Add further sections to [buildUi] the same way. For now there is one,
 * "About App": [UpdateAppActivity] for the update controls and [ReleaseActivity] (About mode) for the
 * installed version's release notes.
 */
class SettingsActivity : Activity() {

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorBar = 0xFF252526.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()

    private lateinit var updates: UpdateController
    private lateinit var updateAppButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateController(this) { renderUpdateButton() }
        buildUi()
        renderUpdateButton()
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

    /** A full-width button that opens a page. */
    private fun pageButton(label: String, onClick: () -> Unit): Button = button(label, onClick).apply {
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun rowParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, 0, 0, dp(8)) }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorAccent)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(8))
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
            text = "Settings"
            setTextColor(colorAccent)
            textSize = 16f
            typeface = Typeface.MONOSPACE
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }

        // ---- About App
        content.addView(sectionTitle("ABOUT APP"))
        updateAppButton = pageButton("Update App") {
            startActivity(Intent(this, UpdateAppActivity::class.java))
        }
        content.addView(updateAppButton, rowParams())
        content.addView(
            pageButton("About") {
                startActivity(Intent(this, ReleaseActivity::class.java).putExtra(ReleaseActivity.EXTRA_MODE, ReleaseActivity.MODE_ABOUT))
            },
            rowParams()
        )

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    /** The editor menu's blue dot also marks the button that leads to the update. */
    private fun renderUpdateButton() {
        if (!::updateAppButton.isInitialized) return
        updateAppButton.text = if (updates.needsAttention()) "Update App ●" else "Update App"
    }
}
