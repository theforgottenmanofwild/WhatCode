package com.whatcode.settings.explorer

import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.whatcode.explorer.ExplorerSettings
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.explorer.logic.ExplorerTrash
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage
import java.io.File

/** Settings > File explorer > Safety: Trash, deleting prompts, and paste clash choices. */
class ExplorerSafetyActivity : SettingsPage("Safety") {

    private val settings by lazy { ExplorerSettings(this) }

    private fun trashRoot(): File = File(filesDir, "trash")

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("DELETING AND TRASH"))
        switchRow(
            "Move deleted items to Trash", settings.useTrash,
            note = "Deleted files can be restored from Trash or brought back with Undo. Off deletes permanently.",
        ) { settings.useTrash = it }
        switchRow(
            "Ask before deleting", settings.confirmDelete,
            note = "Shows a confirmation before deleting or moving to Trash.",
        ) { settings.confirmDelete = it }
        val trashed = ExplorerTrash.list(trashRoot())
        val totalBytes = trashed.sumOf { it.size }
        val trashNote = if (trashed.isEmpty()) "Trash is empty." else "${trashed.size} items (${ExplorerSort.sizeLabel(totalBytes, decimal = false)})"
        content.addView(
            wide("Empty Trash   ·   $trashNote") {
                if (trashed.isEmpty()) return@wide
                AppTheme.dialogBuilder(this)
                    .setTitle("Empty Trash?")
                    .setMessage("Permanently remove ${trashed.size} items from Trash? This can't be undone.")
                    .setPositiveButton("Empty") { _, _ ->
                        val removed = ExplorerTrash.empty(trashRoot())
                        Toast.makeText(this, "Removed $removed items", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show().also { AppTheme.styleDialog(it, this) }
            }.apply { isEnabled = trashed.isNotEmpty(); alpha = if (trashed.isNotEmpty()) 1f else 0.45f },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(4), 0, dp(4)) },
        )

        content.addView(sectionTitle("WHEN A NAME IS TAKEN"))
        choices(ExplorerSettings.Clash.values().toList(), settings.clash, { it.label }) { settings.clash = it }
        content.addView(muted("Used when a copy or a move is pasted into a folder that has the same name."))

        defaultsSection(
            question = "Turn Trash on, ask before deleting, and ask what to do when a name is taken?",
            done = "Safety returned to default",
        ) { settings.resetSafety() }
    }
}
