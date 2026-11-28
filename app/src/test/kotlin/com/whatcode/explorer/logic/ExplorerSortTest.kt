package com.whatcode.explorer.logic

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

    private fun file(name: String, size: Long, modified: Long = 0L) = ExplorerSort.Entry(name, "/x/$name", false, size, modified)

    private fun arranged(entries: List<ExplorerSort.Entry>, key: ExplorerSort.Key, descending: Boolean = false, showHidden: Boolean = true) =
        ExplorerSort.arrange(entries, key, descending, showHidden).map { it.name }

    @Test
    fun arrangeByNameMatchesTheDefaultOrder() {
        val entries = listOf(file("b.txt"), dir("z"), file("a.txt"), dir("m"))
        assertEquals(ExplorerSort.sort(entries).map { it.name }, arranged(entries, ExplorerSort.Key.NAME))
    }

    @Test
    fun arrangeBySizeGoesFromSmallToBig() {
        val entries = listOf(file("big", 900), file("small", 10), file("mid", 200))
        assertEquals(listOf("small", "mid", "big"), arranged(entries, ExplorerSort.Key.SIZE))
    }

    @Test
    fun arrangeByDateGoesFromOldToNew() {
        val entries = listOf(file("new", 1, 300), file("old", 1, 100), file("mid", 1, 200))
        assertEquals(listOf("old", "mid", "new"), arranged(entries, ExplorerSort.Key.DATE))
    }

    @Test
    fun arrangeByTypeGroupsTheSameEndingAndThenUsesTheName() {
        val entries = listOf(file("b.txt"), file("a.kt"), file("a.txt"), file("readme"), file("c.kt"))
        assertEquals(listOf("readme", "a.kt", "c.kt", "a.txt", "b.txt"), arranged(entries, ExplorerSort.Key.TYPE))
    }

    @Test
    fun descendingTurnsTheOrderRoundButFoldersStayFirst() {
        val entries = listOf(file("a", 1), dir("x"), file("c", 3), dir("y"), file("b", 2))
        assertEquals(listOf("y", "x", "c", "b", "a"), arranged(entries, ExplorerSort.Key.NAME, descending = true))
        assertEquals(listOf("y", "x", "c", "b", "a"), arranged(entries, ExplorerSort.Key.SIZE, descending = true))
    }

    @Test
    fun hiddenNamesAreLeftOutWhenTheyAreNotShown() {
        val entries = listOf(file(".git"), file("a"), dir(".cache"), dir("src"))
        assertEquals(listOf("src", "a"), arranged(entries, ExplorerSort.Key.NAME, showHidden = false))
        assertEquals(listOf(".cache", "src", ".git", "a"), arranged(entries, ExplorerSort.Key.NAME, showHidden = true))
    }

    @Test
    fun extensionsAreTheTextAfterTheLastDot() {
        assertEquals("kt", ExplorerSort.extension("Main.KT"))
        assertEquals("gz", ExplorerSort.extension("a.tar.gz"))
        assertEquals("", ExplorerSort.extension("readme"))
        assertEquals("", ExplorerSort.extension(".gitignore"))
        assertEquals("", ExplorerSort.extension("name."))
    }

    private fun ordered(entries: List<ExplorerSort.Entry>, order: ExplorerSort.Order, descending: Boolean = false) =
        ExplorerSort.arrange(entries, ExplorerSort.Key.NAME, descending, true, order).map { it.name }

    @Test
    fun foldersCanBeMixedWithFiles() {
        val entries = listOf(file("b"), dir("c"), file("a"), dir("d"))
        assertEquals(listOf("a", "b", "c", "d"), ordered(entries, ExplorerSort.Order(foldersFirst = false)))
        assertEquals(listOf("d", "c", "b", "a"), ordered(entries, ExplorerSort.Order(foldersFirst = false), descending = true))
    }

    @Test
    fun numbersCanBeReadAsTextInsteadOfAsNumbers() {
        val entries = listOf(file("file10"), file("file2"), file("file1"))
        assertEquals(listOf("file1", "file2", "file10"), ordered(entries, ExplorerSort.Order()))
        assertEquals(listOf("file1", "file10", "file2"), ordered(entries, ExplorerSort.Order(natural = false)))
    }

    @Test
    fun caseCanCountSoCapitalsComeFirst() {
        val entries = listOf(file("banana"), file("Cherry"), file("apple"))
        assertEquals(listOf("apple", "banana", "Cherry"), ordered(entries, ExplorerSort.Order()))
        assertEquals(listOf("Cherry", "apple", "banana"), ordered(entries, ExplorerSort.Order(ignoreCase = false)))
    }

    @Test
    fun namesWithDotsAndDigitsStayInAFixedOrderWithEveryOption() {
        for (natural in listOf(true, false)) for (ignoreCase in listOf(true, false)) {
            assertTrue(ExplorerSort.compareNames("a.1.txt", "a.1.txt", natural, ignoreCase) == 0)
            val one = ExplorerSort.compareNames("v1.2.10", "v1.2.9", natural, ignoreCase)
            val two = ExplorerSort.compareNames("v1.2.9", "v1.2.10", natural, ignoreCase)
            assertTrue(one != 0 && one == -two)
        }
    }

    @Test
    fun decimalSizesCountAThousandBytesToTheKilobyte() {
        assertEquals("999 B", ExplorerSort.sizeLabel(999, decimal = true))
        assertEquals("1.0 kB", ExplorerSort.sizeLabel(1000, decimal = true))
        assertEquals("1.0 KB", ExplorerSort.sizeLabel(1024))
        assertEquals("1000 B", ExplorerSort.sizeLabel(1000))
        assertEquals("2.5 MB", ExplorerSort.sizeLabel(2_500_000, decimal = true))
        assertEquals("4.0 GB", ExplorerSort.sizeLabel(4_000_000_000, decimal = true))
    }
}
