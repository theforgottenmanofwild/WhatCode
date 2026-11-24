package com.whatcodeeditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the installer's progress for an update started by [Updater.install]. */
class InstallResultReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    private fun confirmIntent(i: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val updater = Updater(context)
        when (status) {
            // Android wants the user to confirm: show its install screen.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            // The app is replaced and restarted by Android; the downloaded file is removed after that (see Updater.confirmInstalled).
            PackageInstaller.STATUS_SUCCESS -> updater.setLastResult(null)
            else -> {
                val why = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "The update conflicts with the installed app. It may be signed with a different key."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "The update isn't compatible with this device."
                    PackageInstaller.STATUS_FAILURE_INVALID -> "Android rejected the downloaded file."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "There isn't enough storage to install."
                    else -> "The install failed."
                }
                val text = "$why The downloaded file is still saved: open Settings to install it again or delete it." +
                    if (detail.isNullOrBlank() || status == PackageInstaller.STATUS_FAILURE_ABORTED) "" else " ($detail)"
                updater.setLastResult("Last install attempt: $text")
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Runs in the new version right after an update, so the downloaded file is removed even if the app isn't opened. */
class AppReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater(context).confirmInstalled()
    }
}
