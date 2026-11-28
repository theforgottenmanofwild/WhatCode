package com.whatcode.apk

import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Pure JVM helper for locating the newest APK under a project's Gradle build output directory
 * (`app/build/outputs/apk/` or `build/outputs/apk/`) without following symbolic links (C7).
 */
object BuildOutputLocator {

    data class Candidate(
        val path: String,
        val name: String,
        val lastModified: Long,
        val size: Long,
    )

    /** True when relative path segments match `app/build/outputs/apk/<variant>/<file>.apk` or `build/outputs/apk/<variant>/<file>.apk`. */
    fun isBuildOutputApk(relParts: List<String>): Boolean {
        if (relParts.isEmpty()) return false
        val name = relParts.last()
        if (!name.lowercase(Locale.ROOT).endsWith(".apk")) return false
        val lower = relParts.map { it.lowercase(Locale.ROOT) }
        val appBuild = lower.size >= 5 && lower[0] == "app" && lower[1] == "build" && lower[2] == "outputs" && lower[3] == "apk"
        val modBuild = lower.size >= 4 && lower[0] == "build" && lower[1] == "outputs" && lower[2] == "apk"
        return appBuild || modBuild
    }

    /** Picks the newest candidate APK (tie-breaking on debug variants, then name). */
    fun pickLatest(candidates: List<Candidate>): Candidate? =
        candidates.maxWithOrNull(
            compareBy<Candidate>(
                { it.lastModified },
                { it.name.lowercase(Locale.ROOT).contains("debug") },
                { it.name.lowercase(Locale.ROOT) },
            ),
        )

    /** Walks known build output directories under [projectRoot] without following symlinks and returns the newest APK. */
    fun findNewestApk(projectRoot: File): Candidate? {
        if (!projectRoot.isDirectory) return null
        val found = ArrayList<Candidate>()
        for (rel in listOf("app/build/outputs/apk", "build/outputs/apk")) {
            val base = File(projectRoot, rel)
            if (base.isDirectory && !Files.isSymbolicLink(base.toPath())) {
                collectApks(base, 0, found)
            }
        }
        return pickLatest(found)
    }

    private fun collectApks(dir: File, depth: Int, out: MutableList<Candidate>) {
        if (depth > 4 || Files.isSymbolicLink(dir.toPath())) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (Files.isSymbolicLink(f.toPath())) continue
            if (f.isDirectory) {
                collectApks(f, depth + 1, out)
            } else if (f.isFile && f.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                out.add(Candidate(f.absolutePath, f.name, f.lastModified(), f.length()))
            }
        }
    }
}
