package com.whatcode.settings.explorer

import com.whatcode.explorer.ExplorerOptions
import com.whatcode.explorer.logic.ExplorerFormat
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.ui.SettingsPage

/** Settings > File explorer > View: the layout of the list and what each row shows. */
class ExplorerViewActivity : SettingsPage("View") {

    private val options by lazy { ExplorerOptions(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("LAYOUT"))
        choices(ExplorerSort.Mode.values().toList(), options.mode, ::modeLabel) { options.setMode(it) }
        content.addView(muted("The same choice as View in the explorer's menu."))

        content.addView(sectionTitle("ROW HEIGHT"))
        choices(ExplorerOptions.Density.values().toList(), options.density, { it.label }) { options.density = it }
        content.addView(muted("For the Detailed and Compact layouts."))

        content.addView(sectionTitle("TILE SIZE"))
        choices(ExplorerOptions.TileSize.values().toList(), options.tileSize, { it.label }) { options.tileSize = it }
        content.addView(muted("For the Grid layout."))

        content.addView(sectionTitle("UNDER EACH NAME"))
        switchRow("Show the size", options.showSize) { options.showSize = it }
        switchRow("Show the date", options.showDate) {
            options.showDate = it
            render()
        }
        switchRow(
            "Show how many items a folder holds", options.folderCounts,
            note = "Each folder in the list is read for it, which can be slow in a folder with many folders.",
        ) { options.folderCounts = it }
        content.addView(muted("The Compact and Grid layouts show the name alone."))

        content.addView(sectionTitle("NAMES AND ICONS"))
        switchRow("Show file extensions", options.showExtensions, note = "Off shows notes.txt as notes.") { options.showExtensions = it }
        switchRow("Show file icons", options.fileIcons, note = "Off shows a plain icon for every file.") { options.fileIcons = it }

        val dates = options.showDate
        content.addView(sectionTitle("DATES"))
        choices(ExplorerFormat.DateStyle.values().toList(), options.dateStyle, ::dateLabel, enabled = dates) { options.dateStyle = it }
        content.addView(sectionTitle("TIME"))
        choices(ExplorerOptions.Clock.values().toList(), options.clock, { it.label }, enabled = dates) { options.clock = it }

        content.addView(sectionTitle("SIZES"))
        choices(listOf(false, true), options.decimalSizes, { if (it) "1 kB is 1000 bytes" else "1 KB is 1024 bytes" }) {
            options.decimalSizes = it
        }
        content.addView(muted("Phone settings count in 1000s, many file managers in 1024s."))

        defaultsSection(
            question = "Put the layout, row height, tile size and what the rows show back to their defaults?",
            done = "View returned to default",
        ) { options.resetView() }
    }

    private fun modeLabel(mode: ExplorerSort.Mode): String = when (mode) {
        ExplorerSort.Mode.DETAILED -> "Detailed"
        ExplorerSort.Mode.COMPACT -> "Compact"
        ExplorerSort.Mode.GRID -> "Grid"
    }

    private fun dateLabel(style: ExplorerFormat.DateStyle): String = when (style) {
        ExplorerFormat.DateStyle.RELATIVE -> "How long ago (5 min ago, Yesterday)"
        ExplorerFormat.DateStyle.ABSOLUTE -> "Date and time (12 Mar 2026, 14:05)"
    }
}
