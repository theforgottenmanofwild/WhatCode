package com.whatcode.settings

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.editor.SyntaxRole
import com.whatcode.editor.SyntaxScheme
import com.whatcode.ui.AccentColor
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Contrast
import com.whatcode.ui.ThemeMode

/**
 * Settings > Appearance. A live picture of the editor at the top shows every choice below it as it is made:
 * Light, Dark or System, the dark style (dark grey or true black), the accent colour, the font, its size, the
 * line height, the margins, and the highlight of the current line. Syntax colours have a page of their own
 * ([SyntaxColorsActivity]). Each choice is saved the moment it is tapped, and the screens that are open behind this
 * one build themselves again with it when they are shown (see [AppearanceSettings.signature]).
 */
class AppearanceActivity : SettingsPage("Appearance") {

    private val settings by lazy { AppearanceSettings(this) }

    /** The saved choices that the page on screen was drawn from, to notice a change made on the page above it. */
    private var shown = ""

    override fun onResume() {
        super.onResume()
        // Syntax colours are changed on a page opened from here; show them in the preview when coming back.
        if (shown.isNotEmpty() && shown != AppearanceSettings.signature(this)) rebuild()
    }

    override fun render() {
        content.removeAllViews()
        shown = AppearanceSettings.signature(this)
        val mode = AppTheme.mode(this)

        content.addView(
            EditorPreviewView(this, mode, settings).apply { contentDescription = "${mode.label} appearance preview" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // ---- colour mode
        content.addView(sectionTitle("COLOR MODE"))
        val modes = listOf(
            ThemeMode.LIGHT to "Bright surfaces with clear contrast",
            ThemeMode.DARK to "Low-glare colors for dim rooms",
            ThemeMode.SYSTEM to "Switches with your device's setting",
        )
        content.addView(card(*modes.map { (m, text) ->
            optionRow(SwatchView(this, m), m.label, text, mode == m) {
                if (mode != m) {
                    AppTheme.setMode(this, m)
                    rebuild()
                }
            }
        }.toTypedArray()))
        if (mode == ThemeMode.SYSTEM) {
            val now = if (AppTheme.resolvedLight(this, ThemeMode.SYSTEM)) "light" else "dark"
            content.addView(muted("Your device is using its $now appearance right now, so that is what the app shows.").apply {
                setPadding(dp(4), dp(12), dp(4), 0)
            })
        }

        content.addView(sectionTitle("DARK STYLE"))
        content.addView(toggle("True black", settings.pureBlack) {
            settings.pureBlack = it
            rebuild()
        })
        content.addView(muted("Uses black instead of dark grey for the backgrounds of the dark theme. Saves battery on OLED screens."))

        // ---- accent
        content.addView(sectionTitle("ACCENT COLOR"))
        content.addView(accentRow())
        content.addView(muted("Used for buttons, switches, links, the cursor and the status bar.").apply {
            setPadding(dp(4), dp(10), dp(4), 0)
        })

        // ---- editor text
        content.addView(sectionTitle("EDITOR FONT"))
        content.addView(card(*EditorFont.values().map { f ->
            optionRow(fontTile(f), f.label, f.note, settings.font == f) {
                settings.font = f
                render()
            }
        }.toTypedArray()))

        content.addView(sectionTitle("SIZE AND SPACING"))
        content.addView(stepper(
            "Font size", "${settings.fontSize} sp",
            settings.fontSize > AppearanceSettings.MIN_FONT_SIZE, settings.fontSize < AppearanceSettings.MAX_FONT_SIZE,
            { settings.fontSize -= 1; render() }, { settings.fontSize += 1; render() }
        ))
        content.addView(spacer(8))
        content.addView(stepper(
            "Line height", "${settings.lineSpacing}%",
            settings.lineSpacing > AppearanceSettings.MIN_LINE_SPACING, settings.lineSpacing < AppearanceSettings.MAX_LINE_SPACING,
            { settings.lineSpacing -= AppearanceSettings.LINE_SPACING_STEP; render() },
            { settings.lineSpacing += AppearanceSettings.LINE_SPACING_STEP; render() }
        ))
        content.addView(spacer(8))
        content.addView(card(*EditorSpacing.values().map { s ->
            optionRow(SpacingTile(this, s, palette.accent, palette.accentSoft), "${s.label} margins", s.note, settings.spacing == s) {
                settings.spacing = s
                render()
            }
        }.toTypedArray()))

        content.addView(spacer(12))
        content.addView(toggle("Highlight the current line", settings.highlightLine) {
            settings.highlightLine = it
            render()
        })
        content.addView(spacer(8))
        content.addView(toggle("Show line numbers", settings.lineNumbers) {
            settings.lineNumbers = it
            render()
        })
        content.addView(muted(
            "A column on the left numbers the lines of the file. A line that wraps over several rows is numbered " +
                "once, on its first row, and the number of the line with the cursor is bold. The column is as wide as the " +
                "number of the last line needs."
        ))

        // ---- syntax colours
        content.addView(sectionTitle("SYNTAX COLORS"))
        content.addView(toggle("Syntax highlighting", settings.syntaxHighlighting) { settings.syntaxHighlighting = it })
        content.addView(muted("Colors keywords, strings, comments and the rest by the language of the file. With this off the text keeps one color."))
        content.addView(muted("Choose your own colors for keywords, strings, comments and the rest, for the light and the dark theme.").apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        val custom = settings.syntaxOverrides(true).size + settings.syntaxOverrides(false).size
        content.addView(
            wide(if (custom == 0) "Syntax colors" else "Syntax colors  ·  $custom changed") {
                startActivity(Intent(this, SyntaxColorsActivity::class.java))
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(8), 0, 0) }
        )

        defaultsSection(
            "Return the mode to System, the accent to Blue, the font, size and spacing to their defaults, turn line numbers off, and every syntax color to its default?",
            "Appearance returned to default"
        ) {
            settings.resetAll()
            rebuild()
        }
    }

    // ------------------------------------------------------------------ building blocks

    private fun spacer(heightDp: Int) = View(this).also {
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    /** Rows that belong together share one rounded card, with a thin line between them. */
    private fun card(vararg rows: View): View {
        val p = palette
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.card(this@AppearanceActivity, p, 18f)
            clipToOutline = true
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                box.addView(View(this).apply { setBackgroundColor(p.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    setMargins(dp(72), 0, 0, 0)
                })
            }
            box.addView(row)
        }
        return box
    }

    /** A row with a small picture, a title and a line of explanation, and a round selector at the end. */
    private fun optionRow(leading: View, title: String, description: String, selected: Boolean, onClick: () -> Unit): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(18), dp(12))
            minimumHeight = dp(72)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(p.ripple), null, ColorDrawable(0xFFFFFFFF.toInt()))
            contentDescription = "$title. $description${if (selected) ". Selected" else ""}"
            setOnClickListener { onClick() }
        }
        row.addView(leading, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(14), 0) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(p.text)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(p.muted)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(RadioView(this, selected, p.accent, p.muted), LinearLayout.LayoutParams(dp(24), dp(24)))
        return row
    }

    /** A label with a minus button, the value and a plus button. */
    private fun stepper(
        title: String,
        value: String,
        canDecrease: Boolean,
        canIncrease: Boolean,
        onDecrease: () -> Unit,
        onIncrease: () -> Unit,
    ): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.card(this@AppearanceActivity, p, 14f)
            setPadding(dp(16), dp(8), dp(10), dp(8))
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(p.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun stepButton(label: String, enabled: Boolean, description: String, onClick: () -> Unit): View =
            button(label) { if (enabled) onClick() }.apply {
                textSize = 20f
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.4f
                contentDescription = description
                setPadding(0, 0, 0, 0)
            }

        row.addView(stepButton("−", canDecrease, "Decrease $title", onDecrease), LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(TextView(this).apply {
            text = value
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            minWidth = dp(72)
            setTextColor(p.text)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(stepButton("+", canIncrease, "Increase $title", onIncrease), LinearLayout.LayoutParams(dp(44), dp(44)))
        return row
    }

    /** One round button per accent colour; the chosen one has a ring and a tick. */
    private fun accentRow(): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = AppTheme.card(this@AppearanceActivity, p, 18f)
            setPadding(dp(6), dp(14), dp(6), dp(14))
        }
        for (accent in AccentColor.values()) {
            val color = if (p.isLight) accent.light else accent.dark
            val chosen = settings.accent == accent
            val dot = TextView(this).apply {
                text = if (chosen) "✓" else ""
                textSize = 18f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Contrast.inkOn(color, 0xFF17212C.toInt()))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (chosen) setStroke(dp(3), p.text)
                }
                isClickable = true
                isFocusable = true
                contentDescription = "${accent.label}${if (chosen) ", selected" else ""}"
                setOnClickListener {
                    if (settings.accent != accent) {
                        settings.accent = accent
                        rebuild()
                    }
                }
            }
            val cell = FrameLayout(this)
            cell.addView(dot, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        return row
    }

    /** "Aa" in the typeface of [font], on a tile. */
    private fun fontTile(font: EditorFont): View = TextView(this).apply {
        text = "Aa"
        textSize = 18f
        gravity = Gravity.CENTER
        typeface = font.typeface()
        setTextColor(palette.accent)
        background = AppTheme.rounded(this@AppearanceActivity, palette.accentSoft, 12f)
    }

    // ------------------------------------------------------------------ small drawn views

    /** A round selector: an outline, with a filled dot when chosen. */
    private class RadioView(context: Context, private val on: Boolean, private val accent: Int, private val off: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * d
            paint.color = if (on) accent else off
            canvas.drawCircle(cx, cy, 10f * d, paint)
            if (on) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, 5.5f * d, paint)
            }
        }
    }

    /** Three lines as far apart as the margins of [spacing] are, on a tile. */
    private class SpacingTile(context: Context, private val spacing: EditorSpacing, private val ink: Int, private val tile: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            paint.style = Paint.Style.FILL
            paint.color = tile
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 12f * d, 12f * d, paint)
            paint.color = ink
            val gap = (3f + spacing.verticalDp * 0.6f) * d
            val lineH = 3f * d
            val total = lineH * 3 + gap * 2
            var y = (h - total) / 2f
            val inset = (6f + spacing.horizontalDp * 0.5f) * d
            for (i in 0 until 3) {
                val right = if (i == 2) w - inset - 6f * d else w - inset
                canvas.drawRoundRect(RectF(inset, y, right, y + lineH), lineH / 2, lineH / 2, paint)
                y += lineH + gap
            }
        }
    }

    /** A small "Aa" tile in the colors of one mode; System is split corner to corner. */
    private class SwatchView(context: Context, private val mode: ThemeMode) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val bounds = RectF(0f, 0f, w, h)
            val radius = 12f * d
            val light = AppTheme.palette(context, true)
            val dark = AppTheme.palette(context, false)

            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 17f * d
            val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f

            val save = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
            if (mode != ThemeMode.DARK) drawHalf(canvas, light, w, h, baseline, if (mode == ThemeMode.SYSTEM) 0 else -1)
            if (mode != ThemeMode.LIGHT) drawHalf(canvas, dark, w, h, baseline, if (mode == ThemeMode.SYSTEM) 1 else -1)
            canvas.restoreToCount(save)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d
            paint.color = AppTheme.palette(context).outline
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }

        /** Draws one palette's tile; [part] 0 is the top-left triangle, 1 the bottom-right one, -1 the whole tile. */
        private fun drawHalf(canvas: Canvas, pal: AppTheme.Palette, w: Float, h: Float, baseline: Float, part: Int) {
            val save = canvas.save()
            if (part >= 0) {
                val tri = Path()
                if (part == 0) {
                    tri.moveTo(0f, 0f); tri.lineTo(w, 0f); tri.lineTo(0f, h)
                } else {
                    tri.moveTo(w, 0f); tri.lineTo(w, h); tri.lineTo(0f, h)
                }
                tri.close()
                canvas.clipPath(tri)
            }
            paint.style = Paint.Style.FILL
            paint.color = pal.editor
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.color = pal.accent
            canvas.drawText("Aa", w / 2f, baseline, paint)
            canvas.restoreToCount(save)
        }
    }
}

