package com.whatcode.editor

import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.whatcode.settings.AppearanceSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Contrast

/**
 * Gives the editor its look: the font, size, line height and margins from [settings], whether it has line numbers and whitespace marks, and
 * its colours from [palette] (text, background, selection, cursor, the current-line highlight, the line numbers and the marks). The syntax colours are not
 * part of this; they are put on the text by [Highlighter] with a [SyntaxScheme].
 */
fun CodeEditText.applyAppearance(settings: AppearanceSettings, palette: AppTheme.Palette) {
    val density = resources.displayMetrics.density
    typeface = settings.font.typeface()
    textSize = settings.fontSize.toFloat()
    setLineSpacing(0f, settings.lineSpacing / 100f)
    val spacing = settings.spacing
    val h = (spacing.horizontalDp * density).toInt()
    val v = (spacing.verticalDp * density).toInt()
    setPadding(h, v, h, v)

    setTextColor(palette.text)
    setBackgroundColor(palette.editor)
    highlightColor = palette.selection
    currentLineColor = if (settings.highlightLine) palette.currentLine else 0
    // The gutter uses the theme's muted colour for the numbers, the text colour for the line with the cursor, and the
    // outline colour for the line between the numbers and the text. Its font and size are the editor's own.
    gutterColor = palette.muted
    gutterCurrentColor = palette.text
    gutterDividerColor = palette.outline
    bookmarkColor = palette.accent
    showLineNumbers = settings.lineNumbers
    whitespaceColor = palette.muted
    showWhitespace = settings.showWhitespace
    // The matching brackets get a soft tint of the accent, so the text on them stays readable in every theme.
    bracketColor = Contrast.blend(palette.editor, palette.accent, 0.35f)

    // The cursor takes the accent colour. Setting its drawable needs Android 10; older versions keep the system one.
    if (Build.VERSION.SDK_INT >= 29) {
        try {
            textCursorDrawable = GradientDrawable().apply {
                setColor(palette.accent)
                setSize((2 * density).toInt().coerceAtLeast(2), -1)
            }
        } catch (_: Exception) {
            // keep the default cursor
        }
    }
}
