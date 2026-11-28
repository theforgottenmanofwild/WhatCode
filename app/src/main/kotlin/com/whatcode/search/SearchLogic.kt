package com.whatcode.search

import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.regex.Pattern

/** One matching line. [text] is the line trimmed to a short snippet, with the match at [textCol] for [textLen] characters. */
class SearchMatch(
    val line: Int,
    val col: Int,
    val length: Int,
    val text: String,
    val textCol: Int,
    val textLen: Int,
)

/**
 * The rules of Search in files that need no device, kept apart from [FileSearch] (which reads the folder) so they
 * can be unit tested: which folders and files are not searched, how the text typed in the field becomes a pattern,
 * and how a text is cut into matching lines with a short snippet each.
 */
object SearchLogic {

    /** Files above this size are not searched. */
    const val MAX_BYTES = 1_048_576

    /** At most this many matching lines are reported for one file, and this many in all. */
    const val MAX_PER_FILE = 100
    const val MAX_TOTAL = 1000

    /** How much of the start of a file is looked at for a zero byte, which means it is not text. */
    private const val BINARY_SCAN = 4096

    /** Folders that hold tools' copies, downloads or build results rather than the project's own text. */
    private val SKIP_FOLDERS = setOf(
        ".git", ".hg", ".svn", "node_modules", "build", "dist", "out", "target", ".gradle", ".idea", ".next", ".nuxt",
        "__pycache__", ".cache", ".venv", "venv", ".dart_tool", "Pods", "DerivedData",
    )

    /** File types that are never text, so their contents are not read. Their names can still match. */
    private val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "pdf", "zip", "jar", "aar", "apk", "aab", "gz", "tgz",
        "tar", "7z", "rar", "bz2", "xz", "so", "dll", "exe", "dylib", "a", "o", "class", "dex", "ttf", "otf", "woff",
        "woff2", "mp3", "mp4", "m4a", "mov", "avi", "mkv", "wav", "ogg", "flac", "webm", "keystore", "jks", "bin",
        "dat", "db", "sqlite", "ds_store",
    )

    fun isSkippedFolder(name: String): Boolean = name in SKIP_FOLDERS

    fun isBinaryName(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase(Locale.ROOT) in BINARY_EXTENSIONS
    }

    /** True if the start of the file has a zero byte, which text never does. */
    fun looksBinary(bytes: ByteArray): Boolean {
        for (i in 0 until minOf(bytes.size, BINARY_SCAN)) if (bytes[i] == 0.toByte()) return true
        return false
    }

    /** True if a file of [size] bytes is too big to search. An unknown size (below 0) is checked while reading. */
    fun isTooBig(size: Long): Boolean = size > MAX_BYTES

    /** The pattern for what was typed. Throws [java.util.regex.PatternSyntaxException] for a broken regular expression. */
    fun compile(query: String, caseSensitive: Boolean, wholeWord: Boolean, regex: Boolean): Pattern {
        var expr = if (regex) query else Pattern.quote(query)
        if (wholeWord) expr = "\\b(?:$expr)\\b"
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(expr, flags)
    }

    /** True if the file [name] (not its folders) has a match. */
    fun nameMatches(name: String, pattern: Pattern): Boolean {
        val m = pattern.matcher(name)
        while (m.find()) if (m.end() > m.start()) return true
        return false
    }

    /**
     * The lines of [text] that have a match, one entry per line, at most [limit] of them. Lines are counted from 1 and
     * cut at \n; a \r before it is not part of the line. Matches that are empty (such as `x*` on a line without an x)
     * do not count. [stop] is asked as the text is read, so a search that was cancelled does not have to wait for a
     * slow regular expression to finish: it ends with a [CancellationException].
     */
    fun findLines(text: String, pattern: Pattern, limit: Int, stop: () -> Boolean = { false }): List<SearchMatch> {
        val matches = ArrayList<SearchMatch>()
        var lineNo = 0
        var start = 0
        while (start <= text.length && matches.size < limit) {
            if (stop()) throw CancellationException()
            var end = text.indexOf('\n', start)
            if (end < 0) end = text.length
            lineNo++
            var lineEnd = end
            if (lineEnd > start && text[lineEnd - 1] == '\r') lineEnd--
            val line = text.substring(start, lineEnd)
            val m = pattern.matcher(StoppableText(line, stop))
            while (m.find()) {
                if (m.end() > m.start()) {
                    matches.add(snippet(lineNo, line, m.start(), m.end() - m.start()))
                    break
                }
            }
            if (end >= text.length) break
            start = end + 1
        }
        return matches
    }

    /** The line cut down to a short piece around the match, without its leading spaces. */
    fun snippet(lineNo: Int, line: String, col: Int, len: Int): SearchMatch {
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
}
