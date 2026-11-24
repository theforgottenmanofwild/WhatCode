package com.whatcodeeditor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

enum class Channel(val label: String) { STABLE("stable"), NIGHTLY("nightly") }

/** A published build on GitHub. [build] is the nightly build number, or -1 for a stable release. */
data class Release(
    val channel: Channel,
    val versionName: String,
    val build: Int,
    val apkUrl: String,
    val sha256Url: String?,
    val size: Long,
    /** The release description from GitHub (Markdown), shown on the update screen. */
    val notes: String = "",
) {
    val key: String get() = "${channel.label}:$versionName"
}

/** An update that has been downloaded to this device but not yet confirmed as installed. */
data class Pending(val channel: Channel, val versionName: String, val build: Int, val file: File, val notes: String = "")

/** The published description of a release, for the "What's new" page. */
data class ReleaseInfo(val title: String, val notes: String)

/**
 * Update logic without any UI: finds the newest build on the app's own channel,
 * downloads it, hands it to Android's installer, and cleans up once the update is confirmed.
 */
class Updater(context: Context) {

    private val context: Context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val dir: File get() = File(context.filesDir, "updates")

    // ------------------------------------------------------------ what is installed

    @Suppress("DEPRECATION")
    private val info get() = context.packageManager.getPackageInfo(context.packageName, 0)

    val installedName: String get() = info.versionName ?: "0"

    val installedCode: Long
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    /** Nightly builds carry "-nightly" in their version name; everything else is stable. */
    val channel: Channel get() = if (installedName.contains("-nightly")) Channel.NIGHTLY else Channel.STABLE

    /** "owner/name" of the GitHub repository, injected into the manifest at build time. */
    val repo: String?
        @Suppress("DEPRECATION")
        get() = try {
            val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            ai.metaData?.getString("github_repo")?.trim()?.takeIf { Regex("[\\w.-]+/[\\w.-]+").matches(it) }
        } catch (_: Exception) {
            null
        }

    // ------------------------------------------------------------------- settings

    var autoCheck: Boolean
        get() = prefs.getBoolean("auto_check", true)
        set(v) = prefs.edit().putBoolean("auto_check", v).apply()

    /** The project on GitHub, or null when this build has no repository set. */
    val repoUrl: String? get() = repo?.let { "https://github.com/$it" }

    /** Where the project website lives if it is published with GitHub Pages ("owner.github.io/name"). */
    val websiteUrl: String?
        get() = repo?.let { r -> "https://${r.substringBefore('/').lowercase(Locale.ROOT)}.github.io/${r.substringAfter('/')}/" }

    val lastCheck: Long get() = prefs.getLong("last_check", 0L)

    fun markChecked() = prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()

    /**
     * The newer build found by the last check, kept so every screen agrees on it without another
     * network request. Returns null (and forgets it) once the installed app is at least that new.
     */
    fun available(): Release? {
        val version = prefs.getString("a_version", null) ?: return null
        val url = prefs.getString("a_apk", null) ?: return null
        val ch = if (prefs.getString("a_channel", null) == "nightly") Channel.NIGHTLY else Channel.STABLE
        val r = Release(
            ch, version, prefs.getInt("a_build", -1), url, prefs.getString("a_sha", null),
            prefs.getLong("a_size", -1L), prefs.getString("a_notes", null) ?: ""
        )
        if (ch != channel || !isNewer(r)) {
            setAvailable(null)
            return null
        }
        return r
    }

    fun setAvailable(r: Release?) {
        val e = prefs.edit()
        if (r == null) {
            e.remove("a_version").remove("a_apk").remove("a_sha").remove("a_build").remove("a_size").remove("a_channel").remove("a_notes")
        } else {
            e.putString("a_version", r.versionName).putString("a_apk", r.apkUrl).putString("a_sha", r.sha256Url)
                .putInt("a_build", r.build).putLong("a_size", r.size).putString("a_channel", r.channel.label)
                .putString("a_notes", r.notes)
        }
        e.apply()
    }

    val lastResult: String? get() = prefs.getString("last_result", null)

    fun setLastResult(text: String?) = prefs.edit().putString("last_result", text).apply()

    // -------------------------------------------------------------- finding updates

