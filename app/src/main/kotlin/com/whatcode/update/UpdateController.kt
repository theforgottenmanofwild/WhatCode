package com.whatcode.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import com.whatcode.ui.AppTheme
import java.util.Locale

/**
 * Update checks and the dialogs around them. Nothing is downloaded without the user saying yes,
 * the downloaded file is kept until the update is confirmed, and it can be installed again or deleted.
 *
 * The editor checks every time it opens. When a newer build exists it opens [ReleaseActivity], a full
 * screen with the release notes and the Download / Install buttons. The Update App page (Settings > Update App)
 * shows the same state and has the manual buttons. What was found is kept in [Updater], so every screen agrees.
 */
class UpdateController(private val activity: Activity, private val onChanged: () -> Unit) {

    private val updater = Updater(activity.applicationContext)
    private val ui = Handler(Looper.getMainLooper())
    private var checking = false
    private var lastAttempt = 0L
    private var prompt: AlertDialog? = null

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    fun mb(bytes: Long): String =
        if (bytes > 0) String.format(Locale.US, "%.1f MB", bytes / 1048576.0) else "unknown size"

    /** Shows a question dialog, unless another one from here is still on screen. */
    private fun ask(b: AlertDialog.Builder) {
        if (prompt?.isShowing == true) return
        prompt = b.create().also {
            it.show()
            AppTheme.styleDialog(it, activity)
        }
    }

    /** True when an update is found or downloaded and waiting for the user. */
    fun needsAttention(): Boolean = updater.pending() != null || updater.available() != null

    /** The "check for updates when the app opens" setting. */
    var checkOnOpen: Boolean
        get() = updater.autoCheck
        set(v) {
            updater.autoCheck = v
        }

    /** Call when a screen comes back: picks up a finished update and refreshes whatever shows the state. */
    fun refresh() {
        updater.confirmInstalled()
        updater.takeConfirmed()?.let { toast("Updated to $it. The downloaded file was deleted.") }
        onChanged()
    }

    // ------------------------------------------------------------ automatic check

    /** Quiet check as the app opens. It shows the update screen if there is an update; it never downloads by itself. */
    fun autoCheck() {
        if (updater.repo != null && updater.autoCheck) check(manual = false)
    }

    /** Coming back from another app counts as opening it again, unless it was only away for moments. */
    fun autoCheckIfAway() {
        if (System.currentTimeMillis() - lastAttempt > 10L * 60 * 1000) autoCheck()
    }

    // ------------------------------------------------------- the Update App page

    /** What the Update App page says about updates. */
    fun status(): String {
        val p = updater.pending()
        val a = updater.available()
        val s = StringBuilder("Installed: ${updater.installedName} (${updater.channel.label} channel)")
        if (updater.repo == null) {
            s.append("\n\nUpdates aren't set up in this build.")
            return s.toString()
        }
        if (p != null) {
            s.append("\n\nDownloaded: ${p.versionName} (${mb(p.file.length())}), not installed yet.")
            updater.lastResult?.let { s.append("\n\n$it") }
            s.append("\n\nThe file stays on this device until the update is confirmed. Delete removes it now.")
        }
        if (a != null && a.versionName != p?.versionName) {
            s.append("\n\nUpdate available: ${a.versionName} (${mb(a.size)})")
        }
        if (p == null && a == null) {
            s.append(if (updater.lastCheck > 0) "\n\nYou're up to date." else "\n\nNot checked yet.")
        }
        if (updater.lastCheck > 0) {
            val ago = DateUtils.getRelativeTimeSpanString(updater.lastCheck, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            s.append("\n\nLast checked: $ago")
        }
        return s.toString()
    }

    /** The buttons the Update App page shows for the current state. */
    fun actions(): List<Pair<String, () -> Unit>> {
        val p = updater.pending()
        val a = updater.available()
        val out = mutableListOf<Pair<String, () -> Unit>>()
        if (p != null) {
            out += "Install" to { install() }
            out += "Delete" to { confirmDelete() }
        }
        if (a != null && a.versionName != p?.versionName) out += "View update" to { openUpdatePage() }
        if (updater.repo != null) out += "Check now" to { check(manual = true) }
        return out
    }

    // ----------------------------------------------------------------- checking

    private fun check(manual: Boolean) {
        if (checking) return
        checking = true
        lastAttempt = System.currentTimeMillis()
        if (manual) toast("Checking for updates…")
        Thread {
            var release: Release? = null
            var error: String? = null
            try {
                release = updater.fetchLatest()
                updater.markChecked()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            ui.post {
                checking = false
                if (!alive()) return@post
                when {
                    error != null -> if (manual) toast("Couldn't check for updates: $error")
                    release != null && updater.isNewer(release) -> {
                        updater.setAvailable(release)
                        onChanged()
                        // Shown every time it is found, not just once: "Later" only postpones it to the next time the app opens.
                        // A quiet check does not cover another screen the person has moved on to; the menu dot still shows.
                        if (manual || activity.hasWindowFocus()) openUpdatePage()
                    }
                    else -> {
                        updater.setAvailable(null)
                        onChanged()
                        if (manual) {
                            ask(
                                AppTheme.dialogBuilder(activity)
                                    .setTitle("You're up to date")
                                    .setMessage("${updater.installedName} is the newest ${updater.channel.label} build.")
                                    .setPositiveButton("OK", null)
                            )
                        }
                    }
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- update screen

    /** Opens the full-screen update page: release notes, then Download, or Install if the file is already here. */
    fun openUpdatePage() {
        activity.startActivity(
            Intent(activity, ReleaseActivity::class.java).putExtra(ReleaseActivity.EXTRA_MODE, ReleaseActivity.MODE_UPDATE)
        )
    }

    // --------------------------------------------------------------- installing

    /** Starts Android's installer for the downloaded file (asking for permission first if needed). */
    fun install() {
        val p = updater.pending() ?: return
        if (!updater.canInstall()) {
            AppTheme.dialogBuilder(activity)
                .setTitle("Allow installing updates")
                .setMessage(
                    "Android needs your permission before this app can install updates. " +
                        "Turn on \"Allow from this source\", come back, and tap Install."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show().also { AppTheme.styleDialog(it, activity) }
            return
        }
        try {
            updater.install(p)
        } catch (e: Exception) {
            toast("Couldn't start the installer: ${e.message}")
        }
    }

    fun confirmDelete() {
        val p = updater.pending() ?: return
        AppTheme.dialogBuilder(activity)
            .setTitle("Delete downloaded update?")
            .setMessage("Delete ${p.versionName} (${mb(p.file.length())}) from this device? You can download it again later.")
            .setPositiveButton("Delete") { _, _ ->
                updater.deletePending()
                onChanged()
                toast("Deleted.")
            }
            .setNegativeButton("Keep", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }
}
