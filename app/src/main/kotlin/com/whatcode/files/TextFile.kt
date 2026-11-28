package com.whatcode.files

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The file is not text (an image, a zip, an app). Its message is written to be shown to the person as it is. */
class NotTextException : IOException("That file isn't text, so it can't be opened here")

/**
 * Reads and writes the text of a file. Both directions pass every character through untouched, so a file saved
 * again has the same line endings (and every other byte of its text) as the one that was opened. Nothing here may
 * ever convert `\r\n`, `\r` or `\n`. Plain logic, no Android.
 */
object TextFile {

    /** How much of the start of a file is looked at for a zero byte, which text never has and images, zips and apps do. */
    private const val PROBE = 8000

    /** All of [input] as text, closing it. Throws [NotTextException] for a file that is not text, so it is never saved over. */
    fun read(input: InputStream): String {
        val stream = BufferedInputStream(input, PROBE + 192)
        return stream.use {
            it.mark(PROBE)
            val head = ByteArray(PROBE)
            var size = 0
            while (size < PROBE) {
                val n = it.read(head, size, PROBE - size)
                if (n < 0) break
                size += n
            }
            it.reset()
            for (i in 0 until size) if (head[i] == 0.toByte()) throw NotTextException()
            it.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    /** Writes [text] to [output] as it is, closing it. */
    fun write(output: OutputStream, text: String) {
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }
}
