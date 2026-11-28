package com.whatcode.explorer.logic

import com.whatcode.files.FileOpException
import com.whatcode.files.FileOps
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Pure JVM Trash store: moves deleted files and folders into [trashRoot] while recording their original path so they
 * can be browsed, restored, undone after delete, or emptied permanently. No Android here (unit tested).
 */
object ExplorerTrash {

    data class TrashItem(
        val id: String,
        val originalPath: String,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val deletedAt: Long,
    )

    private const val INDEX_FILE = "trash_index.tsv"

    fun list(trashRoot: File): List<TrashItem> {
        val index = File(trashRoot, INDEX_FILE)
        if (!index.isFile) return emptyList()
        val items = ArrayList<TrashItem>()
        for (line in index.readLines(Charsets.UTF_8)) {
            val parts = line.split('\t')
            if (parts.size < 6) continue
            val id = parts[0]
            val stored = File(trashRoot, id)
            if (!stored.exists() && !Files.isSymbolicLink(stored.toPath())) continue
            items.add(
                TrashItem(
                    id = id,
                    originalPath = unescape(parts[1]),
                    name = unescape(parts[2]),
                    isDir = parts[3] == "1",
                    size = parts[4].toLongOrNull() ?: 0L,
                    deletedAt = parts[5].toLongOrNull() ?: 0L,
                )
            )
        }
        return items.sortedByDescending { it.deletedAt }
    }

    /** Moves [source] into [trashRoot] and records metadata for restoring or undoing later. */
    fun moveToTrash(source: File, trashRoot: File, now: Long = System.currentTimeMillis()): TrashItem {
        val isLink = Files.isSymbolicLink(source.toPath())
        if (!isLink && !source.exists()) throw FileOpException("${source.name} is no longer there")
        if (!trashRoot.exists() && !trashRoot.mkdirs()) throw FileOpException("Couldn't open Trash folder")
        val id = "${now}_${UUID.randomUUID().toString().take(8)}"
        val stored = File(trashRoot, id)
        val isDir = !isLink && source.isDirectory
        val size = if (isDir) FileOps.measure(source)?.bytes ?: 0L else if (!isLink) source.length() else 0L

        if (!source.renameTo(stored)) {
            if (isLink) {
                Files.copy(source.toPath(), stored.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                source.delete()
            } else {
                val moved = FileOps.copy(source, trashRoot, FileOps.Clash.KEEP_BOTH)
                    ?: throw FileOpException("Couldn't move ${source.name} to Trash")
                if (!moved.renameTo(stored)) {
                    FileOps.delete(moved)
                    throw FileOpException("Couldn't move ${source.name} to Trash")
                }
                FileOps.delete(source)
            }
        }
        val item = TrashItem(id, ExplorerPaths.normalize(source.path), source.name, isDir, size, now)
        val updated = list(trashRoot) + item
        writeIndex(trashRoot, updated)
        return item
    }

    /** Restores [item] from [trashRoot] back to its original folder (or [overrideDestDir] if given). */
    fun restore(item: TrashItem, trashRoot: File, overrideDestDir: File? = null, clash: FileOps.Clash = FileOps.Clash.KEEP_BOTH): File {
        val stored = File(trashRoot, item.id)
        if (!stored.exists() && !Files.isSymbolicLink(stored.toPath())) {
            removeIndexEntry(trashRoot, item.id)
            throw FileOpException("${item.name} is no longer in Trash")
        }
        val origFile = File(item.originalPath)
        val destDir = overrideDestDir ?: origFile.parentFile ?: throw FileOpException("Original folder is missing")
        if (!destDir.exists() && !destDir.mkdirs()) throw FileOpException("Couldn't restore folder ${destDir.name}")
        val restored = if (Files.isSymbolicLink(stored.toPath())) {
            val target = File(destDir, item.name)
            Files.copy(stored.toPath(), target.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
            stored.delete()
            target
        } else {
            val want = File(destDir, item.name)
            val finalTarget = when {
                !want.exists() -> want
                clash == FileOps.Clash.REPLACE -> {
                    FileOps.delete(want)
                    want
                }
                clash == FileOps.Clash.SKIP -> return want
                else -> {
                    val unique = com.whatcode.files.FileNames.unique(item.name, item.isDir) { File(destDir, it).exists() }
                    File(destDir, unique)
                }
            }
            if (stored.renameTo(finalTarget)) {
                finalTarget
            } else {
                val copied = FileOps.copy(stored, destDir, clash)
                    ?: throw FileOpException("Couldn't restore ${item.name}")
                FileOps.delete(stored)
                if (copied.name != finalTarget.name) {
                    val renamed = File(destDir, finalTarget.name)
                    if (!renamed.exists() && copied.renameTo(renamed)) renamed else copied
                } else {
                    copied
                }
            }
        }
        removeIndexEntry(trashRoot, item.id)
        return restored
    }

    /** Permanently deletes one [item] from Trash. */
    fun deleteFromTrash(item: TrashItem, trashRoot: File) {
        val stored = File(trashRoot, item.id)
        if (stored.exists() || Files.isSymbolicLink(stored.toPath())) FileOps.delete(stored)
        removeIndexEntry(trashRoot, item.id)
    }

    /** Empties all entries in [trashRoot]. Returns count of deleted entries. */
    fun empty(trashRoot: File): Int {
        val current = list(trashRoot)
        for (item in current) {
            val stored = File(trashRoot, item.id)
            if (stored.exists() || Files.isSymbolicLink(stored.toPath())) {
                try {
                    FileOps.delete(stored)
                } catch (_: Exception) {
                }
            }
        }
        File(trashRoot, INDEX_FILE).delete()
        return current.size
    }

    fun storedFile(trashRoot: File, item: TrashItem): File = File(trashRoot, item.id)

    private fun removeIndexEntry(trashRoot: File, id: String) {
        writeIndex(trashRoot, list(trashRoot).filter { it.id != id })
    }

    private fun writeIndex(trashRoot: File, items: List<TrashItem>) {
        if (!trashRoot.exists()) trashRoot.mkdirs()
        val text = items.joinToString("\n") {
            "${it.id}\t${escape(it.originalPath)}\t${escape(it.name)}\t${if (it.isDir) "1" else "0"}\t${it.size}\t${it.deletedAt}"
        }
        File(trashRoot, INDEX_FILE).writeText(text, Charsets.UTF_8)
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
