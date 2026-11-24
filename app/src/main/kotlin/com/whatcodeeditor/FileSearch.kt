package com.whatcodeeditor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** One matching line. [text] is the line trimmed to a short snippet, with the match at [textCol] for [textLen] characters. */
class SearchMatch(
    val line: Int,
    val col: Int,
    val length: Int,
    val text: String,
    val textCol: Int,
    val textLen: Int,
)

/** All the matching lines of one file. [path] is relative to the opened folder. */
class FileHits(val id: String, val path: String, val matches: List<SearchMatch>)

/**
 * Looks for text in every file below the opened folder, however deep. It runs on its own thread and reports
 * on the main thread, file by file, so results can show up while it is still going. Files that are too big,
 * or that are not text, are skipped, and so are the `.git` and `node_modules` folders. Make a new one for each
 * search and [cancel] the old one.
 */
class FileSearch(private val activity: Activity, private val workspace: Workspace) {

    interface Listener {
        fun onFile(hit: FileHits, files: Int, matches: Int)
        fun onProgress(scanned: Int, files: Int, matches: Int)
        fun onDone(scanned: Int, files: Int, matches: Int, truncated: Boolean)
        fun onError(message: String)
    }

    private companion object {
        const val MAX_BYTES = 1_048_576
        const val MAX_PER_FILE = 100
        const val MAX_TOTAL = 1000
        val SKIP_FOLDERS = setOf(".git", "node_modules")
    }

    private val ui = Handler(Looper.getMainLooper())

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun start(query: String, caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean, listener: Listener) {
        val pattern = try {
            var expr = if (regex) query else Pattern.quote(query)
            if (wholeWord) expr = "\\b(?:$expr)\\b"
            val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            Pattern.compile(expr, flags)
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
                    val hit = searchFile(k, prefix, pattern, MAX_TOTAL - total)
                    if (hit != null) {
                        files++
                        total += hit.matches.size
                        val f = files
                        val m = total
                        ui.post { if (!cancelled) listener.onFile(hit, f, m) }
                        if (total >= MAX_TOTAL) {
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
                    if (k.isDir && k.name !in SKIP_FOLDERS && visited.add(k.id)) stack.add(k.id to "$prefix${k.name}/")
                }
            }

            val s = scanned
            val f = files
            val m = total
            val t = truncated
            ui.post { if (!cancelled) listener.onDone(s, f, m, t) }
        }.start()
    }

    /** The matching lines of one file, or null if it has none, is too big, or is not text. */
    private fun searchFile(k: Workspace.Entry, prefix: String, pattern: Pattern, room: Int): FileHits? {
        if (k.size > MAX_BYTES) return null
        val uri = workspace.uriFor(k.id) ?: return null
        val bytes = try {
            activity.contentResolver.openInputStream(uri)?.use { readCapped(it) }
        } catch (_: Exception) {
            null
        } ?: return null
        for (i in 0 until minOf(bytes.size, 4096)) if (bytes[i] == 0.toByte()) return null // not text

        val text = String(bytes, Charsets.UTF_8)
        val matches = ArrayList<SearchMatch>()
        val limit = minOf(MAX_PER_FILE, room)
        var lineNo = 0
        var start = 0
        while (start <= text.length && matches.size < limit) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = text.length
            lineNo++
            var lineEnd = end
            if (lineEnd > start && text[lineEnd - 1] == '\r') lineEnd--
            val line = text.substring(start, lineEnd)
            val m = pattern.matcher(line)
            while (m.find()) {
                if (m.end() > m.start()) {
                    matches.add(snippet(lineNo, line, m.start(), m.end() - m.start()))
                    break
                }
            }
            if (end >= text.length) break
            start = end + 1
        }
        return if (matches.isEmpty()) null else FileHits(k.id, "$prefix${k.name}", matches)
    }

    /** The line cut down to a short piece around the match, without its leading spaces. */
    private fun snippet(lineNo: Int, line: String, col: Int, len: Int): SearchMatch {
        val lead = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        var from = lead
        if (col - from > 40) from = col - 40
        var to = minOf(line.length, from + 160)
        if (col + len > to) to = minOf(line.length, col + len)
        val body = line.substring(from, to).trimEnd()
        val before = if (from > lead) "…" else ""
        val after = if (to < line.length) "…" else ""
        val textCol = (col - from) + before.length
        val shown = before + body + after
        val textLen = (len).coerceAtMost((shown.length - textCol).coerceAtLeast(0))
        return SearchMatch(lineNo, col, len, shown, textCol.coerceAtMost(shown.length), textLen)
    }

    /** All of the stream, or null if it is longer than [MAX_BYTES]. */
    private fun readCapped(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toByteArray()
    }
}
