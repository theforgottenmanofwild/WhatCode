package com.whatcode.files

import java.io.InputStream
import java.io.OutputStream

/**
 * Reads and writes the text of a file. Both directions pass every character through untouched, so a file saved
 * again has the same line endings (and every other byte of its text) as the one that was opened. Nothing here may
 * ever convert `\r\n`, `\r` or `\n`. Plain logic, no Android.
 */
object TextFile {

    /** All of [input] as text, closing it. */
    fun read(input: InputStream): String = input.bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Writes [text] to [output] as it is, closing it. */
    fun write(output: OutputStream, text: String) {
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
