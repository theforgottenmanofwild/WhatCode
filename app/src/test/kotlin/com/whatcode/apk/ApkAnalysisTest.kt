package com.whatcode.apk

import com.whatcode.apk.ApkCompare.Relation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

class ApkAnalysisTest {

    @Test
    fun hashesMatchStandardSha256AndMd5VectorsAndSupportCancellation() {
        val data = "abc".toByteArray()
        val both = ApkHashes.compute(ByteArrayInputStream(data), sha256 = true, md5 = true)!!
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", both.sha256)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", both.md5)

        val onlyMd5 = ApkHashes.compute(ByteArrayInputStream(data), sha256 = false, md5 = true)!!
        assertNull(onlyMd5.sha256)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", onlyMd5.md5)

        val calls = AtomicInteger(0)
        assertNull(ApkHashes.compute(ByteArrayInputStream(ByteArray(64 * 1024)), true, true) { calls.incrementAndGet() >= 2 })

        assertEquals(
            "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD",
            ApkHashes.certSha256Colon(data),
        )
    }

    @Test
    fun abisAreExtractedCleanlyFromLibraryPaths() {
        val abis = ApkAbis.fromEntryNames(
            listOf(
                "AndroidManifest.xml",
                "lib/x86_64/libmain.so",
                "lib/arm64-v8a/liba.so",
                "lib/arm64-v8a/libb.so",
                "lib/arm64-v8a/readme.txt",
                "lib/../evil/libx.so",
                "lib/nested/deep/liby.so",
                "lib/top.so",
            )
        )
        assertEquals(listOf("arm64-v8a", "x86_64"), abis)
    }

    @Test
    fun compareReportsUpdateSameDowngradeSignatureDiffAndVisibility() {
        val a = setOf("AA:BB")
        val b = setOf("CC:DD")
        val upd = ApkCompare.compare("1.0", 10L, a, archiveCode = 11L, archiveCerts = a, sdkInt = 35)
        assertEquals(Relation.UPDATE, upd.relation)
        assertFalse(upd.signatureDiffers)

        val sameSigDiff = ApkCompare.compare("1.0", 10L, a, archiveCode = 10L, archiveCerts = b, sdkInt = 35)
        assertEquals(Relation.SAME, sameSigDiff.relation)
        assertTrue(sameSigDiff.signatureDiffers)

        val down = ApkCompare.compare("2.0", 20L, a, archiveCode = 19L, archiveCerts = a, sdkInt = 35)
        assertEquals(Relation.DOWNGRADE, down.relation)
        assertFalse(down.signatureDiffers)

        val hidden = ApkCompare.compare(null, null, null, archiveCode = 5L, archiveCerts = a, sdkInt = 34)
        assertEquals(Relation.UNKNOWN, hidden.relation)
        assertTrue(hidden.visibilityLimited)

        val pre11 = ApkCompare.compare(null, null, null, archiveCode = 5L, archiveCerts = a, sdkInt = 29)
        assertFalse(pre11.visibilityLimited)
    }

    @Test
    fun installErrorsIdentifyDowngradeSignatureStorageInvalidAndAborted() {
        assertTrue(ApkNames.installError(InstallFail.OTHER, "INSTALL_FAILED_VERSION_DOWNGRADE").contains("Downgrade"))
        assertTrue(ApkNames.installError(InstallFail.CONFLICT, null).contains("Signature mismatch"))
        assertTrue(ApkNames.installError(InstallFail.STORAGE, null).contains("storage"))
        assertTrue(ApkNames.installError(InstallFail.INVALID, null).contains("read the APK"))
        assertTrue(ApkNames.installError(InstallFail.ABORTED, null).contains("cancelled"))
        assertEquals(".Main", ApkNames.shortComponent("com.app", "com.app.Main"))
        assertEquals("com.other.X", ApkNames.shortComponent("com.app", "com.other.X"))
        assertTrue(ApkNames.isApkFile("app-debug.APK"))
        assertFalse(ApkNames.isApkFile("app-debug.zip"))
    }
}
