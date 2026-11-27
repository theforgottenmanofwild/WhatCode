package com.whatcode.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * The access to all files that the file explorer asks for (Android's "All files access"; before Android 11 it is
 * the storage permission). The question itself is in [ExplorerAccess]; this is the part that needs Android.
 */
object StorageAccess {

    /** True when the app may read and write the phone's shared storage. */
    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Opens the system settings page where it is turned on: the app's own All files access page, or the list of
     * apps on it, or the app's info page. Returns false when none of them could be opened.
     */
    fun openSettings(context: Context): Boolean {
        val appUri = Uri.parse("package:${context.packageName}")
        val pages = ArrayList<Intent>()
        if (Build.VERSION.SDK_INT >= 30) {
            pages.add(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, appUri))
            pages.add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        pages.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
        for (page in pages) {
            try {
                context.startActivity(page)
                return true
            } catch (_: Exception) {
                // this phone has no such page; try the next
            }
        }
        return false
    }
}
