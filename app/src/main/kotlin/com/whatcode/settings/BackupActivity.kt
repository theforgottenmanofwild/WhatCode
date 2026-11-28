package com.whatcode.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage

/**
 * The "Import and export" page, opened from Settings. It saves every setting as one JSON text, to a file or to the
 * clipboard, and reads such a text back from a file or the clipboard after asking. The rules for what a backup holds
 * and how it is checked are in [SettingsBackup]; the settings themselves are read and written by [SettingsBackupStore].
 */
class BackupActivity : SettingsPage("Import and export") {

    /** What the last export or import did, shown on the page. */
    private var result: String? = null

    override fun render() {
        content.removeAllViews()

        content.addView(
            muted(
                "Save your settings as a JSON file, and load them again on this or another device. A backup holds " +
                    "the Appearance choices including the syntax colors, Typing, Tabs, Autosave, the key rows and the " +
                    "update check. It does not hold your files, your folder or your open tabs."
            )
        )

        content.addView(sectionTitle("EXPORT"))
        content.addView(wide("Save to a file") { saveFile() }, row())
        content.addView(wide("Copy to the clipboard") { copyToClipboard() }, row())

        content.addView(sectionTitle("IMPORT"))
        content.addView(
            muted(
                "Importing replaces the settings in the file. A setting the file does not mention goes back to its " +
                    "default, and the pages of Settings the file has nothing for are left as they are. You are asked " +
                    "before anything changes."
            )
        )
        content.addView(wide("Open a file") { openFile() }, row())
        content.addView(wide("Paste from the clipboard") { pasteFromClipboard() }, row())

        result?.let {
            content.addView(sectionTitle("LAST RESULT"))
            content.addView(muted(it))
        }
    }

    private fun row() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(8), 0, 0) }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun json(): String = SettingsBackupStore.export(this, appVersion())

    /** The version of the app, written into the backup so a file can be traced to the build that made it. */
    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    // ------------------------------------------------------------------ export

    private fun saveFile() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "whatcode-settings.json")
        }
        try {
            startActivityForResult(i, REQ_SAVE)
        } catch (e: Exception) {
            toast("No app can save a file here")
        }
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast("The clipboard is not available")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("WhatCode settings", json()))
        result = "Copied your settings to the clipboard."
        render()
        toast("Copied")
    }

    private fun writeFile(uri: Uri) {
        try {
            val out = contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("The file could not be opened")
            out.use { it.write(json().toByteArray(Charsets.UTF_8)) }
            result = "Saved your settings to a file."
            toast("Saved")
        } catch (e: Exception) {
            result = "Could not save the file: ${e.message ?: e.javaClass.simpleName}"
            toast("Save failed")
        }
        render()
    }

    // ------------------------------------------------------------------ import

    private fun openFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(i, REQ_OPEN)
        } catch (e: Exception) {
            toast("No app can open a file here")
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            result = "The clipboard has no text."
            render()
            return
        }
        confirm(text)
    }

    private fun readFile(uri: Uri) {
        val text = try {
            val input = contentResolver.openInputStream(uri) ?: throw java.io.IOException("The file could not be opened")
            input.use { stream ->
                // Stop as soon as the file is longer than a backup can be, without reading the rest of it.
                val bytes = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    if (bytes.size() + n > SettingsBackup.MAX_BYTES) return@use null
                    bytes.write(chunk, 0, n)
                }
                String(bytes.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            result = "Could not open the file: ${e.message ?: e.javaClass.simpleName}"
            render()
            return
        }
        if (text == null) {
            result = "The file is too big to be a settings backup."
            render()
            return
        }
        confirm(text)
    }

    /** Checks [text] and, if it is a backup, asks before it replaces the settings. */
    private fun confirm(text: String) {
        val parsed = SettingsBackup.parse(text)
        val backup = parsed.backup
        if (backup == null) {
            result = parsed.error ?: "This is not a settings backup."
            render()
            return
        }
        val skipped = if (backup.skipped == 0) "" else "\n\n${backup.skipped} entries it holds are not settings of this version and are ignored."
        AppTheme.dialogBuilder(this)
            .setTitle("Import settings?")
            .setMessage(
                "This replaces your settings with the ${backup.count} in the backup. On the pages the backup covers, " +
                    "settings it does not mention go back to their defaults.$skipped"
            )
            .setPositiveButton("Import") { _, _ ->
                SettingsBackupStore.import(this, backup)
                result = "Imported ${backup.count} settings." +
                    if (backup.skipped == 0) "" else " ${backup.skipped} entries were ignored."
                toast("Settings imported")
                // The theme or the accent may have changed, so this page is drawn again with them.
                rebuild()
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, this) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_SAVE -> writeFile(uri)
            REQ_OPEN -> readFile(uri)
        }
    }

    private companion object {
        const val REQ_SAVE = 1
        const val REQ_OPEN = 2
    }
}
