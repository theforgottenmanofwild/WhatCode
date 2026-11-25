package com.whatcodeeditor.ui

import com.whatcodeeditor.editor.SyntaxRole
import com.whatcodeeditor.editor.SyntaxScheme
import com.whatcodeeditor.settings.AppearanceSettings
import com.whatcodeeditor.settings.EditorFont
import com.whatcodeeditor.settings.EditorSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme, accent and dark style is checked for readable text, without a device. */
class ThemeContrastTest {

    private fun all(): List<Triple<String, AccentColor, AppTheme.Palette>> {
        val out = ArrayList<Triple<String, AccentColor, AppTheme.Palette>>()
        for (accent in AccentColor.values()) {
            out.add(Triple("light ${accent.key}", accent, AppTheme.buildPalette(true, accent, false)))
            out.add(Triple("dark ${accent.key}", accent, AppTheme.buildPalette(false, accent, false)))
            out.add(Triple("black ${accent.key}", accent, AppTheme.buildPalette(false, accent, true)))
        }
        return out
    }

    private fun atLeast(name: String, what: String, a: Int, b: Int, min: Double) {
        val r = Contrast.ratio(a, b)
        assertTrue("$name: $what has contrast %.2f, needs %.1f".format(r, min), r >= min)
    }

    @Test
    fun textIsReadableOnEverySurface() {
        for ((name, _, p) in all()) {
            for ((surfaceName, surface) in listOf("background" to p.background, "surface" to p.surface, "raised" to p.raised,
                "rail" to p.rail, "key" to p.key, "editor" to p.editor, "code" to p.code)) {
                atLeast(name, "text on $surfaceName", p.text, surface, Contrast.TEXT)
                atLeast(name, "muted text on $surfaceName", p.muted, surface, Contrast.TEXT)
            }
        }
    }

    @Test
    fun accentIsReadableAsTextAndInk() {
        for ((name, _, p) in all()) {
            for ((surfaceName, surface) in listOf("background" to p.background, "surface" to p.surface, "editor" to p.editor,
                "soft accent" to p.accentSoft)) {
                atLeast(name, "accent on $surfaceName", p.accent, surface, Contrast.TEXT)
            }
            atLeast(name, "text on a filled accent", p.onAccent, p.accent, Contrast.TEXT)
            atLeast(name, "locked text on a filled accent", p.lockedText, p.accent, Contrast.TEXT)
            atLeast(name, "status bar text", p.onStatus, p.status, Contrast.TEXT)
        }
    }

    @Test
    fun selectionAndCurrentLineKeepTheTextReadable() {
        for ((name, _, p) in all()) {
            // The selection is translucent; the worst case is the full colour under the text.
            atLeast(name, "text on the current line", p.text, p.currentLine, Contrast.TEXT)
            val selected = Contrast.blend(p.editor, p.selection or 0xFF000000.toInt(), (p.selection ushr 24) / 255f)
            atLeast(name, "text on the selection", p.text, selected, Contrast.TEXT)
        }
    }

    @Test
    fun trueBlackOnlyChangesTheDarkTheme() {
        for (accent in AccentColor.values()) {
            assertEquals(AppTheme.buildPalette(true, accent, false), AppTheme.buildPalette(true, accent, true))
            assertEquals(0xFF000000.toInt(), AppTheme.buildPalette(false, accent, true).editor)
            assertNotEquals(0xFF000000.toInt(), AppTheme.buildPalette(false, accent, false).editor)
        }
    }

    @Test
    fun accentsAreDistinctAndTheOriginalBlueIsKept() {
        assertEquals(AccentColor.values().size, AccentColor.values().map { it.key }.toSet().size)
        assertEquals(AccentColor.values().size, AccentColor.values().map { it.dark }.toSet().size)
        assertEquals(AccentColor.BLUE, AccentColor.from(null))
        assertEquals(AccentColor.BLUE, AccentColor.from("not-an-accent"))
        assertEquals(0xFF176EB5.toInt(), AppTheme.lightPalette.accent)
        assertEquals(0xFF65B2F7.toInt(), AppTheme.darkPalette.accent)
    }

    @Test
    fun modesFallBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("sepia"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
    }

    // ------------------------------------------------------------------ syntax colours

