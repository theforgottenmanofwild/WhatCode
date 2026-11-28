package com.whatcode.apk

import com.whatcode.files.TempFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveBrowserTest {

    private val temp = TempFolder()

    @After
    fun clean() {
        temp.delete()
    }

    private fun writeZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val f = temp.file(name, "")
        ZipOutputStream(FileOutputStream(f)).use { z ->
            for ((path, data) in entries) {
                z.putNextEntry(ZipEntry(path))
                if (!path.endsWith("/")) z.write(data)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun listsVirtualFoldersAndFilesAndReadsEntries() {
        val z = writeZip(
            "sample.zip",
            listOf(
                "AndroidManifest.xml" to "manifest".toByteArray(),
                "res/layout/main.xml" to "<LinearLayout/>".toByteArray(),
                "res/values/strings.xml" to "<resources/>".toByteArray(),
            ),
        )
        val root = ArchiveBrowser.listFolder(z, "")
        assertEquals(listOf("res", "AndroidManifest.xml"), root.map { it.name })
        assertTrue(root[0].isDir)

        val res = ArchiveBrowser.listFolder(z, "res")
        assertEquals(listOf("layout", "values"), res.map { it.name })

        val layout = ArchiveBrowser.listFolder(z, "res/layout")
        assertEquals(listOf("main.xml"), layout.map { it.name })
        assertEquals("<LinearLayout/>", String(ArchiveBrowser.readEntryBytes(z, "res/layout/main.xml")))
    }

    @Test
    fun blocksZipSlipDuringResolveAndExtraction() {
        val dest = temp.folder("out")
        assertThrows(SecurityException::class.java) {
            ArchiveBrowser.safeTarget(dest, "../../etc/passwd")
        }
        val evil = writeZip("evil.zip", listOf("../escape.txt" to "bad".toByteArray()))
        assertThrows(SecurityException::class.java) {
            ArchiveBrowser.extract(evil, dest)
        }
    }

    @Test
    fun blocksZipBombByBytesAndByEntryCountAndHandlesClashes() {
        val dest = temp.folder("out")
        val big = writeZip("big.zip", listOf("huge.txt" to ByteArray(4096) { 0x41 }))
        assertThrows(SecurityException::class.java) {
            ArchiveBrowser.extract(big, dest, limits = ArchiveBrowser.BombLimits(maxTotalBytes = 1024, maxEntries = 100))
        }
        val many = writeZip("many.zip", (1..10).map { "f$it.txt" to "x".toByteArray() })
        assertThrows(SecurityException::class.java) {
            ArchiveBrowser.extract(many, dest, limits = ArchiveBrowser.BombLimits(maxTotalBytes = 100_000, maxEntries = 5))
        }

        // Clash handling: KEEP_BOTH creates "a (2).txt", SKIP keeps existing.
        val ok = writeZip("ok.zip", listOf("a.txt" to "new".toByteArray()))
        temp.file("out/a.txt", "old")
        val keep = ArchiveBrowser.extract(ok, dest, clashMode = ArchiveBrowser.ClashMode.KEEP_BOTH)
        assertEquals(1, keep.filesExtracted)
        assertEquals("old", File(dest, "a.txt").readText())
        assertEquals("new", File(dest, "a (2).txt").readText())

        val skip = ArchiveBrowser.extract(ok, dest, clashMode = ArchiveBrowser.ClashMode.SKIP)
        assertEquals(0, skip.filesExtracted)
        assertEquals(1, skip.skipped)
    }
}
