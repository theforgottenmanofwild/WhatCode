package com.whatcode.files

import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Find file in the file explorer: the files and folders below a folder whose path, from that folder down, holds
 * every word that was typed. Reads storage, so call it off the main thread. No Android here (unit tested).
 */
object ExplorerFind {

    /** What was found, best first. [truncated] is true when there were more than the limit. */
    class Result(val files: List<File>, val truncated: Boolean)

    /**
     * Looks below [root], however deep, for names that match every word of [query] (case is ignored). A match on
     * the name itself comes before one only on the folders above it, then shorter names come first. Links to
     * folders are not followed, so a loop can't make the search go on for ever. [cancelled] is asked often, and a
     * search that was cancelled returns what it had.
     */
    fun search(root: File, query: String, limit: Int, cancelled: () -> Boolean = { false }): Result {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return Result(emptyList(), false)
        val base = root.path.trimEnd('/')
        val hits = ArrayList<File>()
        val folders = ArrayList<File>()
        folders.add(root)
        while (folders.isNotEmpty() && !cancelled()) {
            val dir = folders.removeAt(folders.size - 1)
            val kids = dir.listFiles() ?: continue
            for (kid in kids) {
                val below = kid.path.removePrefix(base).lowercase(Locale.ROOT)
                if (words.all { below.contains(it) }) {
                    hits.add(kid)
                    if (hits.size > limit * 4) return finish(hits, words, limit, true)
                }
                if (kid.isDirectory && !Files.isSymbolicLink(kid.toPath())) folders.add(kid)
            }
        }
        return finish(hits, words, limit, false)
    }

    private fun finish(hits: List<File>, words: List<String>, limit: Int, stopped: Boolean): Result {
        val ordered = hits.sortedWith(
            compareBy<File>(
                { f -> !words.all { f.name.lowercase(Locale.ROOT).contains(it) } },
                { it.name.length },
            ).thenComparator { a, b -> ExplorerSort.compareNames(a.name, b.name) }
        )
        return Result(ordered.take(limit), stopped || ordered.size > limit)
    }
}
