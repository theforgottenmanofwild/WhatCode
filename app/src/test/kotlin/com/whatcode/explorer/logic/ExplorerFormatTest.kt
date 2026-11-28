package com.whatcode.explorer.logic

import com.whatcode.explorer.logic.ExplorerFormat.DateStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class ExplorerFormatTest {

    private val utc = TimeZone.getTimeZone("UTC")

    /** 2026-03-12 14:05:00 UTC. */
    private val now = 1773324300000L
    private val min = 60_000L
    private val hour = 60 * min
    private val day = 24 * hour

    private fun relative(modified: Long, hour24: Boolean = true) =
        ExplorerFormat.date(modified, now, DateStyle.RELATIVE, hour24, utc, Locale.ENGLISH)

    private fun absolute(modified: Long, hour24: Boolean) =
        ExplorerFormat.date(modified, now, DateStyle.ABSOLUTE, hour24, utc, Locale.ENGLISH)

    @Test
    fun absoluteDatesHaveTheDayAndTheTimeIn12Or24Hours() {
        assertEquals("12 Mar 2026, 14:05", absolute(now, hour24 = true))
        assertEquals("12 Mar 2026, 2:05 PM", absolute(now, hour24 = false))
    }

    @Test
    fun relativeDatesSayHowLongAgo() {
        assertEquals("Just now", relative(now - 30_000))
        assertEquals("5 min ago", relative(now - 5 * min))
        assertEquals("3 h ago", relative(now - 3 * hour))
        assertEquals("Yesterday, 23:00", relative(now - 15 * hour - 5 * min))
        assertEquals("Yesterday, 11:00 PM", relative(now - 15 * hour - 5 * min, hour24 = false))
        assertEquals("4 days ago", relative(now - 4 * day))
    }

    @Test
    fun relativeDatesFallBackToTheDateAfterAWeekOrInTheFuture() {
        assertEquals("1 Mar 2026, 14:05", relative(now - 11 * day))
        assertEquals("13 Mar 2026, 14:05", relative(now + day))
    }

    @Test
    fun yesterdayFollowsTheCalendarOfTheTimeZone() {
        // 13:00 the day before is more than 24 hours ago in hours, but it is still yesterday.
        assertEquals("Yesterday, 13:00", relative(now - 25 * hour - 5 * min))
        // Ten past midnight today is today, even though it was more than 12 hours ago.
        assertEquals("13 h ago", relative(now - 13 * hour - 55 * min))
        val kolkata = TimeZone.getTimeZone("Asia/Kolkata")
        // 14:05 UTC is 19:35 in Kolkata, and 20:00 UTC the day before was 01:30 that same day there.
        assertEquals("18 h ago", ExplorerFormat.date(now - 18 * hour - 5 * min, now, DateStyle.RELATIVE, true, kolkata, Locale.ENGLISH))
    }

    @Test
    fun anUnknownDateIsEmpty() {
        assertEquals("", relative(0))
        assertEquals("", absolute(-1, true))
    }

    @Test
    fun itemCounts() {
        assertEquals("", ExplorerFormat.count(-1))
        assertEquals("0 items", ExplorerFormat.count(0))
        assertEquals("1 item", ExplorerFormat.count(1))
        assertEquals("1200 items", ExplorerFormat.count(1200))
    }

    @Test
    fun extensionsAreHiddenOnlyFromFilesWithOne() {
        assertEquals("Main", ExplorerFormat.name("Main.kt", false, showExtension = false))
        assertEquals("archive.tar", ExplorerFormat.name("archive.tar.gz", false, showExtension = false))
        assertEquals(".gitignore", ExplorerFormat.name(".gitignore", false, showExtension = false))
        assertEquals("name.", ExplorerFormat.name("name.", false, showExtension = false))
        assertEquals("README", ExplorerFormat.name("README", false, showExtension = false))
        assertEquals("my.folder", ExplorerFormat.name("my.folder", true, showExtension = false))
        assertEquals("Main.kt", ExplorerFormat.name("Main.kt", false, showExtension = true))
    }
}
