package com.whatcode.settings.explorer

import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import com.whatcode.explorer.ExplorerSettings
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.settings.ApkSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > APK files: tap action, install prompts, hashes, warnings, archives and row cache. */
class ExplorerApkActivity : SettingsPage("APK files") {

    private val settings by lazy { ApkSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("TAPPING AN APK"))
        choices(ApkSettings.TapAction.values().toList(), settings.tapAction, { it.label }) {
            settings.tapAction = it
        }
        content.addView(muted("Ask what to do shows Info, Install, Browse contents, Extract and Share."))

        content.addView(sectionTitle("INSTALLING"))
        switchRow(
            "Ask before installing", settings.confirmInstall,
            note = "Shows package details and comparison with the installed app before opening Android's installer.",
        ) { settings.confirmInstall = it }
        content.addView(muted("After installing:"))
        choices(ApkSettings.AfterInstall.values().toList(), settings.afterInstall, { it.label }) {
            settings.afterInstall = it
        }
        switchRow(
            "Warn on downgrade", settings.warnDowngrade,
            note = "Highlights when the APK has a lower version code than the installed app.",
        ) { settings.warnDowngrade = it }
        switchRow(
            "Warn on different signature", settings.warnSignature,
            note = "Highlights when the signing certificate differs from the installed app.",
        ) { settings.warnSignature = it }

        content.addView(sectionTitle("APK INFO"))
        switchRow("Compute SHA-256 hash", settings.showSha256) { settings.showSha256 = it }
        switchRow("Compute MD5 hash", settings.showMd5) { settings.showMd5 = it }

        content.addView(sectionTitle("EXPLORER ROWS"))
        switchRow(
            "Show app icon and name on APK rows", settings.showRowAppInfo,
            note = "Reads the app icon and label from each APK in the background and keeps them in a bounded cache.",
        ) { settings.showRowAppInfo = it }
        numberRow(
            "APK icon cache limit", settings.iconCacheMb, ApkSettings.CACHE_PRESETS,
            ApkSettings.MIN_CACHE_MB, ApkSettings.MAX_CACHE_MB,
            show = { "$it MB" },
            enabled = settings.showRowAppInfo,
        ) { settings.iconCacheMb = it }

        content.addView(sectionTitle("BUILD SHORTCUT"))
        switchRow(
            "Show Latest build shortcut in sidebar", settings.latestBuildShortcut,
            note = "Pins the newest APK found under app/build/outputs/apk at the top of the project tree.",
        ) { settings.latestBuildShortcut = it }

        content.addView(sectionTitle("ARCHIVE EXTRACTION"))
        val extractValue = settings.extractFolder.ifBlank { "Same folder as archive" }
        content.addView(
            wide("Default extract folder   ·   $extractValue") { askExtractFolder() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) },
        )
        content.addView(muted("Where extracted folders go by default. Leave blank to extract beside the archive."))
        numberRow(
            "Archive size limit", settings.zipBombMaxMb, ApkSettings.BOMB_MB_PRESETS,
            ApkSettings.MIN_BOMB_MB, ApkSettings.MAX_BOMB_MB,
            show = { "$it MB" },
            note = "Stops extracting if uncompressed bytes pass this limit.",
        ) { settings.zipBombMaxMb = it }
        numberRow(
            "Archive entry limit", settings.zipBombMaxEntries, ApkSettings.BOMB_ENTRY_PRESETS,
            ApkSettings.MIN_BOMB_ENTRIES, ApkSettings.MAX_BOMB_ENTRIES,
            note = "Stops extracting if the archive has more files than this.",
        ) { settings.zipBombMaxEntries = it }

        defaultsSection(
            question = "Put every APK setting back to its default?",
            done = "APK settings returned to default",
        ) { settings.resetToDefault() }
    }

    private fun askExtractFolder() {
        val input = EditText(this).apply {
            setText(settings.extractFolder)
            hint = "/storage/emulated/0/Extracted (blank = beside archive)"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AppTheme.dialogBuilder(this)
            .setTitle("Default extract folder")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val raw = input.text.toString().trim()
                settings.extractFolder = if (raw.isEmpty()) "" else ExplorerPaths.normalize(raw)
                render()
            }
            .setNeutralButton("Reset to default") { _, _ ->
                settings.extractFolder = ""
                render()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, this)
    }
}
