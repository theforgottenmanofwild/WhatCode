package com.whatcode.settings.explorer

import com.whatcode.explorer.ExplorerSettings
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > Safety: asking before deleting, and what a paste does when a name is taken. */
class ExplorerSafetyActivity : SettingsPage("Safety") {

    private val settings by lazy { ExplorerSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("DELETING"))
        switchRow(
            "Ask before deleting", settings.confirmDelete,
            note = "A deleted file or folder can't be brought back.",
        ) { settings.confirmDelete = it }

        content.addView(sectionTitle("WHEN A NAME IS TAKEN"))
        choices(ExplorerSettings.Clash.values().toList(), settings.clash, { it.label }) { settings.clash = it }
        content.addView(muted("Used when a copy or a move is pasted into a folder that has the same name."))

        defaultsSection(
            question = "Ask before deleting, and ask what to do when a name is taken?",
            done = "Safety returned to default",
        ) { settings.resetSafety() }
    }
}
