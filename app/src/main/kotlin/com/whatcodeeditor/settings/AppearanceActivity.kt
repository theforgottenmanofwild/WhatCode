package com.whatcodeeditor.settings

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcodeeditor.ui.AppTheme
import com.whatcodeeditor.ui.ThemeMode

/**
 * Settings > Appearance: choose Light, Dark or System. A live picture of the editor shows what the choice looks
 * like, and the choice is applied (and saved) the moment it is tapped.
 */
class AppearanceActivity : Activity() {
    private var selected = ThemeMode.SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        selected = AppTheme.mode(this)
        AppTheme.applyWindow(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        AppTheme.applyWindow(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val p = AppTheme.palette(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(18), dp(4))
            setBackgroundColor(p.surface)
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(p.text)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            text = "Appearance"
            textSize = 20f
            setTextColor(p.text)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        body.addView(
            EditorPreviewView(this, selected).apply { contentDescription = "${selected.label} appearance preview" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190))
        )

        body.addView(TextView(this).apply {
            text = "COLOR MODE"
            setTextColor(p.muted)
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(26), 0, dp(9))
        })

        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.card(this@AppearanceActivity, p, 18f)
            clipToOutline = true
        }
        val modes = listOf(
            ThemeMode.LIGHT to "Bright surfaces with clear contrast",
            ThemeMode.DARK to "Low-glare colors for dim rooms",
            ThemeMode.SYSTEM to "Switches with your device's setting",
        )
        modes.forEachIndexed { i, (mode, text) ->
            if (i > 0) {
                group.addView(View(this).apply { setBackgroundColor(p.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    setMargins(dp(72), 0, 0, 0)
                })
            }
            group.addView(optionRow(mode, text, p))
        }
        body.addView(group)

        if (selected == ThemeMode.SYSTEM) {
            val now = if (AppTheme.resolvedLight(this, ThemeMode.SYSTEM)) "light" else "dark"
            body.addView(TextView(this).apply {
                text = "Your device is using its $now appearance right now, so that is what the app shows."
                textSize = 13f
                setTextColor(p.muted)
                setPadding(dp(4), dp(14), dp(4), 0)
            })
        }

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(body, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun optionRow(mode: ThemeMode, description: String, p: AppTheme.Palette): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(18), dp(12))
            minimumHeight = dp(72)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(p.ripple), null, ColorDrawable(0xFFFFFFFF.toInt()))
            contentDescription = "${mode.label}. $description${if (selected == mode) ". Selected" else ""}"
            setOnClickListener { select(mode) }
        }
        row.addView(SwatchView(this, mode), LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(0, 0, dp(14), 0) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = mode.label
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
        row.addView(RadioView(this, selected == mode, p.accent, p.muted), LinearLayout.LayoutParams(dp(24), dp(24)))
        return row
    }

    private fun select(mode: ThemeMode) {
        if (selected == mode) return
        selected = mode
        AppTheme.setMode(this, mode)
        AppTheme.applyWindow(this)
        buildUi()
    }

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

    /** A small "Aa" tile in the colors of one mode; System is split corner to corner. */
    private class SwatchView(context: Context, private val mode: ThemeMode) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val bounds = RectF(0f, 0f, w, h)
            val radius = 12f * d
            val light = AppTheme.lightPalette
            val dark = AppTheme.darkPalette

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
            paint.color = if (AppTheme.palette(context).isLight) light.outline else dark.outline
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

    /** A drawn editor window (tabs, line numbers, colored code and a status bar) in the chosen mode's colors. */
    private class EditorPreviewView(context: Context, private val mode: ThemeMode) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private class Colors(
            val keyword: Int, val control: Int, val function: Int, val string: Int, val comment: Int, val number: Int,
        )

        private val darkColors = Colors(0xFF569CD6.toInt(), 0xFFC586C0.toInt(), 0xFFDCDCAA.toInt(), 0xFFCE9178.toInt(), 0xFF6A9955.toInt(), 0xFFB5CEA8.toInt())
        private val lightColors = Colors(0xFF0451A5.toInt(), 0xFFAF00DB.toInt(), 0xFF795E26.toInt(), 0xFFA31515.toInt(), 0xFF008000.toInt(), 0xFF098658.toInt())

        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val bounds = RectF(1f * d, 1f * d, width - 1f * d, height - 1f * d)
            val radius = 18f * d
            val light = AppTheme.lightPalette
            val dark = AppTheme.darkPalette

            val save = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
            when (mode) {
                ThemeMode.LIGHT -> drawEditor(canvas, bounds, light, lightColors)
                ThemeMode.DARK -> drawEditor(canvas, bounds, dark, darkColors)
                ThemeMode.SYSTEM -> {
                    val a = canvas.save()
                    canvas.clipPath(Path().apply {
                        moveTo(bounds.left, bounds.top); lineTo(bounds.right, bounds.top); lineTo(bounds.left, bounds.bottom); close()
                    })
                    drawEditor(canvas, bounds, light, lightColors)
                    canvas.restoreToCount(a)
                    val b = canvas.save()
                    canvas.clipPath(Path().apply {
                        moveTo(bounds.right, bounds.top); lineTo(bounds.right, bounds.bottom); lineTo(bounds.left, bounds.bottom); close()
                    })
                    drawEditor(canvas, bounds, dark, darkColors)
                    canvas.restoreToCount(b)
                }
            }
            canvas.restoreToCount(save)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * d
            paint.color = AppTheme.palette(context).outline
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }

        private fun drawEditor(canvas: Canvas, r: RectF, pal: AppTheme.Palette, c: Colors) {
            val d = resources.displayMetrics.density
            paint.style = Paint.Style.FILL
            paint.color = pal.editor
            canvas.drawRect(r, paint)

            // tab bar
            val tabH = 30f * d
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
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = 12f * d
            val gutter = r.left + 14f * d
            val codeLeft = r.left + 38f * d
            val lineH = 19f * d
            var y = r.top + tabH + 22f * d
            val lines: List<List<Pair<String, Int>>> = listOf(
                listOf("fun " to c.keyword, "main" to c.function, "() {" to pal.text),
                listOf("    val " to c.keyword, "name " to pal.text, "= " to pal.text, "\"Hello\"" to c.string),
                listOf("    if " to c.control, "(name.isNotEmpty()) {" to pal.text),
                listOf("        println(" to pal.text, "42" to c.number, ")" to pal.text),
                listOf("    // reopen this file next time" to c.comment),
            )
            lines.forEachIndexed { i, parts ->
                paint.color = pal.muted
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("${i + 1}", gutter, y, paint)
                var x = codeLeft
                for ((text, color) in parts) {
                    paint.color = color
                    canvas.drawText(text, x, y, paint)
                    x += paint.measureText(text)
                }
                y += lineH
            }

            // status bar
            val statusH = 22f * d
            paint.color = pal.status
            canvas.drawRect(r.left, r.bottom - statusH, r.right, r.bottom, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 10.5f * d
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawText("Kotlin   UTF-8   Ln 3, Col 9", r.left + 14f * d, r.bottom - 7f * d, paint)
        }
    }
}
