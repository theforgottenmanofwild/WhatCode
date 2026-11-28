package com.whatcode.apk

import com.whatcode.explorer.logic.ExplorerSort
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Pure JVM archive inspection and safe extraction for `.apk`, `.zip`, `.jar` and `.aar` files. Blocks zip-slip path
 * escapes and protects against zip bombs using both declared limits and byte-counted streaming checks.
 */
object ArchiveBrowser {

    data class BombLimits(
        val maxTotalBytes: Long = DEFAULT_MAX_BYTES,
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    )

    class ExtractSummary(val filesExtracted: Int, val bytesExtracted: Long, val skipped: Int)

    enum class ClashMode { OVERWRITE, SKIP, KEEP_BOTH }

    fun isArchive(name: String): Boolean {
        val low = name.lowercase(Locale.ROOT)
        return low.endsWith(".apk") || low.endsWith(".zip") || low.endsWith(".jar") || low.endsWith(".aar")
    }

    /**
     * Lists direct child entries inside [archiveFile] under [subFolder] (`""` for archive root), formatted as
     * [ExplorerSort.Entry] items so the explorer can reuse its sorting and layouts directly.
     */
    fun listFolder(archiveFile: File, subFolder: String): List<ExplorerSort.Entry> {
        val prefix = normalizeFolder(subFolder)
        val dirs = LinkedHashMap<String, ExplorerSort.Entry>()
        val files = ArrayList<ExplorerSort.Entry>()

        ZipFile(archiveFile).use { zip ->
            val e = zip.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                val clean = cleanEntryPath(entry.name) ?: continue
                if (prefix.isNotEmpty() && !clean.startsWith(prefix)) continue
                val rest = clean.substring(prefix.length)
                if (rest.isEmpty()) continue
                val slash = rest.indexOf('/')
                if (slash >= 0) {
                    val dirName = rest.substring(0, slash)
                    if (dirName.isNotEmpty()) {
                        val full = prefix + dirName + "/"
                        if (!dirs.containsKey(full)) {
                            dirs[full] = ExplorerSort.Entry(
                                name = dirName,
                                path = full,
                                isDir = true,
                                size = 0L,
                                modified = maxOf(0L, entry.time),
                                count = -1,
                            )
                        }
                    }
                } else if (!entry.isDirectory) {
                    files.add(
                        ExplorerSort.Entry(
                            name = rest,
                            path = clean,
                            isDir = false,
                            size = maxOf(0L, entry.size),
                            modified = maxOf(0L, entry.time),
                            count = -1,
                        )
                    )
                }
            }
        }
        return dirs.values + files
    }

    /** Reads one entry's bytes from [archiveFile], aborting if its decompressed size exceeds [maxBytes]. */
    fun readEntryBytes(archiveFile: File, entryPath: String, maxBytes: Long = DEFAULT_MAX_ENTRY_READ_BYTES): ByteArray {
        val target = cleanEntryPath(entryPath)?.trimEnd('/') ?: throw IllegalArgumentException("Invalid archive path")
        ZipFile(archiveFile).use { zip ->
            val entry = zip.getEntry(target) ?: zip.getEntry(entryPath) ?: throw IllegalArgumentException("Entry not found: $entryPath")
            if (entry.size > maxBytes) throw SecurityException("Entry exceeds the ${maxBytes / (1024 * 1024)} MB limit")
            val out = ByteArrayOutputStream(entry.size.coerceIn(256L, 64 * 1024L).toInt())
            val buf = ByteArray(16 * 1024)
            var total = 0L
            zip.getInputStream(entry).use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw SecurityException("Archive entry exceeds safe decompression limit")
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        }
    }

    /** Resolves [entryPath] under [destDir] and throws [SecurityException] if it would escape [destDir] (zip-slip). */
    fun safeTarget(destDir: File, entryPath: String): File {
        val destCanonical = destDir.canonicalFile
        val out = File(destCanonical, entryPath).canonicalFile
        val destPath = destCanonical.toPath()
        val outPath = out.toPath()
        if (!outPath.startsWith(destPath) || outPath == destPath) {
            throw SecurityException("Blocked zip-slip entry: $entryPath")
        }
        return out
    }

    /**
     * Extracts either a single file/folder prefix ([onlySubPath]) or the whole archive into [destDir]. Enforces zip-slip
     * safety, entry-count caps and byte-counted zip-bomb limits.
     */
    fun extract(
        archiveFile: File,
        destDir: File,
        onlySubPath: String? = null,
        clashMode: ClashMode = ClashMode.KEEP_BOTH,
        limits: BombLimits = BombLimits(),
        cancelled: () -> Boolean = { false },
    ): ExtractSummary {
        destDir.mkdirs()
        val sub = onlySubPath?.let(::cleanEntryPath)
        var filesOut = 0
        var entriesSeen = 0
        var totalBytes = 0L
        var skipped = 0

        ZipFile(archiveFile).use { zip ->
            val all = zip.entries()
            val selected = ArrayList<Pair<ZipEntry, String>>()
            var declaredSum = 0L
            while (all.hasMoreElements()) {
                if (cancelled()) break
                val entry = all.nextElement()
                val clean = cleanEntryPath(entry.name) ?: throw SecurityException("Blocked zip-slip entry: ${entry.name}")
                val relPath = when {
                    sub == null -> clean
                    clean == sub.trimEnd('/') -> File(clean).name
                    clean.startsWith(sub.trimEnd('/') + "/") -> clean.substring(sub.trimEnd('/').length + 1)
                    else -> continue
                }
                if (relPath.isEmpty()) continue
                // Validate target even before counting or creating anything.
                safeTarget(destDir, relPath)
                entriesSeen++
                if (entriesSeen > limits.maxEntries) {
                    throw SecurityException("Archive has more than ${limits.maxEntries} entries (possible zip bomb)")
                }
                if (entry.size > 0) {
                    declaredSum += entry.size
                    if (declaredSum > limits.maxTotalBytes) {
                        throw SecurityException("Archive uncompressed size exceeds limit (possible zip bomb)")
                    }
                }
                selected.add(entry to relPath)
            }

            val buf = ByteArray(32 * 1024)
            for ((entry, relPath) in selected) {
                if (cancelled()) break
                val rawOut = safeTarget(destDir, relPath)
                if (entry.isDirectory) {
                    rawOut.mkdirs()
                    continue
                }
                rawOut.parentFile?.mkdirs()
                val target = resolveClash(rawOut, clashMode)
                if (target == null) {
                    skipped++
                    continue
                }
                try {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output ->
                            while (true) {
                                if (cancelled()) break
                                val n = input.read(buf)
                                if (n < 0) break
                                totalBytes += n
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw SecurityException("Extracted size exceeded limit (possible zip bomb)")
                                }
                                output.write(buf, 0, n)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    target.delete()
                    throw e
                }
                if (entry.time > 0) target.setLastModified(entry.time)
                filesOut++
            }
        }
        return ExtractSummary(filesOut, totalBytes, skipped)
    }

    private fun resolveClash(target: File, mode: ClashMode): File? {
        if (!target.exists()) return target
        return when (mode) {
            ClashMode.OVERWRITE -> target
            ClashMode.SKIP -> null
            ClashMode.KEEP_BOTH -> uniqueName(target)
        }
    }

    private fun uniqueName(file: File): File {
        val dir = file.parentFile ?: return file
        val name = file.name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = File(dir, "$stem ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private fun normalizeFolder(sub: String): String {
        val clean = sub.replace('\\', '/').trim('/')
        return if (clean.isEmpty()) "" else "$clean/"
    }

    /** Rejects `..` path traversal or rooted entry paths cleanly; normalizes forward slashes. */
    fun cleanEntryPath(raw: String): String? {
        val norm = raw.replace('\\', '/').trimStart('/')
        if (norm.isEmpty()) return null
        val parts = norm.split('/')
        for (p in parts) {
            if (p == "..") return null
        }
        return norm
    }

    const val DEFAULT_MAX_MB = 250
    const val MIN_MAX_MB = 10
    const val MAX_MAX_MB = 2048
    const val DEFAULT_MAX_BYTES = DEFAULT_MAX_MB * 1024L * 1024L
    const val DEFAULT_MAX_ENTRIES = 10_000
    const val MIN_MAX_ENTRIES = 100
    const val MAX_MAX_ENTRIES = 200_000
    const val DEFAULT_MAX_ENTRY_READ_BYTES = 16L * 1024L * 1024L
}
