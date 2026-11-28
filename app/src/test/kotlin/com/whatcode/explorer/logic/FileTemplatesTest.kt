package com.whatcode.explorer.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTemplatesTest {

    @Test
    fun rendersTemplateUsingFileStem() {
        assertTrue(FileTemplates.render("kotlin", "UserRepo.kt").startsWith("class UserRepo {"))
        assertTrue(FileTemplates.render("java", "9BadName.java").contains("public class _9BadName"))
        assertTrue(FileTemplates.render("markdown", "Notes.md").startsWith("# Notes"))
        assertEquals("", FileTemplates.render("empty", "plain.txt"))
    }
}
