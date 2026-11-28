package com.whatcode.apk

import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Pure JVM helpers for hashing APKs and certificates, listing native ABIs from archive entry names, comparing an
 * archive against an installed package, and turning installer failure codes into clear messages. No Android imports.
 */
object ApkHashes {

    data class Digests(val sha256: String?, val md5: String?)

    /**
     * Streams [input] in 32 KB chunks and computes whichever of [sha256] and [md5] were requested. Returns null if
     * [cancelled] returns true before reading finishes. Does not close [input].
     */
    fun compute(
        input: InputStream,
        sha256: Boolean,
        md5: Boolean,
        cancelled: () -> Boolean = { false },
    ): Digests? {
        if (!sha256 && !md5) return Digests(null, null)
        val d256 = if (sha256) MessageDigest.getInstance("SHA-256") else null
        val dMd5 = if (md5) MessageDigest.getInstance("MD5") else null
        val buf = ByteArray(32 * 1024)
        while (true) {
            if (cancelled()) return null
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) {
                d256?.update(buf, 0, n)
                dMd5?.update(buf, 0, n)
            }
        }
        if (cancelled()) return null
        return Digests(
            sha256 = d256?.digest()?.toHexLower(),
            md5 = dMd5?.digest()?.toHexLower(),
        )
    }

    /** Computes the colon-separated uppercase SHA-256 fingerprint of raw DER certificate bytes. */
    fun certSha256Colon(derBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
        return digest.joinToString(":") { "%02X".format(Locale.US, it) }
    }

    private fun ByteArray.toHexLower(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}

/** Extracts unique native ABI names from APK zip entry paths (`lib/<abi>/<name>.so`). */
object ApkAbis {

    fun fromEntryNames(names: Iterable<String>): List<String> {
        val found = LinkedHashSet<String>()
        for (raw in names) {
            if (!raw.startsWith("lib/") || !raw.endsWith(".so")) continue
            val rest = raw.substring(4)
            val slash = rest.indexOf('/')
            if (slash <= 0) continue
            val abi = rest.substring(0, slash)
            if (abi != "." && abi != ".." && !rest.substring(slash + 1).contains('/')) {
                found.add(abi)
            }
        }
        return found.sorted()
    }
}

/** Pure comparison between an APK archive and the currently installed package, if visible to the caller. */
object ApkCompare {

    enum class Relation { UPDATE, SAME, DOWNGRADE, UNKNOWN }

    data class Result(
        val relation: Relation,
        val installedVersion: String?,
        val installedCode: Long?,
        val signatureDiffers: Boolean,
        val visibilityLimited: Boolean,
    )

    fun compare(
        installedVersion: String?,
        installedCode: Long?,
        installedCerts: Set<String>?,
        archiveCode: Long,
        archiveCerts: Set<String>,
        sdkInt: Int,
    ): Result {
        if (installedCode == null) {
            return Result(
                relation = Relation.UNKNOWN,
                installedVersion = null,
                installedCode = null,
                signatureDiffers = false,
                visibilityLimited = sdkInt >= 30,
            )
        }
        val relation = when {
            archiveCode > installedCode -> Relation.UPDATE
            archiveCode < installedCode -> Relation.DOWNGRADE
            else -> Relation.SAME
        }
        val sigDiffers = installedCerts != null &&
            installedCerts.isNotEmpty() &&
            archiveCerts.isNotEmpty() &&
            installedCerts.none { it in archiveCerts }
        return Result(
            relation = relation,
            installedVersion = installedVersion,
            installedCode = installedCode,
            signatureDiffers = sigDiffers,
            visibilityLimited = false,
        )
    }
}

enum class InstallFail { ABORTED, BLOCKED, CONFLICT, INCOMPATIBLE, INVALID, STORAGE, OTHER }

/** Pure formatting helpers for APK names, components, SDK levels and installer failures. */
object ApkNames {

    fun isApkFile(name: String): Boolean = name.endsWith(".apk", ignoreCase = true)

    fun shortComponent(packageName: String, name: String): String =
        if (packageName.isNotEmpty() && name.startsWith("$packageName.")) name.substring(packageName.length) else name

    fun sdkLabel(api: Int): String = when (api) {
        0 -> "Not specified"
        21 -> "API 21 (Android 5.0)"
        22 -> "API 22 (Android 5.1)"
        23 -> "API 23 (Android 6.0)"
        24 -> "API 24 (Android 7.0)"
        25 -> "API 25 (Android 7.1)"
        26 -> "API 26 (Android 8.0)"
        27 -> "API 27 (Android 8.1)"
        28 -> "API 28 (Android 9)"
        29 -> "API 29 (Android 10)"
        30 -> "API 30 (Android 11)"
        31 -> "API 31 (Android 12)"
        32 -> "API 32 (Android 12L)"
        33 -> "API 33 (Android 13)"
        34 -> "API 34 (Android 14)"
        35 -> "API 35 (Android 15)"
        36 -> "API 36 (Android 16)"
        else -> "API $api"
    }

    fun installError(fail: InstallFail, detail: String?): String {
        val msg = detail.orEmpty()
        return when {
            fail == InstallFail.ABORTED -> "The install was cancelled."
            msg.contains("VERSION_DOWNGRADE", ignoreCase = true) ->
                "Downgrade rejected: a newer version is already installed."
            fail == InstallFail.CONFLICT ||
                msg.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                msg.contains("SIGNATURES", ignoreCase = true) ->
                "Signature mismatch: the APK is signed with a different key than the installed app."
            fail == InstallFail.STORAGE || msg.contains("INSUFFICIENT_STORAGE", ignoreCase = true) ->
                "There isn't enough storage space to install."
            fail == InstallFail.INVALID || msg.contains("PARSE", ignoreCase = true) || msg.contains("INVALID_APK", ignoreCase = true) ->
                "Android couldn't read the APK file (invalid or damaged package)."
            fail == InstallFail.INCOMPATIBLE || msg.contains("NO_MATCHING_ABIS", ignoreCase = true) ->
                "This APK isn't compatible with this device."
            fail == InstallFail.BLOCKED -> "Android blocked the install."
            else -> "The install failed."
        }
    }
}
