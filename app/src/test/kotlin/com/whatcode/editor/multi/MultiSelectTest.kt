package com.whatcode.editor.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MultiSelectTest {

    private fun s(a: Int, b: Int = a) = Sel(a, b)

    private fun apply(text: String, app: Applied): String = text.substring(0, app.start) + app.insert + text.substring(app.end)

    // ---------------------------------------------------------------- words and normalizing

    @Test
    fun theWordTouchingTheCursorIsFound() {
        assertEquals(s(0, 3), MultiSelect.wordAt("foo bar", 1))
        assertEquals(s(0, 3), MultiSelect.wordAt("foo bar", 3))
        assertEquals(s(4, 7), MultiSelect.wordAt("foo bar", 4))
        assertEquals(s(0, 5), MultiSelect.wordAt("a_1_b", 2))
        assertNull(MultiSelect.wordAt("a  b", 2))
        assertNull(MultiSelect.wordAt("", 0))
        assertNull(MultiSelect.wordAt("abc", 9))
    }

    @Test
    fun overlappingPlacesAreJoinedAndTouchingOnesAreNot() {
        val (list, p) = MultiSelect.normalize(listOf(s(5, 8), s(0, 3), s(2, 4), s(8, 8)), s(5, 8))
        assertEquals(listOf(s(0, 4), s(5, 8), s(8, 8)), list)
        assertEquals(1, p)
    }

    @Test
    fun twoCaretsAtTheSamePlaceBecomeOne() {
        val (list, _) = MultiSelect.normalize(listOf(s(3), s(3), s(1)), s(1))
        assertEquals(listOf(s(1), s(3)), list)
    }

    @Test
    fun aSelectionBackwardsIsRefused() {
        try {
            Sel(5, 2)
            org.junit.Assert.fail("expected a refusal")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---------------------------------------------------------------- finding occurrences

    @Test
    fun theNextOccurrenceComesAfterTheOnesTaken() {
        val t = "foo bar foo baz foo"
        assertEquals(s(8, 11), MultiSelect.nextOccurrence(t, "foo", 3, listOf(s(0, 3)), true, false))
        assertEquals(s(16, 19), MultiSelect.nextOccurrence(t, "foo", 11, listOf(s(0, 3), s(8, 11)), true, false))
    }

    @Test
    fun theSearchGoesRoundToTheStart() {
        val t = "foo bar foo baz foo"
        assertEquals(s(0, 3), MultiSelect.nextOccurrence(t, "foo", 19, listOf(s(16, 19)), true, false))
    }

    @Test
    fun noOccurrenceIsFoundWhenAllAreTaken() {
        val t = "foo bar foo"
        assertNull(MultiSelect.nextOccurrence(t, "foo", 11, listOf(s(0, 3), s(8, 11)), true, false))
        assertNull(MultiSelect.nextOccurrence(t, "zzz", 0, emptyList(), true, false))
        assertNull(MultiSelect.nextOccurrence(t, "", 0, emptyList(), true, false))
        assertNull(MultiSelect.nextOccurrence("fo", "foo", 0, emptyList(), true, false))
    }

    @Test
    fun anOccurrenceThatOverlapsATakenPlaceIsSkipped() {
        assertEquals(s(2, 4), MultiSelect.nextOccurrence("aaaa", "aa", 0, listOf(s(0, 2)), true, false))
    }

    @Test
    fun capitalsCountOrNotAsAsked() {
        assertEquals(s(4, 7), MultiSelect.nextOccurrence("Foo foo", "foo", 0, emptyList(), true, false))
        assertEquals(s(0, 3), MultiSelect.nextOccurrence("Foo foo", "foo", 0, emptyList(), false, false))
    }

    @Test
    fun wholeWordsSkipLongerWords() {
        assertEquals(s(7, 10), MultiSelect.nextOccurrence("foobar foo", "foo", 0, emptyList(), true, true))
        assertEquals(s(0, 3), MultiSelect.nextOccurrence("foobar foo", "foo", 0, emptyList(), true, false))
        assertEquals(s(0, 3), MultiSelect.nextOccurrence("foo_bar foo", "foo", 0, emptyList(), true, false))
        assertEquals(s(8, 11), MultiSelect.nextOccurrence("foo_bar foo", "foo", 0, emptyList(), true, true))
    }

    @Test
    fun everyOccurrenceIsFoundWithoutOverlaps() {
        assertEquals(listOf(s(0, 2), s(2, 4)), MultiSelect.allOccurrences("aaaa", "aa", true, false))
        assertEquals(listOf(s(0, 1), s(2, 3)), MultiSelect.allOccurrences("a a b", "a", true, false))
        assertEquals(emptyList<Sel>(), MultiSelect.allOccurrences("abc", "", true, false))
        assertEquals(3, MultiSelect.allOccurrences("a a a a a", "a", true, false, limit = 3).size)
    }

    @Test
    fun emojiAreMatchedWhole() {
        val t = "x\uD83D\uDE00 y\uD83D\uDE00"
        assertEquals(listOf(s(1, 3), s(5, 7)), MultiSelect.allOccurrences(t, "\uD83D\uDE00", true, false))
    }

    // ---------------------------------------------------------------- editing

    @Test
    fun typingGoesInAtEveryPlaceAsOneEdit() {
        val t = "a b c"
        val app = MultiSelect.insert(t, listOf(s(0, 1), s(2, 3), s(4, 5)), 0, "x")!!
        assertEquals(0, app.start)
        assertEquals(5, app.end)
        assertEquals("x x x", app.insert)
        assertEquals(listOf(s(1), s(3), s(5)), app.ranges)
    }

    @Test
    fun typingAtEmptyCursorsKeepsTheTextBetween() {
        val t = "ab"
        val app = MultiSelect.insert(t, listOf(s(0), s(2)), 1, "-")!!
        assertEquals("-ab-", apply(t, app))
        assertEquals(listOf(s(1), s(4)), app.ranges)
        assertEquals(1, app.primary)
    }

    @Test
    fun anEditThatChangesNothingIsNotMade() {
        assertNull(MultiSelect.insert("aa", listOf(s(0, 1), s(1, 2)), 0, "a"))
        assertNull(MultiSelect.insert("abc", emptyList(), 0, "a"))
    }

    @Test
    fun backspaceRemovesTheCharacterBeforeEachCursor() {
        val t = "abc"
        val app = MultiSelect.backspace(t, listOf(s(1), s(3)), 0)!!
        assertEquals("b", apply(t, app))
        assertEquals(listOf(s(0), s(1)), app.ranges)
    }

    @Test
    fun backspaceRemovesASelectionAndLeavesCursorsAtTheStartAlone() {
        val t = "abcd"
        assertEquals("d", apply(t, MultiSelect.backspace(t, listOf(s(0, 2), s(3)), 0)!!))
        assertEquals("a", apply("ab", MultiSelect.backspace("ab", listOf(s(0), s(2)), 1)!!))
        assertNull(MultiSelect.backspace("ab", listOf(s(0), s(0)), 0))
    }

    @Test
    fun backspaceRemovesAWholeEmoji() {
        val t = "a\uD83D\uDE00"
        assertEquals("a", apply(t, MultiSelect.backspace(t, listOf(s(3)), 0)!!))
        val two = "a\uD83D\uDE00 b\uD83D\uDE00"
        assertEquals("a b", apply(two, MultiSelect.backspace(two, listOf(s(3), s(7)), 0)!!))
    }

    @Test
    fun backspaceOfCursorsNextToEachOtherLeavesTwoCursors() {
        val app = MultiSelect.backspace("abc", listOf(s(1), s(2)), 0)!!
        assertEquals("c", apply("abc", app))
        assertEquals(listOf(s(0), s(0)), app.ranges)
    }

    @Test
    fun deleteRemovesTheCharacterAfterEachCursor() {
        val t = "abc"
        assertEquals("b", apply(t, MultiSelect.deleteForward(t, listOf(s(0), s(2)), 0)!!))
        assertNull(MultiSelect.deleteForward("ab", listOf(s(2)), 0))
    }

    @Test
    fun severalCharactersCanBeRemovedBeforeACursor() {
        assertEquals("d", apply("abcd", MultiSelect.backspace("abcd", listOf(s(3)), 0, count = 3)!!))
    }

    @Test
    fun enterKeepsTheIndentationOfEachLine() {
        val t = "  a\n\tb"
        val app = MultiSelect.newline(t, listOf(s(3), s(6)), 0)!!
        assertEquals("\n  \n\tb\n\t", app.insert)
    }

    @Test
    fun enterInTheMiddleOfTheIndentationCopiesOnlyWhatIsBeforeTheCursor() {
        val t = "    x"
        val app = MultiSelect.newline(t, listOf(s(2), s(5)), 0)!!
        assertEquals("\n  " + "  x" + "\n    ", app.insert)
    }

    @Test
    fun enterKeepsACrLfLineEnding() {
        val t = "a\r\nb"
        val app = MultiSelect.newline(t, listOf(s(1), s(4)), 0)!!
        assertEquals("\r\n" + "\r\nb" + "\n", app.insert)
    }

    @Test
    fun differentTextCanGoInAtEachPlace() {
        val app = MultiSelect.replace("a b", listOf(s(0, 1), s(2, 3)), 0) { "n$it" }!!
        assertEquals("n0 n1", app.insert)
    }

    @Test
    fun anEditThatCoversTooMuchTextIsRefused() {
        val t = "a".repeat(MultiSelect.MAX_SPAN + 10)
        assertNull(MultiSelect.insert(t, listOf(s(0, 1), s(MultiSelect.MAX_SPAN + 5, MultiSelect.MAX_SPAN + 6)), 0, "b"))
        assertNotNull(MultiSelect.insert(t, listOf(s(0, 1), s(10, 11)), 0, "b"))
    }

    // ---------------------------------------------------------------- clipboard

    @Test
    fun theSelectedTextsAreJoinedWithLineBreaks() {
        assertEquals("a\nc", MultiSelect.joined("abc", listOf(s(0, 1), s(2, 3))))
    }

    @Test
    fun aPasteWithAsManyLinesAsCursorsIsSharedOut() {
        assertEquals(listOf("x", "y"), MultiSelect.splitForPaste("x\ny\n", 2))
        assertEquals(listOf("x", "y"), MultiSelect.splitForPaste("x\r\ny", 2))
        assertNull(MultiSelect.splitForPaste("x\ny", 3))
        assertNull(MultiSelect.splitForPaste("x\ny", 1))
        assertNull(MultiSelect.splitForPaste("x", 2))
    }

    // ---------------------------------------------------------------- moving

    @Test
    fun everyCursorStepsTheSameWay() {
        val t = "ab\ncd\nef"
        val (list, p) = MultiSelect.move(t, listOf(s(1), s(4), s(7)), 1, false) { text, pos -> com.whatcode.editor.EditOps.prevIndex(text, pos) }
        assertEquals(listOf(s(0), s(3), s(6)), list)
        assertEquals(1, p)
    }

    @Test
    fun aSelectionIsDroppedAtTheSideItIsMovedTo() {
        val t = "foo bar"
        val left = MultiSelect.move(t, listOf(s(0, 3), s(4, 7)), 0, false) { _, pos -> pos }
        assertEquals(listOf(s(0), s(4)), left.first)
        val right = MultiSelect.move(t, listOf(s(0, 3), s(4, 7)), 0, true) { _, pos -> pos }
        assertEquals(listOf(s(3), s(7)), right.first)
    }

    @Test
    fun cursorsThatMeetBecomeOneAndTheEdgesHold() {
        val (list, _) = MultiSelect.move("abc", listOf(s(0), s(1)), 1, false) { text, pos -> com.whatcode.editor.EditOps.prevIndex(text, pos) }
        assertEquals(listOf(s(0)), list)
        val (end, _) = MultiSelect.move("abc", listOf(s(2), s(3)), 0, true) { text, pos -> com.whatcode.editor.EditOps.nextIndex(text, pos) }
        assertEquals(listOf(s(3)), end)
    }
}
