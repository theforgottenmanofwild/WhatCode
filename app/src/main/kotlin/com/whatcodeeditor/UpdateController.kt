package com.whatcodeeditor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * The update screens. Nothing is downloaded without the user saying yes, the downloaded file
 * is kept until the update is confirmed, and it can be installed again or deleted from the Update panel.
 */
class UpdateController(private val activity: Activity, private val onChanged: () -> Unit) {

    private val updater = Updater(activity.applicationContext)
    private val ui = Handler(Looper.getMainLooper())
    private var found: Release? = null
    private var checking = false

    @Volatile
    private var cancelled = false

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun mb(bytes: Long): String =
        if (bytes > 0) String.format(Locale.US, "%.1f MB", bytes / 1048576.0) else "unknown size"

    /** True when an update is found or downloaded and waiting for the user. */
    fun needsAttention(): Boolean = updater.pending() != null || found != null

    /** Menu entry label: a dot means there is something to do. */
    fun buttonLabel(): String = if (needsAttention()) "Update ●" else "Update"

    /** Call when the screen comes back: picks up a finished update and refreshes the button. */
    fun refresh() {
        updater.confirmInstalled()
        updater.takeConfirmed()?.let { toast("Updated to $it. The downloaded file was deleted.") }
        onChanged()
    }

    /** Quiet check at app start. It only ever asks; it never downloads by itself. */
    fun autoCheck() {
        if (updater.repo != null && updater.autoCheck && updater.pending() == null && updater.checkDue()) check(false)
    }

    // ---------------------------------------------------------------- the panel

    fun showPanel() {
        refresh()
        val p = updater.pending()
        val f = found
        val summary = StringBuilder("Installed: ${updater.installedName} (${updater.channel.label} channel)")
        when {
            p != null -> {
                summary.append("\n\nDownloaded: ${p.versionName} (${mb(p.file.length())}), not installed yet.")
                updater.lastResult?.let { summary.append("\n\n$it") }
                summary.append("\n\nThe file stays on this device until the update is confirmed. Delete removes it now.")
            }
            f != null -> summary.append("\n\nUpdate available: ${f.versionName} (${mb(f.size)})")
            updater.repo == null -> summary.append("\n\nUpdates aren't set up in this build.")
            updater.lastCheck > 0 -> summary.append("\n\nNo update was found at the last check.")
            else -> summary.append("\n\nNot checked yet.")
        }

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), 0)
        }
        box.addView(TextView(activity).apply {
            text = summary.toString()
            textSize = 15f
        })
        box.addView(CheckBox(activity).apply {
            text = "Check for updates when the app starts"
            isChecked = updater.autoCheck
            setOnCheckedChangeListener { _, on -> updater.autoCheck = on }
        })

        val b = AlertDialog.Builder(activity).setTitle("Updates").setView(box)
        when {
            p != null -> b
                .setPositiveButton("Install") { _, _ -> install() }
                .setNegativeButton("Delete") { _, _ -> confirmDelete() }
                .setNeutralButton("Check now") { _, _ -> check(true) }
            f != null -> b
                .setPositiveButton("Download") { _, _ -> promptDownload(f) }
                .setNegativeButton("Close", null)
            updater.repo != null -> b
                .setPositiveButton("Check now") { _, _ -> check(true) }
                .setNegativeButton("Close", null)
            else -> b.setNegativeButton("Close", null)
        }
        b.show()
    }

    // ----------------------------------------------------------------- checking

    private fun check(manual: Boolean) {
        if (checking) return
        checking = true
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
                val p = updater.pending()
                when {
                    error != null -> if (manual) toast("Couldn't check for updates: $error")
                    release != null && updater.isNewer(release) -> {
                        found = release
                        onChanged()
                        if (p != null && p.versionName == release.versionName) {
                            if (manual) toast("${release.versionName} is already downloaded. Open Update to install it.")
                        } else if (manual || updater.dismissed != release.key) {
                            promptDownload(release)
                        }
                    }
                    else -> {
                        found = null
                        onChanged()
                        if (manual) {
                            AlertDialog.Builder(activity)
                                .setTitle("You're up to date")
                                .setMessage("${updater.installedName} is the newest ${updater.channel.label} build.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            }
        }.start()
    }

    // -------------------------------------------------------------- downloading

    private fun promptDownload(r: Release) {
        val replaces = updater.pending()?.let { "\n\nThis replaces the downloaded ${it.versionName}." } ?: ""
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(
                "${r.versionName} is available on the ${r.channel.label} channel. You have ${updater.installedName}.\n\n" +
                    "Download it now? (${mb(r.size)})$replaces"
            )
            .setPositiveButton("Download") { _, _ -> download(r) }
            .setNegativeButton("Later") { _, _ -> updater.dismiss(r.key) }
            .show()
    }

    private fun download(r: Release) {
        cancelled = false
        val label = TextView(activity).apply { text = "Starting…" }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(label)
            addView(bar)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading ${r.versionName}")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ -> cancelled = true }
            .show()

        Thread {
            var error: String? = null
            try {
                updater.download(r, { cancelled }) { done, total ->
                    ui.post {
                        if (total > 0) {
                            bar.isIndeterminate = false
                            bar.progress = (done * 100 / total).toInt()
                        }
                        label.text = "${mb(done)} of ${mb(total)}"
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            ui.post {
                try {
                    dialog.dismiss()
                } catch (_: Exception) {
                    // the screen is already gone
                }
                if (!alive()) return@post
                when {
                    cancelled -> toast("Download cancelled.")
                    error != null -> toast("Download failed: $error")
                    else -> {
                        found = null
                        onChanged()
                        promptInstall()
                    }
                }
            }
        }.start()
    }

    // --------------------------------------------------------------- installing

    private fun promptInstall() {
        val p = updater.pending() ?: return
        AlertDialog.Builder(activity)
            .setTitle("Update downloaded")
            .setMessage(
                "${p.versionName} is ready to install. The file stays on this device until the update is confirmed, " +
                    "so you can install it later from the Update panel.\n\nAfter installing, open the app again."
            )
            .setPositiveButton("Install") { _, _ -> install() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun install() {
        val p = updater.pending() ?: return
        if (!updater.canInstall()) {
            AlertDialog.Builder(activity)
                .setTitle("Allow installing updates")
                .setMessage(
                    "Android needs your permission before this app can install updates. " +
                        "Turn on \"Allow from this source\", come back, and tap Update > Install."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        try {
            updater.install(p)
        } catch (e: Exception) {
            toast("Couldn't start the installer: ${e.message}")
        }
    }

    private fun confirmDelete() {
        val p = updater.pending() ?: return
        AlertDialog.Builder(activity)
            .setTitle("Delete downloaded update?")
            .setMessage("Delete ${p.versionName} (${mb(p.file.length())}) from this device? You can download it again later.")
            .setPositiveButton("Delete") { _, _ ->
                updater.deletePending()
                onChanged()
                toast("Deleted.")
            }
            .setNegativeButton("Keep", null)
            .show()
    }
}
