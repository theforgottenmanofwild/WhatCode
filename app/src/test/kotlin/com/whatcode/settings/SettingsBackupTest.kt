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
    fun theFileExplorerSettingsAreBackedUp() {
        val saved = mapOf<String, Map<String, Any?>>(
            "explorer_view" to mapOf(
                "mode" to "GRID", "sort" to "DATE", "descending" to true, "show_hidden" to false, "density" to "DENSE",
                "tile_size" to "LARGE", "show_size" to false, "show_date" to true, "date_style" to "RELATIVE",
                "clock" to "H24", "decimal_sizes" to true, "show_extensions" to false, "folder_counts" to true,
                "file_icons" to false, "folders_first" to false, "natural_order" to false, "ignore_case" to false,
            ),
            "explorer" to mapOf(
                "start" to "FOLDER", "start_folder" to "/storage/emulated/0/Projects", "last_folder" to "/x",
                "path_box" to false, "pull_to_refresh" to false, "back_goes_up" to false, "menu_button" to false,
                "text_tap" to "ASK", "other_tap" to "OPEN_WITH", "long_press" to "MENU", "confirm_delete" to false,
                "paste_clash" to "SKIP", "row_menu" to "share,-rename,copy,move,delete,properties,copy_path,open_with",
            ),
        )
        val back = SettingsBackup.parse(SettingsBackup.toJson("0.0.6", saved)).backup!!
        assertEquals(0, back.skipped)
        // Everything but the last folder, which is where the explorer was and not a choice.
        assertEquals(17 + 12, back.count)
        assertEquals("GRID", back.sections["explorer_view"]!!["mode"])
        assertEquals("/storage/emulated/0/Projects", back.sections["explorer"]!!["start_folder"])
        assertNull(back.sections["explorer"]!!["last_folder"])
        assertEquals("SKIP", back.sections["explorer"]!!["paste_clash"])
        assertEquals("share,-rename,copy,move,delete,properties,copy_path,open_with", back.sections["explorer"]!!["row_menu"])
    }

    @Test
    fun aRowMenuThatIsNotValidIsSkipped() {
        for (bad in listOf("rename,rename", "teleport", "", "rename,,copy", "a".repeat(500))) {
            val back = parsed("""{ "explorer": { "row_menu": "$bad", "paste_clash": "REPLACE", "long_press": "NEVER" } }""").backup!!
            assertEquals("row menu accepted: $bad", setOf("paste_clash"), back.sections["explorer"]!!.keys)
            assertEquals(2, back.skipped)
        }
    }

    @Test
    fun fileExplorerChoicesThatDoNotExistAreSkipped() {
        val back = parsed(
            """{ "explorer_view": { "mode": "LIST", "tile_size": "HUGE", "density": "DENSE" },""" +
                """ "explorer": { "start": "SOMEWHERE", "start_folder": "a\nb", "other_tap": "MESSAGE" } }"""
        ).backup!!
        assertEquals(setOf("density"), back.sections["explorer_view"]!!.keys)
        assertEquals(setOf("other_tap"), back.sections["explorer"]!!.keys)
        assertEquals(4, back.skipped)
    }

    @Test
    fun aStartFolderThatIsTooLongIsSkipped() {
        val long = "/" + "a".repeat(com.whatcode.explorer.ExplorerSettings.MAX_PATH)
        val back = parsed("""{ "explorer": { "start_folder": "$long", "path_box": true } }""").backup!!
        assertEquals(1, back.count)
        assertEquals(1, back.skipped)
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
        assertEquals(
            listOf("appearance", "editor", "autosave", "large_files", "keys", "explorer_view", "explorer", "explorer_apk", "snippets", "updates"),
            SettingsBackup.SECTIONS.map { it.file },
        )
        assertNotNull(SettingsBackup.section("keys"))
        assertNotNull(SettingsBackup.section("explorer_apk"))
        assertNull(SettingsBackup.section("session"))
        assertNull(SettingsBackup.section("workspace"))
    }

    @Test
    fun theApkSettingsAreBackedUp() {
        val back = parsed(
            """{ "explorer_apk": { "apk_tap": "BROWSE", "confirm_install": false, "after_install": "ALWAYS",
                "row_app_info": false, "icon_cache_mb": 16, "show_sha256": true, "show_md5": false,
                "warn_downgrade": false, "warn_signature": true, "latest_build_shortcut": false,
                "extract_folder": "/sdcard/Unzipped", "zip_bomb_max_mb": 500, "zip_bomb_max_entries": 50000 } }""",
        ).backup!!
        val apk = back.sections["explorer_apk"]!!
        assertEquals("BROWSE", apk["apk_tap"])
        assertEquals(false, apk["confirm_install"])
        assertEquals("ALWAYS", apk["after_install"])
        assertEquals(16, apk["icon_cache_mb"])
        assertEquals(false, apk["latest_build_shortcut"])
        assertEquals("/sdcard/Unzipped", apk["extract_folder"])
        assertEquals(500, apk["zip_bomb_max_mb"])
        assertEquals(50000, apk["zip_bomb_max_entries"])
        assertEquals(0, back.skipped)
        assertEquals(
            3,
            parsed("""{ "explorer_apk": { "apk_tap": "RUN", "icon_cache_mb": 64, "zip_bomb_max_mb": 5 } }""").backup!!.skipped,
        )
    }

    @Test
    fun theAppearanceSwitchesAreInTheBackupAndNeedOnOrOff() {
        val appearance = SettingsBackup.section("appearance")!!
        for (key in listOf("pure_black", "highlight_line", "syntax_highlighting", "line_numbers", "show_whitespace")) {
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
            "open_in_tabs", "reopen_last", "show_keys", "show_status_bar", "indent_default", "quote_attributes",
            "show_scope", "bookmarks", "jump_history", "code_folding", "fold_markers", "fold_by", "fold_min_lines",
        )) assertNotNull("missing $key", editor.kindOf(key))
        assertNull(editor.kindOf("anything_else"))
    }

    @Test
    fun theCodeFoldingChoicesAreBackedUp() {
        val back = parsed("""{ "editor": { "code_folding": false, "fold_markers": false, "fold_by": "brackets", "fold_min_lines": 5 } }""").backup!!
        val ed = back.sections["editor"]!!
        assertEquals(false, ed["code_folding"])
        assertEquals(false, ed["fold_markers"])
        assertEquals("brackets", ed["fold_by"])
        assertEquals(5, ed["fold_min_lines"])
        assertEquals(0, back.skipped)
        assertEquals(2, parsed("""{ "editor": { "fold_by": "other", "fold_min_lines": 99 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "fold_min_lines": 0 } }""").backup!!.skipped)
    }
    @Test
    fun theGuidesAndTheRulerAreBackedUp() {
        val appearance = SettingsBackup.section("appearance")!!
        for (key in listOf("indent_guides", "ruler")) assertEquals(key, SettingsBackup.Kind.Flag, appearance.kindOf(key))
        val back = parsed("""{ "appearance": { "ruler_column": 100 } }""").backup!!
        assertEquals(100, back.sections["appearance"]!!["ruler_column"])
        assertEquals(1, parsed("""{ "appearance": { "ruler_column": 500, "ruler": true } }""").backup!!.skipped)
    }

    @Test
    fun theTextToolChoicesAreBackedUp() {
        val editor = SettingsBackup.section("editor")!!
        for (key in listOf("text_tools", "sort_ignore_case", "sort_natural", "unique_ignore_case")) {
            assertEquals("missing $key", SettingsBackup.Kind.Flag, editor.kindOf(key))
        }
        val back = parsed("""{ "editor": { "tab_width": 8, "sort_natural": false } }""").backup!!
        assertEquals(8, back.sections["editor"]!!["tab_width"])
        assertEquals(false, back.sections["editor"]!!["sort_natural"])
        for (bad in listOf("0", "17", "\"4\"")) {
            assertEquals(bad, 1, parsed("""{ "editor": { "tab_width": $bad, "sort_natural": true } }""").backup!!.skipped)
        }
    }

    @Test
    fun theJsonIndentIsBackedUp() {
        val back = parsed("""{ "editor": { "json_indent": 0 } }""").backup!!
        assertEquals(0, back.sections["editor"]!!["json_indent"])
        assertEquals(8, parsed("""{ "editor": { "json_indent": 8 } }""").backup!!.sections["editor"]!!["json_indent"])
        for (bad in listOf("-1", "9", "\"2\"", "true")) {
            assertEquals(bad, 1, parsed("""{ "editor": { "json_indent": $bad, "sort_natural": true } }""").backup!!.skipped)
        }
    }

    @Test
    fun theSnippetChoicesAreBackedUp() {
        val editor = SettingsBackup.section("editor")!!
        for (key in listOf("snippets", "snippets_tab", "snippets_builtin", "abbreviations")) {
            assertEquals("missing $key", SettingsBackup.Kind.Flag, editor.kindOf(key))
        }
        val back = parsed("""{ "editor": { "snippets": false, "snippets_tab": false, "snippets_builtin": true } }""").backup!!
        assertEquals(false, back.sections["editor"]!!["snippets"])
        assertEquals(false, back.sections["editor"]!!["snippets_tab"])
        assertEquals(1, parsed("""{ "editor": { "snippets": "yes", "snippets_tab": true } }""").backup!!.skipped)
    }

    @Test
    fun theCompletionChoicesAreBackedUp() {
        val editor = SettingsBackup.section("editor")!!
        for (key in listOf("completion", "completion_other", "completion_enter")) {
            assertEquals("missing $key", SettingsBackup.Kind.Flag, editor.kindOf(key))
        }
        assertNotNull(editor.kindOf("completion_min"))
        assertNotNull(editor.kindOf("completion_delay"))
        val back = parsed("""{ "editor": { "completion": false, "completion_min": 2, "completion_delay": 0, "completion_enter": true } }""").backup!!
        assertEquals(false, back.sections["editor"]!!["completion"])
        assertEquals(2, (back.sections["editor"]!!["completion_min"] as Number).toInt())
        assertEquals(0, (back.sections["editor"]!!["completion_delay"] as Number).toInt())
        assertEquals(true, back.sections["editor"]!!["completion_enter"])
    }

    @Test
    fun completionNumbersOutsideTheirRangeAreSkipped() {
        assertEquals(1, parsed("""{ "editor": { "completion_min": 0 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "completion_min": 9 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "completion_delay": 5000 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "completion": "yes" } }""").backup!!.skipped)
    }

    @Test
    fun theMultipleCursorChoicesAreBackedUp() {
        val editor = SettingsBackup.section("editor")!!
        for (key in listOf("multi_cursor", "multi_cursor_case", "multi_cursor_word", "multi_cursor_lines")) {
            assertEquals("missing $key", SettingsBackup.Kind.Flag, editor.kindOf(key))
        }
        assertNotNull(editor.kindOf("multi_cursor_max"))
        val back = parsed("""{ "editor": { "multi_cursor": false, "multi_cursor_max": 50, "multi_cursor_word": true, "multi_cursor_lines": false } }""").backup!!
        assertEquals(false, back.sections["editor"]!!["multi_cursor"])
        assertEquals(50, (back.sections["editor"]!!["multi_cursor_max"] as Number).toInt())
        assertEquals(true, back.sections["editor"]!!["multi_cursor_word"])
        assertEquals(false, back.sections["editor"]!!["multi_cursor_lines"])
    }

    @Test
    fun theMostCursorsOutsideItsRangeIsSkipped() {
        assertEquals(1, parsed("""{ "editor": { "multi_cursor_max": 1 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "multi_cursor_max": 1001 } }""").backup!!.skipped)
        assertEquals(1, parsed("""{ "editor": { "multi_cursor": "yes" } }""").backup!!.skipped)
        assertEquals(0, parsed("""{ "editor": { "multi_cursor_max": 2 } }""").backup!!.skipped)
        assertEquals(0, parsed("""{ "editor": { "multi_cursor_max": 1000 } }""").backup!!.skipped)
    }

    @Test
    fun yourSnippetsAreBackedUpAsAListAndChecked() {
        val saved = mapOf<String, Map<String, Any?>>(
            "snippets" to mapOf("user" to """[{"prefix": "todo", "name": "To do", "lang": "Go", "body": "// TODO\n${'$'}0"}]"""),
        )
        val text = SettingsBackup.toJson("0.0.6", saved)
        assertTrue(text, text.contains("\"prefix\": \"todo\""))
        val back = SettingsBackup.parse(text).backup!!
        assertEquals(0, back.skipped)
        val stored = back.sections["snippets"]!!["user"] as String
        val list = com.whatcode.editor.snippets.SnippetFile.decodeSaved(stored)
        assertEquals("todo", list.single().prefix)
        assertEquals("Go", list.single().lang)
        assertEquals("// TODO\n${'$'}0", list.single().body)

        // A snippet that fails a check is dropped; the rest are kept.
        val mixed = parsed("""{ "snippets": { "user": [ { "prefix": "ok", "body": "x" }, { "prefix": "no good", "body": "x" } ] } }""").backup!!
        val kept = com.whatcode.editor.snippets.SnippetFile.decodeSaved(mixed.sections["snippets"]!!["user"] as String)
        assertEquals(listOf("ok"), kept.map { it.prefix })
        assertEquals(1, parsed("""{ "snippets": { "user": "not a list" } }""").backup?.skipped ?: 1)
    }

    @Test
    fun theLargeFileSettingsAreBackedUp() {
        val back = parsed("""{ "large_files": { "long_line": 50000, "highlight_delay": 300, "highlight_margin": 100 } }""").backup!!
        assertEquals(50000, back.sections["large_files"]!!["long_line"])
        assertEquals(300, back.sections["large_files"]!!["highlight_delay"])
        assertEquals(100, back.sections["large_files"]!!["highlight_margin"])
        val text = SettingsBackup.toJson("0.0.6", mapOf("large_files" to mapOf<String, Any?>("long_line" to 20000, "other" to 1)))
        assertTrue(text.contains("\"long_line\": 20000"))
        assertFalse(text.contains("other"))
    }

    @Test
    fun theLargeFileModeChoicesAreBackedUp() {
        val back = parsed(
            """{ "large_files": { "large_file_mode": false, "large_above": 100000, "very_large_above": 5000000,
                "large_off": 3, "very_large_off": 12, "large_undo_steps": 50 } }""",
        ).backup!!
        val saved = back.sections["large_files"]!!
        assertEquals(false, saved["large_file_mode"])
        assertEquals(100000, saved["large_above"])
        assertEquals(5000000, saved["very_large_above"])
        assertEquals(3, saved["large_off"])
        assertEquals(12, saved["very_large_off"])
        assertEquals(50, saved["large_undo_steps"])
        assertEquals(0, back.skipped)
    }

    @Test
    fun largeFileModeChoicesOutsideTheirRangeAreSkipped() {
        for ((key, bad) in listOf(
            "large_above" to listOf(19_999, 100_000_001),
            "very_large_above" to listOf(0, 100_000_001),
            "large_off" to listOf(-1, 16),
            "very_large_off" to listOf(-1, 99),
            "large_undo_steps" to listOf(9, 1_001),
        )) for (n in bad) {
            val back = parsed("""{ "large_files": { "$key": $n } }""").backup!!
            assertEquals("$key $n accepted", emptySet<String>(), back.sections["large_files"]!!.keys)
            assertEquals(1, back.skipped)
        }
        val wrong = parsed("""{ "large_files": { "large_file_mode": "yes" } }""").backup!!
        assertEquals(1, wrong.skipped)
        val edges = parsed("""{ "large_files": { "large_above": 20000, "very_large_above": 100000000, "large_off": 0, "very_large_off": 15, "large_undo_steps": 1000 } }""").backup!!
        assertEquals(5, edges.count)
    }

    @Test
    fun largeFileNumbersOutsideTheirRangeAreSkipped() {
        for ((key, bad) in listOf(
            "long_line" to listOf(999, 1_000_001, -5),
            "highlight_delay" to listOf(49, 5_001),
            "highlight_margin" to listOf(9, 1_001),
        )) for (n in bad) {
            val back = parsed("""{ "large_files": { "$key": $n } }""").backup!!
            assertEquals("$key $n accepted", emptySet<String>(), back.sections["large_files"]!!.keys)
            assertEquals(1, back.skipped)
        }
        val limits = parsed("""{ "large_files": { "long_line": 1000, "highlight_delay": 5000, "highlight_margin": 10 } }""").backup!!
        assertEquals(3, limits.count)
    }

    @Test
    fun viewerSettingsInLargeFilesAreBackedUp() {
        val back = parsed(
            """{ "large_files": { "viewer_above": 16000000, "viewer_open": "ALWAYS", "viewer_wrap": true,
                "viewer_follow_end": true, "viewer_chunk_lines": 512, "viewer_search_cap": 10000 } }""",
        ).backup!!
        val lf = back.sections["large_files"]!!
        assertEquals(16_000_000, lf["viewer_above"])
        assertEquals("ALWAYS", lf["viewer_open"])
        assertEquals(true, lf["viewer_wrap"])
        assertEquals(true, lf["viewer_follow_end"])
        assertEquals(512, lf["viewer_chunk_lines"])
        assertEquals(10_000, lf["viewer_search_cap"])
        assertEquals(0, back.skipped)
        assertEquals(3, parsed("""{ "large_files": { "viewer_open": "NOPE", "viewer_chunk_lines": 8, "viewer_search_cap": 10 } }""").backup!!.skipped)
    }

    @Test
    fun explorerCompletionFlagsAreBackedUp() {
        val back = parsed(
            """{ "explorer_view": { "per_folder_sort": true },
                 "explorer": { "use_trash": false, "show_bookmarks": true, "show_recent_files": false, "show_storage_roots": true } }""",
        ).backup!!
        assertEquals(true, back.sections["explorer_view"]!!["per_folder_sort"])
        assertEquals(false, back.sections["explorer"]!!["use_trash"])
        assertEquals(false, back.sections["explorer"]!!["show_recent_files"])
        assertEquals(0, back.skipped)
    }
}
