package com.whatcode.settings

import android.content.Context
import com.whatcode.bigfile.LargeFileConfig
import com.whatcode.bigfile.Saving
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

    /** Whether large file mode is on at all. Off, no size turns anything off. */
    var largeFileMode: Boolean
        get() = prefs.getBoolean(KEY_MODE, LargeFileConfig.DEFAULT_ENABLED)
        set(v) = prefs.edit().putBoolean(KEY_MODE, v).apply()

    /** A text with more characters than this is a large file. */
    var largeAbove: Int
        get() = prefs.getInt(KEY_LARGE, LargeFileConfig.DEFAULT_LARGE).coerceIn(LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE)
        set(v) = prefs.edit().putInt(KEY_LARGE, v.coerceIn(LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE)).apply()

    /** A text with more characters than this is a very large file. */
    var veryLargeAbove: Int
        get() = prefs.getInt(KEY_VERY_LARGE, LargeFileConfig.DEFAULT_VERY_LARGE).coerceIn(LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE)
        set(v) = prefs.edit().putInt(KEY_VERY_LARGE, v.coerceIn(LargeFileConfig.MIN_SIZE, LargeFileConfig.MAX_SIZE)).apply()

    /** What a large file turns off ([Saving] bits). */
    var largeOff: Int
        get() = prefs.getInt(KEY_LARGE_OFF, LargeFileConfig.DEFAULT_TIER1_OFF) and LargeFileConfig.ALL_BITS
        set(v) = prefs.edit().putInt(KEY_LARGE_OFF, v and LargeFileConfig.ALL_BITS).apply()

    /** What a very large file turns off as well ([Saving] bits). */
    var veryLargeOff: Int
        get() = prefs.getInt(KEY_VERY_LARGE_OFF, LargeFileConfig.DEFAULT_TIER2_OFF) and LargeFileConfig.ALL_BITS
        set(v) = prefs.edit().putInt(KEY_VERY_LARGE_OFF, v and LargeFileConfig.ALL_BITS).apply()

    /** How many undo steps a file keeps when its undo history is cut short. */
    var undoSteps: Int
        get() = prefs.getInt(KEY_UNDO, LargeFileConfig.DEFAULT_UNDO_STEPS).coerceIn(LargeFileConfig.MIN_UNDO_STEPS, LargeFileConfig.NORMAL_UNDO_STEPS)
        set(v) = prefs.edit().putInt(KEY_UNDO, v.coerceIn(LargeFileConfig.MIN_UNDO_STEPS, LargeFileConfig.NORMAL_UNDO_STEPS)).apply()

    /** The choices above, as the editor reads them. */
    fun config(): LargeFileConfig = LargeFileConfig(largeFileMode, largeAbove, veryLargeAbove, largeOff, veryLargeOff, undoSteps)

    fun setSaving(very: Boolean, saving: Saving, off: Boolean) {
        val now = if (very) veryLargeOff else largeOff
        val next = if (off) now or saving.bit else now and saving.bit.inv()
        if (very) veryLargeOff = next else largeOff = next
    }

    fun resetToDefault() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FILE = "large_files"

        const val KEY_LONG_LINE = "long_line"
        const val KEY_DELAY = "highlight_delay"
        const val KEY_MARGIN = "highlight_margin"
        const val KEY_MODE = "large_file_mode"
        const val KEY_LARGE = "large_above"
        const val KEY_VERY_LARGE = "very_large_above"
        const val KEY_LARGE_OFF = "large_off"
        const val KEY_VERY_LARGE_OFF = "very_large_off"
        const val KEY_UNDO = "large_undo_steps"

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
        val LARGE_PRESETS = listOf(100_000, 200_000, 300_000, 500_000, 1_000_000)
        val VERY_LARGE_PRESETS = listOf(1_000_000, 2_000_000, 3_000_000, 5_000_000, 8_000_000)
        val UNDO_PRESETS = listOf(20, 50, 100, 200, 500)
    }
}
