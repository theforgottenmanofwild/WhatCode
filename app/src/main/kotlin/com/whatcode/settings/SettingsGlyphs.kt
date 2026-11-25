package com.whatcode.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** The small pictures shown at the start of each row on the Settings screen. */
enum class SettingsGlyph { APPEARANCE, KEYS, TYPING, TABS, AUTOSAVE, UPDATE, ABOUT }

/** A rounded tile with one [SettingsGlyph] drawn in [tint] on top of it. */
class SettingsGlyphView(
    context: Context,
    private val glyph: SettingsGlyph,
    private val tint: Int,
    private val tile: Int,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        paint.style = Paint.Style.FILL
        paint.color = tile
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 12f * d, 12f * d, paint)

        paint.color = tint
        paint.strokeWidth = 2f * d
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (glyph) {
            SettingsGlyph.APPEARANCE -> {
                val r = 8f * d
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, r, paint)
                paint.style = Paint.Style.FILL
                canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 90f, 180f, true, paint)
            }
            SettingsGlyph.KEYS -> {
                paint.style = Paint.Style.FILL
                for (row in 0..1) {
                    for (col in 0..2) {
                        val x = cx + (col - 1) * 6.5f * d
                        val y = cy + (row - 0.5f) * 7f * d
                        canvas.drawRoundRect(RectF(x - 2.6f * d, y - 2.6f * d, x + 2.6f * d, y + 2.6f * d), d, d, paint)
                    }
                }
            }
            SettingsGlyph.TYPING -> {
                paint.style = Paint.Style.FILL
                paint.textSize = 17f * d
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                canvas.drawText("{}", cx, cy + 5.5f * d, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.DEFAULT
            }
            SettingsGlyph.TABS -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(cx - 9f * d, cy - 4f * d, cx + 9f * d, cy + 8f * d), 3f * d, 3f * d, paint)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(cx - 9f * d, cy - 8.5f * d, cx - 0.5f * d, cy - 2f * d), 2f * d, 2f * d, paint)
            }
            SettingsGlyph.AUTOSAVE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(cx - 8f * d, cy - 8f * d, cx + 8f * d, cy + 8f * d), 3f * d, 3f * d, paint)
                paint.style = Paint.Style.FILL
                canvas.drawRect(RectF(cx - 4f * d, cy - 8f * d, cx + 3f * d, cy - 3f * d), paint)
                canvas.drawRoundRect(RectF(cx - 4.5f * d, cy + 1f * d, cx + 4.5f * d, cy + 8f * d), d, d, paint)
            }
            SettingsGlyph.UPDATE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx, cy - 7f * d, cx, cy + 3f * d, paint)
                canvas.drawLine(cx - 5f * d, cy - 2f * d, cx, cy + 3f * d, paint)
                canvas.drawLine(cx + 5f * d, cy - 2f * d, cx, cy + 3f * d, paint)
                canvas.drawLine(cx - 7f * d, cy + 8f * d, cx + 7f * d, cy + 8f * d, paint)
            }
            SettingsGlyph.ABOUT -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, 8.5f * d, paint)
                canvas.drawLine(cx, cy - 0.5f * d, cx, cy + 4.5f * d, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy - 4f * d, 1.4f * d, paint)
            }
        }
    }
}
