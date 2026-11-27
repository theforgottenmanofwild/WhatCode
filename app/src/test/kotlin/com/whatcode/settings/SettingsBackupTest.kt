package com.whatcode.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a settings backup holds, and what is thrown away from one, is checked on plain text so no device is needed. */
class SettingsBackupTest {

    private fun file(settings: String, format: String = "1", app: String = "\"WhatCode\"") =
        """{ "app": $app, "format": $format, "version": "0.0.5", "settings": $settings }"""

    private fun parsed(settings: String) = SettingsBackup.parse(file(settings))

    @Test
    fun aBackupOfSavedSettingsIsReadBackTheSame() {
        val saved = mapOf<String, Map<String, Any?>>(
            "editor" to mapOf("word_wrap" to false, "indent_default" to "tab", "close_tags" to true),
            "appearance" to mapOf("font_size" to 18, "accent" to "teal", "color_mode" to "dark", "syntax_dark_keyword" to -65536),
            "autosave" to mapOf("enabled" to true, "delay" to 10),
            "keys" to mapOf("row1" to """["shift","tab","t:{"]""", "row2@Go" to """["ctrl"]"""),
            "updates" to mapOf("auto_check" to false),
        )
        val back = SettingsBackup.parse(SettingsBackup.toJson("0.0.5", saved))
        assertNull(back.error)
        val backup = back.backup!!
        assertEquals(0, backup.skipped)
        assertEquals(12, backup.count)
        assertEquals(false, backup.sections["editor"]!!["word_wrap"])
        assertEquals("tab", backup.sections["editor"]!!["indent_default"])
        assertEquals(18, backup.sections["appearance"]!!["font_size"])
        assertEquals(-65536, backup.sections["appearance"]!!["syntax_dark_keyword"])
        assertEquals(10, backup.sections["autosave"]!!["delay"])
        assertEquals("""["shift", "tab", "t:{"]""", backup.sections["keys"]!!["row1"])
        assertEquals(false, backup.sections["updates"]!!["auto_check"])
    }

    @Test
    fun theFileIsReadableJsonWithTheAppAndVersionInIt() {
        val text = SettingsBackup.toJson("0.0.5-nightly.9", mapOf("editor" to mapOf<String, Any?>("word_wrap" to true)))
        assertTrue(text.contains("\"app\": \"WhatCode\""))
        assertTrue(text.contains("\"format\": 1"))
        assertTrue(text.contains("\"version\": \"0.0.5-nightly.9\""))
        assertTrue(text.contains("\"word_wrap\": true"))
    }

    @Test
    fun anEmptyBackupHasEmptySectionsAndIsStillAValidBackup() {
        val text = SettingsBackup.toJson("0.0.5", mapOf("editor" to emptyMap(), "appearance" to emptyMap()))
        val back = SettingsBackup.parse(text)
        assertNull(back.error)
        assertEquals(0, back.backup!!.count)
        assertEquals(setOf("appearance", "editor"), back.backup!!.sections.keys)
    }

    @Test
    fun noSavedSettingsAtAllStillMakesValidJson() {
        val back = SettingsBackup.parse(SettingsBackup.toJson("0.0.5", emptyMap()))
        assertNotNull(back.error) // nothing in it that a restore could use
        assertNull(back.backup)
    }

    @Test
    fun stateThatIsNotASettingIsNotExported() {
        val saved = mapOf<String, Map<String, Any?>>(
            "updates" to mapOf("auto_check" to true, "last_check" to 12345L, "a_version" to "9.9.9"),
            "editor" to mapOf("not_a_setting" to true),
        )
        val text = SettingsBackup.toJson("0.0.5", saved)
        assertTrue(text.contains("auto_check"))
        assertFalse(text.contains("last_check"))
        assertFalse(text.contains("a_version"))
        assertFalse(text.contains("not_a_setting"))
    }

