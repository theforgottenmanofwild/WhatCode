package com.whatcode.explorer.logic

import com.whatcode.files.TempFolder
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerFindTest {

    private val temp = TempFolder()

    @After
    fun cleanUp() = temp.delete()

    private fun names(r: ExplorerFind.Result) = r.files.map { it.name }

    @Test
    fun findsFilesAndFoldersAtAnyDepth() {
        temp.file("a/b/c/notes.txt")
        temp.file("notes-old.md")
        temp.folder("my-notes")
        val found = names(ExplorerFind.search(temp.root, "notes", 50))
        assertEquals(setOf("notes.txt", "notes-old.md", "my-notes"), found.toSet())
    }

    @Test
    fun everyWordMustMatchAndCaseIsIgnored() {
        temp.file("docs/Readme.MD")
        temp.file("docs/other.md")
        assertEquals(listOf("Readme.MD"), names(ExplorerFind.search(temp.root, "readme MD", 50)))
        assertEquals(listOf("Readme.MD"), names(ExplorerFind.search(temp.root, "docs readme", 50)))
    }

    @Test
    fun theFolderSearchedFromIsNotPartOfThePath() {
        temp.file("project/a.txt")
        val found = ExplorerFind.search(File(temp.root, "project"), "project", 50)
        assertTrue(found.files.isEmpty())
    }

    @Test
    fun aMatchOnTheNameComesBeforeAMatchOnlyInAFolder() {
        temp.file("docs/zzz.txt")
        temp.file("other/docs.txt")
        val found = names(ExplorerFind.search(temp.root, "docs", 50))
        assertEquals(listOf("docs", "docs.txt", "zzz.txt"), found)
    }

    @Test
    fun shorterNamesComeFirst() {
        temp.file("app.json")
        temp.file("app.json.bak")
        temp.file("app.json.backup")
        assertEquals(listOf("app.json", "app.json.bak", "app.json.backup"), names(ExplorerFind.search(temp.root, "app", 50)))
    }

    @Test
    fun aBlankQueryFindsNothing() {
        temp.file("a.txt")
        assertTrue(ExplorerFind.search(temp.root, "  ", 50).files.isEmpty())
        assertTrue(ExplorerFind.search(temp.root, "", 50).files.isEmpty())
    }

    @Test
    fun theLimitIsReportedAsTruncated() {
        for (i in 1..10) temp.file("f$i.txt")
        val found = ExplorerFind.search(temp.root, "f", 3)
        assertEquals(3, found.files.size)
        assertTrue(found.truncated)
        assertFalse(ExplorerFind.search(temp.root, "f", 50).truncated)
    }

    @Test
    fun aCancelledSearchStopsAtOnce() {
        temp.file("a/b/c.txt")
        val found = ExplorerFind.search(temp.root, "c", 50) { true }
        assertTrue(found.files.isEmpty())
    }

    @Test
    fun aLinkToAFolderIsNotFollowed() {
        temp.file("real/x.txt")
        try {
            java.nio.file.Files.createSymbolicLink(File(temp.root, "loop").toPath(), temp.root.toPath())
        } catch (e: Exception) {
            return // storage without links
        }
        val found = names(ExplorerFind.search(temp.root, "x.txt", 50))
        assertEquals(listOf("x.txt"), found)
    }
}
