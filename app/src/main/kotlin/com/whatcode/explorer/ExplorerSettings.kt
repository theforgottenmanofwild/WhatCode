package com.whatcode.explorer

import android.content.Context
import com.whatcode.explorer.logic.ExplorerBookmarks
import com.whatcode.explorer.logic.RowMenu
import com.whatcode.files.FileOps

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

    /** What pressing and holding a row does. */
    enum class LongPress(val label: String) { SELECT("Start selecting"), MENU("Open the row menu") }

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

    var longPress: LongPress
        get() = read(KEY_LONG_PRESS, LongPress.values(), LongPress.SELECT)
        set(v) = prefs.edit().putString(KEY_LONG_PRESS, v.name).apply()

    /** Ask before deleting. */
    var confirmDelete: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_DELETE, true)
        set(v) = prefs.edit().putBoolean(KEY_CONFIRM_DELETE, v).apply()

    /** What a paste does when a name is already taken in the folder: ask, or one of [FileOps.Clash] every time. */
    var clash: Clash
        get() = read(KEY_CLASH, Clash.values(), Clash.ASK)
        set(v) = prefs.edit().putString(KEY_CLASH, v.name).apply()

    /** Which actions the row menu shows and in what order, as [RowMenu] reads it. */
    var rowMenu: List<RowMenu.Item>
        get() = RowMenu.parse(prefs.getString(KEY_ROW_MENU, null))
        set(v) = prefs.edit().putString(KEY_ROW_MENU, RowMenu.write(v)).apply()

    var textTap: TextTap
        get() = read(KEY_TEXT_TAP, TextTap.values(), TextTap.EDITOR)
        set(v) = prefs.edit().putString(KEY_TEXT_TAP, v.name).apply()

    var otherTap: OtherTap
        get() = read(KEY_OTHER_TAP, OtherTap.values(), OtherTap.MESSAGE)
        set(v) = prefs.edit().putString(KEY_OTHER_TAP, v.name).apply()

    /** Move deleted items to Trash instead of deleting permanently. */
    var useTrash: Boolean
        get() = prefs.getBoolean(KEY_USE_TRASH, true)
        set(v) = prefs.edit().putBoolean(KEY_USE_TRASH, v).apply()

    var showBookmarks: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BOOKMARKS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_BOOKMARKS, v).apply()

    var showRecentFiles: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECENT, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_RECENT, v).apply()

    var showStorageRoots: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ROOTS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_ROOTS, v).apply()

    var bookmarks: List<ExplorerBookmarks.Bookmark>
        get() = ExplorerBookmarks.parseBookmarks(prefs.getString(KEY_BOOKMARKS, null))
        set(v) = prefs.edit().putString(KEY_BOOKMARKS, ExplorerBookmarks.writeBookmarks(v)).apply()

    var recentFiles: List<ExplorerBookmarks.RecentFile>
        get() = ExplorerBookmarks.parseRecent(prefs.getString(KEY_RECENT, null))
        set(v) = prefs.edit().putString(KEY_RECENT, ExplorerBookmarks.writeRecent(v)).apply()

    /** Puts the Start and navigation page's choices back to their defaults. */
    fun resetNavigation() = remove(
        listOf(KEY_START, KEY_START_FOLDER, KEY_PATH_BOX, KEY_PULL, KEY_BACK_UP, KEY_SHOW_BOOKMARKS, KEY_SHOW_RECENT, KEY_SHOW_ROOTS),
    )

    /** Puts the Taps page's choices back to their defaults. */
    fun resetTaps() = remove(listOf(KEY_MENU_BUTTON, KEY_LONG_PRESS, KEY_TEXT_TAP, KEY_OTHER_TAP))

    /** Puts the Safety page's choices back to their defaults. */
    fun resetSafety() = remove(listOf(KEY_CONFIRM_DELETE, KEY_CLASH, KEY_USE_TRASH))

    /** Puts the Row menu page's choice back to its default: every action, in the usual order. */
    fun resetRowMenu() = remove(listOf(KEY_ROW_MENU))

    private fun remove(keys: List<String>) {
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
    }

    private fun <T : Enum<T>> read(key: String, values: Array<T>, fallback: T): T {
        val saved = prefs.getString(key, null)
        return values.firstOrNull { it.name == saved } ?: fallback
    }

    /** What a paste does when a name is taken: ask each time, or always do the same. */
    enum class Clash(val label: String, val ops: FileOps.Clash?) {
        ASK("Ask each time", null),
        KEEP_BOTH("Keep both, adding a number to the new one", FileOps.Clash.KEEP_BOTH),
        REPLACE("Replace the one that is there", FileOps.Clash.REPLACE),
        SKIP("Skip it", FileOps.Clash.SKIP),
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
        const val KEY_LONG_PRESS = "long_press"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_CLASH = "paste_clash"
        const val KEY_ROW_MENU = "row_menu"
        const val KEY_USE_TRASH = "use_trash"
        const val KEY_SHOW_BOOKMARKS = "show_bookmarks"
        const val KEY_SHOW_RECENT = "show_recent_files"
        const val KEY_SHOW_ROOTS = "show_storage_roots"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_RECENT = "recent_files"

        /** The longest folder path kept for [startFolder]. */
        const val MAX_PATH = 1024

        /** The on/off settings of this file, for the settings backup. */
        val FLAGS: Set<String> = setOf(
            KEY_PATH_BOX, KEY_PULL, KEY_BACK_UP, KEY_MENU_BUTTON, KEY_CONFIRM_DELETE,
            KEY_USE_TRASH, KEY_SHOW_BOOKMARKS, KEY_SHOW_RECENT, KEY_SHOW_ROOTS,
        )

        /** The settings of this file that take one of a list of words, with the words, for the settings backup. */
        val CHOICES: Map<String, Set<String>> = mapOf(
            KEY_START to Start.values().map { it.name }.toSet(),
            KEY_TEXT_TAP to TextTap.values().map { it.name }.toSet(),
            KEY_OTHER_TAP to OtherTap.values().map { it.name }.toSet(),
            KEY_LONG_PRESS to LongPress.values().map { it.name }.toSet(),
            KEY_CLASH to Clash.values().map { it.name }.toSet(),
        )
    }
}