    @Test
    fun valuesOfTheWrongKindAreNotExported() {
        val saved = mapOf<String, Map<String, Any?>>("editor" to mapOf("word_wrap" to "yes", "indent_default" to 4))
        assertFalse(SettingsBackup.toJson("0.0.5", saved).contains("word_wrap"))
    }

    @Test
    fun unknownSettingsAndSectionsAreSkippedAndCounted() {
        val back = parsed("""{ "editor": { "word_wrap": false, "from_the_future": true }, "tabs": { "a": 1, "b": 2 } }""")
        assertNull(back.error)
        assertEquals(1, back.backup!!.count)
        assertEquals(3, back.backup!!.skipped)
        assertEquals(setOf("editor"), back.backup!!.sections.keys)
    }

    @Test
    fun valuesOfTheWrongKindOrOutOfRangeAreSkipped() {
        val back = parsed(
            """{ "appearance": { "font_size": 500, "line_spacing": 150, "accent": "magenta", "pure_black": "true", "font": "sans" } }"""
        )
        val appearance = back.backup!!.sections["appearance"]!!
        assertEquals(setOf("line_spacing", "font"), appearance.keys)
        assertEquals(3, back.backup!!.skipped)
        assertEquals(2, back.backup!!.count)
    }

    @Test
    fun wholeNumbersMustBeWholeAndNotFractions() {
        val back = parsed("""{ "appearance": { "font_size": 14.5 }, "autosave": { "delay": 1e1 } }""")
        assertEquals(0, back.backup!!.count)
        assertEquals(2, back.backup!!.skipped)
    }

    @Test
    fun syntaxColorsAreKeptPerThemeAndRole() {
        val back = parsed("""{ "appearance": { "syntax_light_string": -5000000, "syntax_dark_nonsense": 1 } }""")
        assertEquals(-5000000, back.backup!!.sections["appearance"]!!["syntax_light_string"])
        assertEquals(1, back.backup!!.skipped)
    }

    @Test
    fun keyRowsKeepKnownKeysAndTextKeysAndDropTheRest() {
        val back = parsed("""{ "keys": { "row1": ["shift", "match", "nonsense", "t:<", 7], "row2": ["ctrl", "nonsense", "t:)"] } }""")
        // A row with something that is not a text is not a row of keys at all.
        assertEquals(1, back.backup!!.count)
        assertEquals(1, back.backup!!.skipped)
        assertEquals("""["ctrl", "t:)"]""", back.backup!!.sections["keys"]!!["row2"])
    }

    @Test
    fun theJumpToMatchingKeyCanBeSavedInARow() {
        val back = parsed("""{ "keys": { "row1": ["shift", "match"] } }""")
        assertEquals("""["shift", "match"]""", back.backup!!.sections["keys"]!!["row1"])
    }

    @Test
    fun aRowForOneLanguageNeedsTheLanguageName() {
        val back = parsed("""{ "keys": { "row1@Go": ["tab"], "row2@": ["tab"], "row3": ["tab"], "row1": ["tab"] } }""")
        assertEquals(setOf("row1@Go", "row1"), back.backup!!.sections["keys"]!!.keys)
        assertEquals(2, back.backup!!.skipped)
    }

    @Test
    fun aRowHoldsNoMoreKeysThanTheKeyEditorAllows() {
        val many = (1..200).joinToString(", ") { "\"t:$it\"" }
        val back = parsed("""{ "keys": { "row1": [$many] } }""")
        val kept = back.backup!!.sections["keys"]!!["row1"] as String
        assertEquals(com.whatcode.keys.KeyLayout.MAX_KEYS, kept.split("\",").size)
    }

    @Test
    fun textThatIsNotJsonIsRefused() {
        for (bad in listOf("", "hello", "{", "[1, 2", "{\"a\": }", "{\"a\": 1,}", "{'a': 1}", "{\"a\": 01}", "{\"a\": \"x\ny\"}")) {
            val r = SettingsBackup.parse(bad)
            assertNull("accepted: $bad", r.backup)
            assertNotNull(r.error)
        }
    }

