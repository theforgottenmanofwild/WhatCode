package com.whatcode.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.io.OutputStream

/** Reads and writes the documents the editor opens, by address, through the system's content resolver. */
internal class DocumentIo(private val context: Context) {

    /** The name the provider gives the file, or null if it gives none. */
    fun displayName(u: Uri): String? = try {
        context.contentResolver.query(u, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    /** The text of the file. Throws if it can't be read. */
    fun read(u: Uri): String {
        val input = context.contentResolver.openInputStream(u) ?: throw IOException("The file could not be opened")
        return TextFile.read(input)
    }

    /** Opens [u] for writing and replacing what is in it. Some providers return nothing instead of failing. */
    fun openForWrite(u: Uri): OutputStream =
        context.contentResolver.openOutputStream(u, "wt") ?: throw IOException("The file could not be opened for writing")
}

/** What to tell the person about a failure: the system's message, or the kind of error when it has none. */
internal fun reason(e: Throwable): String = e.message ?: e.javaClass.simpleName
