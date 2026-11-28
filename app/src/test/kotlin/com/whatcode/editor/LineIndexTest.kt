package com.whatcode.editor

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineIndexTest {

    private fun starts(text: String): List<Int> {
        val out = arrayListOf(0)
        for (i in text.indices) if (text[i] == '\n') out.add(i + 1)
        return out
    }

    private fun same(index: LineIndex, text: String) {
        val want = starts(text)
        assertEquals(want.size, index.lineCount)
        for (i in want.indices) assertEquals("line $i of ${text.replace("\n", "\\n")}", want[i], index.startOf(i))
        for (p in 0..text.length) assertEquals("line of $p", want.indexOfLast { it <= p }, index.lineOf(p))
    }

    @Test
    fun emptyAndSingleLine() {
        val index = LineIndex()
        index.build("")
        assertEquals(1, index.lineCount)
        assertEquals(0, index.lineOf(0))
        index.build("abc")
        assertEquals(1, index.lineCount)
        assertEquals(0, index.lineOf(3))
        assertEquals(3, index.endOf(0, 3))
    }

    @Test
    fun trailingNewlineMakesAnEmptyLastLine() {
        val index = LineIndex()
        index.build("a\nb\n")
        assertEquals(3, index.lineCount)
        assertEquals(4, index.startOf(2))
        assertEquals(2, index.lineOf(4))
        assertEquals(1, index.endOf(0, 4))
        assertEquals(4, index.endOf(2, 4))
    }

    @Test
    fun crlfKeepsTheCarriageReturnInTheLine() {
        val index = LineIndex()
        index.build("a\r\nb\r\n")
        assertEquals(3, index.lineCount)
        assertEquals(3, index.startOf(1))
        assertEquals(2, index.endOf(0, 6)) // the \r is part of line 0
    }

    @Test
    fun spliceDescribesWhatChanged() {
        val index = LineIndex()
        index.build("one\ntwo\nthree")
        val s = index.replace(4, 3, "x\ny\nz") // "two" becomes three lines
        assertEquals(1, s.line)
        assertEquals(0, s.removed)
        assertEquals(2, s.added)
        same(index, "one\nx\ny\nz\nthree")
        val t = index.replace(3, 1, "") // joins "one" and "x"
        assertEquals(0, t.line)
        assertEquals(1, t.removed)
        assertEquals(0, t.added)
        same(index, "onex\ny\nz\nthree")
    }

    @Test
    fun randomEditsMatchABuild() {
        val rnd = Random(7)
        val pieces = arrayOf("a", "bc", "\n", "\n\n", "xyz\n", "\r\n", "", " ")
        for (round in 0 until 300) {
            var text = StringBuilder()
            repeat(rnd.nextInt(30)) { text.append(pieces[rnd.nextInt(pieces.size)]) }
            val index = LineIndex()
            index.build(text)
            repeat(25) {
                val start = rnd.nextInt(text.length + 1)
                val removed = rnd.nextInt(minOf(6, text.length - start) + 1)
                val ins = StringBuilder()
                repeat(rnd.nextInt(4)) { ins.append(pieces[rnd.nextInt(pieces.size)]) }
                text.replace(start, start + removed, ins.toString())
                index.replace(start, removed, ins)
                same(index, text.toString())
            }
        }
    }

    @Test
    fun growsPastItsFirstSize() {
        val index = LineIndex()
        index.build("")
        val text = StringBuilder()
        repeat(2000) {
            index.replace(text.length, 0, "line $it\n")
            text.append("line $it\n")
        }
        assertEquals(2001, index.lineCount)
        assertTrue(index.startOf(2000) == text.length)
    }
}
