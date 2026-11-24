package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * The opened folder: picking it, browsing it level by level, and finding files anywhere inside it.
 * Access goes through Android's folder picker, so the app only sees the folder the user chose.
 */
class Workspace(
    private val activity: Activity,
    private val requestCode: Int,
    private val onOpenFile: (Uri) -> Unit,
) {
    /** One file or folder inside the opened folder. [size] is in bytes, or -1 when the provider does not say. */
    class Entry(val id: String, val name: String, val isDir: Boolean, val size: Long = -1L)

    private class Found(val id: String, val name: String, val path: String) {
        val lowerPath: String = path.lowercase(Locale.ROOT)
        val lowerName: String = name.lowercase(Locale.ROOT)
    }

    private val prefs = activity.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())

    private var treeUri: Uri? = null
    private var rootName = ""

    /** Called after another folder has been opened. */
    var onFolderChanged: (() -> Unit)? = null

    val hasFolder: Boolean get() = treeUri != null
    val folderName: String get() = rootName
    val rootId: String? get() = treeUri?.let { DocumentsContract.getTreeDocumentId(it) }

    /** The address of a file or folder inside the opened folder, from its document id. */
    fun uriFor(id: String): Uri? = treeUri?.let { docUri(it, id) }

    /** What is directly inside a folder of the opened folder. Reads from storage, so call it off the main thread. */
    fun listChildren(parentId: String): List<Entry> {
        val tree = treeUri ?: return emptyList()
        return children(tree, parentId)
    }

    /** Folders from the root down to where the Files list currently is: (document id, name). */
    private val browse = ArrayList<Pair<String, String>>()

    // Search index of every file below the opened folder. Only the main thread touches these.
    private val index = ArrayList<Found>()
    private var indexGen = 0
    private var indexing = false
    private var indexedAt = 0L
    private var onIndexChanged: (() -> Unit)? = null

    init {
        val saved = prefs.getString("tree", null)
        if (saved != null) {
            val u = Uri.parse(saved)
            if (activity.contentResolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission }) {
                treeUri = u
                rootName = prefs.getString("name", null) ?: "Folder"
            }
        }
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun docUri(tree: Uri, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    // ------------------------------------------------------------- choosing a folder

    fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        activity.startActivityForResult(i, requestCode)
    }

    fun onFolderPicked(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            activity.contentResolver.takePersistableUriPermission(uri, rw)
        } catch (_: Exception) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                toast("Couldn't keep access to that folder: ${e.message}")
                return
            }
        }
        val old = treeUri
        if (old != null && old != uri) {
            try {
                activity.contentResolver.releasePersistableUriPermission(old, rw)
            } catch (_: Exception) {
                // already gone
            }
        }
        treeUri = uri
        rootName = displayName(uri, DocumentsContract.getTreeDocumentId(uri)) ?: "Folder"
        prefs.edit().putString("tree", uri.toString()).putString("name", rootName).apply()
        browse.clear()
        indexGen++
        index.clear()
        indexing = false
        indexedAt = 0L
        onIndexChanged?.invoke()
        onFolderChanged?.invoke()
        toast("Opened folder: $rootName")
    }

    private fun displayName(tree: Uri, id: String): String? = try {
        activity.contentResolver.query(
            docUri(tree, id), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** True (after asking) when no folder is open yet. */
    private fun needFolder(): Boolean {
        if (treeUri != null) return false
        AlertDialog.Builder(activity)
            .setTitle("No folder open")
            .setMessage("Open a folder first to browse and search its files.")
            .setPositiveButton("Open folder") { _, _ -> pickFolder() }
            .setNegativeButton("Cancel", null)
            .show()
        return true
    }

    private fun children(tree: Uri, parentId: String): List<Entry> {
        val out = ArrayList<Entry>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        activity.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Entry(
                        c.getString(0),
                        c.getString(1) ?: "?",
                        c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (c.isNull(3)) -1L else c.getLong(3),
                    )
                )
            }
        }
        return out
    }

    // ------------------------------------------------------------------- Files

    fun showFiles() {
        if (needFolder()) return
        val tree = treeUri ?: return
        if (browse.isEmpty()) browse.add(DocumentsContract.getTreeDocumentId(tree) to rootName)

        val header = TextView(activity).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        val list = ListView(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(header)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.55).toInt()))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(rootName)
            .setView(box)
            .setNegativeButton("Close", null)
            .create()

        var rows: List<Pair<String, () -> Unit>> = emptyList()
        var token = 0

        fun show(labels: List<String>) {
            list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels)
        }

        fun load() {
            val here = browse.last().first
            header.text = "/" + browse.drop(1).joinToString("/") { it.second }
            val mine = ++token
            rows = emptyList()
            show(listOf("Loading…"))
            Thread {
                var entries: List<Entry> = emptyList()
                var error: String? = null
                try {
                    entries = children(tree, here)
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
                ui.post {
                    if (mine != token || !alive()) return@post
                    if (error != null) {
                        show(listOf("Couldn't read this folder: $error"))
                        return@post
                    }
                    val sorted = entries.sortedWith(
                        compareBy<Entry>({ !it.isDir }, { it.name.lowercase(Locale.ROOT) })
                    )
                    val r = ArrayList<Pair<String, () -> Unit>>()
                    if (browse.size > 1) {
                        r.add("⬆  .." to {
                            browse.removeAt(browse.size - 1)
                            load()
                        })
                    }
                    for (e in sorted) {
                        if (e.isDir) {
                            r.add("📁  ${e.name}" to {
                                browse.add(e.id to e.name)
                                load()
                            })
                        } else {
                            r.add("📄  ${e.name}" to {
                                dialog.dismiss()
                                onOpenFile(docUri(tree, e.id))
                            })
                        }
                    }
                    if (sorted.isEmpty()) r.add("(empty folder)" to {})
                    rows = r
                    show(r.map { it.first })
                }
            }.start()
        }

        list.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.second?.invoke() }
        dialog.show()
        load()
    }

    // ---------------------------------------------------------------- Find file

    /** Walks the whole folder tree, however deep, and streams what it finds to the main thread. */
    private fun startIndex() {
        val tree = treeUri ?: return
        val gen = ++indexGen
        index.clear()
        indexing = true
        indexedAt = 0L
        Thread {
            val visited = HashSet<String>()
            val stack = ArrayList<Pair<String, String>>() // document id to path so far
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            stack.add(rootId to "")
            visited.add(rootId)
            var batch = ArrayList<Found>()

            fun flush(done: Boolean) {
                val b = batch
                batch = ArrayList()
                ui.post {
                    if (gen != indexGen) return@post
                    index.addAll(b)
                    if (done) {
                        indexing = false
                        indexedAt = System.currentTimeMillis()
                    }
                    onIndexChanged?.invoke()
                }
            }

            var lastFlush = System.currentTimeMillis()
            while (stack.isNotEmpty() && gen == indexGen) {
                val (id, prefix) = stack.removeAt(stack.size - 1)
                val kids = try {
                    children(tree, id)
                } catch (_: Exception) {
                    continue // an unreadable folder is skipped, the rest is still searched
                }
                for (k in kids) {
                    if (k.isDir) {
                        if (visited.add(k.id)) stack.add(k.id to "$prefix${k.name}/")
                    } else {
                        batch.add(Found(k.id, k.name, "$prefix${k.name}"))
                    }
                }
                if (batch.size >= 300 || System.currentTimeMillis() - lastFlush > 400) {
                    flush(false)
                    lastFlush = System.currentTimeMillis()
                }
            }
            if (gen == indexGen) flush(true)
        }.start()
    }

    fun showFind() {
        if (needFolder()) return
        val tree = treeUri ?: return
        val stale = indexedAt != 0L && System.currentTimeMillis() - indexedAt > 10 * 60_000L
        if (!indexing && (indexedAt == 0L || stale)) startIndex()

        val input = EditText(activity).apply {
            hint = "Part of a file name or path"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val status = TextView(activity).apply {
            textSize = 12f
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val list = ListView(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(input)
            addView(status)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.4).toInt()))
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Find file in $rootName")
            .setView(box)
            .setNeutralButton("Rescan", null)
            .setNegativeButton("Close", null)
            .create()

        var shown: List<Found> = emptyList()

        fun refresh() {
            val tokens = input.text.toString().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val cap = 200
            val byName = ArrayList<Found>()
            val byPath = ArrayList<Found>()
            if (tokens.isNotEmpty()) {
                for (f in index) {
                    if (!tokens.all { f.lowerPath.contains(it) }) continue
                    if (tokens.all { f.lowerName.contains(it) }) byName.add(f) else byPath.add(f)
                    if (byName.size >= cap) break
                }
            }
            val matches = (byName.sortedBy { it.name.length } + byPath).take(cap)
            shown = matches
            list.adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_list_item_1,
                matches.map { f ->
                    val parent = f.path.substringBeforeLast('/', "")
                    if (parent.isEmpty()) f.name else "${f.name}   ·   $parent"
                }
            )
            val count = String.format(Locale.US, "%,d", index.size)
            status.text = when {
                indexing -> "Searching… $count files found so far" + if (tokens.isNotEmpty()) " · ${matches.size} match" else ""
                tokens.isEmpty() -> "$count files in the folder and every folder below it. Type to search."
                matches.size >= cap -> "$cap+ matches. Keep typing to narrow it down."
                else -> "${matches.size} match" + if (matches.size == 1) "" else "es"
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        list.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let {
                dialog.dismiss()
                onOpenFile(docUri(tree, it.id))
            }
        }
        dialog.setOnDismissListener { onIndexChanged = null }
        onIndexChanged = { if (alive()) refresh() }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        // "Rescan" must not close the dialog, so its click is set after show().
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            startIndex()
            refresh()
        }
        input.requestFocus()
        refresh()
    }
}
