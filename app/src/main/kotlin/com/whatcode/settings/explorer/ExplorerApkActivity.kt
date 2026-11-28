package com.whatcode.settings.explorer

import com.whatcode.settings.ApkSettings
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > APK files: tap action, install prompts, hashes, warnings and row cache. */
class ExplorerApkActivity : SettingsPage("APK files") {

    private val settings by lazy { ApkSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("TAPPING AN APK"))
        choices(ApkSettings.TapAction.values().toList(), settings.tapAction, { it.label }) {
            settings.tapAction = it
        }
        content.addView(muted("Ask what to do shows Info, Install and Share."))

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

        defaultsSection(
            question = "Put every APK setting back to its default?",
            done = "APK settings returned to default",
        ) { settings.resetToDefault() }
    }
}
