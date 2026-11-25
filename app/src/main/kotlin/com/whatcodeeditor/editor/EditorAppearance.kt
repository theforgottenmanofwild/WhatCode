package com.whatcodeeditor.editor

import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.whatcodeeditor.settings.AppearanceSettings
import com.whatcodeeditor.ui.AppTheme

/**
 * Gives the editor its look: the font, size, line height and margins from [settings], and its colours from
 * [palette] (text, background, selection, cursor and the current-line highlight). The syntax colours are not
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
