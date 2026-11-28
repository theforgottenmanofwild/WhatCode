package com.whatcode.files

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/** The file is not text (an image, a zip, an app). Its message is written to be shown to the person as it is. */
class NotTextException : IOException("That file isn't text, so it can't be opened here")

/** A read that was stopped by the person. Its message is written to be shown as it is. */
class ReadCancelled : IOException("Cancelled")

/**
 * Reads and writes the text of a file. Both directions pass every character through untouched, so a file saved
 * again has the same line endings (and every other byte of its text) as the one that was opened. Nothing here may
 * ever convert `\r\n`, `\r` or `\n`. Plain logic, no Android.
 */
object TextFile {

    /** How much of the start of a file is looked at for a zero byte, which text never has and images, zips and apps do. */
    private const val PROBE = 8000

    /** How many characters are read or written between two looks at the progress and at the request to stop. */
    const val CHUNK = 64 * 1024

    /** A file is never read into a buffer made ahead of time for more characters than this (a wrong size must not take all the memory). */
    private const val MAX_AHEAD = 400_000_000L

    /**
     * All of [input] as text, closing it. Throws [NotTextException] for a file that is not text, so it is never saved
     * over, and [ReadCancelled] when [cancelled] says so (it is asked before each piece of [chunk] characters).
     *
     * [sizeHint] is how many bytes the file has, or -1 when that is not known. With it the text is read straight into
     * one buffer of the right size instead of one that grows by copying itself. A wrong hint costs nothing but time.
     * [progress] gets the number of bytes read so far after each piece. It may be called from any thread.
     */
    fun read(
        input: InputStream,
        sizeHint: Long = -1,
        progress: (Long) -> Unit = {},
        cancelled: () -> Boolean = { false },
        chunk: Int = CHUNK,
    ): String {
        val counting = CountingInputStream(input)
        val stream = BufferedInputStream(counting, PROBE + 192)
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
            if (!looksLikeText(head, size)) throw NotTextException()
            val reader = InputStreamReader(it, Charsets.UTF_8)
            // A byte makes at most one character, so the size in bytes is room enough; one more lets the end be seen.
            var buf = CharArray(if (sizeHint in 1..MAX_AHEAD) sizeHint.toInt() + 1 else chunk.coerceAtLeast(16))
            var n = 0
            while (true) {
                if (cancelled()) throw ReadCancelled()
                if (n == buf.size) buf = buf.copyOf(grown(buf.size))
                val got = reader.read(buf, n, minOf(chunk, buf.size - n))
                if (got < 0) break
                n += got
                progress(counting.count)
            }
            String(buf, 0, n)
        }
    }

    private fun grown(size: Int): Int {
        if (size >= Int.MAX_VALUE - 16) throw IOException("The file is too large to open")
        return (size.toLong() + size / 2 + 16).coerceAtMost((Int.MAX_VALUE - 16).toLong()).toInt()
    }

    /** False when the first [size] bytes of a file, [head], hold a zero byte, which text never has. */
    fun looksLikeText(head: ByteArray, size: Int): Boolean {
        for (i in 0 until minOf(size, head.size)) if (head[i] == 0.toByte()) return false
        return true
    }

    /** True when [input] looks like text from its first bytes, the same check [read] makes. Closes it. */
    fun isText(input: InputStream): Boolean = input.use {
        val head = ByteArray(PROBE)
        var size = 0
        while (size < PROBE) {
            val n = it.read(head, size, PROBE - size)
            if (n < 0) break
            size += n
        }
        looksLikeText(head, size)
    }

    /**
     * Writes [text] to [output] as it is, closing it, [chunk] characters at a time. [progress] gets the number of
     * characters written so far after each piece. It may be called from any thread. A piece may end between the two
     * halves of an emoji; the writer keeps the first half until the second comes.
     */
    fun write(output: OutputStream, text: String, progress: (Int) -> Unit = {}, chunk: Int = CHUNK) {
        output.bufferedWriter(Charsets.UTF_8).use { w ->
            var i = 0
            while (i < text.length) {
                val n = minOf(chunk.coerceAtLeast(1), text.length - i)
                w.write(text, i, n)
                i += n
                progress(i)
            }
        }
    }

    /** Counts the bytes that pass through, for the progress of a read. */
    private class CountingInputStream(private val inner: InputStream) : InputStream() {
        @Volatile
        var count = 0L
            private set

        override fun read(): Int = inner.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) count += it }

        override fun available(): Int = inner.available()

        override fun close() = inner.close()
    }
}
