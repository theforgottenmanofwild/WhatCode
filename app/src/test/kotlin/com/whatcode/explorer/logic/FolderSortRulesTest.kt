package com.whatcode.explorer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderSortRulesTest {

    @Test
    fun storesLooksUpRoundTripsAndCapsFolderSortSpecs() {
        var map: Map<String, FolderSortRules.Spec> = emptyMap()
        val s1 = FolderSortRules.Spec(ExplorerSort.Key.DATE, descending = true, showHidden = false)
        map = FolderSortRules.put(map, "/storage/emulated/0/Logs/", s1)
        assertEquals(s1, FolderSortRules.get(map, "/storage/emulated/0/Logs"))

        val parsed = FolderSortRules.parse(FolderSortRules.write(map))
        assertEquals(s1, FolderSortRules.get(parsed, "/storage/emulated/0/Logs"))

        for (i in 1..(FolderSortRules.MAX_ENTRIES + 10)) {
            map = FolderSortRules.put(map, "/dir/$i", s1)
        }
        assertEquals(FolderSortRules.MAX_ENTRIES, map.size)
        assertNull(FolderSortRules.get(map, "/storage/emulated/0/Logs"))
    }
}
