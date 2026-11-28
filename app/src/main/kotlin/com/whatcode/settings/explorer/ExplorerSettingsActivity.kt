package com.whatcode.settings.explorer

import android.content.Intent
import com.whatcode.settings.SettingsGlyph
import com.whatcode.settings.SettingsHubPage

/**
 * Settings > File explorer: the list of the explorer's own pages. Each page saves its choices at once, and the
 * explorer picks them up when it is shown again, without a restart.
 */
class ExplorerSettingsActivity : SettingsHubPage("File explorer") {

    override fun render() {
        content.removeAllViews()
        content.addView(groupTitle("THE LIST"))
        content.addView(group(
            settingRow(SettingsGlyph.VIEW, "View", "Layout, row height, tile size, sizes, dates and icons") {
                startActivity(Intent(this, ExplorerViewActivity::class.java))
            },
            settingRow(SettingsGlyph.SORT, "Sorting", "Order, folders first, numbers, capitals and hidden files") {
                startActivity(Intent(this, ExplorerSortActivity::class.java))
            },
        ))
        content.addView(groupTitle("USING IT"))
        content.addView(group(
            settingRow(SettingsGlyph.START, "Start and navigation", "Start folder, typing a path, pull to refresh and Back") {
                startActivity(Intent(this, ExplorerStartActivity::class.java))
            },
            settingRow(SettingsGlyph.TAPS, "Taps", "The ⋮ button, pressing and holding, and what tapping a file does") {
                startActivity(Intent(this, ExplorerTapsActivity::class.java))
            },
            settingRow(SettingsGlyph.SAFETY, "Safety", "Asking before deleting, and what a paste does when a name is taken") {
                startActivity(Intent(this, ExplorerSafetyActivity::class.java))
            },
            settingRow(SettingsGlyph.ROW_MENU, "Row menu", "Which actions the menu of a row shows, and in what order") {
                startActivity(Intent(this, ExplorerRowMenuActivity::class.java))
            },
            settingRow(SettingsGlyph.APK, "APK files", "What tapping an APK does, install prompts, hashes and icon cache") {
                startActivity(Intent(this, ExplorerApkActivity::class.java))
            },
        ))
    }
}