    /** Newest release on this app's channel, or null if none is published. Blocks; call off the main thread. */
    fun fetchLatest(): Release? {
        val repo = repo ?: throw IOException("Updates are not set up in this build")
        val nightly = channel == Channel.NIGHTLY
        val url = if (nightly) {
            "https://api.github.com/repos/$repo/releases/tags/nightly"
        } else {
            "https://api.github.com/repos/$repo/releases/latest"
        }
        val rel = JSONObject(getText(url, api = true) ?: return null)
        val assets = rel.optJSONArray("assets") ?: return null

        var apk: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) apk = a
        }
        if (apk == null) return null
        val apkName = apk.getString("name")
        var shaUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name") == "$apkName.sha256") shaUrl = a.optString("browser_download_url")
        }

        val version: String
        val build: Int
        if (nightly) {
            val m = Regex("""(\d+(?:\.\d+)*-nightly\.(\d+))""").find(rel.optString("name"))
                ?: throw IOException("Unrecognised nightly release")
            version = m.groupValues[1]
            build = m.groupValues[2].toInt()
        } else {
            version = rel.optString("tag_name").removePrefix("v")
            if (!Regex("""\d+(\.\d+)*""").matches(version)) throw IOException("Unrecognised release '$version'")
            build = -1
        }
        return Release(
            channel = channel,
            versionName = version,
            build = build,
            apkUrl = apk.getString("browser_download_url"),
            sha256Url = shaUrl,
            size = apk.optLong("size", -1L),
            notes = rel.optString("body").trim(),
        )
    }

    /** The release notes of the installed version, or null if none are published. Blocks; call off the main thread. */
    fun fetchInstalledInfo(): ReleaseInfo? {
        val repo = repo ?: return null
        val tag = if (channel == Channel.NIGHTLY) "nightly" else "v$installedName"
        val rel = JSONObject(getText("https://api.github.com/repos/$repo/releases/tags/$tag", api = true) ?: return null)
        return ReleaseInfo(rel.optString("name"), rel.optString("body").trim())
    }

    /** True if [url] answers. Used to show only the links that work. Blocks; call off the main thread. */
    fun reachable(url: String): Boolean {
        return try {
            val c = open(url, api = false)
            c.requestMethod = "HEAD"
            c.connectTimeout = 6_000
            c.readTimeout = 6_000
            try {
                c.responseCode in 200..399
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isNewer(r: Release): Boolean =
        if (r.channel == Channel.NIGHTLY) r.build > installedCode else compareVersions(r.versionName, installedName) > 0

    private fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun open(url: String, api: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "WhatCodeEditor-updater")
        if (api) c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    private fun getText(url: String, api: Boolean): String? {
        val c = open(url, api)
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code != 200) {
                throw IOException("GitHub answered HTTP $code" + if (code == 403) " (too many requests, try later)" else "")
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ----------------------------------------------------------------- downloading

    /** Downloads and verifies [r]. The previous download is only replaced once this one is complete. */
    fun download(r: Release, cancelled: () -> Boolean, progress: (done: Long, total: Long) -> Unit): Pending {
        dir.mkdirs()
        val expected = r.sha256Url?.let { u ->
            getText(u, api = false)?.let { Regex("[0-9a-fA-F]{64}").find(it)?.value }
        }
        val part = File(dir, "download.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val c = open(r.apkUrl, api = false)
        try {
            if (c.responseCode != 200) throw IOException("Download failed (HTTP ${c.responseCode})")
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else r.size
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancelled()) throw IOException("Download cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        progress(done, total)
                    }
                }
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        } finally {
            c.disconnect()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() == 0L || (expected != null && !actual.equals(expected, ignoreCase = true))) {
            part.delete()
            throw IOException("The download is corrupted (checksum mismatch)")
        }

        dir.listFiles()?.filter { it.name != part.name }?.forEach { it.delete() }
        val file = File(dir, "update-${r.channel.label}-${r.versionName}.apk")
        if (!part.renameTo(file)) {
            part.delete()
            throw IOException("Couldn't save the download")
        }
        prefs.edit()
            .putString("p_file", file.absolutePath)
            .putString("p_version", r.versionName)
            .putInt("p_build", r.build)
            .putString("p_channel", r.channel.label)
            .putString("p_notes", r.notes)
            .putString("last_result", null)
            .apply()
        return Pending(r.channel, r.versionName, r.build, file, r.notes)
    }

    // ------------------------------------------------------ downloaded but not installed

    fun pending(): Pending? {
        val path = prefs.getString("p_file", null) ?: return null
        val file = File(path)
        val version = prefs.getString("p_version", null)
        if (!file.isFile || version == null) {
            clearPending()
            return null
        }
        val channel = if (prefs.getString("p_channel", null) == "nightly") Channel.NIGHTLY else Channel.STABLE
        return Pending(channel, version, prefs.getInt("p_build", -1), file, prefs.getString("p_notes", null) ?: "")
    }

    private fun clearPending() {
        prefs.edit().remove("p_file").remove("p_version").remove("p_build").remove("p_channel").remove("p_notes").apply()
    }

    /** Deletes the downloaded file (used by the Delete button and after a confirmed update). */
    fun deletePending() {
        pending()?.file?.delete()
        dir.listFiles()?.forEach { it.delete() }
        clearPending()
    }

    /**
     * The update counts as confirmed once the installed app is at least as new as the downloaded one.
     * Only then is the file removed. Returns true if it was.
     */
    fun confirmInstalled(): Boolean {
        val p = pending() ?: return false
        val done = if (p.channel == Channel.NIGHTLY) installedCode >= p.build else compareVersions(installedName, p.versionName) >= 0
        if (!done) return false
        deletePending()
        prefs.edit().putString("confirmed", installedName).putString("last_result", null).apply()
        return true
    }

    /** The version of a just-confirmed update, once, so the app can say so. */
    fun takeConfirmed(): String? {
        val v = prefs.getString("confirmed", null) ?: return null
        prefs.edit().remove("confirmed").apply()
        return v
    }

    // ------------------------------------------------------------------ installing

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Hands the downloaded file to Android's installer. The result arrives at [InstallResultReceiver]. */
    fun install(p: Pending) {
        val installer = context.packageManager.packageInstaller
        for (s in installer.mySessions) {
            try {
                installer.abandonSession(s.sessionId)
            } catch (_: Exception) {
                // already finished
            }
        }
        setLastResult(null)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            p.file.inputStream().use { input ->
                session.openWrite("update.apk", 0, p.file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val result = Intent(context, InstallResultReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
        }
    }
}