    @Test
    fun defaultSyntaxColorsAreReadableOnEveryEditorBackground() {
        for ((name, _, p) in all()) {
            val scheme = SyntaxScheme.of(p.isLight, p.editor)
            for (role in SyntaxRole.values()) {
                atLeast(name, "${role.key} syntax color", scheme.color(role), p.editor, Contrast.MINIMUM)
            }
        }
    }

    @Test
    fun aChosenColorThatCannotBeReadIsMovedAndOneThatCanIsKept() {
        val black = 0xFF000000.toInt()
        val scheme = SyntaxScheme.of(false, black, mapOf(SyntaxRole.STRING to black, SyntaxRole.KEYWORD to 0xFFFFD700.toInt()))
        atLeast("true black", "string chosen as black", scheme.color(SyntaxRole.STRING), black, Contrast.MINIMUM)
        assertEquals(0xFFFFD700.toInt(), scheme.color(SyntaxRole.KEYWORD))
        assertEquals(SyntaxRole.COMMENT.darkDefault, SyntaxScheme.of(false, black).color(SyntaxRole.COMMENT))
    }

    @Test
    fun theHighlighterMarkerBecomesTheShownColor() {
        val scheme = SyntaxScheme.of(true, AppTheme.lightPalette.editor, mapOf(SyntaxRole.NUMBER to 0xFF112233.toInt()))
        assertEquals(0xFF112233.toInt(), scheme.resolve(SyntaxRole.NUMBER.darkDefault))
        assertEquals(SyntaxRole.KEYWORD.lightDefault, scheme.resolve(SyntaxRole.KEYWORD.darkDefault))
        assertEquals(0x12345678, scheme.resolve(0x12345678))
    }

    @Test
    fun everyRoleHasItsOwnMarkerAndKey() {
        val roles = SyntaxRole.values()
        assertEquals(roles.size, roles.map { it.darkDefault }.toSet().size)
        assertEquals(roles.size, roles.map { it.key }.toSet().size)
        for (role in roles) assertEquals(role, SyntaxRole.from(role.key))
        assertNull(SyntaxRole.from("nope"))
    }

    // ------------------------------------------------------------------ helpers

    @Test
    fun contrastMathMatchesWcag() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.001)
        assertEquals(1.0, Contrast.ratio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.001)
        assertEquals(0xFF000000.toInt(), Contrast.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Contrast.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
    }

    @Test
    fun hexCodesAreReadAndWritten() {
        assertEquals(0xFF1A2B3C.toInt(), Hex.parse("#1a2b3c"))
        assertEquals(0xFF1A2B3C.toInt(), Hex.parse("1A2B3C"))
        assertEquals(0xFFAABBCC.toInt(), Hex.parse("#abc"))
        assertNull(Hex.parse("#12345"))
        assertNull(Hex.parse("#GGGGGG"))
        assertNull(Hex.parse(""))
        assertEquals("#1A2B3C", Hex.format(0xFF1A2B3C.toInt()))
        assertEquals("#000001", Hex.format(0xFF000001.toInt()))
    }

    // ------------------------------------------------------------------ limits of the other settings

    @Test
    fun fontSizeAndLineSpacingStayInRange() {
        assertEquals(AppearanceSettings.MIN_FONT_SIZE, AppearanceSettings.clampFontSize(-5))
        assertEquals(AppearanceSettings.MAX_FONT_SIZE, AppearanceSettings.clampFontSize(900))
        assertEquals(15, AppearanceSettings.clampFontSize(15))
        assertEquals(100, AppearanceSettings.clampLineSpacing(0))
        assertEquals(200, AppearanceSettings.clampLineSpacing(999))
        assertEquals(120, AppearanceSettings.clampLineSpacing(118))
        assertEquals(AppearanceSettings.DEFAULT_LINE_SPACING, AppearanceSettings.clampLineSpacing(AppearanceSettings.DEFAULT_LINE_SPACING))
    }

    @Test
    fun unknownSavedChoicesFallBackToTheDefaults() {
        assertEquals(EditorFont.MONOSPACE, EditorFont.from(null))
        assertEquals(EditorFont.MONOSPACE, EditorFont.from("comic"))
        assertEquals(EditorFont.SANS, EditorFont.from("sans"))
        assertEquals(EditorSpacing.NORMAL, EditorSpacing.from(null))
        assertEquals(EditorSpacing.NORMAL, EditorSpacing.from("huge"))
        assertEquals(EditorSpacing.COMPACT, EditorSpacing.from("compact"))
    }
}
