package com.whatcodeeditor.settings

import android.content.Context
import android.graphics.Typeface
import com.whatcodeeditor.editor.SyntaxRole
import com.whatcodeeditor.ui.AccentColor

/** The typefaces the editor text can use. All of them come with Android, so nothing is downloaded or bundled. */
enum class EditorFont(val key: String, val label: String, val note: String, private val family: String) {
    MONOSPACE("monospace", "Monospace", "Every character has the same width", "monospace"),
    SERIF_MONOSPACE("serif-monospace", "Serif monospace", "Same width, with small feet on the letters", "serif-monospace"),
    SANS("sans", "Sans-serif", "Easier on the eye, but columns do not line up", "sans-serif");

    fun typeface(): Typeface = Typeface.create(family, Typeface.NORMAL)

    companion object {
        fun from(key: String?): EditorFont = values().firstOrNull { it.key == key } ?: MONOSPACE
    }
}

/** How much room there is round the text in the editor, in dp. */
enum class EditorSpacing(val key: String, val label: String, val note: String, val horizontalDp: Int, val verticalDp: Int) {
    COMPACT("compact", "Compact", "Small margins, more code on screen", 8, 4),
    NORMAL("normal", "Normal", "The default", 14, 8),
    COMFORTABLE("comfortable", "Comfortable", "Wide margins, easier to read", 22, 14);

    companion object {
        fun from(key: String?): EditorSpacing = values().firstOrNull { it.key == key } ?: NORMAL
    }
}

/**
 * How the app and the editor look, saved on the device. Changed on Settings > Appearance ([AppearanceActivity]) and
 * Settings > Appearance > Syntax colors ([SyntaxColorsActivity]), and read by every screen through
 * [com.whatcodeeditor.ui.AppTheme] and by the editor ([com.whatcodeeditor.editor.applyAppearance]).
 *
 * It uses the same saved file as the Light / Dark / System choice ([com.whatcodeeditor.ui.AppTheme.mode]), which is
 * why [signature] changes for that choice too. Every value is checked when it is read, so a missing or odd value
 * falls back to the default instead of breaking a screen.
 */
class AppearanceSettings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The colour that buttons, links, switches and the status bar use. */
    var accent: AccentColor
        get() = AccentColor.from(prefs.getString("accent", null))
        set(v) = prefs.edit().putString("accent", v.key).apply()

    /** The dark theme uses black instead of dark grey for its backgrounds. */
    var pureBlack: Boolean
        get() = prefs.getBoolean("pure_black", DEFAULT_PURE_BLACK)
        set(v) = prefs.edit().putBoolean("pure_black", v).apply()

    var font: EditorFont
        get() = EditorFont.from(prefs.getString("font", null))
        set(v) = prefs.edit().putString("font", v.key).apply()

    /** Size of the editor text in sp. */
    var fontSize: Int
        get() = clampFontSize(prefs.getInt("font_size", DEFAULT_FONT_SIZE))
        set(v) = prefs.edit().putInt("font_size", clampFontSize(v)).apply()

    /** Line height as a percentage of the normal one: 100 is the normal height. */
    var lineSpacing: Int
        get() = clampLineSpacing(prefs.getInt("line_spacing", DEFAULT_LINE_SPACING))
        set(v) = prefs.edit().putInt("line_spacing", clampLineSpacing(v)).apply()

    var spacing: EditorSpacing
        get() = EditorSpacing.from(prefs.getString("spacing", null))
        set(v) = prefs.edit().putString("spacing", v.key).apply()

    /** The line with the cursor gets a soft background. */
    var highlightLine: Boolean
        get() = prefs.getBoolean("highlight_line", DEFAULT_HIGHLIGHT_LINE)
        set(v) = prefs.edit().putBoolean("highlight_line", v).apply()

    // ------------------------------------------------------------------ syntax colours

    private fun syntaxKey(role: SyntaxRole, light: Boolean) = "syntax_${if (light) "light" else "dark"}_${role.key}"

    /** The colour the person chose for [role] in the light or dark theme, or null to use the default. */
    fun syntaxColor(role: SyntaxRole, light: Boolean): Int? {
        val key = syntaxKey(role, light)
        return if (prefs.contains(key)) opaque(prefs.getInt(key, 0)) else null
    }

    /** Saves [color] for [role], or goes back to the default when it is null. */
    fun setSyntaxColor(role: SyntaxRole, light: Boolean, color: Int?) {
        val edit = prefs.edit()
        if (color == null) edit.remove(syntaxKey(role, light)) else edit.putInt(syntaxKey(role, light), opaque(color))
        edit.apply()
    }

    /** The colours chosen for the light or dark theme; roles left at their default are not in it. */
    fun syntaxOverrides(light: Boolean): Map<SyntaxRole, Int> {
        val out = LinkedHashMap<SyntaxRole, Int>()
        for (role in SyntaxRole.values()) syntaxColor(role, light)?.let { out[role] = it }
        return out
    }

    // ------------------------------------------------------------------ resetting

    /** Puts the font, size, spacing and current-line choices back to their defaults. */
    fun resetEditorText() {
        prefs.edit().remove("font").remove("font_size").remove("line_spacing").remove("spacing")
            .remove("highlight_line").apply()
    }

    /** Puts every syntax colour back to its default, in both themes. */
    fun resetSyntaxColors() {
        val edit = prefs.edit()
        for (role in SyntaxRole.values()) edit.remove(syntaxKey(role, true)).remove(syntaxKey(role, false))
        edit.apply()
    }

    /** Puts everything on the Appearance page back to its default, including Light / Dark / System. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        /** The saved file; Light / Dark / System lives in it too (see AppTheme). */
        const val FILE = "appearance"

        const val DEFAULT_PURE_BLACK = false
        const val DEFAULT_FONT_SIZE = 15
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 28
        const val DEFAULT_LINE_SPACING = 100
        const val MIN_LINE_SPACING = 100
        const val MAX_LINE_SPACING = 200
        const val LINE_SPACING_STEP = 10
        const val DEFAULT_HIGHLIGHT_LINE = false

        fun clampFontSize(v: Int): Int = v.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

        /** Keeps [v] inside the range and on one of the steps, so an odd saved number still gives a sensible height. */
        fun clampLineSpacing(v: Int): Int {
            val steps = Math.round((v.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING) - MIN_LINE_SPACING) / LINE_SPACING_STEP.toFloat())
            return MIN_LINE_SPACING + steps * LINE_SPACING_STEP
        }

        private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

        /**
         * A string that differs whenever any appearance choice differs. A screen keeps the one from when it was built
         * and builds itself again when it no longer matches.
         */
        fun signature(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).all.toSortedMap().toString()
    }
}
