package com.whatcode.files

import com.whatcode.explorer.logic.ExplorerSort

/** Decides when opening or saving a file is done off the main thread, and words its progress. No Android here. */
object IoPlan {

    /** A file of this many bytes (or a text of this many characters) or more is read or written in the background. */
    const val BACKGROUND_FROM = 1_000_000L

    /** How long a slow job runs before the progress box shows, so a quick one never flashes it. */
    const val SHOW_AFTER_MS = 250L

    /** [size] is the number of bytes, or -1 when the provider does not say, which could be anything. */
    fun readInBackground(size: Long): Boolean = size < 0 || size >= BACKGROUND_FROM

    fun writeInBackground(chars: Int): Boolean = chars >= BACKGROUND_FROM

    /** 0 to 100, or -1 when the total is not known (the bar then just moves). */
    fun percent(done: Long, total: Long): Int =
        if (total <= 0) -1 else (done.coerceIn(0, total).toDouble() / total * 100).toInt()

    /** "12.3 MB of 40.1 MB", or just the first part when the total is not known. */
    fun describe(done: Long, total: Long): String {
        val d = ExplorerSort.sizeLabel(done.coerceAtLeast(0), decimal = false)
        return if (total <= 0) d else "$d of ${ExplorerSort.sizeLabel(total, decimal = false)}"
    }
}
