package com.whatcode.apk

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BuildOutputLocatorTest {

    private val tmpRoot = Files.createTempDirectory("build_locator_test").toFile()

    @After
    fun clean() {
        tmpRoot.deleteRecursively()
    }

    @Test
    fun matchesExpectedGradleApkPaths() {
        assertTrue(BuildOutputLocator.isBuildOutputApk(listOf("app", "build", "outputs", "apk", "debug", "app-debug.apk")))
        assertTrue(BuildOutputLocator.isBuildOutputApk(listOf("build", "outputs", "apk", "release", "app-release.apk")))
        assertFalse(BuildOutputLocator.isBuildOutputApk(listOf("app", "build", "outputs", "apk", "debug", "output-metadata.json")))
        assertFalse(BuildOutputLocator.isBuildOutputApk(listOf("dist", "other.apk")))
    }

    @Test
    fun picksNewestBuildAndPrefersDebugOnTie() {
        val older = BuildOutputLocator.Candidate("/a/app-debug.apk", "app-debug.apk", 1000L, 100L)
        val newerRelease = BuildOutputLocator.Candidate("/a/app-release.apk", "app-release.apk", 2000L, 100L)
        val sameTimeDebug = BuildOutputLocator.Candidate("/a/app-debug.apk", "app-debug.apk", 2000L, 100L)
        assertEquals(newerRelease, BuildOutputLocator.pickLatest(listOf(older, newerRelease)))
        assertEquals(sameTimeDebug, BuildOutputLocator.pickLatest(listOf(newerRelease, sameTimeDebug)))
        assertNull(BuildOutputLocator.pickLatest(emptyList()))
    }

    @Test
    fun findsNewestApkUnderLocalProjectFolder() {
        val debugDir = File(tmpRoot, "app/build/outputs/apk/debug").apply { mkdirs() }
        val releaseDir = File(tmpRoot, "app/build/outputs/apk/release").apply { mkdirs() }
        val oldApk = File(debugDir, "app-debug.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(1_000_000L)
        }
        val newApk = File(releaseDir, "app-release.apk").apply {
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(5_000_000L)
        }
        val found = BuildOutputLocator.findNewestApk(tmpRoot)
        assertEquals(newApk.absolutePath, found?.path)
        oldApk.setLastModified(9_000_000L)
        assertEquals(oldApk.absolutePath, BuildOutputLocator.findNewestApk(tmpRoot)?.path)
    }
}
