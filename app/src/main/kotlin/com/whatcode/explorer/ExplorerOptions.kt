package com.whatcode.explorer

import android.content.Context
import com.whatcode.explorer.logic.ExplorerSort

/**
 * How the file explorer shows its list: the layout, whether hidden files show, and the order. They are chosen in the
 * explorer's menu (the sidebar) and are kept for the next time the app starts. [onChange] is called after each
 * change, so the explorer can lay its list out again without reading the folder.
 */
class ExplorerOptions(context: Context) {
    private val prefs = context.getSharedPreferences("explorer_view", Context.MODE_PRIVATE)

    var mode: ExplorerSort.Mode = read(KEY_MODE, ExplorerSort.Mode.values(), ExplorerSort.Mode.DETAILED)
        private set

    var sort: ExplorerSort.Key = read(KEY_SORT, ExplorerSort.Key.values(), ExplorerSort.Key.NAME)
        private set

    /** True when the list is turned round: Z to A, newest and biggest first. */
    var descending: Boolean = prefs.getBoolean(KEY_DESC, false)
        private set

    /** True when files and folders whose names start with a dot are listed. They are, until this is turned off. */
    var showHidden: Boolean = prefs.getBoolean(KEY_HIDDEN, true)
        private set

    var onChange: (() -> Unit)? = null

    fun setMode(value: ExplorerSort.Mode) {
        if (value == mode) return
        mode = value
        prefs.edit().putString(KEY_MODE, value.name).apply()
        onChange?.invoke()
    }

    fun setSort(value: ExplorerSort.Key) {
        if (value == sort) return
        sort = value
        prefs.edit().putString(KEY_SORT, value.name).apply()
        onChange?.invoke()
    }

    fun toggleDescending() {
        descending = !descending
        prefs.edit().putBoolean(KEY_DESC, descending).apply()
        onChange?.invoke()
    }

    fun toggleHidden() {
        showHidden = !showHidden
        prefs.edit().putBoolean(KEY_HIDDEN, showHidden).apply()
        onChange?.invoke()
    }

    private fun <T : Enum<T>> read(key: String, values: Array<T>, fallback: T): T {
        val saved = prefs.getString(key, null)
        return values.firstOrNull { it.name == saved } ?: fallback
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_SORT = "sort"
        const val KEY_DESC = "descending"
        const val KEY_HIDDEN = "show_hidden"
    }
}
