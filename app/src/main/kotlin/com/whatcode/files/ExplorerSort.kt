package com.whatcode.files

/**
 * The order of the file explorer's list, and the short size shown under a file name. No Android here (unit tested).
 */
object ExplorerSort {

    /** One file or folder in the explorer. [size] is in bytes (0 for a folder), [modified] in milliseconds. */
    class Entry(val name: String, val path: String, val isDir: Boolean, val size: Long = 0L, val modified: Long = 0L)

    /** Folders first, then files, each by name: case is ignored and numbers count as numbers (`file2` before `file10`). */
    val order: Comparator<Entry> = Comparator { a, b ->
        if (a.isDir != b.isDir) {
            if (a.isDir) -1 else 1
        } else {
            compareNames(a.name, b.name)
        }
    }

    fun sort(entries: Collection<Entry>): List<Entry> = entries.sortedWith(order)

    /** Compares two names as people read them: case is ignored and runs of digits are compared as numbers. */
    fun compareNames(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val ei = digitsEnd(a, i)
                val ej = digitsEnd(b, j)
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        if (rest != 0) return rest
        // Same when read: keep a fixed order, so "File" and "file" never swap places.
        return a.compareTo(b)
    }

    private fun digitsEnd(s: String, from: Int): Int {
        var k = from
        while (k < s.length && s[k].isDigit()) k++
        return k
    }

    /** A short size: `0 B`, `900 B`, `1.5 KB`, `12 KB`, `3.4 MB`, `2.0 GB`. */
    fun sizeLabel(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val text = if (value < 10) String.format(java.util.Locale.ROOT, "%.1f", value) else value.toLong().toString()
        return "$text ${units[unit]}"
    }
}
