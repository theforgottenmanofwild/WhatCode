package com.whatcode.settings

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.whatcode.editor.SyntaxRole
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Contrast
import com.whatcode.ui.Hex
import com.whatcode.ui.ThemeMode

/**
 * Settings > Appearance > Syntax colors. Every kind of piece the highlighter colours (keywords, strings, comments
 * and so on) can get its own colour, separately for the light and for the dark theme. A picture of the editor at the
 * top shows the colours as they are chosen. Tapping a row opens a small colour editor: three sliders and a hex field.
 * A colour is only saved when **Use this color** is tapped, and one that would be hard to read on the editor
 * background is shown, and used, in the nearest readable shade (see [SyntaxScheme]).
 */
class SyntaxColorsActivity : SettingsPage("Syntax colors") {

    private val settings by lazy { AppearanceSettings(this) }

    /** The theme being edited: null until the page is first drawn, then Light or Dark. */
    private var editingLight: Boolean? = null

    /** The row whose colour editor is open, and the colour in it that has not been saved yet. */
    private var openRole: SyntaxRole? = null
    private var draft = 0

    override fun render() {
        content.removeAllViews()
        val light = editingLight ?: AppTheme.resolvedLight(this).also { editingLight = it }
        val mode = if (light) ThemeMode.LIGHT else ThemeMode.DARK
        val editorBg = AppTheme.palette(this, light).editor

        content.addView(
            EditorPreviewView(this, mode, settings).apply { contentDescription = "${mode.label} syntax colors preview" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        content.addView(sectionTitle("THEME"))
        content.addView(themeChooser(light))
        content.addView(muted("Colors are kept separately for the light and the dark theme.").apply {
            setPadding(dp(4), dp(10), dp(4), 0)
        })

        content.addView(sectionTitle("COLORS"))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.card(this@SyntaxColorsActivity, palette, 18f)
            clipToOutline = true
        }
        SyntaxRole.values().forEachIndexed { i, role ->
            if (i > 0) {
                card.addView(View(this).apply { setBackgroundColor(palette.outline) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
            card.addView(roleRow(role, light, editorBg))
            if (openRole == role) card.addView(editor(role, light, editorBg))
        }
        content.addView(card)

        val changed = settings.syntaxOverrides(light).size
        content.addView(
            wide(if (changed == 0) "Every color is at its default" else "Reset the $changed changed ${mode.label.lowercase()} color${if (changed == 1) "" else "s"}") {
                if (changed > 0) {
                    for (role in settings.syntaxOverrides(light).keys) settings.setSyntaxColor(role, light, null)
                    openRole = null
                    render()
                }
            }.apply {
                isEnabled = changed > 0
                alpha = if (changed > 0) 1f else 0.5f
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { setMargins(0, dp(12), 0, 0) }
        )

        defaultsSection(
            "Return every syntax color, in both the light and the dark theme, to its default?",
            "Syntax colors returned to default"
        ) {
            settings.resetSyntaxColors()
            openRole = null
        }
    }

    // ------------------------------------------------------------------ pieces of the page

    /** Two buttons, Light and Dark; the one being edited is filled. */
    private fun themeChooser(light: Boolean): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun choice(label: String, forLight: Boolean): View = button(label) {
            if (editingLight != forLight) {
                editingLight = forLight
                openRole = null
                render()
            }
        }.apply {
            AppTheme.button(this@SyntaxColorsActivity, this, palette, primary = light == forLight)
            contentDescription = "$label theme${if (light == forLight) ", selected" else ""}"
        }
        row.addView(choice("Light", true), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(8), 0) })
        row.addView(choice("Dark", false), LinearLayout.LayoutParams(0, dp(48), 1f))
        return row
    }

    /** The colour that is shown for [role] now: the one chosen (made readable if it has to be) or the default. */
    private fun shownColor(role: SyntaxRole, light: Boolean, editorBg: Int): Int {
        val chosen = settings.syntaxColor(role, light) ?: return role.defaultColor(light)
        return Contrast.ensure(chosen, editorBg, Contrast.MINIMUM)
    }

    /** One line: a dot in the colour, the name, the sample on the editor background, and "Default" or the hex code. */
    private fun roleRow(role: SyntaxRole, light: Boolean, editorBg: Int): View {
        val p = palette
        val chosen = settings.syntaxColor(role, light)
        val color = shownColor(role, light, editorBg)
        val open = openRole == role
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            minimumHeight = dp(64)
            isClickable = true
            isFocusable = true
            background = AppTheme.pressable(this@SyntaxColorsActivity, if (open) p.accentSoft else p.surface, p.ripple, 0f)
            contentDescription = "${role.label}, ${if (chosen == null) "default" else Hex.format(chosen)}. ${if (open) "Close" else "Change"} color"
            setOnClickListener {
                if (open) {
                    openRole = null
                } else {
                    openRole = role
                    draft = chosen ?: role.defaultColor(light)
                }
                render()
            }
        }
        row.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(2), p.outline)
            }
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(0, 0, dp(14), 0) })

        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply {
            text = role.label
            textSize = 16f
            setTextColor(p.text)
            typeface = Typeface.DEFAULT_BOLD
        })
        labels.addView(TextView(this).apply {
            text = if (chosen == null) "Default" else Hex.format(chosen)
            textSize = 13f
            setTextColor(p.muted)
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // The sample sits on the real editor background, so the person sees what they will get.
        row.addView(TextView(this).apply {
            text = role.sample
            textSize = 14f
            typeface = settings.font.typeface()
            setTextColor(color)
            maxLines = 1
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = AppTheme.rounded(this@SyntaxColorsActivity, editorBg, 10f, p.outline)
        })
        return row
    }

    /** The colour editor under an open row. */
    private fun editor(role: SyntaxRole, light: Boolean, editorBg: Int): View {
        val p = palette
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.accentSoft)
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }

        val preview = TextView(this).apply {
            textSize = 15f
            typeface = settings.font.typeface()
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val hexField = EditText(this).apply {
            setText(Hex.format(draft))
            setTextColor(p.text)
            setHintTextColor(p.muted)
            hint = "#RRGGBB"
            textSize = 16f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
            isSingleLine = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = AppTheme.rounded(this@SyntaxColorsActivity, p.surface, 12f, p.outline)
            contentDescription = "Hex color for ${role.label}"
        }
        val note = TextView(this).apply {
            textSize = 13f
            setTextColor(p.muted)
            setPadding(0, dp(8), 0, 0)
        }

        val sliders = ArrayList<SeekBar>()
        var updating = false

        fun show() {
            val shown = Contrast.ensure(draft, editorBg, Contrast.MINIMUM)
            preview.text = "${role.sample}   Aa"
            preview.setTextColor(shown)
            preview.background = AppTheme.rounded(this, editorBg, 12f, p.outline)
            val ratio = Contrast.ratio(draft, editorBg)
            note.text = if (shown != draft) {
                "Hard to read on this editor background (contrast %.1f:1). It will be shown as %s instead.".format(ratio, Hex.format(shown))
            } else {
                "Contrast on the editor background: %.1f:1. Easy to read.".format(ratio)
            }
        }

        fun setDraft(color: Int, fromHex: Boolean) {
            draft = color or 0xFF000000.toInt()
            updating = true
            sliders[0].progress = (draft shr 16) and 0xFF
            sliders[1].progress = (draft shr 8) and 0xFF
            sliders[2].progress = draft and 0xFF
            if (!fromHex) hexField.setText(Hex.format(draft))
            updating = false
            show()
        }

        val names = listOf("Red", "Green", "Blue")
        for (i in 0 until 3) {
            val bar = SeekBar(this).apply {
                max = 255
                progressTintList = ColorStateList.valueOf(p.accent)
                thumbTintList = ColorStateList.valueOf(p.accent)
                progressBackgroundTintList = ColorStateList.valueOf(p.outline)
                contentDescription = "${names[i]} amount for ${role.label}"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (updating || !fromUser) return
                        val shift = 16 - 8 * i
                        setDraft((draft and (0xFF shl shift).inv()) or (value shl shift), false)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
            }
            sliders.add(bar)
        }

        box.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, dp(8))
        })
        for (i in 0 until 3) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(TextView(this).apply {
                text = names[i]
                textSize = 13f
                setTextColor(p.muted)
            }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
            line.addView(sliders[i], LinearLayout.LayoutParams(0, dp(40), 1f))
            box.addView(line)
        }
        box.addView(hexField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(8), 0, 0)
        })
        box.addView(note)

        hexField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                Hex.parse(s?.toString().orEmpty())?.let { setDraft(it, true) }
            }
        })

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        buttons.addView(button("Use this color") {
            // A hex code that is half typed is not saved; the last good colour is.
            settings.setSyntaxColor(role, light, draft)
            openRole = null
            render()
        }.apply {
            AppTheme.button(this@SyntaxColorsActivity, this, p, primary = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1.4f).apply { setMargins(0, 0, dp(8), 0) })
        buttons.addView(button("Default") {
            settings.setSyntaxColor(role, light, null)
            openRole = null
            render()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, 0, dp(8), 0) })
        buttons.addView(button("Cancel") {
            openRole = null
            render()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        box.addView(buttons)

        setDraft(draft, false)
        return box
    }
}
