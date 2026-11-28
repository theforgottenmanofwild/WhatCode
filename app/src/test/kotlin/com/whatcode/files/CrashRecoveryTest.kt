package com.whatcode.files

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CrashRecoveryTest {

    private val temp = TempFolder()

    @After
    fun clean() = temp.delete()

    @Test
    fun writesReadsAndClearsCrashRecoveryEntries() {
        val dir = File(temp.root, "recovery")
        assertFalse(CrashRecovery.hasUncleanSession(dir))

        val e1 = CrashRecovery.RecoveryEntry("tab0", "file:///a/Main.kt", "Main.kt", "UTF-8", "fun main() {\n}", 1234L)
        CrashRecovery.markActive(dir)
        CrashRecovery.saveEntries(dir, listOf(e1))
        assertTrue(CrashRecovery.hasUncleanSession(dir))

        val loaded = CrashRecovery.readAll(dir)
        assertEquals(1, loaded.size)
        assertEquals(e1, loaded[0])

        CrashRecovery.clearAll(dir)
        assertFalse(CrashRecovery.hasUncleanSession(dir))
    }
}
