package com.whatcode.settings

import android.content.Context
import com.whatcode.editor.EditorColouring
import com.whatcode.editor.LongLines

/**
 * How the editor treats big texts, saved on the device. Changed on the Settings > Code Editor > Large files page
 * ([LargeFileActivity]) and read by the editor screen ([MainActivity]) when it colours the text.
 */
class LargeFileSettings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A line with more characters than this is not coloured and not wrapped. */
    var longLineLimit: Int
        get() = prefs.getInt(KEY_LONG_LINE, DEFAULT_LONG_LINE).coerceIn(MIN_LONG_LINE, MAX_LONG_LINE)
        set(v) = prefs.edit().putInt(KEY_LONG_LINE, v.coerceIn(MIN_LONG_LINE, MAX_LONG_LINE)).apply()

    /** Milliseconds of not typing before the colours are worked out again. */
    var highlightDelayMs: Int
        get() = prefs.getInt(KEY_DELAY, DEFAULT_DELAY).coerceIn(MIN_DELAY, MAX_DELAY)
        set(v) = prefs.edit().putInt(KEY_DELAY, v.coerceIn(MIN_DELAY, MAX_DELAY)).apply()

    /** How many lines above and below the screen are coloured as well, in a long text. */
    var highlightMargin: Int
        get() = prefs.getInt(KEY_MARGIN, DEFAULT_MARGIN).coerceIn(MIN_MARGIN, MAX_MARGIN)
        set(v) = prefs.edit().putInt(KEY_MARGIN, v.coerceIn(MIN_MARGIN, MAX_MARGIN)).apply()

    fun resetToDefault() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FILE = "large_files"

        const val KEY_LONG_LINE = "long_line"
        const val KEY_DELAY = "highlight_delay"
        const val KEY_MARGIN = "highlight_margin"

        const val DEFAULT_LONG_LINE = LongLines.DEFAULT_LIMIT
        const val MIN_LONG_LINE = 1_000
        const val MAX_LONG_LINE = 1_000_000

        const val DEFAULT_DELAY = 200
        const val MIN_DELAY = 50
        const val MAX_DELAY = 5_000

        const val DEFAULT_MARGIN = EditorColouring.DEFAULT_MARGIN
        const val MIN_MARGIN = 10
        const val MAX_MARGIN = 1_000

        /** The choices of the drop-downs, besides "Custom...". */
        val LONG_LINE_PRESETS = listOf(5_000, 10_000, 20_000, 50_000, 100_000, 500_000)
        val DELAY_PRESETS = listOf(100, 200, 300, 500, 1_000)
        val MARGIN_PRESETS = listOf(20, 40, 60, 100, 200)
    }
}
