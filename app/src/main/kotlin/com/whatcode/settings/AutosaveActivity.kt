package com.whatcode.settings

import android.view.ViewGroup
import android.widget.LinearLayout
import com.whatcode.ui.SettingsPage

/**
 * The "Autosave" page, opened from Settings > Code Editor. It has the switch for autosave, how long to wait
 * after typing stops, whether to also save when the app is left, and a button that puts all three back to
 * their defaults. Each choice is saved at once; the editor screen reads them when it needs them.
 */
class AutosaveActivity : SettingsPage("Autosave") {

    private val settings by lazy { AutosaveSettings(this) }

    /** Redraws the page from the saved settings. */
    override fun render() {
        content.removeAllViews()
        val on = settings.enabled

        content.addView(toggle("Autosave", on) {
            settings.enabled = it
            render()
        })
        content.addView(
            muted(
                "Saves the open file by itself, so you don't have to tap Save. " +
                    "A new file that has no name yet is not autosaved; use Save as once first."
            )
        )

        // How long to wait after typing stops.
        content.addView(sectionTitle("SAVE AFTER YOU STOP TYPING FOR"))
        for (seconds in AutosaveSettings.DELAYS) {
            content.addView(
                choice(AutosaveSettings.label(seconds), seconds == settings.delaySeconds, enabled = on) {
                    settings.delaySeconds = seconds
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, 0, 0, dp(4)) }
            )
        }
        content.addView(muted("Each key you type starts the wait again, so the file is saved when you pause."))

        // Also when leaving the app.
        content.addView(sectionTitle("LEAVING THE APP"))
        content.addView(toggle("Also save when I leave the app", settings.saveOnLeave, enabled = on) { settings.saveOnLeave = it })
        content.addView(muted("Saves straight away when you switch to another app or close this one, without waiting."))

        // Back to the defaults.
        defaultsSection(
            question = "Turn autosave off, wait ${AutosaveSettings.label(AutosaveSettings.DEFAULT_DELAY)} " +
                "and save when leaving the app?",
            done = "Autosave returned to default",
        ) { settings.resetToDefault() }
    }
}
