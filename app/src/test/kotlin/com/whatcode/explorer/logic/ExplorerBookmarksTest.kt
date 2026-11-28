package com.whatcode.explorer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerBookmarksTest {

    @Test
    fun togglesRebasesAndRoundTripsBookmarks() {
        var list = emptyList<ExplorerBookmarks.Bookmark>()
        val (added, wasAdded) = ExplorerBookmarks.toggleBookmark(list, "/sdcard/Projects//app")
        assertTrue(wasAdded)
        assertEquals(1, added.size)
        assertEquals("/sdcard/Projects/app", added[0].path)
        assertEquals("app", added[0].label)

        val rebased = ExplorerBookmarks.rebaseBookmarks(added, "/sdcard/Projects", "/sdcard/Work")
        assertEquals("/sdcard/Work/app", rebased[0].path)

        val parsed = ExplorerBookmarks.parseBookmarks(ExplorerBookmarks.writeBookmarks(rebased))
        assertEquals(rebased, parsed)

        val (removed, nowAdded) = ExplorerBookmarks.toggleBookmark(parsed, "/sdcard/Work/app")
        assertFalse(nowAdded)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun recordsDeduplicatesAndCapsRecentFiles() {
        var recent = emptyList<ExplorerBookmarks.RecentFile>()
        recent = ExplorerBookmarks.recordRecent(recent, "/a/one.kt", "one.kt", 10L)
        recent = ExplorerBookmarks.recordRecent(recent, "/a/two.kt", "two.kt", 20L)
        recent = ExplorerBookmarks.recordRecent(recent, "/a/one.kt", "one.kt", 30L)
        assertEquals(listOf("/a/one.kt", "/a/two.kt"), recent.map { it.path })

        val round = ExplorerBookmarks.parseRecent(ExplorerBookmarks.writeRecent(recent))
        assertEquals(recent, round)

        val pruned = ExplorerBookmarks.removeRecent(round, "/a")
        assertTrue(pruned.isEmpty())
    }
}
