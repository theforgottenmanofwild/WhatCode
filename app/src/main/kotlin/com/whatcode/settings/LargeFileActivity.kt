package com.whatcode.settings

import com.whatcode.ui.SettingsPage

/**
 * The "Large files" page, opened from Settings > Code Editor. It sets how long a line may be before the editor leaves
 * it uncolored and unwrapped, and how the colors of a long text follow typing and scrolling. Each choice is saved at
 * once; the editor screen reads them when it next colors the text.
 */
class LargeFileActivity : SettingsPage("Large files") {

    private val settings by lazy { LargeFileSettings(this) }

    override fun render() {
        content.removeAllViews()

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
            question = "Put the longest line, the wait after typing and the lines colored around the screen back to their defaults?",
            done = "Large files returned to default",
        ) { settings.resetToDefault() }
    }

    private fun characters(n: Int) = "%,d characters".format(n)
    private fun milliseconds(n: Int) = if (n % 1000 == 0) "${n / 1000} s" else "$n ms"
    private fun lines(n: Int) = "$n lines"
}
