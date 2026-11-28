package com.whatcode.files

import android.content.ActivityNotFoundException
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.whatcode.explorer.logic.ExplorerSort
import java.io.File
import java.io.FileNotFoundException

/**
 * Lets another app read one file the person chose to open with it, or to share. The provider is not exported: an
 * app can only read a file whose address it was handed with a read grant ([viewIntent]), and only that file, and only
 * to read it. Nothing can be written through it.
 */
class SharedFiles : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun fileOf(uri: Uri): File {
        val path = uri.path ?: throw FileNotFoundException("No file")
        val file = File(path)
        if (!file.isFile) throw FileNotFoundException("No such file")
        return file
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Files are shared to be read only")
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = mimeType(uri.lastPathSegment.orEmpty())

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val file = fileOf(uri)
        val columns = projection?.filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else file.name }.toTypedArray())
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    companion object {
        /** The address another app reads [file] at. */
        fun uriFor(context: Context, file: File): Uri =
            Uri.Builder().scheme("content").authority(context.packageName + ".files").path(file.absolutePath).build()

        /** The type of a file from the ending of its [name], for the apps that can open it. */
        fun mimeType(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ExplorerSort.extension(name)) ?: "application/octet-stream"

        /**
         * Asks which app should open [file], with a grant to read it. Returns false when no app can, so the caller
         * can say so.
         */
        fun openWith(context: Context, file: File): Boolean {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(context, file), mimeType(file.name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return start(context, Intent.createChooser(view, "Open ${file.name} with"))
        }

        private fun start(context: Context, intent: Intent): Boolean = try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
