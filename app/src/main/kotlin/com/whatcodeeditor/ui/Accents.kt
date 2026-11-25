package com.whatcodeeditor.ui

/**
 * The accent colors to choose from in Settings > Appearance. Each has a shade for the light theme and one for the
 * dark theme, and the darker fill that the status bar uses with white text on it. Every shade is checked against
 * the theme surfaces in ThemeContrastTest, so a new accent cannot be added without being readable.
 */
enum class AccentColor(
    val key: String,
    val label: String,
    val light: Int,
    val dark: Int,
    val statusLight: Int,
    val statusDark: Int,
) {
    BLUE("blue", "Blue", 0xFF176EB5.toInt(), 0xFF65B2F7.toInt(), 0xFF236DA8.toInt(), 0xFF286EA9.toInt()),
    TEAL("teal", "Teal", 0xFF0A7069.toInt(), 0xFF4DD0C4.toInt(), 0xFF0A6B65.toInt(), 0xFF1F7A72.toInt()),
    GREEN("green", "Green", 0xFF2B752F.toInt(), 0xFF7BCB7F.toInt(), 0xFF2B752F.toInt(), 0xFF2E7A33.toInt()),
    ORANGE("orange", "Orange", 0xFFA84D07.toInt(), 0xFFF5A25D.toInt(), 0xFFA84D07.toInt(), 0xFFA45410.toInt()),
    PURPLE("purple", "Purple", 0xFF6D4AC4.toInt(), 0xFFB69CFF.toInt(), 0xFF6D4AC4.toInt(), 0xFF6B52B8.toInt()),
    PINK("pink", "Pink", 0xFFC2185B.toInt(), 0xFFF48FB1.toInt(), 0xFFB5154F.toInt(), 0xFFA8325C.toInt());

    companion object {
        /** The accent saved under [key]; Blue, the original color of the app, for anything unknown. */
        fun from(key: String?): AccentColor = values().firstOrNull { it.key == key } ?: BLUE
    }
}
