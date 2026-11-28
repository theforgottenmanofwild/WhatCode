package com.whatcode.editor

import com.whatcode.editor.highlight.ScanStates
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirtyRangeTest {

    @Test
    fun oneEditIsItsOwnRange() {
        val d = DirtyRange()
        assertTrue(d.isEmpty)
        d.add(10, 3, 5)
        assertEquals(10, d.lo)
        assertEquals(15, d.hi)
        assertEquals(3, d.oldLength)
    }

    @Test
    fun editsApartAreOneRangeBetweenThem() {
        val d = DirtyRange()
        d.add(10, 0, 2) // now 10..12
        d.add(30, 4, 0) // a later deletion
        assertEquals(10, d.lo)
        assertEquals(30, d.hi)
        assertEquals(22, d.oldLength) // old positions 10 to 32
        assertEquals(d.hi - d.lo - d.delta, d.oldLength)
    }

    @Test
    fun clearEmptiesIt() {
        val d = DirtyRange()
        d.add(1, 1, 1)
        d.clear()
        assertTrue(d.isEmpty)
    }

    /** The stretch is what a reader of the changed text has to look at: outside it, old and new text agree. */
    @Test
    fun textOutsideTheRangeIsUnchanged() {
        val rnd = Random(7)
        repeat(300) {
            val old = StringBuilder((0 until 40 + rnd.nextInt(40)).map { ('a' + rnd.nextInt(5)) }.joinToString(""))
            val cur = StringBuilder(old)
            val d = DirtyRange()
            repeat(1 + rnd.nextInt(6)) {
                val start = rnd.nextInt(cur.length + 1)
                val removed = rnd.nextInt(minOf(6, cur.length - start) + 1)
                val ins = (0 until rnd.nextInt(6)).map { 'A' + rnd.nextInt(5) }.joinToString("")
                cur.replace(start, start + removed, ins)
                d.add(start, removed, ins.length)
            }
            assertTrue(d.oldLength >= 0)
            assertEquals(old.substring(0, d.lo), cur.substring(0, d.lo))
            assertEquals(old.substring(d.lo + d.oldLength), cur.substring(d.hi))
        }
    }

    /**
     * The way [EditorColouring] uses it: many edits become one splice of the index and of the scan states, which
     * must then agree with states built from scratch on the new text.
     */
    @Test
    fun oneMergedSpliceKeepsTheStatesRight() {
        val frags = listOf("/* open", "close */", "val s = \"x\"", "\"\"\"raw", "text\"\"\"", "// note", "fun f() {", "}", "", "a / b")
        val lang = Highlighter.languageFor("a.kt")
        for (seed in 1..60) {
            val rnd = Random(seed.toLong())
            val sb = StringBuilder()
            repeat(50 + rnd.nextInt(50)) { sb.append(frags[rnd.nextInt(frags.size)]).append('\n') }
            val index = LineIndex().also { it.build(sb) }
            val states = ScanStates(lang)
            assertTrue(states.build(sb.toString(), index))
            repeat(8) {
                val d = DirtyRange()
                repeat(1 + rnd.nextInt(5)) {
                    val start = rnd.nextInt(sb.length + 1)
                    val removed = rnd.nextInt(minOf(15, sb.length - start) + 1)
                    val ins = frags[rnd.nextInt(frags.size)] + (if (rnd.nextBoolean()) "\n" else "")
                    sb.replace(start, start + removed, ins)
                    d.add(start, removed, ins.length)
                }
                val text = sb.toString()
                val splice = index.replace(d.lo, d.oldLength, text.subSequence(d.lo, d.hi))
                assertTrue(states.replace(splice, text, index))
                val fresh = LineIndex().also { it.build(text) }
                assertEquals(fresh.lineCount, index.lineCount)
                for (l in 0 until fresh.lineCount) assertEquals(fresh.startOf(l), index.startOf(l))
                val freshStates = ScanStates(lang)
                assertTrue(freshStates.build(text, fresh))
                for (l in 0 until fresh.lineCount) {
                    if (states.isStart(l)) assertTrue("seed $seed: line $l wrongly a start", freshStates.isStart(l))
                }
            }
        }
    }
}
