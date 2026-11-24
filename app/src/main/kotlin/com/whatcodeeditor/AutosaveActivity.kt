package com.whatcodeeditor

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
 * The "Autosave" page, opened from Settings > Code Editor. It has the switch for autosave, how long to wait
 * after typing stops, whether to also save when the app is left, and a button that puts all three back to
 * their defaults. Each choice is saved at once; the editor screen reads them when it needs them.
 */
class AutosaveActivity : Activity() {

    private val colorBg = 0xFF1E1E1E.toInt()
    private val colorBar = 0xFF252526.toInt()
    private val colorKey = 0xFF333333.toInt()
    private val colorText = 0xFFD4D4D4.toInt()
    private val colorMuted = 0xFF9DA5B0.toInt()
    private val colorAccent = 0xFF9CDCFE.toInt()

    private val settings by lazy { AutosaveSettings(this) }
    private lateinit var content: LinearLayout
    private var confirmReset = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        setBackgroundColor(colorKey)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorAccent)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun muted(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorMuted)
        textSize = 13f
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
            setPadding(dp(4), dp(4), dp(10), dp(4))
        }
        val back = button("‹") { finish() }.apply {
            textSize = 26f
            contentDescription = "Back"
            setBackgroundColor(colorBar)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(40)))
        bar.addView(TextView(this).apply {
            text = "Autosave"
            setTextColor(colorAccent)
            textSize = 16f
            typeface = Typeface.MONOSPACE
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    /** Redraws the page from the saved settings. */
    private fun render() {
        content.removeAllViews()
        val on = settings.enabled

        content.addView(Switch(this).apply {
            text = "Autosave"
            setTextColor(colorText)
            textSize = 16f
            isChecked = on
            setPadding(0, dp(4), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                settings.enabled = checked
                confirmReset = false
                render()
            }
        })
        content.addView(
            muted(
                "Saves the open file by itself, so you don't have to tap Save. " +
                    "A new file that has no name yet is not autosaved; use Save as once first."
            )
        )

        // How long to wait after typing stops.
        content.addView(sectionTitle("SAVE AFTER YOU STOP TYPING FOR"))
        for (seconds in AutosaveSettings.DELAYS) {
            val chosen = seconds == settings.delaySeconds
            content.addView(
                choice(AutosaveSettings.label(seconds), chosen, on) {
                    settings.delaySeconds = seconds
                    confirmReset = false
                    render()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, 0, 0, dp(4)) }
            )
        }
        content.addView(muted("Each key you type starts the wait again, so the file is saved when you pause."))

        // Also when leaving the app.
        content.addView(sectionTitle("LEAVING THE APP"))
        content.addView(Switch(this).apply {
            text = "Also save when I leave the app"
            setTextColor(colorText)
            textSize = 15f
            isChecked = settings.saveOnLeave
            isEnabled = on
            alpha = if (on) 1f else 0.4f
            setPadding(0, dp(4), 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                settings.saveOnLeave = checked
                confirmReset = false
            }
        })
        content.addView(muted("Saves straight away when you switch to another app or close this one, without waiting."))

        // Back to the defaults.
        content.addView(sectionTitle("DEFAULTS"))
        if (!confirmReset) {
            content.addView(
                wide("Return to default") {
                    confirmReset = true
                    render()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            )
        } else {
            content.addView(
                muted(
                    "Turn autosave off, wait ${AutosaveSettings.label(AutosaveSettings.DEFAULT_DELAY)} " +
                        "and save when leaving the app?"
                )
            )
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            line.addView(
                wide("Return") {
                    settings.resetToDefault()
                    confirmReset = false
                    Toast.makeText(this, "Autosave returned to default", Toast.LENGTH_SHORT).show()
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
    }

    /** A full-width, left-aligned button. */
    private fun wide(label: String, onClick: () -> Unit): Button = button(label, onClick).apply {
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(16), 0, dp(16), 0)
    }

    /** One choice in a list: a tick shows the chosen one. Dimmed and inactive while autosave is off. */
    private fun choice(label: String, chosen: Boolean, active: Boolean, onClick: () -> Unit): Button =
        wide((if (chosen) "✓  " else "     ") + label) { if (active) onClick() }.apply {
            if (chosen) setTextColor(colorAccent)
            alpha = if (active) 1f else 0.4f
        }
}
