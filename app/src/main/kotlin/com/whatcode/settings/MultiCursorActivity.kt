package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Multiple cursors" page, opened from Settings > Code Editor: whether several cursors can be used, how many at
 * most, how the next place with the same text is found, and whether a paste with one line for each cursor is split.
 * Each choice is saved at once; the editor screen reads it when it is shown again.
 */
class MultiCursorActivity : SettingsPage("Multiple cursors") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        switchRow(
            "Multiple cursors", settings.multiCursor,
            note = "The +Nx key (Ctrl+Shift+D) selects the word at the cursor and then adds a cursor at the next place " +
                "with the same text. The +All key (Ctrl+Shift+L) puts one at every place. What you type, delete, cut, " +
                "paste or move then happens at each cursor, and one undo takes it all back. Esc, a touch on the text, " +
                "or any other change drops the extra cursors. Add the keys in the Key Editor.",
        ) { settings.multiCursor = it }

        val on = settings.multiCursor
        content.addView(sectionTitle("FINDING PLACES"))
        switchRow(
            "Match capitals", settings.multiCursorMatchCase, enabled = on,
            note = "With this off, Name also matches name and NAME.",
        ) { settings.multiCursorMatchCase = it }
        switchRow(
            "Whole words only", settings.multiCursorWholeWord, enabled = on,
            note = "With this on, the word cat is not found inside concatenate.",
        ) { settings.multiCursorWholeWord = it }
        numberRow(
            "Most cursors", settings.multiCursorMax, listOf(50, 200, 500, 1000), EditorSettings.MIN_MULTI_CURSOR_MAX,
            EditorSettings.MAX_MULTI_CURSOR_MAX,
            show = { "$it cursors" },
            note = "Adding more is refused. Every cursor is drawn and edited at once, so a big number can be slow on a long text.",
        ) { settings.multiCursorMax = it }

        content.addView(sectionTitle("PASTE"))
        switchRow(
            "One line to each cursor", settings.multiCursorPasteLines, enabled = on,
            note = "Pasting text with as many lines as there are cursors gives the first line to the first cursor, the " +
                "second to the second, and so on. With any other number of lines, all of the text goes in at every cursor.",
        ) { settings.multiCursorPasteLines = it }

        defaultsSection("Put the Multiple cursors choices back to their defaults?", "Multiple cursors returned to default") { settings.resetMultiCursor() }
    }
}