/**
 * A drawn editor window (tabs, line numbers when they are on, coloured code and a status bar) in the colours of the chosen mode, with
 * the person's font, size, line height, margins, accent, syntax colours and current-line highlight. [mode] is
 * Light or Dark for one theme, or System for both, split corner to corner. Shared by the Appearance and Syntax
 * colors pages.
 */
internal class EditorPreviewView(context: Context, private val mode: ThemeMode, private val settings: AppearanceSettings) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    private val lightPalette = AppTheme.palette(context, true)
    private val darkPalette = AppTheme.palette(context, false)
    private val lightScheme = SyntaxScheme.of(true, lightPalette.editor, settings.syntaxOverrides(true))
    private val darkScheme = SyntaxScheme.of(false, darkPalette.editor, settings.syntaxOverrides(false))

    private val codeFont = settings.font.typeface()

    // The picture is smaller than the real editor, so the text is scaled down (and kept between two sizes).
    private val textPx = (settings.fontSize * 0.8f).coerceIn(9f, 15f) * d
    private val lineH = textPx * 1.35f * settings.lineSpacing / 100f
    private val padH = settings.spacing.horizontalDp * d
    private val padV = settings.spacing.verticalDp * d
    private val tabH = 30f * d
    private val statusH = 22f * d
    private val lineCount = 5

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = tabH + padV + lineCount * lineH + padV + statusH + 4f * d
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val bounds = RectF(1f * d, 1f * d, width - 1f * d, height - 1f * d)
        val radius = 18f * d

        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        when (mode) {
            ThemeMode.LIGHT -> drawEditor(canvas, bounds, lightPalette, lightScheme)
            ThemeMode.DARK -> drawEditor(canvas, bounds, darkPalette, darkScheme)
            ThemeMode.SYSTEM -> {
                val a = canvas.save()
                canvas.clipPath(Path().apply {
                    moveTo(bounds.left, bounds.top); lineTo(bounds.right, bounds.top); lineTo(bounds.left, bounds.bottom); close()
                })
                drawEditor(canvas, bounds, lightPalette, lightScheme)
                canvas.restoreToCount(a)
                val b = canvas.save()
                canvas.clipPath(Path().apply {
                    moveTo(bounds.right, bounds.top); lineTo(bounds.right, bounds.bottom); lineTo(bounds.left, bounds.bottom); close()
                })
                drawEditor(canvas, bounds, darkPalette, darkScheme)
                canvas.restoreToCount(b)
            }
        }
        canvas.restoreToCount(save)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * d
        paint.color = AppTheme.palette(context).outline
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private fun drawEditor(canvas: Canvas, r: RectF, pal: AppTheme.Palette, scheme: SyntaxScheme) {
        paint.style = Paint.Style.FILL
        paint.color = pal.editor
        canvas.drawRect(r, paint)

        // tab bar
        paint.color = pal.rail
        canvas.drawRect(r.left, r.top, r.right, r.top + tabH, paint)
        paint.color = pal.editor
        canvas.drawRect(r.left + 10f * d, r.top + 6f * d, r.left + 96f * d, r.top + tabH, paint)
        paint.color = pal.accent
        canvas.drawRect(r.left + 10f * d, r.top + 6f * d, r.left + 96f * d, r.top + 8f * d, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f * d
        paint.color = pal.text
        canvas.drawText("Main.kt", r.left + 20f * d, r.top + 22f * d, paint)
        paint.color = pal.muted
        canvas.drawText("notes.md", r.left + 108f * d, r.top + 22f * d, paint)

        // code
        paint.typeface = codeFont
        paint.textSize = textPx
        val metrics = paint.fontMetrics
        val gutter = r.left + padH
        val codeLeft = if (settings.lineNumbers) gutter + 22f * d else gutter
        val codeTop = r.top + tabH + padV
        val lines: List<List<Pair<String, Int>>> = listOf(
            listOf("fun " to scheme.color(SyntaxRole.KEYWORD), "main" to scheme.color(SyntaxRole.FUNCTION), "() {" to pal.text),
            listOf("  val " to scheme.color(SyntaxRole.KEYWORD), "n = " to pal.text, "\"Hi\"" to scheme.color(SyntaxRole.STRING)),
            listOf("  if " to scheme.color(SyntaxRole.CONTROL), "(n) {" to pal.text),
            listOf("    " to pal.text, "print" to scheme.color(SyntaxRole.FUNCTION), "(" to pal.text, "42" to scheme.color(SyntaxRole.NUMBER), ")" to pal.text),
            listOf("  } " to pal.text, "// done" to scheme.color(SyntaxRole.COMMENT)),
        )
        lines.forEachIndexed { i, parts ->
            val top = codeTop + i * lineH
            if (settings.highlightLine && i == 1) {
                paint.color = pal.currentLine
                canvas.drawRect(r.left, top, r.right, top + lineH, paint)
            }
            val baseline = top + (lineH - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
            if (settings.lineNumbers) {
                paint.color = if (i == 1) pal.text else pal.muted
                paint.typeface = if (i == 1) Typeface.create(codeFont, Typeface.BOLD) else codeFont
                canvas.drawText("${i + 1}", gutter, baseline, paint)
                paint.typeface = codeFont
            }
            var x = codeLeft
            for ((text, color) in parts) {
                paint.color = color
                canvas.drawText(text, x, baseline, paint)
                x += paint.measureText(text)
            }
        }

        // status bar
        paint.color = pal.status
        canvas.drawRect(r.left, r.bottom - statusH, r.right, r.bottom, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10.5f * d
        paint.color = pal.onStatus
        canvas.drawText("Kotlin   UTF-8   Ln 2, Col 9", r.left + 14f * d, r.bottom - 7f * d, paint)
    }
}
