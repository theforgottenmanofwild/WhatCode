package com.whatcode.settings.explorer

import com.whatcode.explorer.ExplorerSettings
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > Taps: the ⋮ button, pressing and holding, and what tapping a file does. */
class ExplorerTapsActivity : SettingsPage("Taps") {

    private val settings by lazy { ExplorerSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("ROW MENU"))
        switchRow(
            "Show the ⋮ button on rows", settings.menuButton,
            note = "The ⋮ button opens the menu of that row. Choose which actions it has in Row menu.",
        ) { settings.menuButton = it }

        content.addView(sectionTitle("PRESSING AND HOLDING A ROW"))
        choices(ExplorerSettings.LongPress.values().toList(), settings.longPress, { it.label }) { settings.longPress = it }
        content.addView(muted("Selecting lets you pick several files and folders to copy, move, delete or share."))

        content.addView(sectionTitle("TAPPING A TEXT FILE"))
        choices(ExplorerSettings.TextTap.values().toList(), settings.textTap, { it.label }) { settings.textTap = it }
        content.addView(muted("Asking offers the code editor or another app on the phone."))

        content.addView(sectionTitle("TAPPING A FILE THAT ISN'T TEXT"))
        choices(ExplorerSettings.OtherTap.values().toList(), settings.otherTap, { it.label }) { settings.otherTap = it }
        content.addView(muted("Images, zips and apps can't be opened in the code editor."))

        defaultsSection(
            question = "Show the ⋮ button, select on a long press, open text files in the code editor, and say when a file isn't text?",
            done = "Taps returned to default",
        ) { settings.resetTaps() }
    }
}
