package com.whatcode.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerSortTest {

    private fun file(name: String) = ExplorerSort.Entry(name, "/x/$name", false)
    private fun dir(name: String) = ExplorerSort.Entry(name, "/x/$name", true)

    @Test
    fun foldersComeBeforeFiles() {
        val sorted = ExplorerSort.sort(listOf(file("a.txt"), dir("z"), file("b.txt"), dir("m")))
        assertEquals(listOf("m", "z", "a.txt", "b.txt"), sorted.map { it.name })
    }

    @Test
    fun caseIsIgnored() {
        val sorted = ExplorerSort.sort(listOf(file("banana"), file("Apple"), file("cherry")))
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.name })
    }

    @Test
    fun numbersCountAsNumbers() {
        val sorted = ExplorerSort.sort(listOf(file("file10.txt"), file("file2.txt"), file("file1.txt")))
        assertEquals(listOf("file1.txt", "file2.txt", "file10.txt"), sorted.map { it.name })
    }

    @Test
    fun leadingZerosDoNotChangeTheNumber() {
        assertTrue(ExplorerSort.compareNames("a2", "a10") < 0)
        assertTrue(ExplorerSort.compareNames("a002", "a10") < 0)
        assertTrue(ExplorerSort.compareNames("a010", "a10") != 0)
    }

    @Test
    fun aNameThatIsThePrefixOfAnotherComesFirst() {
        assertTrue(ExplorerSort.compareNames("note", "note2") < 0)
        assertTrue(ExplorerSort.compareNames("note.txt", "note2.txt") < 0)
    }

    @Test
    fun namesThatDifferOnlyInCaseKeepAFixedOrder() {
        val one = ExplorerSort.compareNames("File", "file")
        val two = ExplorerSort.compareNames("file", "File")
        assertTrue(one != 0)
        assertTrue(one * two < 0)
    }

    @Test
    fun hiddenNamesSortWithTheRest() {
        val sorted = ExplorerSort.sort(listOf(file("b"), file(".gitignore"), file("a")))
        assertEquals(listOf(".gitignore", "a", "b"), sorted.map { it.name })
    }

    @Test
    fun sizeLabels() {
        assertEquals("0 B", ExplorerSort.sizeLabel(0))
        assertEquals("0 B", ExplorerSort.sizeLabel(-5))
        assertEquals("900 B", ExplorerSort.sizeLabel(900))
        assertEquals("1.0 KB", ExplorerSort.sizeLabel(1024))
        assertEquals("1.5 KB", ExplorerSort.sizeLabel(1536))
        assertEquals("12 KB", ExplorerSort.sizeLabel(12 * 1024L))
        assertEquals("3.4 MB", ExplorerSort.sizeLabel((3.4 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", ExplorerSort.sizeLabel(2L * 1024 * 1024 * 1024))
    }
}
