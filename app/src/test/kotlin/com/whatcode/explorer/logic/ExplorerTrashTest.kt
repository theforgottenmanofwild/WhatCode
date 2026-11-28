package com.whatcode.explorer.logic

import com.whatcode.files.TempFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExplorerTrashTest {

    private val temp = TempFolder()

    @After
    fun clean() = temp.delete()

    @Test
    fun movesToTrashRestoresAndEmpties() {
        val trash = File(temp.root, ".trash")
        val f = temp.file("work/notes.txt", "hello trash")
        val item = ExplorerTrash.moveToTrash(f, trash, now = 5000L)
        assertFalse(f.exists())
        val listed = ExplorerTrash.list(trash)
        assertEquals(1, listed.size)
        assertEquals("notes.txt", listed[0].name)

        val restored = ExplorerTrash.restore(item, trash)
        assertTrue(restored.exists())
        assertEquals("hello trash", restored.readText())
        assertTrue(ExplorerTrash.list(trash).isEmpty())

        val f2 = temp.file("work/dir/a.txt", "nested")
        ExplorerTrash.moveToTrash(f2.parentFile!!, trash, now = 6000L)
        assertEquals(1, ExplorerTrash.list(trash).size)
        assertEquals(1, ExplorerTrash.empty(trash))
        assertTrue(ExplorerTrash.list(trash).isEmpty())
    }
}
