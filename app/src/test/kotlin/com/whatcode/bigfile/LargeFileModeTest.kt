package com.whatcode.bigfile

import com.whatcode.editor.BracketMatch
import com.whatcode.editor.EditHistory
import com.whatcode.editor.Highlighter
import com.whatcode.editor.TagMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFileModeTest {

    private val config = LargeFileConfig()

    @Test
    fun theDefaultsAreTheSizesOfTheTwoTiers() {
        assertEquals(300_000, config.largeAbove)
        assertEquals(2_000_000, config.veryLargeAbove)
        assertEquals(Saving.WRAP.bit or Saving.MATCHING.bit, config.tier1Off)
        assertEquals(Saving.WHITESPACE.bit or Saving.UNDO.bit, config.tier2Off)
    }

    @Test
    fun aTextExactlyAsLongAsASizeIsStillBelowIt() {
        assertEquals(Tier.NONE, config.tierOf(0))
        assertEquals(Tier.NONE, config.tierOf(300_000))
        assertEquals(Tier.LARGE, config.tierOf(300_001))
        assertEquals(Tier.LARGE, config.tierOf(2_000_000))
        assertEquals(Tier.VERY_LARGE, config.tierOf(2_000_001))
        assertEquals(Tier.VERY_LARGE, config.tierOf(Int.MAX_VALUE))
    }

    @Test
    fun switchedOffNothingIsEverLarge() {
        val off = config.copy(enabled = false)
        assertEquals(Tier.NONE, off.tierOf(50_000_000))
        assertSame(Plan.NONE, off.planFor(50_000_000))
    }

    @Test
    fun anOrdinaryFileTurnsNothingOffAndKeepsTheNormalUndo() {
        val plan = config.planFor(1000)
        assertFalse(plan.active)
        assertEquals(0, plan.off)
        assertEquals(EditHistory.MAX_EDITS, plan.undoSteps)
        assertEquals(EditHistory.MAX_EDITS, LargeFileConfig.NORMAL_UNDO_STEPS)
    }

    @Test
    fun aLargeFileTurnsOffWrapAndFarMatchingOnly() {
        val plan = config.planFor(500_000)
        assertEquals(Tier.LARGE, plan.tier)
        assertTrue(plan.isOff(Saving.WRAP))
        assertTrue(plan.isOff(Saving.MATCHING))
        assertFalse(plan.isOff(Saving.WHITESPACE))
        assertFalse(plan.isOff(Saving.UNDO))
        assertEquals(1000, plan.undoSteps)
    }

    @Test
    fun aVeryLargeFileTurnsOffWhatTheLargeOneDoesAndMore() {
        val plan = config.planFor(3_000_000)
        assertEquals(Tier.VERY_LARGE, plan.tier)
        for (s in Saving.entries) assertTrue("$s", plan.isOff(s))
        assertEquals(200, plan.undoSteps)
    }

    @Test
    fun theChecklistsDecideWhatIsOff() {
        val none = config.copy(tier1Off = 0, tier2Off = 0)
        assertEquals(0, none.planFor(500_000).off)
        assertEquals(0, none.planFor(5_000_000).off)
        assertEquals(1000, none.planFor(5_000_000).undoSteps)
        val onlyUndo = config.copy(tier1Off = 0, tier2Off = Saving.UNDO.bit, undoSteps = 50)
        assertEquals(0, onlyUndo.planFor(500_000).off)
        assertEquals(50, onlyUndo.planFor(5_000_000).undoSteps)
    }

    @Test
    fun aBitNoOneKnowsIsIgnoredAndTheUndoIsKeptInRange() {
        assertEquals(Saving.WRAP.bit, config.copy(tier1Off = 0x7000 or Saving.WRAP.bit).planFor(400_000).off)
        assertEquals(10, config.copy(tier2Off = Saving.UNDO.bit, undoSteps = 1).planFor(5_000_000).undoSteps)
        assertEquals(1000, config.copy(tier2Off = Saving.UNDO.bit, undoSteps = 99_999).planFor(5_000_000).undoSteps)
    }

    @Test
    fun aVeryLargeSizeBelowTheLargeOneIsReadAsTheSameSize() {
        val odd = config.copy(largeAbove = 500_000, veryLargeAbove = 100_000)
        assertEquals(Tier.NONE, odd.tierOf(500_000))
        assertEquals(Tier.VERY_LARGE, odd.tierOf(500_001))
    }

    @Test
    fun theBitsOfTheSavedChoicesNeverChange() {
        assertEquals(listOf(1, 2, 4, 8), Saving.entries.map { it.bit })
        assertEquals(15, LargeFileConfig.ALL_BITS)
    }

    @Test
    fun theExplanationSaysOnlyWhatIsReallyOff() {
        val large = LargeFileText.explain(config.planFor(500_000), 500_000, config)
        assertTrue(large.contains("500,000 characters"))
        assertTrue(large.contains("where large files start"))
        assertTrue(large.contains("Word wrap is off"))
        assertTrue(large.contains("matched only when both ends are on the screen"))
        assertFalse(large.contains("Whitespace"))
        assertFalse(large.contains("Undo"))
        val very = LargeFileText.explain(config.planFor(3_000_000), 3_000_000, config)
        assertTrue(very.contains("where very large files start"))
        assertTrue(very.contains("Whitespace marks are not drawn"))
        assertTrue(very.contains("Undo keeps the last 200 steps"))
        assertTrue(very.contains("Settings > Large files"))
        assertEquals("This file is not large, so nothing is turned off.", LargeFileText.explain(Plan.NONE, 10, config))
    }

    // ------------------------------------------------------------------ TextWindow

    @Test
    fun aWindowReadsLikeATextOfItsOwn() {
        val w = TextWindow("0123456789", 3, 7)
        assertEquals(4, w.length)
        assertEquals('3', w[0])
        assertEquals('6', w[3])
        assertEquals("3456", w.toString())
        assertEquals("45", w.subSequence(1, 3).toString())
        assertEquals(0, TextWindow("abc", 2, 2).length)
        assertEquals("", TextWindow("", 0, 0).toString())
    }

    @Test
    fun aWindowRefusesWhatLiesOutsideIt() {
        val w = TextWindow("0123456789", 3, 7)
        assertThrows(IndexOutOfBoundsException::class.java) { w[4] }
        assertThrows(IndexOutOfBoundsException::class.java) { w[-1] }
        assertThrows(IndexOutOfBoundsException::class.java) { w.subSequence(2, 9) }
        assertThrows(IllegalArgumentException::class.java) { TextWindow("abc", 2, 9) }
        assertThrows(IllegalArgumentException::class.java) { TextWindow("abc", 2, 1) }
    }

    @Test
    fun bracketsInAWindowPairLikeInTheWholeTextWhenBothEndsAreInside() {
        val text = "x".repeat(50) + "f(a, (b), c)" + "y".repeat(50)
        val lang = Highlighter.languageFor("a.js")
        val whole = BracketMatch.find(text, 51, lang)!!
        val w = TextWindow(text, 40, 80)
        val inside = BracketMatch.find(w, 51 - 40, lang)!!
        assertEquals(whole.open, inside.open + 40)
        assertEquals(whole.close, inside.close + 40)
    }

    @Test
    fun aBracketWhosePartnerIsOutsideTheWindowIsNotMatched() {
        val text = "(" + "x".repeat(100) + ")"
        val lang = Highlighter.languageFor("a.js")
        assertTrue(BracketMatch.find(text, 1, lang) != null)
        val w = TextWindow(text, 0, 50)
        assertEquals(null, BracketMatch.find(w, 1, lang))
    }

    @Test
    fun aTagInAWindowIsMatchedWithOffsetsThatShiftBack() {
        val text = "z".repeat(30) + "<div><b>hi</b></div>" + "z".repeat(30)
        val lang = Highlighter.languageFor("a.html")
        val whole = TagMatch.find(text, 31, lang)!!
        val w = TextWindow(text, 20, 70)
        val inside = TagMatch.find(w, 31 - 20, lang)!!
        assertEquals(whole.openStart, inside.openStart + 20)
        assertEquals(whole.closeEnd, inside.closeEnd + 20)
    }

    @Test
    fun undoStepsCanBeLoweredAndTheOldestAreDropped() {
        val h = EditHistory()
        var t = 0L
        for (i in 0 until 50) { h.record(i, "", "x", t); t += 5_000 }
        assertEquals(50, h.undoCount)
        h.limitEdits(20)
        assertEquals(20, h.undoCount)
        h.record(0, "", "y", t + 5_000)
        assertEquals(20, h.undoCount)
        h.limitEdits(1000)
        h.record(0, "", "y", t + 20_000)
        assertEquals(21, h.undoCount)
    }
}
