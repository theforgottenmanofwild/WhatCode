package com.whatcode.settings

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.whatcode.editor.snippets.Snippet
import com.whatcode.editor.snippets.SnippetFile
import com.whatcode.editor.snippets.UserSnippets
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage

/**
 * The "Snippets" page, opened from Settings > Code Editor: whether snippets can be put in, whether Tab does it after
 * the prefix of one, whether the snippets that come with the editor are on offer, and the person's own snippets, which
 * can be added, changed, deleted, and written to or read from a file. Each choice is saved at once.
 */
class SnippetsActivity : SettingsPage("Snippets") {

    private val settings by lazy { EditorSettings(this) }
    private var mine: List<Snippet> = emptyList()

    override fun onPrepare() {
        mine = UserSnippets.load(this)
    }

    override fun render() {
        content.removeAllViews()

        switchRow(
            "Snippets", settings.snippets,
            note = "A snippet is a short word that stands for a piece of code, such as fn or for. Type the word and press Tab " +
                "to put the code in, then press Tab again to go to the next place to fill in. Shift+Tab goes back, and " +
                "ESC stops. The Snip key (Ctrl+.) lists the snippets of the open language. Each snippet is one undo step.",
        ) { settings.snippets = it }

        val on = settings.snippets
        content.addView(sectionTitle("WHICH SNIPPETS"))
        switchRow(
            "Built-in snippets", settings.snippetsBuiltIn,
            note = "The ones that come with the editor, for JavaScript, TypeScript, Python, Kotlin, Java, Go, Rust, C, C++, C#, " +
                "Swift, Ruby, shell, HTML, CSS, Markdown and SQL. One of yours with the same word takes the place of a built-in one.",
            enabled = on,
        ) { settings.snippetsBuiltIn = it }

        content.addView(sectionTitle("PUTTING ONE IN"))
        switchRow(
            "Expand with Tab", settings.snippetsTab,
            note = "Tab after a snippet's word puts the snippet in. A word that follows a . - $ @ # or \\ is left alone. " +
                "When this is off, only the Snip key puts a snippet in, and Tab always indents.",
            enabled = on,
        ) { settings.snippetsTab = it }

        yourSnippets()

        defaultsSection(
            "Put the Snippets switches back to their defaults? Your own snippets are kept.",
            "Snippets returned to default",
        ) { settings.resetSnippets() }
    }

    // ------------------------------------------------------------------ the person's own snippets

    private fun yourSnippets() {
        content.addView(sectionTitle("YOUR SNIPPETS"))
        content.addView(
            muted(
                if (mine.isEmpty()) "You have no snippets of your own yet. Tap Add a snippet to write one."
                else "${mine.size} of ${SnippetFile.MAX_SNIPPETS}. Tap one to change or delete it."
            )
        )
        for ((i, s) in mine.withIndex()) {
            val row = wide("${s.prefix}   ·   ${s.name.ifEmpty { "No name" }}   ·   ${s.lang ?: "Every language"}") {
                edit(i)
            }.apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            content.addView(row, rowParams())
        }
        val add = wide("Add a snippet") { edit(-1) }
        add.setTextColor(colorAccent)
        content.addView(add, rowParams())

        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(wide("Export to a file") { saveFile() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(8), 0) })
        line.addView(wide("Import a file") { openFile() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, 0)
        })
        content.addView(
            muted(
                "A file of snippets can be shared and read on another phone. Importing adds its snippets to yours, " +
                    "and one with the word and language of one of yours replaces it. Import and export in Settings " +
                    "keeps your snippets too."
            ).apply { setPadding(0, dp(8), 0, dp(4)) }
        )
    }

    private fun rowParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(4), 0, 0) }

    /** Writes a snippet ([at] -1), or changes or deletes the one at [at]. */
    private fun edit(at: Int) {
        if (at < 0 && mine.size >= SnippetFile.MAX_SNIPPETS) {
            toast("You can have up to ${SnippetFile.MAX_SNIPPETS} snippets. Delete one first.")
            return
        }
        val others = if (at < 0) mine else mine.filterIndexed { i, _ -> i != at }
        editSnippet(
            this, mine.getOrNull(at), others,
            onSave = { s ->
                store(if (at < 0) mine + s else mine.toMutableList().also { it[at] = s })
                toast("Saved ${s.prefix}")
            },
            onDelete = if (at < 0) null else ({
                store(others)
                toast("Deleted")
            }),
        )
    }

    private fun store(list: List<Snippet>) {
        mine = list
        UserSnippets.save(this, list)
        cancelReset()
        render()
    }

    // ------------------------------------------------------------------ files

    private fun saveFile() {
        if (mine.isEmpty()) {
            toast("You have no snippets to export")
            return
        }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "whatcode-snippets.json")
        }
        try {
            startActivityForResult(i, REQ_SAVE)
        } catch (_: Exception) {
            toast("No app can save a file here")
        }
    }

    private fun openFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(i, REQ_OPEN)
        } catch (_: Exception) {
            toast("No app can open a file here")
        }
    }

    /** Writes the file on another thread; the answer is shown when it is done. */
    private fun writeFile(uri: Uri) {
        val text = SnippetFile.export(mine)
        val count = mine.size
        Thread {
            val msg = try {
                val out = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("The file could not be opened")
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                "Exported $count snippets"
            } catch (e: Exception) {
                "Could not save the file: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread { if (!isFinishing) toast(msg) }
        }.start()
    }

    /** Reads the file on another thread, then asks before its snippets are added. */
    private fun readFile(uri: Uri) {
        Thread {
            val outcome = load(uri)
            runOnUiThread { if (!isFinishing) offer(outcome) }
        }.start()
    }

    /** The text of the file at [uri], or why it could not be read. */
    private fun load(uri: Uri): Pair<String?, String?> {
        val text = try {
            val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("The file could not be opened")
            input.use { stream ->
                // Stop as soon as the file is longer than a file of snippets can be, without reading the rest of it.
                val bytes = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    if (bytes.size() + n > SnippetFile.MAX_BYTES) return@use null
                    bytes.write(chunk, 0, n)
                }
                String(bytes.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            return null to "Could not open the file: ${e.message ?: e.javaClass.simpleName}"
        }
        return if (text == null) null to "The file is too big to be a file of snippets" else text to null
    }

    private fun offer(outcome: Pair<String?, String?>) {
        val text = outcome.first
        if (text == null) {
            toast(outcome.second ?: "Could not open the file")
            return
        }
        val imported = SnippetFile.import(text)
        val read = imported.read
        if (read == null) {
            AppTheme.dialogBuilder(this)
                .setTitle("Not imported")
                .setMessage(imported.error)
                .setPositiveButton("OK", null)
                .show().also { AppTheme.styleDialog(it, this) }
            return
        }
        val merged = SnippetFile.merge(mine, read.snippets)
        val parts = ArrayList<String>()
        if (merged.added > 0) parts.add("${merged.added} added")
        if (merged.replaced > 0) parts.add("${merged.replaced} replaced one of yours")
        if (merged.dropped > 0) parts.add("${merged.dropped} did not fit")
        if (read.skipped > 0) parts.add("${read.skipped} could not be read")
        AppTheme.dialogBuilder(this)
            .setTitle("Import ${read.snippets.size} snippets?")
            .setMessage(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
            .setPositiveButton("Import") { _, _ ->
                store(merged.list)
                toast("Imported")
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, this) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_SAVE -> writeFile(uri)
            REQ_OPEN -> readFile(uri)
        }
    }

    private companion object {
        const val REQ_SAVE = 1
        const val REQ_OPEN = 2
    }
}
