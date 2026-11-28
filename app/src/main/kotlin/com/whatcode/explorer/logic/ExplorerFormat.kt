package com.whatcode.explorer.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The words under a name in the explorer's list: the date a file changed, a folder's item count, and the name itself
 * with or without its extension. No Android here (unit tested).
 */
object ExplorerFormat {

    /** How a date is written: how long ago (`5 min ago`, `Yesterday`), or as a date and time. */
    enum class DateStyle { RELATIVE, ABSOLUTE }

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /**
     * The date [modified] as the list shows it, seen at [now]. [hour24] picks `14:05` over `2:05 PM`. A relative date
     * says how long ago for the last week (`Just now`, `5 min ago`, `3 h ago`, `Yesterday, 14:05`, `4 days ago`) and
     * falls back to the date for anything older, or newer than [now]. An unknown date (0 or less) is empty.
     */
    fun date(
        modified: Long,
        now: Long,
        style: DateStyle,
        hour24: Boolean,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (modified <= 0) return ""
        val time = clock(modified, hour24, zone, locale)
        if (style == DateStyle.RELATIVE && modified <= now) {
            val ago = now - modified
            val days = dayNumber(now, zone) - dayNumber(modified, zone)
            when {
                ago < MINUTE -> return "Just now"
                ago < HOUR -> return "${ago / MINUTE} min ago"
                days == 0L -> return "${ago / HOUR} h ago"
                days == 1L -> return "Yesterday, $time"
                days < 7 -> return "$days days ago"
            }
        }
        val day = SimpleDateFormat("d MMM yyyy", locale).apply { timeZone = zone }.format(Date(modified))
        return "$day, $time"
    }

    private fun clock(at: Long, hour24: Boolean, zone: TimeZone, locale: Locale): String =
        SimpleDateFormat(if (hour24) "HH:mm" else "h:mm a", locale).apply { timeZone = zone }.format(Date(at))

    /** Days since 1970 on the calendar of [zone], so "yesterday" follows midnight and not 24 hours. */
    private fun dayNumber(at: Long, zone: TimeZone): Long = Math.floorDiv(at + zone.getOffset(at), 24 * HOUR)

    /** `1 item`, `12 items`; empty when the folder was not counted (a [count] below 0). */
    fun count(count: Int): String = when {
        count < 0 -> ""
        count == 1 -> "1 item"
        else -> "$count items"
    }

    /**
     * The name as the list shows it: a file without its extension unless [showExtension]. A folder, a name with no
     * dot, a dotfile (`.gitignore`) and a name that ends in a dot are never cut.
     */
    fun name(name: String, isDir: Boolean, showExtension: Boolean): String {
        if (showExtension || isDir) return name
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.lastIndex) name else name.substring(0, dot)
    }
}
