package com.whatcode.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.whatcode.apk.ApkNames
import com.whatcode.apk.InstallFail

/** Result of an explorer APK install reported by [InstallResultReceiver]. */
data class ApkInstallResult(
    val apkPath: String?,
    val label: String?,
    val success: Boolean,
    val message: String?,
)

/** In-process delivery so an open explorer screen handles APK install results without polling. */
object ApkInstallBus {
    private var listener: ((ApkInstallResult) -> Boolean)? = null

    fun listen(onResult: ((ApkInstallResult) -> Boolean)?) {
        listener = onResult
    }

    internal fun deliver(result: ApkInstallResult): Boolean = listener?.invoke(result) == true
}

/** Receives the installer's progress for an update or an explorer APK started by [Updater.installApk]. */
class InstallResultReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    private fun confirmIntent(i: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_UPDATE
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = confirmIntent(intent)
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            return
        }
        val fail = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFail.ABORTED
            PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFail.BLOCKED
            PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFail.CONFLICT
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallFail.INCOMPATIBLE
            PackageInstaller.STATUS_FAILURE_INVALID -> InstallFail.INVALID
            PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFail.STORAGE
            else -> InstallFail.OTHER
        }
        val why = ApkNames.installError(fail, detail)
        if (source == SOURCE_APK) {
            val path = intent.getStringExtra(EXTRA_APK_PATH)
            val label = intent.getStringExtra(EXTRA_APK_LABEL)
            val success = status == PackageInstaller.STATUS_SUCCESS
            val message = when {
                success -> "Installed ${label ?: "package"}."
                detail.isNullOrBlank() || status == PackageInstaller.STATUS_FAILURE_ABORTED -> why
                else -> "$why ($detail)"
            }
            if (!ApkInstallBus.deliver(ApkInstallResult(path, label, success, message))) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            }
            return
        }
        val updater = Updater(context)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            updater.setLastResult(null)
        } else {
            val text = "$why The downloaded file is still saved: open Settings to install it again or delete it." +
                if (detail.isNullOrBlank() || status == PackageInstaller.STATUS_FAILURE_ABORTED) "" else " ($detail)"
            updater.setLastResult("Last install attempt: $text")
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_SOURCE = "whatcode_install_source"
        const val EXTRA_APK_PATH = "whatcode_install_apk_path"
        const val EXTRA_APK_LABEL = "whatcode_install_apk_label"
        const val SOURCE_UPDATE = "update"
        const val SOURCE_APK = "apk"
    }
}

/** Runs in the new version right after an update, so the downloaded file is removed even if the app isn't opened. */
class AppReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater(context).confirmInstalled()
    }
}
