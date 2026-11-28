package com.whatcode.explorer.logic

import com.whatcode.files.TempFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class FileFactsTest {

    private val temp = TempFolder()

    @After
    fun cleanUp() = temp.delete()

    @Test
    fun permissionsReadAsOwnerGroupAndOthers() {
        assertEquals("rwxr-x---", FileFacts.permissionText(PosixFilePermissions.fromString("rwxr-x---")))
        assertEquals("---------", FileFacts.permissionText(emptySet()))
        assertEquals("rw-r--r--", FileFacts.permissionText(PosixFilePermissions.fromString("rw-r--r--")))
    }

    @Test
    fun aFileHasItsSizeDateAndPermissions() {
        val f = temp.file("notes.txt", "hello")
        f.setLastModified(1_700_000_000_000L)
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-r-----"))
        val facts = FileFacts.read(f)
        assertEquals(5L, facts.size)
        assertEquals(1_700_000_000_000L, facts.modified)
        assertEquals("rw-r-----", facts.permissions)
        assertFalse(facts.isDir)
        assertFalse(facts.isLink)
        assertNull(facts.linkTarget)
        assertTrue(facts.canRead)
    }

    @Test
    fun aLinkIsShownAsALinkWithWhereItPoints() {
        val folder = temp.folder("real")
        val link = File(temp.root, "shortcut")
        Files.createSymbolicLink(link.toPath(), folder.toPath())
        val facts = FileFacts.read(link)
        assertTrue(facts.isLink)
        assertFalse(facts.isDir)
        assertEquals(folder.path, facts.linkTarget)
    }

    @Test
    fun aBrokenLinkCanStillBeShown() {
        val link = File(temp.root, "dangling")
        Files.createSymbolicLink(link.toPath(), File(temp.root, "gone").toPath())
        val facts = FileFacts.read(link)
        assertTrue(facts.isLink)
        assertTrue(facts.linkTarget!!.endsWith("gone"))
    }

    @Test
    fun aNameWithDotsAndSpacesIsKeptAsItIs() {
        val f = temp.file("my.notes v1.2 .txt", "")
        assertEquals(f.absolutePath, FileFacts.read(f).path)
    }
}
