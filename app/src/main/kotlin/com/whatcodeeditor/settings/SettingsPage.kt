package com.whatcodeeditor.settings

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.whatcodeeditor.ui.AppTheme

/**
 * The frame shared by the small settings pages that are a few switches and a "Return to default" button
 * ([TypingActivity], [TabsActivity]): the top bar with the back arrow and [title], a scrolling column called
 * [content], and a few helpers to fill it. A page only says what is on it, in [render], which is called again
 * whenever the page changes.
 */
abstract class SettingsPage(private val title: String) : Activity() {

    protected val palette by lazy { AppTheme.palette(this) }
    protected val colorBg: Int get() = palette.background
    protected val colorBar: Int get() = palette.surface
    protected val colorKey: Int get() = palette.key
    protected val colorText: Int get() = palette.text
    protected val colorMuted: Int get() = palette.muted
    protected val colorAccent: Int get() = palette.accent

    protected lateinit var content: LinearLayout

    /** True while "Return to default" is waiting for its second tap. */
    private var confirmReset = false

    /** Fills [content] from the saved settings. Starts by removing everything that is there. */
    protected abstract fun render()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        buildUi()
        render()
    }

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    protected fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        AppTheme.button(this@SettingsPage, this, palette)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    /** A full-width, left-aligned button. */
    protected fun wide(label: String, onClick: () -> Unit): Button = button(label, onClick).apply {
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(16), 0, dp(16), 0)
    }

    protected fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorAccent)
        textSize = 12f
        letterSpacing = 0.1f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(24), 0, dp(10))
    }

    protected fun muted(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorMuted)
        textSize = 13f
        setPadding(0, 0, 0, dp(4))
    }

    /** A switch with its label. [onChange] gets the new state; the choice is not saved by this helper. */
    protected fun toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        text = label
        setTextColor(colorText)
        textSize = 15f
        isChecked = on
        thumbTintList = AppTheme.switchTint(palette)
        trackTintList = AppTheme.switchTint(palette)
        background = AppTheme.card(this@SettingsPage, palette, 14f)
        setPadding(dp(16), dp(10), dp(12), dp(10))
        setOnCheckedChangeListener { _, checked ->
            confirmReset = false
            onChange(checked)
        }
    }

    /** The "Defaults" section: a button that asks [question] and then calls [reset]. */
    protected fun defaultsSection(question: String, done: String, reset: () -> Unit) {
        content.addView(sectionTitle("DEFAULTS"))
        if (!confirmReset) {
            content.addView(
                wide("Return to default") {
                    confirmReset = true
                    render()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
            return
        }
        content.addView(muted(question))
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        line.addView(
            wide("Return") {
                reset()
                confirmReset = false
                Toast.makeText(this, done, Toast.LENGTH_SHORT).show()
                render()
            }.apply { setTextColor(colorAccent) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(8), 0) }
        )
        line.addView(
            wide("Cancel") {
                confirmReset = false
                render()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        content.addView(line)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }

        // Same edge-to-edge handling as the other settings pages.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        // Top bar: back arrow and the page title.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(8), dp(6), dp(16), dp(6))
        }
        val back = button("‹") { finish() }.apply {
            textSize = 28f
            contentDescription = "Back"
            background = AppTheme.pressable(this@SettingsPage, colorBar, palette.ripple, 12f)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(44)))
        bar.addView(TextView(this).apply {
            text = title
            setTextColor(colorText)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }
}
