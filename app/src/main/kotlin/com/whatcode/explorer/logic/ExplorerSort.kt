package com.whatcode.explorer.logic

/**
 * The order of the file explorer's list, and the short size shown under a file name. No Android here (unit tested).
 */
object ExplorerSort {

    /**
     * One file or folder in the explorer. [size] is in bytes (0 for a folder), [modified] in milliseconds. [count] is
     * how many things a folder holds, or -1 when it was not counted (a file, or the folder counts are off).
     */
    class Entry(
        val name: String,
        val path: String,
        val isDir: Boolean,
        val size: Long = 0L,
        val modified: Long = 0L,
        val count: Int = -1,
    )

    /**
     * How names are compared and where folders go. [foldersFirst] keeps folders above files; [natural] reads runs of
     * digits as numbers (`file2` before `file10`); [ignoreCase] puts `apple` and `Banana` in reading order.
     */
    class Order(val foldersFirst: Boolean = true, val natural: Boolean = true, val ignoreCase: Boolean = true)

    /** Folders first, then files, each by name: case is ignored and numbers count as numbers (`file2` before `file10`). */
    val order: Comparator<Entry> = Comparator { a, b ->
        if (a.isDir != b.isDir) {
            if (a.isDir) -1 else 1
        } else {
            compareNames(a.name, b.name)
        }
    }

    fun sort(entries: Collection<Entry>): List<Entry> = entries.sortedWith(order)

    /** How the explorer lays its list out: a name with size and date under it, a name alone, or tiles. */
    enum class Mode { DETAILED, COMPACT, GRID }

    /** What the list is ordered by. */
    enum class Key { NAME, SIZE, DATE, TYPE }

    /** True for a name that starts with a dot, which is how files are hidden on the phone's storage. */
    fun isHidden(name: String): Boolean = name.startsWith(".")

    /** The part of a name after its last dot, in lower case; empty for no dot, a name that ends in one, or a dotfile. */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) "" else name.substring(dot + 1).lowercase(java.util.Locale.ROOT)
    }

    /**
     * The list as the explorer shows it: [entries] without the hidden ones unless [showHidden], ordered by [key]
     * (or by name for what [key] cannot tell apart), reversed when [descending]. With [Order.foldersFirst] folders stay
     * before files either way, so turning the order round never buries them. Newest and biggest come first when
     * descending. [order] says how names are compared.
     */
    fun arrange(
        entries: Collection<Entry>,
        key: Key,
        descending: Boolean,
        showHidden: Boolean,
        order: Order = Order(),
    ): List<Entry> {
        val kept = if (showHidden) entries else entries.filter { !isHidden(it.name) }
        val byName = Comparator<Entry> { a, b -> compareNames(a.name, b.name, order.natural, order.ignoreCase) }
        val by: Comparator<Entry> = when (key) {
            Key.NAME -> byName
            Key.SIZE -> compareBy<Entry> { if (it.isDir) 0L else it.size }.then(byName)
            Key.DATE -> compareBy<Entry> { it.modified }.then(byName)
            Key.TYPE -> compareBy<Entry> { if (it.isDir) "" else extension(it.name) }.then(byName)
        }
        val directed = if (descending) by.reversed() else by
        if (!order.foldersFirst) return kept.sortedWith(directed)
        val foldersFirst = Comparator<Entry> { a, b -> if (a.isDir == b.isDir) 0 else if (a.isDir) -1 else 1 }
        return kept.sortedWith(foldersFirst.then(directed))
    }

    /**
     * Compares two names as people read them: case is ignored unless [ignoreCase] is false, and runs of digits are
     * compared as numbers unless [natural] is false. Two names that only differ in case keep a fixed order.
     */
    fun compareNames(a: String, b: String, natural: Boolean = true, ignoreCase: Boolean = true): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (natural && ca.isDigit() && cb.isDigit()) {
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
                val c = if (ignoreCase) ca.lowercaseChar().compareTo(cb.lowercaseChar()) else ca.compareTo(cb)
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

    /**
     * A short size: `0 B`, `900 B`, `1.5 KB`, `12 KB`, `3.4 MB`, `2.0 GB`. With [decimal] a kilobyte is 1000 bytes
     * (`1.5 kB`), as storage makers count; otherwise it is 1024 bytes, as the phone's file manager counts.
     */
    fun sizeLabel(bytes: Long, decimal: Boolean = false): String {
        val step = if (decimal) 1000.0 else 1024.0
        if (bytes < step) return "${bytes.coerceAtLeast(0)} B"
        val units = if (decimal) arrayOf("kB", "MB", "GB", "TB") else arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / step
        var unit = 0
        while (value >= step && unit < units.lastIndex) {
            value /= step
            unit++
        }
        val text = if (value < 10) String.format(java.util.Locale.ROOT, "%.1f", value) else value.toLong().toString()
        return "$text ${units[unit]}"
    }
}
