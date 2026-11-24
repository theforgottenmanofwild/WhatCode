package com.whatcodeeditor

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Selects the app's light, dark, or device-controlled appearance. */
class AppearanceActivity : Activity() {
    private lateinit var choices: LinearLayout
    private lateinit var description: TextView
    private var selected = ThemeMode.SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        selected = AppTheme.mode(this)
        AppTheme.applyWindow(this)
        buildUi()
        renderChoices()
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
            setPadding(dp(8), dp(6), dp(18), dp(6))
            setBackgroundColor(p.surface)
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(p.text)
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply {
            text = "☾"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(p.text)
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        header.addView(TextView(this).apply {
            text = "Color mode"
            textSize = 20f
            setTextColor(p.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        body.addView(TextView(this).apply {
            text = "Choose your appearance"
            textSize = 21f
            setTextColor(p.text)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        body.addView(TextView(this).apply {
            text = "Choose the appearance that feels right for your editor."
            textSize = 14f
            setTextColor(p.muted)
            setPadding(0, dp(6), 0, dp(24))
        })

        choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        body.addView(choices, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        description = TextView(this).apply {
            textSize = 14f
            setTextColor(p.muted)
            setPadding(0, dp(24), 0, 0)
        }
        body.addView(description)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(body, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun renderChoices() {
        val p = AppTheme.palette(this)
        choices.removeAllViews()
        val options = listOf(
            ThemeMode.LIGHT to AppTheme.lightPalette,
            ThemeMode.DARK to AppTheme.darkPalette,
            ThemeMode.SYSTEM to AppTheme.lightPalette,
        )
        options.forEachIndexed { index, pair ->
            val mode = pair.first
            val sample = pair.second
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = AppTheme.pressable(this@AppearanceActivity, p.background, p.ripple, 14f)
                contentDescription = "${mode.label} color mode${if (selected == mode) ", selected" else ""}"
                setOnClickListener { select(mode) }
                setPadding(dp(3), dp(4), dp(3), dp(8))
            }
            val preview = ThemePreviewView(this, mode, sample, selected == mode).apply {
                contentDescription = "${mode.label} appearance preview"
            }
            tile.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val label = TextView(this).apply {
                text = mode.label
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (selected == mode) p.accent else p.text)
                typeface = if (selected == mode) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setPadding(0, dp(8), 0, 0)
            }
            tile.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (index < options.lastIndex) {
                choices.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, dp(8), 0)
                })
            } else {
                choices.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        description.text = when (selected) {
            ThemeMode.LIGHT -> "Light keeps the editor bright, with soft surfaces and clear contrast."
            ThemeMode.DARK -> "Dark uses a low-glare palette for dim environments."
            ThemeMode.SYSTEM -> "System follows your Android device's light or dark appearance."
        }
    }

    private fun select(mode: ThemeMode) {
        if (selected == mode) return
        selected = mode
        AppTheme.setMode(this, mode)
        AppTheme.applyWindow(this)
        buildUi()
        renderChoices()
    }

    /** A small, real palette preview rather than a static screenshot or a fake app screen. */
    private class ThemePreviewView(
        context: android.content.Context,
        private val mode: ThemeMode,
        private val sample: AppTheme.Palette,
        private val selected: Boolean,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
            val measuredWidth = if (widthMode == View.MeasureSpec.UNSPECIFIED) dp(120)
            else View.MeasureSpec.getSize(widthMeasureSpec)
            val desiredHeight = (measuredWidth * 0.62f).toInt().coerceIn(dp(58), dp(124))
            val measuredHeight = View.resolveSize(desiredHeight, heightMeasureSpec)
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val inset = 4f * density
            val bounds = RectF(inset, inset, width - inset, height - inset)
            val radius = 13f * density
            paint.style = Paint.Style.FILL
            paint.color = sample.background
            canvas.drawRoundRect(bounds, radius, radius, paint)

            val outerPath = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
            val save = canvas.save()
            canvas.clipPath(outerPath)
            if (mode == ThemeMode.SYSTEM) {
                paint.color = AppTheme.darkPalette.background
                canvas.drawRect(bounds, paint)
                paint.color = AppTheme.lightPalette.background
                val split = Path().apply {
                    moveTo(bounds.left, bounds.top)
                    lineTo(bounds.right, bounds.top)
                    lineTo(bounds.left, bounds.bottom)
                    close()
                }
                canvas.drawPath(split, paint)
            }

            val mini = RectF(bounds.left + 8f * density, bounds.top + 12f * density, bounds.right - 8f * density, bounds.bottom - 12f * density)
            val miniRadius = 9f * density
            val miniPath = Path().apply { addRoundRect(mini, miniRadius, miniRadius, Path.Direction.CW) }
            val miniSave = canvas.save()
            canvas.clipPath(miniPath)
            paint.color = if (mode == ThemeMode.SYSTEM) AppTheme.darkPalette.surface else sample.surface
            canvas.drawRect(mini, paint)
            if (mode == ThemeMode.SYSTEM) {
                paint.color = AppTheme.lightPalette.surface
                val miniSplit = Path().apply {
                    moveTo(mini.left, mini.top)
                    lineTo(mini.right, mini.top)
                    lineTo(mini.left, mini.bottom)
                    close()
                }
                canvas.drawPath(miniSplit, paint)
            }
            canvas.restoreToCount(miniSave)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = sample.outline
            canvas.drawRoundRect(mini, 9f * density, 9f * density, paint)
            paint.style = Paint.Style.FILL

            val lineLeft = mini.left + 10f * density
            paint.color = sample.muted
            canvas.drawRoundRect(RectF(lineLeft, mini.top + 10f * density, lineLeft + mini.width() * 0.47f, mini.top + 14f * density), 2f * density, 2f * density, paint)
            canvas.drawRoundRect(RectF(lineLeft, mini.top + 19f * density, lineLeft + mini.width() * 0.32f, mini.top + 23f * density), 2f * density, 2f * density, paint)
            paint.color = sample.accent
            canvas.drawCircle(mini.right - 17f * density, mini.bottom - 16f * density, 6f * density, paint)
            paint.color = sample.accentSoft
            canvas.drawRoundRect(RectF(mini.left + 9f * density, mini.bottom - 21f * density, mini.left + 34f * density, mini.bottom - 11f * density), 4f * density, 4f * density, paint)
            canvas.restoreToCount(save)

            if (selected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                paint.color = AppTheme.palette(context).accent
                canvas.drawRoundRect(bounds, radius, radius, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = density
                paint.color = sample.outline
                canvas.drawRoundRect(bounds, radius, radius, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }
}
