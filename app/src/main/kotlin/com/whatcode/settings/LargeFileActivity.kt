package com.whatcode.settings

import com.whatcode.bigfile.LargeFileConfig
import com.whatcode.bigfile.Saving
import com.whatcode.ui.SettingsPage

/**
 * The "Large files" page, opened from Settings > Code Editor. It sets when a text counts as large and what the editor
 * then turns off, how long a line may be before the editor leaves it uncolored and unwrapped, and how the colors of a
 * long text follow typing and scrolling. Each choice is saved at once; the editor screen reads them when it next
 * colors the text or the text changes size.
 */
class LargeFileActivity : SettingsPage("Large files") {

    private val settings by lazy { LargeFileSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("LARGE FILE MODE"))
        switchRow(
            "Large file mode", settings.largeFileMode,
            note = "A big text turns off some things so that it stays quick to edit. The status bar says Large file mode, " +
                "and tapping it shows what is off.",
        ) { settings.largeFileMode = it; render() }
        numberRow(
            "Large files start at", settings.largeAbove, LargeFileSettings.LARGE_PRESETS,
            LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE, ::characters,
            note = "A text with more characters than this is a large file. A file of plain English is about one byte " +
                "for each character.",
        ) { settings.largeAbove = it }
        numberRow(
            "Very large files start at", settings.veryLargeAbove, LargeFileSettings.VERY_LARGE_PRESETS,
            LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE, ::characters,
            note = "A text with more characters than this turns off the next list as well. A number below the first " +
                "is read as the same size.",
        ) { settings.veryLargeAbove = it }

        content.addView(sectionTitle("TURNED OFF IN A LARGE FILE"))
        for (s in listOf(Saving.WRAP, Saving.MATCHING)) savingRow(s, very = false)
        content.addView(sectionTitle("ALSO TURNED OFF IN A VERY LARGE FILE"))
        for (s in listOf(Saving.WHITESPACE, Saving.UNDO)) savingRow(s, very = true)
        numberRow(
            "Undo steps kept", settings.undoSteps, LargeFileSettings.UNDO_PRESETS,
            LargeFileConfig.MIN_UNDO_STEPS, LargeFileConfig.NORMAL_UNDO_STEPS, ::steps,
            note = "How many steps of undo a big text keeps when fewer are kept. An ordinary file keeps ${LargeFileConfig.NORMAL_UNDO_STEPS}.",
        ) { settings.undoSteps = it }

        content.addView(sectionTitle("VERY LONG LINES"))
        numberRow(
            "Longest line", settings.longLineLimit, LargeFileSettings.LONG_LINE_PRESETS,
            LargeFileSettings.MIN_LONG_LINE, LargeFileSettings.MAX_LONG_LINE, ::characters,
            note = "A line with more characters than this is not colored and not wrapped, so a minified script or a one-line " +
                "JSON file stays quick to edit. The status bar says when a file has one.",
        ) { settings.longLineLimit = it }

        content.addView(sectionTitle("COLORS IN A LONG TEXT"))
        numberRow(
            "Wait after typing", settings.highlightDelayMs, LargeFileSettings.DELAY_PRESETS,
            LargeFileSettings.MIN_DELAY, LargeFileSettings.MAX_DELAY, ::milliseconds,
            note = "The colors are worked out again when you stop typing for this long.",
        ) { settings.highlightDelayMs = it }
        numberRow(
            "Lines colored around the screen", settings.highlightMargin, LargeFileSettings.MARGIN_PRESETS,
            LargeFileSettings.MIN_MARGIN, LargeFileSettings.MAX_MARGIN, ::lines,
            note = "A text longer than 20,000 characters is colored only where you see it, and this many lines above and " +
                "below. A bigger number colors more before you scroll to it, and costs more at each pause.",
        ) { settings.highlightMargin = it }

        defaultsSection(
            question = "Put every choice on this page back to its default?",
            done = "Large files returned to default",
        ) { settings.resetToDefault() }
    }

    /** One switch of the lists of what a big text turns off; on means that it is turned off. */
    private fun savingRow(saving: Saving, very: Boolean) {
        val on = (if (very) settings.veryLargeOff else settings.largeOff) and saving.bit != 0
        switchRow(saving.label, on, enabled = settings.largeFileMode) { settings.setSaving(very, saving, it) }
    }

    private fun steps(n: Int) = "$n steps"
    private fun characters(n: Int) = "%,d characters".format(n)
    private fun milliseconds(n: Int) = if (n % 1000 == 0) "${n / 1000} s" else "$n ms"
    private fun lines(n: Int) = "$n lines"
}