    @Test
    fun jsonThatIsNotOurBackupIsRefused() {
        assertNull(SettingsBackup.parse("[1, 2, 3]").backup)
        assertNull(SettingsBackup.parse("""{ "settings": { "editor": {} } }""").backup)
        assertNull(SettingsBackup.parse(file("""{ "editor": {} }""", app = "\"Other\"")).backup)
        assertNull(SettingsBackup.parse(file("""{ "editor": {} }""", format = "\"1\"")).backup)
        assertNull(SettingsBackup.parse(file("""{ "editor": {} }""", format = "0")).backup)
        assertNull(SettingsBackup.parse(file("[]")).backup)
    }

    @Test
    fun aBackupFromANewerFormatIsRefusedWithAHint() {
        val r = SettingsBackup.parse(file("""{ "editor": {} }""", format = "2"))
        assertNull(r.backup)
        assertTrue(r.error!!.contains("newer version"))
    }

    @Test
    fun aBackupWithNoKnownSectionIsRefused() {
        val r = parsed("""{ "tabs": { "x": true } }""")
        assertNull(r.backup)
        assertNotNull(r.error)
    }

    @Test
    fun aTooBigTextIsRefusedBeforeItIsRead() {
        val r = SettingsBackup.parse(" ".repeat(SettingsBackup.MAX_BYTES + 1))
        assertNull(r.backup)
        assertTrue(r.error!!.contains("too big"))
    }

    @Test
    fun deeplyNestedJsonIsRefusedWithoutOverflowingTheStack() {
        val deep = "[".repeat(5000) + "]".repeat(5000)
        assertNull(SettingsBackup.parse(deep).backup)
    }

    @Test
    fun stringsKeepTheirEscapes() {
        val json = SettingsBackup.Json
        assertEquals("a\"b\\c\nd\u00e9", json.parse("\"a\\\"b\\\\c\\nd\\u00e9\""))
        assertEquals("\"q\\\"\\n\"", json.write("q\"\n"))
        assertEquals("tab\tand \"quote\"", json.parse(json.write("tab\tand \"quote\"") as String))
        assertEquals(listOf(1L, "x", true, null), json.parse("[1, \"x\", true, null]"))
    }

    @Test
    fun everySectionNamesTheFileItIsSavedIn() {
        assertEquals(listOf("appearance", "editor", "autosave", "keys", "updates"), SettingsBackup.SECTIONS.map { it.file })
        assertNotNull(SettingsBackup.section("keys"))
        assertNull(SettingsBackup.section("session"))
        assertNull(SettingsBackup.section("workspace"))
    }

    @Test
    fun theAppearanceSwitchesAreInTheBackupAndNeedOnOrOff() {
        val appearance = SettingsBackup.section("appearance")!!
        for (key in listOf("pure_black", "highlight_line", "syntax_highlighting", "line_numbers")) {
            assertEquals("missing $key", SettingsBackup.Kind.Flag, appearance.kindOf(key))
        }
        val back = parsed("""{ "appearance": { "line_numbers": true, "highlight_line": "yes" } }""")
        assertEquals(true, back.backup!!.sections["appearance"]!!["line_numbers"])
        assertEquals(1, back.backup!!.skipped)
        val text = SettingsBackup.toJson("0.0.5", mapOf("appearance" to mapOf<String, Any?>("line_numbers" to true)))
        assertTrue(text.contains("\"line_numbers\": true"))
    }

    @Test
    fun everySettingOfTheTypingPageIsInTheBackup() {
        val editor = SettingsBackup.section("editor")!!
        for (key in listOf(
            "auto_close", "smart_indent", "close_tags", "split_tags", "rename_tags", "match_highlight",
            "whole_line_clipboard", "plain_paste", "hardware_shortcuts", "word_wrap", "hide_keys", "keys_by_language",
            "open_in_tabs", "reopen_last", "show_keys", "show_status_bar", "indent_default",
        )) assertNotNull("missing $key", editor.kindOf(key))
        assertNull(editor.kindOf("anything_else"))
    }
}
