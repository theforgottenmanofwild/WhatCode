package com.whatcode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The undo rules work on plain text and numbers, so they can be checked without a device. */
class EditHistoryTest {

    /** A text that applies the steps the way the editor does, so undo and redo can be checked end to end. */
    private class Doc(var text: String, val h: EditHistory = EditHistory()) {
        var now = 0L

        fun type(at: Int, s: String, gap: Long = 50) = replace(at, "", s, gap)

        fun replace(at: Int, removed: String, added: String, gap: Long = 50) {
            assertEquals(removed, text.substring(at, at + removed.length))
            text = text.substring(0, at) + added + text.substring(at + removed.length)
            now += gap
            h.record(at, removed, added, now)
        }

        fun undo(): Boolean {
            val e = h.takeUndo() ?: return false
            text = text.substring(0, e.start) + e.removed + text.substring(e.start + e.added.length)
            h.addRedo(e)
            return true
        }

        fun redo(): Boolean {
            val e = h.takeRedo() ?: return false
            text = text.substring(0, e.start) + e.added + text.substring(e.start + e.removed.length)
            h.addUndo(e)
            return true
        }
    }

    @Test
    fun typedLettersBecomeOneStep() {
        val d = Doc("")
        d.type(0, "a"); d.type(1, "b"); d.type(2, "c")
        assertEquals(1, d.h.undoCount)
        d.undo()
        assertEquals("", d.text)
        d.redo()
        assertEquals("abc", d.text)
    }

    @Test
    fun aPauseStartsANewStep() {
        val d = Doc("")
        d.type(0, "a"); d.type(1, "b", gap = 2000)
        assertEquals(2, d.h.undoCount)
    }

    @Test
    fun enterIsAStepOnItsOwn() {
        val d = Doc("ab")
        d.type(2, "c"); d.type(3, "\n")
        assertEquals(2, d.h.undoCount)
    }

    @Test
    fun aRunOfBackspacesIsOneStep() {
        val d = Doc("hello")
        d.replace(4, "o", ""); d.replace(3, "l", ""); d.replace(2, "l", "")
        assertEquals(1, d.h.undoCount)
        assertEquals("he", d.text)
        d.undo()
        assertEquals("hello", d.text)
    }

    @Test
    fun aRunOfForwardDeletesIsOneStep() {
        val d = Doc("hello")
        d.replace(1, "e", ""); d.replace(1, "l", ""); d.replace(1, "l", "")
        assertEquals(1, d.h.undoCount)
        assertEquals("ho", d.text)
        d.undo()
        assertEquals("hello", d.text)
        d.redo()
        assertEquals("ho", d.text)
    }

    @Test
    fun aKeyboardReplacingTheWordItComposesIsOneStep() {
        val d = Doc("x ")
        d.type(2, "h")
        d.replace(2, "h", "he"); d.replace(2, "he", "hel"); d.replace(2, "hel", "hell"); d.replace(2, "hell", "hello")
        assertEquals(1, d.h.undoCount)
        assertEquals("x hello", d.text)
        d.undo()
        assertEquals("x ", d.text)
        d.redo()
        assertEquals("x hello", d.text)
    }

    @Test
    fun aReplacementThatDoesNotEndTheTypedTextIsNotJoined() {
        val d = Doc("")
        d.type(0, "a"); d.type(1, "b"); d.type(2, "c")
        d.replace(0, "a", "A")
        assertEquals(2, d.h.undoCount)
        d.undo()
        assertEquals("abc", d.text)
        d.undo()
        assertEquals("", d.text)
    }

    @Test
    fun typingAfterAnUndoStartsANewStep() {
        val d = Doc("")
        d.type(0, "a")
        d.type(0, "Z") // a second step, at the front
        d.undo()
        assertEquals("a", d.text)
        d.type(1, "b") // would continue the first step if no undo had come in between
        assertEquals(2, d.h.undoCount)
        d.undo()
        assertEquals("a", d.text)
    }

    @Test
    fun aNewEditClearsRedo() {
        val d = Doc("")
        d.type(0, "a", gap = 2000)
        d.undo()
        assertTrue(d.h.canRedo)
        d.type(0, "b")
        assertFalse(d.h.canRedo)
    }

