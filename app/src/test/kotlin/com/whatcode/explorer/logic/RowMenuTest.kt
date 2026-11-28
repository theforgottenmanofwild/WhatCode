package com.whatcode.explorer.logic

import com.whatcode.explorer.logic.RowMenu.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowMenuTest {

    @Test
    fun nothingSavedIsEveryActionShownInTheUsualOrder() {
        assertEquals(RowMenu.DEFAULT, RowMenu.parse(null))
        assertEquals(RowMenu.DEFAULT, RowMenu.parse(""))
        assertEquals(Action.values().toList(), RowMenu.DEFAULT.map { it.action })
        assertTrue(RowMenu.DEFAULT.all { it.shown })
    }

    @Test
    fun theOrderAndHiddenActionsAreReadBack() {
        val items = RowMenu.parse("delete,-share,rename")
        assertEquals(listOf(Action.DELETE, Action.SHARE, Action.RENAME), items.take(3).map { it.action })
        assertFalse(items[1].shown)
        // Actions the text does not name go at the end, shown.
        assertEquals(Action.values().size, items.size)
        assertTrue(items.drop(3).all { it.shown })
        assertEquals(items, RowMenu.parse(RowMenu.write(items)))
    }

    @Test
    fun unknownAndRepeatedIdsAreDropped() {
        val items = RowMenu.parse("copy,teleport,copy,-copy, move")
        assertEquals(listOf(Action.COPY, Action.MOVE), items.take(2).map { it.action })
        assertTrue(items[0].shown)
        assertFalse(RowMenu.isValid("copy,teleport"))
        assertFalse(RowMenu.isValid("copy,-copy"))
        assertTrue(RowMenu.isValid("copy,-move"))
        assertFalse(RowMenu.isValid(""))
    }

    @Test
    fun movingUpAndDownStopsAtTheEnds() {
        val items = RowMenu.DEFAULT
        assertEquals(Action.COPY, RowMenu.moved(items, 1, -1)[0].action)
        assertEquals(items, RowMenu.moved(items, 0, -1))
        assertEquals(items, RowMenu.moved(items, items.lastIndex, 1))
        assertEquals(Action.RENAME, RowMenu.moved(items, 0, 1)[1].action)
    }

    @Test
    fun aSelectionOfManyOnlyGetsWhatWorksOnMany() {
        val all = RowMenu.DEFAULT
        assertEquals(listOf(Action.COPY, Action.MOVE, Action.DELETE, Action.SHARE, Action.COMPRESS), RowMenu.actionsFor(all, 3, 0))
        assertEquals(listOf(Action.COPY, Action.MOVE, Action.DELETE, Action.COMPRESS), RowMenu.actionsFor(all, 3, 1))
        assertEquals(emptyList<Action>(), RowMenu.actionsFor(all, 0, 0))
    }

    @Test
    fun oneFolderHasNoShareOrOpenWith() {
        val folder = RowMenu.actionsFor(RowMenu.DEFAULT, 1, 1)
        assertFalse(Action.SHARE in folder)
        assertFalse(Action.OPEN_WITH in folder)
        assertFalse(Action.APK_INFO in folder)
        assertFalse(Action.BROWSE_CONTENTS in folder)
        assertTrue(Action.PROPERTIES in folder)
        assertEquals(Action.values().filter { !it.apkOnly && !it.archiveOnly }, RowMenu.actionsFor(RowMenu.DEFAULT, 1, 0))
        assertEquals(Action.values().filter { !it.apkOnly }, RowMenu.actionsFor(RowMenu.DEFAULT, 1, 0, isApk = false, isArchive = true))
        assertEquals(Action.values().toList(), RowMenu.actionsFor(RowMenu.DEFAULT, 1, 0, isApk = true))
    }

    @Test
    fun hiddenActionsAreLeftOut() {
        val items = RowMenu.parse("-rename,-delete")
        val actions = RowMenu.actionsFor(items, 1, 0)
        assertFalse(Action.RENAME in actions)
        assertFalse(Action.DELETE in actions)
        assertEquals(Action.COPY, actions.first())
    }
}
