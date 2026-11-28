package com.whatcode.files

import java.io.File
import java.nio.charset.Charset

/**
 * Pure JVM crash-recovery journal. Keeps a snapshot of unsaved editor buffers under [dir] plus an `.active` session
 * flag that is removed only on clean shutdown or explicit user discard. No Android here (unit tested).
 */
object CrashRecovery {

    data class RecoveryEntry(
        val id: String,
        val uri: String?,
        val name: String,
        val encodingId: String,
        val text: String,
        val savedAt: Long,
    )

    private const val ACTIVE_FLAG = ".active"
    private const val INDEX_FILE = "index.tsv"

    fun hasUncleanSession(dir: File): Boolean =
        File(dir, ACTIVE_FLAG).isFile && readAll(dir).isNotEmpty()

    fun markActive(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, ACTIVE_FLAG).writeText("1", Charsets.UTF_8)
    }

    fun clearAll(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }

    fun saveEntries(dir: File, entries: List<RecoveryEntry>) {
        if (!dir.exists()) dir.mkdirs()
        val keepFiles = HashSet<String>()
        keepFiles.add(ACTIVE_FLAG)
        keepFiles.add(INDEX_FILE)
        val index = StringBuilder()
        for (e in entries) {
            val fname = "buf_${e.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.txt"
            keepFiles.add(fname)
            File(dir, fname).writeText(e.text, Charsets.UTF_8)
            index.append(e.id).append('\t')
                .append(escape(e.uri.orEmpty())).append('\t')
                .append(escape(e.name)).append('\t')
                .append(escape(e.encodingId)).append('\t')
                .append(fname).append('\t')
                .append(e.savedAt).append('\n')
        }
        File(dir, INDEX_FILE).writeText(index.toString(), Charsets.UTF_8)
        dir.listFiles()?.forEach { f ->
            if (f.name !in keepFiles) f.delete()
        }
    }

    fun readAll(dir: File): List<RecoveryEntry> {
        val idx = File(dir, INDEX_FILE)
        if (!idx.isFile) return emptyList()
        val out = ArrayList<RecoveryEntry>()
        for (line in idx.readLines(Charsets.UTF_8)) {
            val p = line.split('\t')
            if (p.size < 6) continue
            val bufFile = File(dir, p[4])
            if (!bufFile.isFile) continue
            val text = try {
                bufFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                continue
            }
            out.add(
                RecoveryEntry(
                    id = p[0],
                    uri = unescape(p[1]).ifEmpty { null },
                    name = unescape(p[2]).ifEmpty { "untitled.txt" },
                    encodingId = unescape(p[3]).ifEmpty { TextEncoding.Spec.UTF_8.id },
                    text = text,
                    savedAt = p[5].toLongOrNull() ?: 0L,
                )
            )
        }
        return out
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
