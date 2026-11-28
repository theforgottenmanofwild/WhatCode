package com.whatcode.explorer

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import com.whatcode.explorer.logic.ExplorerFormat
import com.whatcode.explorer.logic.ExplorerSort
import com.whatcode.explorer.logic.FileFacts
import com.whatcode.files.FileOps
import com.whatcode.ui.AppTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Properties of a file or folder: its path, type, size, dates and permissions, and for a link where it points. A
 * folder's size is added up in the background while the dialog shows, and that stops when the dialog closes.
 */
internal object PropertiesDialog {

    fun show(activity: Activity, file: File, decimalSizes: Boolean, copyPath: (String) -> Unit) {
        val ui = Handler(Looper.getMainLooper())
        val stopped = AtomicBoolean(false)
        val dialog = AppTheme.dialogBuilder(activity)
            .setTitle(file.name.ifEmpty { file.path })
            .setMessage("Reading\u2026")
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy path") { _, _ -> copyPath(file.absolutePath) }
            .create()
        dialog.setOnDismissListener { stopped.set(true) }
        dialog.show()
        AppTheme.styleDialog(dialog, activity)

        val hour24 = DateFormat.is24HourFormat(activity)
        Thread {
            val facts = FileFacts.read(file)
            var size: FileOps.Size? = null
            fun post() {
                val text = describe(facts, size, measuring = facts.isDir && !stopped.get(), decimalSizes, hour24)
                ui.post { if (dialog.isShowing) dialog.setMessage(text) }
            }
            post()
            if (facts.isDir) {
                size = FileOps.measure(file, cancelled = { stopped.get() }) { soFar ->
                    size = soFar
                    post()
                }
                if (size != null) {
                    val done = describe(facts, size, measuring = false, decimalSizes, hour24)
                    ui.post { if (dialog.isShowing) dialog.setMessage(done) }
                }
            }
        }.start()
    }

    private fun describe(facts: FileFacts, size: FileOps.Size?, measuring: Boolean, decimal: Boolean, hour24: Boolean): String {
        val lines = ArrayList<Pair<String, String>>()
        lines.add("Path" to facts.path)
        lines.add(
            "Type" to when {
                facts.isLink -> "Link"
                facts.isDir -> "Folder"
                else -> ExplorerSort.extension(File(facts.path).name).let { if (it.isEmpty()) "File" else ".$it file" }
            }
        )
        if (facts.isLink) lines.add("Points to" to (facts.linkTarget ?: "(can't be read)"))
        when {
            facts.isLink -> Unit
            facts.isDir && size == null -> lines.add("Size" to "Adding up\u2026")
            facts.isDir -> {
                val more = if (measuring) " so far\u2026" else ""
                lines.add("Size" to ExplorerSort.sizeLabel(size!!.bytes, decimal) + "  (${"%,d".format(size.bytes)} bytes)" + more)
                lines.add("Contains" to "${count(size.files, "file")}, ${count(size.folders, "folder")}$more")
            }
            else -> lines.add("Size" to ExplorerSort.sizeLabel(facts.size, decimal) + "  (${"%,d".format(facts.size)} bytes)")
        }
        val now = System.currentTimeMillis()
        fun date(at: Long) = ExplorerFormat.date(at, now, ExplorerFormat.DateStyle.ABSOLUTE, hour24)
        lines.add("Modified" to date(facts.modified))
        facts.created?.let { lines.add("Created" to date(it)) }
        facts.accessed?.let { if (it != facts.modified) lines.add("Opened" to date(it)) }
        facts.permissions?.let { lines.add("Permissions" to it) }
        lines.add("This app can" to listOfNotNull("read".takeIf { facts.canRead }, "write".takeIf { facts.canWrite }).joinToString(" and ").ifEmpty { "neither read nor write" })
        return lines.joinToString("\n\n") { (k, v) -> "$k\n$v" }
    }

    private fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
