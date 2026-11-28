package com.whatcode.settings.explorer

import com.whatcode.explorer.ExplorerOptions
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > Sorting: the order of the list, how names are compared, and hidden files. */
class ExplorerSortActivity : SettingsPage("Sorting") {

    private val options by lazy { ExplorerOptions(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("SORT BY"))
        choices(ExplorerSort.Key.values().toList(), options.sort, ::keyLabel) { options.setSort(it) }

        content.addView(sectionTitle("DIRECTION"))
        choices(listOf(false, true), options.descending, {
            if (it) "Descending (Z to A, newest and biggest first)" else "Ascending (A to Z, oldest and smallest first)"
        }) { options.setDescending(it) }
        content.addView(muted("The same choices as Sort in the explorer's menu."))

        content.addView(sectionTitle("NAMES"))
        switchRow("Folders first", options.foldersFirst, note = "Off mixes folders in with the files.") { options.foldersFirst = it }
        switchRow("Read numbers as numbers", options.naturalOrder, note = "Puts file2 before file10.") { options.naturalOrder = it }
        switchRow("Ignore capital letters", options.ignoreCase, note = "Off puts names that start with a capital first.") {
            options.ignoreCase = it
        }

        content.addView(sectionTitle("HIDDEN FILES"))
        switchRow("Show hidden files", options.showHidden, note = "Files and folders whose names start with a dot.") {
            options.setShowHidden(it)
        }

        defaultsSection(
            question = "Sort by name from A to Z, folders first, numbers as numbers, capitals ignored and hidden files shown?",
            done = "Sorting returned to default",
        ) { options.resetSort() }
    }

    private fun keyLabel(key: ExplorerSort.Key): String = when (key) {
        ExplorerSort.Key.NAME -> "Name"
        ExplorerSort.Key.SIZE -> "Size"
        ExplorerSort.Key.DATE -> "Date"
        ExplorerSort.Key.TYPE -> "Type (the ending of the name)"
    }
}
