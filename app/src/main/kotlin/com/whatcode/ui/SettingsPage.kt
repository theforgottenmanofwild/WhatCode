package com.whatcode.ui

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

/**
 * The one frame every settings screen is built on (the Settings hub, Appearance, Typing, Tabs, Autosave, the Key
 * Editor, Update App, Import and export, Syntax colors): the top bar with the back arrow and [title], the edge-to-edge
 * window handling, a scrolling column called [content], and the helpers to fill it. A page only says what is on it,
 * in [render], which is called again whenever the page changes.
 *
 * A page that has to set something up before its first [render] (a controller, a loaded list) does it in [onPrepare].
 */
abstract class SettingsPage(private val title: String) : Activity() {

    private var currentPalette: AppTheme.Palette? = null

    /** The colours of the theme that is showing. [rebuild] reads them again after the theme was changed on this page. */
    protected val palette: AppTheme.Palette
        get() = currentPalette ?: AppTheme.palette(this).also { currentPalette = it }
    protected val colorBg: Int get() = palette.background
    protected val colorBar: Int get() = palette.surface
    protected val colorKey: Int get() = palette.key
    protected val colorKeyHi: Int get() = palette.keyPressed
    protected val colorText: Int get() = palette.text
    protected val colorMuted: Int get() = palette.muted
    protected val colorAccent: Int get() = palette.accent

    protected lateinit var content: LinearLayout

    /** The scrolling frame around [content], for a page that keeps its scroll position when it redraws itself. */
    protected lateinit var scroll: ScrollView
        private set

    /** True once the page has been built, so a callback that arrives early can tell whether it may call [render]. */
    protected val built: Boolean get() = ::content.isInitialized

    /** True while "Return to default" is waiting for its second tap. */
    private var confirmReset = false

    /** Fills [content] from the saved settings. Starts by removing everything that is there. */
    protected abstract fun render()

    /** Called once, after the window is set up and before the page is built. For what [render] needs to find ready. */
    protected open fun onPrepare() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        onPrepare()
        buildUi()
        render()
    }

    /**
     * Builds the page again with the colours that are now chosen, for a page where Light / Dark / System, the accent
     * or the dark style can be changed. The scroll position stays where it was.
     */
    protected fun rebuild() {
        val y = scroll.scrollY
        currentPalette = null
        AppTheme.applyWindow(this)
        buildUi()
        render()
        scroll.post { scroll.scrollTo(0, y) }
    }

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    protected fun button(label: String, onClick: () -> Unit): Button = button(label, false, onClick)

    /** A button that can be the page's main action ([primary] gives it the accent look). */
    protected fun button(label: String, primary: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        AppTheme.button(this@SettingsPage, this, palette, primary)
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

    /**
     * A switch with its label. [onChange] gets the new state; the choice is not saved by this helper. A switch that
     * is not [enabled] is dimmed and does nothing.
     */
    protected fun toggle(label: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        text = label
        setTextColor(colorText)
        textSize = 15f
        isChecked = on
        thumbTintList = AppTheme.switchTint(palette)
        trackTintList = AppTheme.switchTint(palette)
        background = AppTheme.card(this@SettingsPage, palette, 14f)
        setPadding(dp(16), dp(10), dp(12), dp(10))
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.4f
        setOnCheckedChangeListener { _, checked ->
            confirmReset = false
            onChange(checked)
        }
    }

    /**
     * One choice in a list of choices, of which a tick shows the chosen one. [onPick] saves the choice; the page is
     * drawn again afterwards, so the tick moves. A choice that is not [enabled] is dimmed and does nothing.
     */
    protected fun choice(label: String, chosen: Boolean, enabled: Boolean = true, onPick: () -> Unit): Button =
        wide((if (chosen) "✓  " else "     ") + label) {
            if (enabled) {
                confirmReset = false
                onPick()
                render()
            }
        }.apply {
            if (chosen) setTextColor(colorAccent)
            background = AppTheme.pressable(this@SettingsPage, if (chosen) palette.accentSoft else palette.surface, palette.ripple, 12f, palette.outline)
            alpha = if (enabled) 1f else 0.4f
        }

    /**
     * Adds a list of choices, one row per item of [options], with a tick on [chosen]. [onPick] saves the one tapped,
     * and the page is drawn again.
     */
    protected fun <T> choices(options: List<T>, chosen: T, label: (T) -> String, enabled: Boolean = true, onPick: (T) -> Unit) {
        for (option in options) {
            content.addView(
                choice(label(option), option == chosen, enabled) { onPick(option) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, 0, 0, dp(4)) }
            )
        }
    }

    /** Adds a switch, with [note] under it in small grey text when there is one. */
    protected fun switchRow(label: String, on: Boolean, note: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
        content.addView(
            toggle(label, on, enabled, onChange),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) }
        )
        if (note != null) content.addView(muted(note))
    }

    /** Drops a waiting "Return to default" question, for a page whose other controls make it out of date. */
    protected fun cancelReset() {
        confirmReset = false
    }

    /**
     * The "Defaults" section: a button ([label]) that asks [question] and then calls [reset]. [onAsk] runs when the
     * question is first shown.
     */
    protected fun defaultsSection(
        question: String,
        done: String,
        label: String = "Return to default",
        onAsk: () -> Unit = {},
        reset: () -> Unit,
    ) {
        content.addView(sectionTitle("DEFAULTS"))
        if (!confirmReset) {
            content.addView(
                wide(label) {
                    confirmReset = true
                    onAsk()
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

        // Edge-to-edge: keep the page clear of the system bars and the keyboard.
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
        scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }
}
