package com.whatcode.explorer

import android.content.Context
import com.whatcode.explorer.logic.ExplorerFormat
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.explorer.logic.FolderSortRules

/**
 * How the file explorer shows its list: the layout and what each row shows, whether hidden files show, and the
 * order. The layout, hidden files and order are chosen in the explorer's menu (the sidebar) and on the pages of
 * Settings > File explorer; both change the same saved values, and every value is read from them when it is asked
 * for, so a change made on either shows the next time the list is drawn. [onChange] is called after a change made
 * here, so the explorer can lay its list out again without reading the folder.
 */
class ExplorerOptions(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** How tall the rows of the Detailed and Compact layouts are. */
    enum class Density(val label: String) { ROOMY("Roomy"), NORMAL("Normal"), DENSE("Dense") }

    /** How big the tiles of the Grid layout are. */
    enum class TileSize(val label: String, val dp: Int) { SMALL("Small", 76), MEDIUM("Medium", 92), LARGE("Large", 120) }

    /** Whether times are written with 12 or 24 hours, or as the phone is set. */
    enum class Clock(val label: String) { SYSTEM("As the phone is set"), H12("12-hour"), H24("24-hour") }

    val mode: ExplorerSort.Mode get() = read(KEY_MODE, ExplorerSort.Mode.values(), ExplorerSort.Mode.DETAILED)
    val sort: ExplorerSort.Key get() = read(KEY_SORT, ExplorerSort.Key.values(), ExplorerSort.Key.NAME)

    /** True when the list is turned round: Z to A, newest and biggest first. */
    val descending: Boolean get() = prefs.getBoolean(KEY_DESC, false)

    /** True when files and folders whose names start with a dot are listed. They are, until this is turned off. */
    val showHidden: Boolean get() = prefs.getBoolean(KEY_HIDDEN, true)

    var density: Density
        get() = read(KEY_DENSITY, Density.values(), Density.NORMAL)
        set(v) = put(KEY_DENSITY, v.name)
    var tileSize: TileSize
        get() = read(KEY_TILE, TileSize.values(), TileSize.MEDIUM)
        set(v) = put(KEY_TILE, v.name)
    var showSize: Boolean
        get() = prefs.getBoolean(KEY_SIZE, true)
        set(v) = put(KEY_SIZE, v)
    var showDate: Boolean
        get() = prefs.getBoolean(KEY_DATE, true)
        set(v) = put(KEY_DATE, v)
    var dateStyle: ExplorerFormat.DateStyle
        get() = read(KEY_DATE_STYLE, ExplorerFormat.DateStyle.values(), ExplorerFormat.DateStyle.ABSOLUTE)
        set(v) = put(KEY_DATE_STYLE, v.name)
    var clock: Clock
        get() = read(KEY_CLOCK, Clock.values(), Clock.SYSTEM)
        set(v) = put(KEY_CLOCK, v.name)

    /** Sizes in thousands of bytes (kB) instead of 1024s (KB). */
    var decimalSizes: Boolean
        get() = prefs.getBoolean(KEY_DECIMAL, false)
        set(v) = put(KEY_DECIMAL, v)
    var showExtensions: Boolean
        get() = prefs.getBoolean(KEY_EXTENSIONS, true)
        set(v) = put(KEY_EXTENSIONS, v)

    /** Show how many things each folder holds. Each folder is read for it, so it is off until turned on. */
    var folderCounts: Boolean
        get() = prefs.getBoolean(KEY_COUNTS, false)
        set(v) = put(KEY_COUNTS, v)
    var fileIcons: Boolean
        get() = prefs.getBoolean(KEY_ICONS, true)
        set(v) = put(KEY_ICONS, v)

    var foldersFirst: Boolean
        get() = prefs.getBoolean(KEY_FOLDERS_FIRST, true)
        set(v) = put(KEY_FOLDERS_FIRST, v)
    var naturalOrder: Boolean
        get() = prefs.getBoolean(KEY_NATURAL, true)
        set(v) = put(KEY_NATURAL, v)
    var ignoreCase: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_CASE, true)
        set(v) = put(KEY_IGNORE_CASE, v)

    /** Remembers sort key, direction and hidden-files per folder path. */
    var perFolderSort: Boolean
        get() = prefs.getBoolean(KEY_PER_FOLDER_SORT, false)
        set(v) = put(KEY_PER_FOLDER_SORT, v)

    /** How names are compared and where folders go, from the switches above. */
    val order: ExplorerSort.Order get() = ExplorerSort.Order(foldersFirst, naturalOrder, ignoreCase)

    /** Applies the remembered sort/hidden settings for [folderPath] if [perFolderSort] is enabled. */
    fun applyFolderSort(folderPath: String) {
        if (!perFolderSort) return
        val map = FolderSortRules.parse(prefs.getString(KEY_FOLDER_SORTS, null))
        val spec = FolderSortRules.get(map, folderPath) ?: return
        prefs.edit()
            .putString(KEY_SORT, spec.key.name)
            .putBoolean(KEY_DESC, spec.descending)
            .putBoolean(KEY_HIDDEN, spec.showHidden)
            .apply()
    }

    /** Records the current sort/hidden choice for [folderPath] when [perFolderSort] is enabled. */
    fun recordFolderSort(folderPath: String) {
        if (!perFolderSort) return
        val map = FolderSortRules.parse(prefs.getString(KEY_FOLDER_SORTS, null))
        val updated = FolderSortRules.put(map, folderPath, FolderSortRules.Spec(sort, descending, showHidden))
        prefs.edit().putString(KEY_FOLDER_SORTS, FolderSortRules.write(updated)).apply()
    }

    var onChange: (() -> Unit)? = null

    fun setMode(value: ExplorerSort.Mode) {
        if (value != mode) put(KEY_MODE, value.name)
    }

    fun setSort(value: ExplorerSort.Key) {
        if (value != sort) put(KEY_SORT, value.name)
    }

    fun setDescending(value: Boolean) {
        if (value != descending) put(KEY_DESC, value)
    }

    fun setShowHidden(value: Boolean) {
        if (value != showHidden) put(KEY_HIDDEN, value)
    }

    fun toggleDescending() = setDescending(!descending)

    fun toggleHidden() = setShowHidden(!showHidden)

    /** Puts the View page's choices (the layout and what the rows show) back to their defaults. */
    fun resetView() = remove(VIEW_KEYS)

    /** Puts the Sorting page's choices (the order, hidden files and how names compare) back to their defaults. */
    fun resetSort() = remove(SORT_KEYS)

    private fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        onChange?.invoke()
    }

    private fun put(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        onChange?.invoke()
    }

    private fun remove(keys: List<String>) {
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
        onChange?.invoke()
    }

    private fun <T : Enum<T>> read(key: String, values: Array<T>, fallback: T): T {
        val saved = prefs.getString(key, null)
        return values.firstOrNull { it.name == saved } ?: fallback
    }

    companion object {
        /** The saved file these choices live in. */
        const val FILE = "explorer_view"

        const val KEY_MODE = "mode"
        const val KEY_SORT = "sort"
        const val KEY_DESC = "descending"
        const val KEY_HIDDEN = "show_hidden"
        const val KEY_DENSITY = "density"
        const val KEY_TILE = "tile_size"
        const val KEY_SIZE = "show_size"
        const val KEY_DATE = "show_date"
        const val KEY_DATE_STYLE = "date_style"
        const val KEY_CLOCK = "clock"
        const val KEY_DECIMAL = "decimal_sizes"
        const val KEY_EXTENSIONS = "show_extensions"
        const val KEY_COUNTS = "folder_counts"
        const val KEY_ICONS = "file_icons"
        const val KEY_FOLDERS_FIRST = "folders_first"
        const val KEY_NATURAL = "natural_order"
        const val KEY_IGNORE_CASE = "ignore_case"
        const val KEY_PER_FOLDER_SORT = "per_folder_sort"
        const val KEY_FOLDER_SORTS = "folder_sorts"

        val VIEW_KEYS = listOf(
            KEY_MODE, KEY_DENSITY, KEY_TILE, KEY_SIZE, KEY_DATE, KEY_DATE_STYLE, KEY_CLOCK, KEY_DECIMAL, KEY_EXTENSIONS,
            KEY_COUNTS, KEY_ICONS,
        )
        val SORT_KEYS = listOf(KEY_SORT, KEY_DESC, KEY_HIDDEN, KEY_FOLDERS_FIRST, KEY_NATURAL, KEY_IGNORE_CASE, KEY_PER_FOLDER_SORT)

        /** The on/off settings of this file, for the settings backup. */
        val FLAGS: Set<String> = setOf(
            KEY_DESC, KEY_HIDDEN, KEY_SIZE, KEY_DATE, KEY_DECIMAL, KEY_EXTENSIONS, KEY_COUNTS, KEY_ICONS,
            KEY_FOLDERS_FIRST, KEY_NATURAL, KEY_IGNORE_CASE, KEY_PER_FOLDER_SORT,
        )

        /** The settings of this file that take one of a list of words, with the words, for the settings backup. */
        val CHOICES: Map<String, Set<String>> = mapOf(
            KEY_MODE to ExplorerSort.Mode.values().map { it.name }.toSet(),
            KEY_SORT to ExplorerSort.Key.values().map { it.name }.toSet(),
            KEY_DENSITY to Density.values().map { it.name }.toSet(),
            KEY_TILE to TileSize.values().map { it.name }.toSet(),
            KEY_DATE_STYLE to ExplorerFormat.DateStyle.values().map { it.name }.toSet(),
            KEY_CLOCK to Clock.values().map { it.name }.toSet(),
        )
    }
}
