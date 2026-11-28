package com.whatcode.settings

import com.whatcode.editor.TextTools
import com.whatcode.ui.SettingsPage

/**
 * The "Text tools" page, opened from Settings > Code Editor: whether the text tools can be used, how lines are sorted
 * and compared, and how wide a tab is when the indentation is converted. Each choice is saved at once.
 */
class TextToolsActivity : SettingsPage("Text tools") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        switchRow(
            "Text tools", settings.textTools,
            note = "The Txt key (Ctrl+P) lists the tools: sort, reverse and remove duplicate lines, change case, swap " +
                "characters, convert indentation and line endings, and format or minify JSON. Tapping the line ending in the status bar converts " +
                "it too. Each use is one undo step.",
        ) { settings.textTools = it }

        val on = settings.textTools
        content.addView(sectionTitle("SORTING LINES"))
        switchRow("Ignore capitals when sorting", settings.sortIgnoreCase, enabled = on) { settings.sortIgnoreCase = it }
        switchRow("Sort numbers by value", settings.sortNatural, note = "So item2 comes before item10.", enabled = on) {
            settings.sortNatural = it
        }

        content.addView(sectionTitle("DUPLICATE LINES"))
        switchRow("Ignore capitals when comparing", settings.uniqueIgnoreCase, enabled = on) { settings.uniqueIgnoreCase = it }

        content.addView(sectionTitle("INDENTATION"))
        numberRow(
            "Tab width", settings.tabWidth, listOf(2, 4, 8), EditorSettings.MIN_TAB_WIDTH, EditorSettings.MAX_TAB_WIDTH,
            show = { if (it == 1) "1 column" else "$it columns" },
            note = "How many spaces one tab stands for in Indent with tabs and Indent with spaces.",
        ) { settings.tabWidth = it }

        content.addView(sectionTitle("JSON"))
        numberRow(
            "JSON indentation", settings.jsonIndent, listOf(0, 2, 4, 8), 0, TextTools.MAX_JSON_INDENT,
            show = { if (it == 0) "A tab" else if (it == 1) "1 space" else "$it spaces" },
            note = "One level of Format JSON. Minify JSON takes all the white space out.",
        ) { settings.jsonIndent = it }

        defaultsSection("Put the Text tools choices back to their defaults?", "Text tools returned to default") { settings.resetTextTools() }
    }
}
