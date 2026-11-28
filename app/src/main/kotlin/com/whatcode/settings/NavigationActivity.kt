package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Navigation" page, opened from Settings > Code Editor. It holds the choices for finding your way in a file:
 * the switch that shows the current scope in the status bar, and the switches for bookmarks and for Back and Forward. Each choice is saved at once; the editor screen
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

        content.addView(sectionTitle("MOVING ABOUT"))
        switchRow(
            "Bookmarks", settings.bookmarks,
            note = "Mark a line with the Mk key (Ctrl+B) and go between the marked lines with Mk> and Mk< (Ctrl+N, and " +
                "Ctrl+Shift+N). A bar at the left edge shows a marked line. Bookmarks stay with their line while you edit, " +
                "and with their tab until it is closed.",
        ) { settings.bookmarks = it }
        switchRow(
            "Back and forward", settings.jumpHistory,
            note = "Go to line, Go to symbol, a bookmark and a search hit remember where the cursor was. The <- key " +
                "(Ctrl+-) goes back there, and the -> key (Ctrl+Shift+-) goes forward again. A run of search hits counts as one jump.",
        ) { settings.jumpHistory = it }

        defaultsSection("Put the Navigation choices back to their defaults?", "Navigation returned to default") { settings.resetNavigation() }
    }
}
