package com.whatcode.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.keys.KeyEditorActivity
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage
import com.whatcode.ui.ThemeMode
import com.whatcode.update.ReleaseActivity
import com.whatcode.update.UpdateAppActivity
import com.whatcode.update.UpdateController

/** The app's settings hub: appearance, editor behaviour, updates and project information. */
class SettingsActivity : SettingsPage("Settings") {

    private lateinit var updateTitle: TextView
    private lateinit var updates: UpdateController
    private var themeAtCreate: ThemeMode = ThemeMode.SYSTEM

    override fun onPrepare() {
        themeAtCreate = AppTheme.mode(this)
        updates = UpdateController(this) { renderUpdateButton(updates.needsAttention()) }
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

    override fun render() {
        content.removeAllViews()

        content.addView(groupTitle("LOOK"))
        content.addView(group(
            settingRow(SettingsGlyph.APPEARANCE, "Appearance", "Theme, accent, font, line numbers and syntax colors") {
                startActivity(Intent(this, AppearanceActivity::class.java))
            },
        ))

        content.addView(groupTitle("CODE EDITOR"))
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

        content.addView(groupTitle("BACKUP"))
        content.addView(group(
            settingRow(SettingsGlyph.BACKUP, "Import and export", "Save or restore your settings as JSON") {
                startActivity(Intent(this, BackupActivity::class.java))
            },
        ))

        content.addView(groupTitle("ABOUT APP"))
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
        renderUpdateButton(updates.needsAttention())
    }

    private fun groupTitle(label: String) = TextView(this).apply {
        text = label
        setTextColor(colorMuted)
        textSize = 12f
        letterSpacing = 0.12f
        typeface = Typeface.DEFAULT_BOLD
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
            typeface = Typeface.DEFAULT_BOLD
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
