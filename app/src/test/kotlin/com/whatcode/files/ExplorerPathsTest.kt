package com.whatcode.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerPathsTest {

    @Test
    fun normalizeRemovesDoubledAndTrailingSlashes() {
        assertEquals("/a/b", ExplorerPaths.normalize("/a//b/"))
        assertEquals("/", ExplorerPaths.normalize("/"))
        assertEquals("/", ExplorerPaths.normalize("///"))
        assertEquals("a/b", ExplorerPaths.normalize("a/b/"))
        assertEquals("", ExplorerPaths.normalize(""))
    }

    @Test
    fun childJoinsWithOneSlash() {
        assertEquals("/a/b", ExplorerPaths.child("/a", "b"))
        assertEquals("/a/b", ExplorerPaths.child("/a/", "b"))
        assertEquals("/b", ExplorerPaths.child("/", "b"))
    }

    @Test
    fun nameIsTheLastPart() {
        assertEquals("c.txt", ExplorerPaths.name("/a/b/c.txt"))
        assertEquals("b", ExplorerPaths.name("/a/b/"))
        assertEquals("/", ExplorerPaths.name("/"))
    }

    @Test
    fun isInsideDoesNotConfuseSiblingsWithASharedPrefix() {
        assertTrue(ExplorerPaths.isInside("/a/b", "/a/b"))
        assertTrue(ExplorerPaths.isInside("/a/b/c", "/a/b"))
        assertFalse(ExplorerPaths.isInside("/a/bc", "/a/b"))
        assertFalse(ExplorerPaths.isInside("/a", "/a/b"))
        assertTrue(ExplorerPaths.isInside("/anything", "/"))
    }

    @Test
    fun parentStopsAtTheRoot() {
        assertEquals("/s/Download", ExplorerPaths.parent("/s/Download/x", "/s"))
        assertEquals("/s", ExplorerPaths.parent("/s/Download", "/s"))
        assertNull(ExplorerPaths.parent("/s", "/s"))
        assertNull(ExplorerPaths.parent("/other", "/s"))
        assertEquals("/", ExplorerPaths.parent("/a", "/"))
        assertNull(ExplorerPaths.parent("/", "/"))
    }

    @Test
    fun crumbsStartAtTheRootLabelAndGoDown() {
        val crumbs = ExplorerPaths.crumbs("/s/Download/pics", "/s", "Phone storage")
        assertEquals(listOf("Phone storage", "Download", "pics"), crumbs.map { it.label })
        assertEquals(listOf("/s", "/s/Download", "/s/Download/pics"), crumbs.map { it.path })
    }

    @Test
    fun crumbsOfTheRootOrOfAPathOutsideItAreTheRootAlone() {
        assertEquals(1, ExplorerPaths.crumbs("/s", "/s", "Phone storage").size)
        val outside = ExplorerPaths.crumbs("/elsewhere/x", "/s", "Phone storage")
        assertEquals(listOf("/s"), outside.map { it.path })
    }

    @Test
    fun crumbsWorkFromTheSystemRoot() {
        val crumbs = ExplorerPaths.crumbs("/a/b", "/", "Root")
        assertEquals(listOf("/", "/a", "/a/b"), crumbs.map { it.path })
    }

    @Test
    fun rebaseMovesAPathWithItsFolder() {
        assertEquals("/x/new", ExplorerPaths.rebase("/x/old", "/x/old", "/x/new"))
        assertEquals("/x/new/f.txt", ExplorerPaths.rebase("/x/old/f.txt", "/x/old", "/x/new"))
        assertNull(ExplorerPaths.rebase("/x/older/f.txt", "/x/old", "/x/new"))
        assertNull(ExplorerPaths.rebase("/y/f.txt", "/x/old", "/x/new"))
    }

    @Test
    fun treeIdOfThePhonesOwnStorageBecomesAPath() {
        val root = "/storage/emulated/0"
        assertEquals("/storage/emulated/0/Documents/site", ExplorerPaths.fromTreeId("primary:Documents/site", root))
        assertEquals(root, ExplorerPaths.fromTreeId("primary:", root))
        assertEquals("/storage/emulated/0/a", ExplorerPaths.fromTreeId("primary:/a/", root))
    }

    @Test
    fun treeIdOfOtherStorageHasNoPath() {
        assertNull(ExplorerPaths.fromTreeId("1A2B-3C4D:Music", "/storage/emulated/0"))
        assertNull(ExplorerPaths.fromTreeId("raw:/x", "/storage/emulated/0"))
    }
}
