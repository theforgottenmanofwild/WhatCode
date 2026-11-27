package com.whatcode.files

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FileOpsTest {

    private val temp = TempFolder()

    @After
    fun cleanUp() = temp.delete()

    private fun failsWith(text: String, block: () -> Unit) {
        try {
            block()
            fail("expected: $text")
        } catch (e: FileOpException) {
            assertTrue(e.message, e.message!!.contains(text))
        }
    }

    // ------------------------------------------------------------ names

    @Test
    fun problemsWithNames() {
        assertNull(FileNames.problem("notes.txt"))
        assertNull(FileNames.problem(".gitignore"))
        assertNull(FileNames.problem("a b (1).txt"))
        assertEquals("Type a name", FileNames.problem(""))
        assertEquals("Type a name", FileNames.problem("   "))
        assertEquals("That name can't be used", FileNames.problem("."))
        assertEquals("That name can't be used", FileNames.problem(".."))
        assertEquals("A name can't contain /", FileNames.problem("a/b"))
        assertEquals("That name is too long", FileNames.problem("x".repeat(256)))
        assertNull(FileNames.problem("x".repeat(255)))
    }

    @Test
    fun aNameThatIsTooLongInBytesIsRefused() {
        // 128 two-byte letters are 256 bytes.
        assertEquals("That name is too long", FileNames.problem("\u00E9".repeat(128)))
    }

    @Test
    fun uniqueNameKeepsAFreeNameAndNumbersATakenOne() {
        assertEquals("a.txt", FileNames.unique("a.txt") { false })
        assertEquals("a (2).txt", FileNames.unique("a.txt") { it == "a.txt" })
        assertEquals("a (3).txt", FileNames.unique("a.txt") { it == "a.txt" || it == "a (2).txt" })
        assertEquals("a (3).txt", FileNames.unique("a (2).txt") { it == "a (2).txt" })
        assertEquals("folder (2)", FileNames.unique("folder") { it == "folder" })
        assertEquals(".env (2)", FileNames.unique(".env") { it == ".env" })
        assertEquals("a.tar (2).gz", FileNames.unique("a.tar.gz") { it == "a.tar.gz" })
    }

    // ------------------------------------------------------------ create

    @Test
    fun createFileAndFolder() {
        val file = FileOps.createFile(temp.root, "new.txt")
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
        val folder = FileOps.createFolder(temp.root, "sub")
        assertTrue(folder.isDirectory)
    }

    @Test
    fun createRefusesATakenOrBadName() {
        temp.file("a.txt")
        failsWith("already exists") { FileOps.createFile(temp.root, "a.txt") }
        failsWith("already exists") { FileOps.createFolder(temp.root, "a.txt") }
        failsWith("Type a name") { FileOps.createFile(temp.root, " ") }
        failsWith("can't contain /") { FileOps.createFolder(temp.root, "x/y") }
        failsWith("is not a folder") { FileOps.createFile(File(temp.root, "missing"), "x") }
    }

    // ------------------------------------------------------------ rename

    @Test
    fun renameKeepsTheContent() {
        val file = temp.file("a.txt", "hello")
        val renamed = FileOps.rename(file, "b.txt")
        assertEquals("b.txt", renamed.name)
        assertFalse(file.exists())
        assertEquals("hello", renamed.readText())
    }

    @Test
    fun renameToTheSameNameChangesNothing() {
        val file = temp.file("a.txt", "x")
        assertEquals(file, FileOps.rename(file, "a.txt"))
        assertTrue(file.exists())
    }

    @Test
    fun renameRefusesATakenNameAndALostFile() {
        val a = temp.file("a.txt")
        temp.file("b.txt")
        failsWith("already exists") { FileOps.rename(a, "b.txt") }
        assertTrue(a.exists())
        failsWith("no longer there") { FileOps.rename(File(temp.root, "gone.txt"), "x.txt") }
    }

    @Test
    fun renameOnlyTheCase() {
        val file = temp.file("readme.md", "r")
        val renamed = FileOps.rename(file, "README.md")
        assertEquals("README.md", renamed.name)
        assertEquals("r", renamed.readText())
        assertEquals(listOf("README.md"), temp.root.list()!!.toList())
    }

    @Test
    fun renameOnlyTheCaseNeverReplacesAnotherFile() {
        val lower = temp.file("note.txt", "lower")
        val upper = temp.file("NOTE.TXT", "upper")
        // On storage that ignores case the two are one file and the second line above replaced the first.
        if (temp.root.list()!!.size < 2) return
        failsWith("already exists") { FileOps.rename(lower, "NOTE.TXT") }
        assertEquals("lower", lower.readText())
        assertEquals("upper", upper.readText())
    }

    @Test
    fun renameAFolderKeepsWhatIsInIt() {
        temp.file("old/inner/f.txt", "deep")
        val renamed = FileOps.rename(File(temp.root, "old"), "new")
        assertEquals("deep", File(renamed, "inner/f.txt").readText())
    }

    // ------------------------------------------------------------ delete

    @Test
    fun deleteAFileAndAFolderWithContent() {
        val file = temp.file("a.txt")
        FileOps.delete(file)
        assertFalse(file.exists())
        temp.file("d/e/f.txt")
        FileOps.delete(File(temp.root, "d"))
        assertFalse(File(temp.root, "d").exists())
    }

    @Test
    fun deletingWhatIsAlreadyGoneIsFine() {
        FileOps.delete(File(temp.root, "never-was"))
    }

    @Test
    fun deletingALinkLeavesWhatItPointsTo() {
        val target = temp.file("real/keep.txt", "keep")
        val link = File(temp.root, "link")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), File(temp.root, "real").toPath())
        } catch (e: Exception) {
            return // storage without links
        }
        FileOps.delete(link)
        assertFalse(link.exists())
        assertEquals("keep", target.readText())
    }

    // ------------------------------------------------------------ copy

    @Test
    fun copyAFileGivesItAFreeNameWhenTheNameIsTaken() {
        val file = temp.file("a.txt", "one")
        val dest = temp.folder("dest")
        val first = FileOps.copy(file, dest)
        assertEquals("a.txt", first.name)
        val second = FileOps.copy(file, dest)
        assertEquals("a (2).txt", second.name)
        assertEquals("one", second.readText())
        assertTrue(file.exists())
    }

    @Test
    fun copyAFolderWithEverythingInIt() {
        temp.file("src/a.txt", "a")
        temp.file("src/sub/b.txt", "b")
        val dest = temp.folder("dest")
        val copy = FileOps.copy(File(temp.root, "src"), dest)
        assertEquals("a", File(copy, "a.txt").readText())
        assertEquals("b", File(copy, "sub/b.txt").readText())
        assertTrue(File(temp.root, "src/a.txt").exists())
    }

    @Test
    fun copyInTheSameFolderMakesANumberedCopy() {
        val file = temp.file("a.txt", "x")
        val copy = FileOps.copy(file, temp.root)
        assertEquals("a (2).txt", copy.name)
    }

    @Test
    fun aFolderCanNotBeCopiedOrMovedIntoItself() {
        val folder = temp.folder("f")
        val inner = temp.folder("f/inner")
        failsWith("copied into itself") { FileOps.copy(folder, inner) }
        failsWith("moved into itself") { FileOps.move(folder, inner) }
        failsWith("copied into itself") { FileOps.copy(folder, folder) }
        assertTrue(folder.isDirectory)
    }

    @Test
    fun aFolderCanBeCopiedBesideASiblingWithASharedPrefix() {
        val a = temp.file("ab/x.txt", "x").parentFile!!
        val dest = temp.folder("abc")
        val copy = FileOps.copy(a, dest)
        assertEquals("x", File(copy, "x.txt").readText())
    }

    @Test
    fun copyOfALostFileFails() {
        failsWith("no longer there") { FileOps.copy(File(temp.root, "gone"), temp.root) }
    }

    // ------------------------------------------------------------ move

    @Test
    fun moveAFileKeepsContentAndRemovesTheOriginal() {
        val file = temp.file("a.txt", "m")
        val dest = temp.folder("dest")
        val moved = FileOps.move(file, dest)
        assertEquals(File(dest, "a.txt"), moved)
        assertEquals("m", moved.readText())
        assertFalse(file.exists())
    }

    @Test
    fun moveToTheFolderItIsInChangesNothing() {
        val file = temp.file("a.txt", "m")
        assertEquals(file, FileOps.move(file, temp.root))
        assertEquals("m", file.readText())
        assertEquals(listOf("a.txt"), temp.root.list()!!.toList())
    }

    @Test
    fun moveNumbersANameThatIsTaken() {
        val file = temp.file("a.txt", "new")
        val dest = temp.folder("dest")
        temp.file("dest/a.txt", "old")
        val moved = FileOps.move(file, dest)
        assertEquals("a (2).txt", moved.name)
        assertEquals("old", File(dest, "a.txt").readText())
        assertEquals("new", moved.readText())
    }

    @Test
    fun moveAFolderWithItsContent() {
        temp.file("src/deep/a.txt", "a")
        val dest = temp.folder("dest")
        val moved = FileOps.move(File(temp.root, "src"), dest)
        assertEquals("a", File(moved, "deep/a.txt").readText())
        assertFalse(File(temp.root, "src").exists())
    }
}
