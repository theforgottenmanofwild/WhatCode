package com.whatcode.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

class FindLogicTest {

    private fun pat(q: String, case: Boolean = false, word: Boolean = false, regex: Boolean = false) =
        FindLogic.buildPattern(q, case, word, regex)!!

    private fun starts(f: FindLogic.Found) = f.hits.map { it.start }

    @Test
    fun anEmptyOrBrokenQueryHasNoPattern() {
        assertNull(FindLogic.buildPattern("", false, false, false))
        assertNull(FindLogic.buildPattern("(", false, false, true))
        assertNotNull(FindLogic.buildPattern("(", false, false, false)) // plain text is quoted
    }

    @Test
    fun caseWholeWordAndRegexOptionsChangeWhatMatches() {
        val t = "Cat cat concat cat_x"
        assertEquals(listOf(0, 4, 11, 15), starts(FindLogic.findAll(t, pat("cat"))))
        assertEquals(listOf(4, 11, 15), starts(FindLogic.findAll(t, pat("cat", case = true))))
        assertEquals(listOf(0, 4), starts(FindLogic.findAll(t, pat("cat", word = true))))
        assertEquals(listOf(0, 3), starts(FindLogic.findAll("ab a1", pat("a\\w", regex = true))))
    }

    @Test
    fun emptyMatchesAreLeftOut() {
        assertTrue(FindLogic.findAll("abc", pat("x*", regex = true)).hits.isEmpty())
        assertEquals(listOf(1), starts(FindLogic.findAll("axb", pat("x*", regex = true))))
    }

    @Test
    fun offsetsCountUtf16UnitsSoAnEmojiIsTwo() {
        val f = FindLogic.findAll("a😀b😀", pat("😀"))
        assertEquals(listOf(1, 4), starts(f))
        assertEquals(listOf(3, 6), f.hits.map { it.end })
    }

    @Test
    fun lineStartsAndEndsWorkWithCrlfAndLf() {
        assertEquals(listOf(0, 3, 5), starts(FindLogic.findAll("a\r\na\na", pat("^a", regex = true))))
    }

    @Test
    fun theListStopsAtTheCapAndSaysSo() {
        val t = "x".repeat(50)
        val f = FindLogic.findAll(t, pat("x"), cap = 10)
        assertEquals(10, f.hits.size)
        assertTrue(f.capped)
        val exact = FindLogic.findAll("x".repeat(10), pat("x"), cap = 10)
        assertEquals(10, exact.hits.size)
        assertFalse(exact.capped)
    }

    @Test
    fun aSearchThatIsToldToStopEndsWithACancellation() {
        val t = "ab".repeat(100_000)
        try {
            FindLogic.findAll(t, pat("zzz"), stop = { true })
            fail()
        } catch (_: CancellationException) {
        }
        try {
            FindLogic.replaceAll(t, pat("ab"), "x", false, stop = { true })
            fail()
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun aSearchAsksToStopOftenWhileItReads() {
        var asked = 0
        FindLogic.findAll("abc".repeat(333_334), pat("\\d", regex = true), stop = { asked++; false })
        assertTrue("asked $asked times", asked > 500)
    }

    @Test
    fun progressGrowsFromTheStartToTheEnd() {
        val t = "line\n".repeat(100_000)
        val seen = ArrayList<Int>()
        FindLogic.findAll(t, pat("nothing"), progress = { seen.add(it) })
        assertTrue(seen.size > 20)
        assertTrue(seen.first() < t.length / 10)
        assertTrue(seen.last() > t.length * 9 / 10)
    }

    @Test
    fun replaceAllGivesOnePieceFromTheFirstMatchToTheEndOfTheLast() {
        val r = FindLogic.replaceAll("..a..a..", pat("a"), "bb", false)!!
        assertEquals(2, r.from)
        assertEquals(6, r.to)
        assertEquals("bb..bb", r.text.toString())
        assertEquals(2, r.count)
    }

    @Test
    fun replaceAllWithNothingToReplaceIsNull() {
        assertNull(FindLogic.replaceAll("abc", pat("x"), "y", false))
        assertNull(FindLogic.replaceAll("", pat("x"), "y", false))
        assertNull(FindLogic.replaceAll("abc", pat("x*", regex = true), "y", true))
    }

    @Test
    fun aPlainReplacementIsUsedAsTyped() {
        val r = FindLogic.replaceAll("a.b", pat("."), "\\n$1", false)!!
        assertEquals("\\n$1", r.text.toString())
    }

    @Test
    fun aRegexReplacementExpandsGroupsAndEscapes() {
        val r = FindLogic.replaceAll("k1=v1;k2=v2", pat("(\\w+)=(\\w+)", regex = true), "$2=$1\\t", true)!!
        assertEquals("v1=k1\t;v2=k2\t", r.text.toString())
        assertEquals(2, r.count)
        val missing = FindLogic.replaceAll("ab", pat("(a)", regex = true), "[$2][$1][\\", true)!!
        assertEquals("[][a][\\", missing.text.toString())
    }

    @Test
    fun crlfAndEmojiAroundTheMatchesAreKept() {
        val r = FindLogic.replaceAll("a\r\n😀b\r\nb", pat("b"), "c", false)!!
        assertEquals(5, r.from)
        assertEquals("c\r\nc", r.text.toString())
    }

    @Test
    fun aBigTextIsSearchedAndReplacedWhole() {
        val t = "word ".repeat(400_000) // two million characters
        val f = FindLogic.findAll(t, pat("word"))
        assertEquals(400_000.coerceAtMost(FindLogic.MAX_HITS), f.hits.size)
        assertTrue(f.capped)
        val r = FindLogic.replaceAll(t, pat("word"), "w", false)!!
        assertEquals(400_000, r.count)
        assertEquals(0, r.from)
        assertEquals(t.length - 1, r.to)
    }
}
