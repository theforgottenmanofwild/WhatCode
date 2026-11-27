package com.whatcode.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerAccessTest {

    private val root = "/storage/emulated/0"
    private val app = ExplorerAccess.appFolders(root, "com.whatcode")

    @Test
    fun appFoldersAreTheAppsOwnOnesOnSharedStorage() {
        assertTrue(app.contains("$root/Android/data/com.whatcode"))
        assertTrue(app.contains("$root/Android/media/com.whatcode"))
        assertTrue(app.contains("$root/Android/obb/com.whatcode"))
    }

    @Test
    fun theStorageRootIsListedWithoutAsking() {
        assertFalse(ExplorerAccess.needsAllFiles(root, root, app))
        assertFalse(ExplorerAccess.needsAllFiles("$root/", root, app))
    }

    @Test
    fun foldersOnSharedStorageNeedIt() {
        assertTrue(ExplorerAccess.needsAllFiles("$root/Download", root, app))
        assertTrue(ExplorerAccess.needsAllFiles("$root/Documents/site/index.html", root, app))
        assertTrue(ExplorerAccess.needsAllFiles("$root/Android/data", root, app))
    }

    @Test
    fun theAppsOwnFoldersDoNot() {
        assertFalse(ExplorerAccess.needsAllFiles("$root/Android/data/com.whatcode", root, app))
        assertFalse(ExplorerAccess.needsAllFiles("$root/Android/data/com.whatcode/files/a.txt", root, app))
        assertFalse(ExplorerAccess.needsAllFiles("$root/Android/media/com.whatcode", root, app))
    }

    @Test
    fun otherAppsFoldersDo() {
        assertTrue(ExplorerAccess.needsAllFiles("$root/Android/data/com.other", root, app))
        assertTrue(ExplorerAccess.needsAllFiles("$root/Android/data/com.whatcodex", root, app))
    }

    @Test
    fun pathsOffSharedStorageDoNot() {
        assertFalse(ExplorerAccess.needsAllFiles("/data/data/com.whatcode/files", root, app))
        assertFalse(ExplorerAccess.needsAllFiles("/storage/1A2B-3C4D/Music", root, app))
        assertFalse(ExplorerAccess.needsAllFiles("/storage/emulated/01/x", root, app))
    }
}
