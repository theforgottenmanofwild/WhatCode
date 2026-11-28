package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Tabs" page, opened from Settings > Code Editor. Its switches decide whether opening a file adds a tab
 * for it or replaces the file in the editor, and whether the last open files come back on start. Choices are saved at once.
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

        content.addView(sectionTitle("ON START"))
        content.addView(toggle("Reopen last files", settings.reopenLastFiles) { settings.reopenLastFiles = it })
        content.addView(
            muted(
                "When you open the app, the files that were open when you left it come back in their tabs, with " +
                    "the cursor and scroll position where you left them. Files that were moved or deleted are skipped. " +
                    "With this off, the app starts on the file explorer."
            )
        )

        content.addView(sectionTitle("SAFETY AND SYNC"))
        switchRow(
            "Crash recovery journal", settings.crashRecovery,
            note = "Keeps unsaved edits in app storage so a sudden close offers to restore them next launch.",
        ) { settings.crashRecovery = it }
        switchRow(
            "Detect external file changes", settings.watchExternalChanges,
            note = "Checks if an open file changed on disk and offers to reload or compare it.",
        ) { settings.watchExternalChanges = it }

        content.addView(sectionTitle("PREVIEW AND SPLIT VIEW"))
        switchRow(
            "Split view on wide screens", settings.splitView,
            note = "Shows a second tab side by side on tablets or wide landscape screens.",
        ) { settings.splitView = it }
        switchRow(
            "Run JavaScript in HTML preview", settings.previewJavascript,
            note = "Off by default for safety. Turn on only when previewing HTML files you trust.",
        ) { settings.previewJavascript = it }

        defaultsSection("Open files in tabs and reopen the last files?", "Tabs returned to default") { settings.resetTabs() }
    }
}
