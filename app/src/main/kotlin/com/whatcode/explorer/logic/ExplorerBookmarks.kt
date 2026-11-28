package com.whatcode.explorer.logic

/**
 * Pure persistence rules for explorer bookmarks (pinned paths) and recent files list.
 * Encoded safely as newline-free escaped lines so paths containing `|` or tabs survive intact.
 * No Android here (unit tested).
 */
object ExplorerBookmarks {

    data class Bookmark(val path: String, val label: String)
    data class RecentFile(val path: String, val name: String, val openedAt: Long)

    const val MAX_BOOKMARKS = 50
    const val MAX_RECENT = 20

    fun parseBookmarks(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<Bookmark>()
        val seen = HashSet<String>()
        for (line in raw.lineSequence()) {
            if (line.isBlank()) continue
            val tab = line.indexOf('\t')
            val path = unescape(if (tab < 0) line else line.substring(0, tab))
            val label = if (tab < 0) "" else unescape(line.substring(tab + 1))
            if (path.isNotEmpty() && seen.add(path)) {
                out.add(Bookmark(path, label.ifEmpty { ExplorerPaths.name(path) }))
                if (out.size >= MAX_BOOKMARKS) break
            }
        }
        return out
    }

    fun writeBookmarks(items: List<Bookmark>): String =
        items.take(MAX_BOOKMARKS).joinToString("\n") { "${escape(it.path)}\t${escape(it.label)}" }

    fun toggleBookmark(items: List<Bookmark>, path: String, label: String = ExplorerPaths.name(path)): Pair<List<Bookmark>, Boolean> {
        val norm = ExplorerPaths.normalize(path)
        val idx = items.indexOfFirst { it.path == norm }
        return if (idx >= 0) {
            items.filterIndexed { i, _ -> i != idx } to false
        } else {
            val added = listOf(Bookmark(norm, label.ifEmpty { ExplorerPaths.name(norm) })) + items
            added.take(MAX_BOOKMARKS) to true
        }
    }

    fun parseRecent(raw: String?): List<RecentFile> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<RecentFile>()
        val seen = HashSet<String>()
        for (line in raw.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val path = unescape(parts[0])
            val name = unescape(parts[1])
            val ts = parts[2].toLongOrNull() ?: 0L
            if (path.isNotEmpty() && seen.add(path)) {
                out.add(RecentFile(path, name.ifEmpty { ExplorerPaths.name(path) }, ts))
                if (out.size >= MAX_RECENT) break
            }
        }
        return out
    }

    fun writeRecent(items: List<RecentFile>): String =
        items.take(MAX_RECENT).joinToString("\n") { "${escape(it.path)}\t${escape(it.name)}\t${it.openedAt}" }

    fun recordRecent(items: List<RecentFile>, path: String, name: String, timestamp: Long = System.currentTimeMillis()): List<RecentFile> {
        val norm = ExplorerPaths.normalize(path)
        val filtered = items.filter { it.path != norm }
        return (listOf(RecentFile(norm, name.ifEmpty { ExplorerPaths.name(norm) }, timestamp)) + filtered).take(MAX_RECENT)
    }

    fun removeRecent(items: List<RecentFile>, path: String): List<RecentFile> {
        val norm = ExplorerPaths.normalize(path)
        return items.filter { !ExplorerPaths.isInside(it.path, norm) }
    }

    fun rebaseBookmarks(items: List<Bookmark>, from: String, to: String): List<Bookmark> =
        items.map { b ->
            val moved = ExplorerPaths.rebase(b.path, from, to)
            if (moved != null) Bookmark(moved, if (b.label == ExplorerPaths.name(b.path)) ExplorerPaths.name(moved) else b.label) else b
        }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> sb.append('\\')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}