    @Test
    fun theOldestStepsGoWhenThereAreTooMany() {
        val h = EditHistory(maxEdits = 3)
        for (i in 0 until 10) h.record(0, "", "x$i", i * 5000L)
        assertEquals(3, h.undoCount)
        assertEquals("x9", h.takeUndo()!!.added)
    }

    @Test
    fun theOldestStepsGoWhenTheyHoldTooMuchText() {
        val h = EditHistory(maxChars = 100)
        for (i in 0 until 10) h.record(0, "", "y".repeat(40), i * 5000L)
        assertTrue(h.undoCount in 1..3)
        assertNotNull(h.takeUndo())
    }

    @Test
    fun oneHugeEditIsKeptEvenOverTheLimit() {
        val h = EditHistory(maxChars = 100)
        h.record(0, "", "z".repeat(1000), 0)
        assertEquals(1, h.undoCount)
    }

    @Test
    fun aSnapshotKeepsItsOwnStepsAndCanBeLoaded() {
        val d = Doc("")
        d.type(0, "a", gap = 2000)
        val snap = d.h.snapshot()
        d.type(1, "b", gap = 2000)
        d.undo(); d.undo()
        assertFalse(d.h.canUndo)
        d.h.load(snap)
        assertEquals(1, d.h.undoCount)
        assertFalse(d.h.canRedo)
        assertEquals("a", d.h.takeUndo()!!.added)
    }

    @Test
    fun aJoinedChangeIsUndoneAndRedoneWithTheOneBeforeIt() {
        val h = EditHistory()
        h.record(4, "", "x", 100)
        h.recordJoined(8, "div", "divx", 100)
        assertEquals(2, h.undoCount)
        val partner = h.takeUndo()!!
        assertTrue(partner.joined)
        h.addRedo(partner)
        val typed = h.takeUndo()!!
        assertFalse(typed.joined)
        h.addRedo(typed)
        assertFalse(h.canUndo)
        // Redo hands out the typed change first, and says the partner comes with it.
        assertEquals(4, h.takeRedo()!!.start)
        assertTrue(h.redoJoinsNext)
        assertEquals(8, h.takeRedo()!!.start)
        assertFalse(h.redoJoinsNext)
    }

    @Test
    fun aTagRenamedLetterByLetterIsOneStepWithItsPartner() {
        val h = EditHistory()
        // <div></div>: "x" is typed after the name and the closing name follows, then "y" the same way.
        h.record(4, "", "x", 100)
        h.recordJoined(8, "div", "divx", 100)
        h.record(5, "", "y", 150)
        h.recordJoined(9, "divx", "divxy", 150)
        assertEquals(2, h.undoCount)
        val partner = h.takeUndo()!!
        assertEquals(9, partner.start)
        assertEquals("div", partner.removed)
        assertEquals("divxy", partner.added)
        val typed = h.takeUndo()!!
        assertEquals(4, typed.start)
        assertEquals("xy", typed.added)
    }

    @Test
    fun aPauseKeepsRenamedTagsInSeparateSteps() {
        val h = EditHistory()
        h.record(4, "", "x", 100)
        h.recordJoined(8, "div", "divx", 100)
        h.record(5, "", "y", 5000)
        h.recordJoined(9, "divx", "divxy", 5000)
        assertEquals(4, h.undoCount)
    }

    @Test
    fun trimmingTheOldestStepsNeverLeavesAJoinedChangeWithoutItsFirstHalf() {
        val h = EditHistory(maxEdits = 3)
        for (i in 0 until 5) {
            h.record(i * 20, "", "x", i * 5000L)
            h.recordJoined(i * 20 + 10, "a", "b", i * 5000L)
        }
        // Every step that is left starts with a change a person made, so one undo never cuts a pair in half.
        val first = h.snapshot().undo.first()
        assertFalse(first.joined)
    }

    @Test
    fun nothingToUndoGivesNull() {
        val h = EditHistory()
        assertNull(h.takeUndo())
        assertNull(h.takeRedo())
    }

    @Test
    fun anEmptyChangeIsNotRecorded() {
        val h = EditHistory()
        h.record(3, "", "", 0)
        assertFalse(h.canUndo)
    }
}
