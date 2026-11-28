package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Completion" page, opened from Settings > Code Editor: whether words from the open files are suggested while
 * you type, how many letters it waits for, how long it waits after the last one, whether the other open tabs are
 * read, and whether Enter takes a suggestion. Each choice is saved at once; the editor screen reads it when it is
 * shown again.
 */
class CompletionActivity : SettingsPage("Completion") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        switchRow(
            "Suggest words", settings.completion,
            note = "While you type, a short list under the cursor offers the words of the file that start with what " +
                "you typed. Tab takes the first one, the up and down keys of a keyboard choose another, and Esc closes " +
                "the list. The Cmp key (Ctrl+Space) asks for suggestions at any time. Taking one is a single undo step. " +
                "After a snippet word or an abbreviation, Tab still puts the snippet in.",
        ) { settings.completion = it }

        val on = settings.completion
        content.addView(sectionTitle("WHEN"))
        numberRow(
            "Letters first", settings.completionMinLetters, listOf(1, 2, 3, 4), EditorSettings.MIN_COMPLETION_MIN,
            EditorSettings.MAX_COMPLETION_MIN,
            show = { if (it == 1) "1 letter" else "$it letters" },
            note = "How many letters of a word are typed before words are suggested. The Cmp key ignores this.",
        ) { settings.completionMinLetters = it }
        numberRow(
            "Wait after typing", settings.completionDelay, listOf(0, 150, 250, 500), 0, EditorSettings.MAX_COMPLETION_DELAY,
            show = { if (it == 0) "No wait" else "$it ms" },
            note = "How long to wait after the last letter before looking for words.",
        ) { settings.completionDelay = it }

        content.addView(sectionTitle("WHERE FROM"))
        switchRow(
            "Words of the other open tabs", settings.completionOtherFiles, enabled = on,
            note = "Words found only in your other tabs come after the words of the file you are in. At most 10 tabs, " +
                "and the first 300,000 characters of each, are read. A big file is never read whole: only 100,000 " +
                "characters on each side of the cursor are looked at.",
        ) { settings.completionOtherFiles = it }

        content.addView(sectionTitle("KEYS"))
        switchRow(
            "Enter takes the suggestion", settings.completionEnter, enabled = on,
            note = "With this off, Enter always starts a new line and closes the list.",
        ) { settings.completionEnter = it }

        defaultsSection("Put the Completion choices back to their defaults?", "Completion returned to default") { settings.resetCompletion() }
    }
}
