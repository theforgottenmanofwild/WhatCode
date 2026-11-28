package com.whatcode.settings

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.whatcode.keys.KeyEditorActivity
import com.whatcode.ui.AppTheme
import com.whatcode.ui.ThemeMode
import com.whatcode.update.ReleaseActivity
import com.whatcode.update.UpdateAppActivity
import com.whatcode.update.UpdateController

/** The app's settings hub: appearance, editor behaviour, updates and project information. */
class SettingsActivity : SettingsHubPage("Settings") {

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
            settingRow(SettingsGlyph.NAVIGATION, "Navigation", "Scope, bookmarks and going back") {
                startActivity(Intent(this, NavigationActivity::class.java))
            },
            settingRow(SettingsGlyph.TEXT_TOOLS, "Text tools", "Lines, case, indentation and line endings") {
                startActivity(Intent(this, TextToolsActivity::class.java))
            },
            settingRow(SettingsGlyph.SNIPPETS, "Snippets", "Short words that Tab turns into code") {
                startActivity(Intent(this, SnippetsActivity::class.java))
            },
            settingRow(SettingsGlyph.AUTOSAVE, "Autosave", "Choose when changes are saved") {
                startActivity(Intent(this, AutosaveActivity::class.java))
            },
            settingRow(SettingsGlyph.LARGE_FILES, "Large files", "What a big file turns off, very long lines and colors") {
                startActivity(Intent(this, LargeFileActivity::class.java))
            },
        ))

        content.addView(groupTitle("FILE EXPLORER"))
        content.addView(group(
            settingRow(SettingsGlyph.EXPLORER, "File explorer", "View, sorting, start folder and what taps do") {
                startActivity(Intent(this, com.whatcode.settings.explorer.ExplorerSettingsActivity::class.java))
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

    private fun renderUpdateButton(needsAttention: Boolean) {
        if (!::updateTitle.isInitialized) return
        updateTitle.text = if (needsAttention) "Update App  •" else "Update App"
    }
}
