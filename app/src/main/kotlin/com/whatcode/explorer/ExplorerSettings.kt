package com.whatcode.explorer

import android.content.Context

/**
 * How the file explorer behaves: where it starts, how it moves between folders, and what a tap does. Changed on the
 * pages of Settings > File explorer and read by the explorer each time it needs one, so a change shows at once.
 * How the list looks and its order are in [ExplorerOptions].
 */
class ExplorerSettings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The folder the explorer opens in when the app starts. */
    enum class Start(val label: String) { PHONE("Phone storage"), LAST("The last folder"), FOLDER("A folder I choose") }

    /** What tapping a text file does. */
    enum class TextTap(val label: String) { EDITOR("Open it in the code editor"), ASK("Ask what to do") }

    /** What tapping a file that isn't text does. */
    enum class OtherTap(val label: String) { MESSAGE("Say that it isn't text"), OPEN_WITH("Open it with another app") }

    var start: Start
        get() = read(KEY_START, Start.values(), Start.PHONE)
        set(v) = prefs.edit().putString(KEY_START, v.name).apply()

    /** The folder typed for [Start.FOLDER]; empty until one is chosen. */
    var startFolder: String
        get() = prefs.getString(KEY_START_FOLDER, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_START_FOLDER, v).apply()

    /** The folder the explorer showed last. Not a setting: it is where [Start.LAST] goes. */
    var lastFolder: String
        get() = prefs.getString(KEY_LAST_FOLDER, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_LAST_FOLDER, v).apply()

    /** Tapping the folder's own name in the path turns the path into a box to type a folder in. */
    var pathBox: Boolean
        get() = prefs.getBoolean(KEY_PATH_BOX, true)
        set(v) = prefs.edit().putBoolean(KEY_PATH_BOX, v).apply()

    /** Pulling the top of the list down reads the folder again. */
    var pullToRefresh: Boolean
        get() = prefs.getBoolean(KEY_PULL, true)
        set(v) = prefs.edit().putBoolean(KEY_PULL, v).apply()

    /** Back in the explorer goes up a folder, and leaves the app only from the top. */
    var backGoesUp: Boolean
        get() = prefs.getBoolean(KEY_BACK_UP, true)
        set(v) = prefs.edit().putBoolean(KEY_BACK_UP, v).apply()

    /** The **⋮** button at the end of each row, for its menu. A long press opens the same menu either way. */
    var menuButton: Boolean
        get() = prefs.getBoolean(KEY_MENU_BUTTON, true)
        set(v) = prefs.edit().putBoolean(KEY_MENU_BUTTON, v).apply()

    var textTap: TextTap
        get() = read(KEY_TEXT_TAP, TextTap.values(), TextTap.EDITOR)
        set(v) = prefs.edit().putString(KEY_TEXT_TAP, v.name).apply()

    var otherTap: OtherTap
        get() = read(KEY_OTHER_TAP, OtherTap.values(), OtherTap.MESSAGE)
        set(v) = prefs.edit().putString(KEY_OTHER_TAP, v.name).apply()

    /** Puts the Start and navigation page's choices back to their defaults. */
    fun resetNavigation() = remove(listOf(KEY_START, KEY_START_FOLDER, KEY_PATH_BOX, KEY_PULL, KEY_BACK_UP))

    /** Puts the Taps page's choices back to their defaults. */
    fun resetTaps() = remove(listOf(KEY_MENU_BUTTON, KEY_TEXT_TAP, KEY_OTHER_TAP))

    private fun remove(keys: List<String>) {
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun <T : Enum<T>> read(key: String, values: Array<T>, fallback: T): T {
        val saved = prefs.getString(key, null)
        return values.firstOrNull { it.name == saved } ?: fallback
    }

    companion object {
        const val FILE = "explorer"

        const val KEY_START = "start"
        const val KEY_START_FOLDER = "start_folder"
        const val KEY_LAST_FOLDER = "last_folder"
        const val KEY_PATH_BOX = "path_box"
        const val KEY_PULL = "pull_to_refresh"
        const val KEY_BACK_UP = "back_goes_up"
        const val KEY_MENU_BUTTON = "menu_button"
        const val KEY_TEXT_TAP = "text_tap"
        const val KEY_OTHER_TAP = "other_tap"

        /** The longest folder path kept for [startFolder]. */
        const val MAX_PATH = 1024

        /** The on/off settings of this file, for the settings backup. */
        val FLAGS: Set<String> = setOf(KEY_PATH_BOX, KEY_PULL, KEY_BACK_UP, KEY_MENU_BUTTON)

        /** The settings of this file that take one of a list of words, with the words, for the settings backup. */
        val CHOICES: Map<String, Set<String>> = mapOf(
            KEY_START to Start.values().map { it.name }.toSet(),
            KEY_TEXT_TAP to TextTap.values().map { it.name }.toSet(),
            KEY_OTHER_TAP to OtherTap.values().map { it.name }.toSet(),
        )
    }
}
