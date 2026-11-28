package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Navigation" page, opened from Settings > Code Editor. It holds the choices for finding your way in a file:
 * for now the switch that shows the current scope in the status bar. Each choice is saved at once; the editor screen
 * reads it when it is shown again.
 */
class NavigationActivity : SettingsPage("Navigation") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("STATUS BAR"))
        switchRow(
            "Show the current scope", settings.showScope,
            note = "The status bar names the class and function that contain the cursor, such as Foo › run, or the " +
                "Markdown heading above it. It is found from the indentation of the lines, and it is not shown in a text of over 200,000 characters or in large file mode.",
        ) { settings.showScope = it }

        defaultsSection("Put the Navigation choices back to their defaults?", "Navigation returned to default") { settings.resetNavigation() }
    }
}
