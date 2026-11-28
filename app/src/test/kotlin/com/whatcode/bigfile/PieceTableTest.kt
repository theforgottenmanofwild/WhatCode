package com.whatcode.bigfile

import com.whatcode.files.TempFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PieceTableTest {

    private val temp = TempFolder()

    @After
    fun clean() = temp.delete()

    private fun readAll(pt: PieceTable, pf: PagedFile): String {
        val total = pt.totalLength.toInt()
        val dst = ByteArray(total)
        val got = pt.readBytes(pf, 0L, total, dst)
        return String(dst, 0, got, Charsets.UTF_8)
    }

    @Test
    fun insertsDeletesReplacesUndoesRedoesAndSavesAtomically() {
        val f = temp.file("big.txt", "alpha\nbeta\ngamma\n")
        val pf = PagedFile(f, preferMap = false)
        val pt = PieceTable(pf.length)
        assertFalse(pt.isDirty)

        // Replace "beta" (at offset 6, len 4) with "BETA-2"
        pt.replace(6L, 4L, "BETA-2".toByteArray(Charsets.UTF_8))
        assertTrue(pt.isDirty)
        assertEquals("alpha\nBETA-2\ngamma\n", readAll(pt, pf))

        // Insert prefix at 0
        pt.insert(0L, "// header\n".toByteArray(Charsets.UTF_8))
        assertEquals("// header\nalpha\nBETA-2\ngamma\n", readAll(pt, pf))

        // Undo & Redo
        pt.undo()
        assertEquals("alpha\nBETA-2\ngamma\n", readAll(pt, pf))
        pt.undo()
        assertEquals("alpha\nbeta\ngamma\n", readAll(pt, pf))
        assertFalse(pt.isDirty)

        pt.redo()
        pt.redo()
        assertEquals("// header\nalpha\nBETA-2\ngamma\n", readAll(pt, pf))

        // Atomic save replaces file on disk cleanly
        val savedBytes = pt.saveAtomic(pf, f)
        assertEquals(pt.totalLength, savedBytes)
        assertEquals("// header\nalpha\nBETA-2\ngamma\n", f.readText(Charsets.UTF_8))
    }

    @Test
    fun sparseLineIndexReadsThroughPieceTableOverlay() {
        val f = temp.file("lines.txt", "first\nsecond\nthird\n")
        val pf = PagedFile(f, preferMap = false)
        val pt = PieceTable(pf.length)
        pt.replace(6L, 7L, "SECOND-A\nSECOND-B\n".toByteArray(Charsets.UTF_8))
        val idx = SparseLineIndex(chunkLines = 2).apply { overlay = pt }
        assertTrue(idx.build(pf))
        assertEquals(5L, idx.lineCount)
        assertEquals("first", idx.readLine(pf, 0L, 1000)?.text)
        assertEquals("SECOND-A", idx.readLine(pf, 1L, 1000)?.text)
        assertEquals("SECOND-B", idx.readLine(pf, 2L, 1000)?.text)
        assertEquals("third", idx.readLine(pf, 3L, 1000)?.text)
        pf.close()
    }
}
