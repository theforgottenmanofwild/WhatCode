package com.whatcode.settings

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.keys.KeyEditorActivity
import com.whatcode.ui.AppTheme
import com.whatcode.ui.ThemeMode
import com.whatcode.update.ReleaseActivity
import com.whatcode.update.UpdateAppActivity
import com.whatcode.update.UpdateController

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
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }

        content.addView(sectionTitle("LOOK"))
        content.addView(group(
            settingRow(SettingsGlyph.APPEARANCE, "Appearance", "Theme, accent, font, line numbers and syntax colors") {
                startActivity(Intent(this, AppearanceActivity::class.java))
            },
        ))

        content.addView(sectionTitle("CODE EDITOR"))
        content.addView(group(
            settingRow(SettingsGlyph.KEYS, "Key Editor", "Choose the extra keys above the keyboard") {
                startActivity(Intent(this, KeyEditorActivity::class.java))
            },
            settingRow(SettingsGlyph.TYPING, "Typing", "Brackets, tags, indentation, clipboard and keyboard") {
                startActivity(Intent(this, TypingActivity::class.java))
            },
            settingRow(SettingsGlyph.TABS, "Tabs", "Open files, and reopening them on start") {
                startActivity(Intent(this, TabsActivity::class.java))
            },
            settingRow(SettingsGlyph.AUTOSAVE, "Autosave", "Choose when changes are saved") {
                startActivity(Intent(this, AutosaveActivity::class.java))
            },
        ))

        content.addView(sectionTitle("BACKUP"))
        content.addView(group(
            settingRow(SettingsGlyph.BACKUP, "Import and export", "Save or restore your settings as JSON") {
                startActivity(Intent(this, BackupActivity::class.java))
            },
        ))

        content.addView(sectionTitle("ABOUT APP"))
        val updateRow = settingRow(SettingsGlyph.UPDATE, "Update App", "Check for updates and manage downloads", keepTitle = true) {
            startActivity(Intent(this, UpdateAppActivity::class.java))
        }
        updateTitle = updateRow.tag as TextView
        content.addView(group(
            updateRow,
            settingRow(SettingsGlyph.ABOUT, "About", "Version, what's new and community") {
                startActivity(Intent(this, ReleaseActivity::class.java).putExtra(ReleaseActivity.EXTRA_MODE, ReleaseActivity.MODE_ABOUT))
            },
        ))

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
        setPadding(dp(4), dp(22), 0, dp(9))
    }

    /** Rows that belong together share one rounded card, with a thin line between them. */
    private fun group(vararg rows: View): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.card(this@SettingsActivity, palette, 18f)
            clipToOutline = true
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                card.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    setMargins(dp(70), 0, 0, 0)
                })
            }
            card.addView(row)
        }
        return card
    }

    private fun settingRow(
        glyph: SettingsGlyph,
        title: String,
        subtitle: String,
        keepTitle: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RippleDrawable(ColorStateList.valueOf(p.ripple), null, ColorDrawable(0xFFFFFFFF.toInt()))
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
        row.addView(SettingsGlyphView(this, glyph, p.accent, p.accentSoft), LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            setMargins(0, 0, dp(14), 0)
        })
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
            textSize = 13f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 26f
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
