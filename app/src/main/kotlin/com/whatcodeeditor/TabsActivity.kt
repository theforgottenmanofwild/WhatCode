package com.whatcodeeditor

/**
 * The "Tabs" page, opened from Settings > Code Editor. Its one switch decides whether opening a file adds a tab
 * for it or replaces the file in the editor. The choice is saved at once.
 */
class TabsActivity : SettingsPage("Tabs") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(toggle("Open files in tabs", settings.openInTabs) { settings.openInTabs = it })
        content.addView(
            muted(
                "Each file you open gets its own tab above the editor, so you can switch between files without " +
                    "losing your place or your undo history. A dot on a tab means it has unsaved changes. Tap a tab " +
                    "to show it, × to close it, and long-press a tab to close the others or all of them. " +
                    "The tab bar shows once two files are open."
            )
        )
        content.addView(
            muted(
                "With this off, opening a file replaces the one in the editor (after asking about unsaved changes). " +
                    "Tabs that are already open stay in the bar until you close them."
            )
        )

        defaultsSection("Open files in tabs?", "Tabs returned to default") { settings.resetTabs() }
    }
}
