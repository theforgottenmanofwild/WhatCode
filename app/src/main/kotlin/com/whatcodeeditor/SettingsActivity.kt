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
import android.widget.Switch
import android.widget.TextView

/**
 * The settings page, opened from the editor's menu. It holds the updates section and an About section
 * with the "What's new" page; add further sections to [buildUi] the same way.
 */
class SettingsActivity : Activity() {

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
        val back = button("\u2039") { finish() }.apply {
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

        // ---- Updates
        content.addView(sectionTitle("UPDATES"))
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

        // ---- About
        content.addView(sectionTitle("ABOUT").apply { setPadding(0, dp(28), 0, dp(8)) })
        content.addView(TextView(this).apply {
            val u = Updater(applicationContext)
            text = "WhatCodeEditor ${u.installedName} (${u.channel.label} channel)"
            setTextColor(colorText)
            textSize = 15f
        })
        content.addView(
            button("What's new") {
                startActivity(Intent(this, ReleaseActivity::class.java).putExtra(ReleaseActivity.EXTRA_MODE, ReleaseActivity.MODE_ABOUT))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(0, dp(12), 0, 0) }
        )
        content.addView(TextView(this).apply {
            text = "The release notes of this version, with links to the project."
            setTextColor(colorMuted)
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    /** Redraws the updates section from the current state. */
    private fun renderUpdates() {
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
