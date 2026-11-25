package com.whatcodeeditor.files

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * What was found in one file: its matching lines, and whether its name matched too. [path] is relative to the opened
 * folder. A file with only a name match has no lines.
 */
class FileHits(val id: String, val path: String, val matches: List<SearchMatch>, val nameMatch: Boolean = false) {
    /** How much this file adds to the count of matches: each line, and one for the name. */
    val count: Int get() = matches.size + if (nameMatch) 1 else 0
}

/**
 * Looks for text, and for file names, in every file below the opened folder, however deep. It runs on its own
 * thread and reports on the main thread, file by file, so results can show up while it is still going. Files that
 * are too big or are not text are not read (their names are still matched), and folders such as `.git`,
 * `node_modules` and `build` are skipped (see [SearchLogic]). Make a new one for each search and [cancel] the old one;
 * cancelling also stops a regular expression that is in the middle of a line.
 */
class FileSearch(private val activity: Activity, private val workspace: Workspace) {

    interface Listener {
        fun onFile(hit: FileHits, files: Int, matches: Int)
        fun onProgress(scanned: Int, files: Int, matches: Int)
        fun onDone(scanned: Int, files: Int, matches: Int, truncated: Boolean)
        fun onError(message: String)
    }

    private val ui = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun start(query: String, caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean, listener: Listener) {
        val pattern = try {
            SearchLogic.compile(query, caseSensitive, wholeWord, regex)
        } catch (e: PatternSyntaxException) {
            listener.onError("Not a valid regular expression: ${e.description ?: query}")
            return
        }
        val root = workspace.rootId ?: return

        Thread {
            var scanned = 0
            var files = 0
            var total = 0
            var truncated = false
            var lastProgress = 0L
            val visited = HashSet<String>()
            val stack = ArrayList<Pair<String, String>>() // document id to path so far
            stack.add(root to "")
            visited.add(root)

            outer@ while (stack.isNotEmpty() && !cancelled) {
                val (id, prefix) = stack.removeAt(stack.size - 1)
                val kids = try {
                    workspace.listChildren(id)
                } catch (_: Exception) {
                    continue // an unreadable folder is skipped, the rest is still searched
                }
                val sorted = kids.sortedBy { it.name.lowercase(Locale.ROOT) }

                for (k in sorted) {
                    if (k.isDir) continue
                    if (cancelled) break@outer
                    scanned++
                    val hit = try {
                        searchFile(k, prefix, pattern, SearchLogic.MAX_TOTAL - total)
                    } catch (_: java.util.concurrent.CancellationException) {
                        break@outer
                    }
                    if (hit != null) {
                        files++
                        total += hit.count
                        val f = files
                        val m = total
                        ui.post { if (!cancelled) listener.onFile(hit, f, m) }
                        if (total >= SearchLogic.MAX_TOTAL) {
                            truncated = true
                            break@outer
                        }
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastProgress > 300) {
                        lastProgress = now
                        val s = scanned
                        val f = files
                        val m = total
                        ui.post { if (!cancelled) listener.onProgress(s, f, m) }
                    }
                }
                // Folders go on the stack backwards, so they are searched in name order.
                for (k in sorted.asReversed()) {
                    if (k.isDir && !SearchLogic.isSkippedFolder(k.name) && visited.add(k.id)) stack.add(k.id to "$prefix${k.name}/")
                }
            }

            val s = scanned
            val f = files
            val m = total
            val t = truncated
            ui.post { if (!cancelled) listener.onDone(s, f, m, t) }
        }.start()
    }

    /**
     * What one file has: its name matching, and its matching lines. Null if it has neither. The contents are not read
     * for a file that is too big, whose type is never text, or whose first bytes show that it is not text.
     */
    private fun searchFile(k: Workspace.Entry, prefix: String, pattern: Pattern, room: Int): FileHits? {
        val nameHit = SearchLogic.nameMatches(k.name, pattern)
        val lines = readMatches(k, pattern, room - if (nameHit) 1 else 0)
        if (!nameHit && lines.isEmpty()) return null
        return FileHits(k.id, "$prefix${k.name}", lines, nameHit)
    }

    private fun readMatches(k: Workspace.Entry, pattern: Pattern, room: Int): List<SearchMatch> {
        if (room <= 0 || SearchLogic.isBinaryName(k.name) || SearchLogic.isTooBig(k.size)) return emptyList()
        val uri = workspace.uriFor(k.id) ?: return emptyList()
        val bytes = try {
            activity.contentResolver.openInputStream(uri)?.use { readCapped(it) }
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        if (SearchLogic.looksBinary(bytes)) return emptyList()
        return SearchLogic.findLines(String(bytes, Charsets.UTF_8), pattern, minOf(SearchLogic.MAX_PER_FILE, room)) { cancelled }
    }

    /** All of the stream, or null if it is longer than [SearchLogic.MAX_BYTES]. */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > SearchLogic.MAX_BYTES) return null
        }
        return out.toByteArray()
    }
}
