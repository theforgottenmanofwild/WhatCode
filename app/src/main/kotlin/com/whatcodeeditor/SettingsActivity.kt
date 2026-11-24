package com.whatcodeeditor

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The app's settings hub: appearance, editor behaviour, updates and project information. */
class SettingsActivity : Activity() {

    private val palette by lazy { AppTheme.palette(this) }
    private lateinit var updateTitle: TextView
    private lateinit var updates: UpdateController
    private var themeAtCreate: ThemeMode = ThemeMode.SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        themeAtCreate = AppTheme.mode(this)
        AppTheme.applyWindow(this)
        updates = UpdateController(this) { renderUpdateButton(updates.needsAttention()) }
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (themeAtCreate != AppTheme.mode(this)) {
            recreate()
            return
        }
        AppTheme.applyWindow(this)
        if (::updateTitle.isInitialized) {
            updates.refresh()
            renderUpdateButton(updates.needsAttention())
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val p = palette
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(p.surface)
            setPadding(dp(8), dp(4), dp(18), dp(4))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(p.text)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(TextView(this).apply {
            text = "Settings"
            textSize = 20f
            setTextColor(p.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        content.addView(TextView(this).apply {
            text = "Make the editor yours"
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(p.text)
        })
        content.addView(TextView(this).apply {
            text = "Adjust the look, typing tools and app settings."
            textSize = 14f
            setTextColor(p.muted)
            setPadding(0, dp(5), 0, dp(8))
        })

        content.addView(sectionTitle("APPEARANCE"))
        content.addView(settingRow("Appearance", "Color mode, including your device's system setting") {
            startActivity(Intent(this, AppearanceActivity::class.java))
        })

        content.addView(sectionTitle("CODE EDITOR"))
        content.addView(settingRow("Key Editor", "Choose the extra keys shown above the keyboard") {
            startActivity(Intent(this, KeyEditorActivity::class.java))
        })
        content.addView(settingRow("Typing", "Automatic brackets, indentation and keyboard behaviour") {
            startActivity(Intent(this, TypingActivity::class.java))
        })
        content.addView(settingRow("Tabs", "Control how open files are kept and switched") {
            startActivity(Intent(this, TabsActivity::class.java))
        })
        content.addView(settingRow("Autosave", "Choose when changes are saved automatically") {
            startActivity(Intent(this, AutosaveActivity::class.java))
        })

        content.addView(sectionTitle("ABOUT APP"))
        val updateRow = settingRow("Update App", "Check for updates and manage downloads", keepTitle = true) {
            startActivity(Intent(this, UpdateAppActivity::class.java))
        }
        updateTitle = updateRow.tag as TextView
        content.addView(updateRow)
        content.addView(settingRow("About", "Version information and what's new") {
            startActivity(Intent(this, ReleaseActivity::class.java).putExtra(ReleaseActivity.EXTRA_MODE, ReleaseActivity.MODE_ABOUT))
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun sectionTitle(label: String) = TextView(this).apply {
        text = label
        setTextColor(palette.muted)
        textSize = 12f
        letterSpacing = 0.12f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(2), dp(24), 0, dp(9))
    }

    private fun settingRow(
        title: String,
        subtitle: String,
        keepTitle: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.pressable(this@SettingsActivity, p.surface, p.ripple, 15f, p.outline)
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(13), dp(14), dp(13))
            setOnClickListener { onClick() }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val heading = TextView(this).apply {
            text = title
            setTextColor(p.text)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        labels.addView(heading)
        labels.addView(TextView(this).apply {
            text = subtitle
            setTextColor(p.muted)
            textSize = 12f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(p.muted)
        }, LinearLayout.LayoutParams(dp(28), dp(36)))
        row.minimumHeight = dp(68)
        row.tag = if (keepTitle) heading else null
        return row
    }

    private fun renderUpdateButton(needsAttention: Boolean) {
        if (!::updateTitle.isInitialized) return
        updateTitle.text = if (needsAttention) "Update App  •" else "Update App"
    }
}
