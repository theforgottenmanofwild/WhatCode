package com.whatcode.keys

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.whatcode.editor.Highlighter
import com.whatcode.editor.Lang
import com.whatcode.settings.EditorSettings
import com.whatcode.ui.AppTheme

/**
 * The "Key Editor" page, opened from Settings > Code Editor. It lists the keys of both rows above the
 * keyboard in order. Each key has buttons to move it up or down (long-press to jump to the start or end)
 * and to remove it; "Add key" opens a panel under the row with every built-in key and a field for a key that
 * types any text. Every change is saved at once, and the editor screen picks it up when you go back to it.
 * The menu's "Edit keys" shortcut ([KeyEditor]) edits the same saved layout.
 *
 * With "Change keys by language" on, the page can also edit the keys of one language at a time: pick it under
 * "Editing keys for", and the rows below are the ones that language shows (see [KeyLayout.rowsFor]).
 */
class KeyEditorActivity : Activity() {

    private val palette by lazy { AppTheme.palette(this) }
    private val colorBg: Int get() = palette.background
    private val colorBar: Int get() = palette.surface
    private val colorKey: Int get() = palette.key
    private val colorKeyHi: Int get() = palette.keyPressed
    private val colorText: Int get() = palette.text
    private val colorMuted: Int get() = palette.muted
    private val colorAccent: Int get() = palette.accent

    private val prefs by lazy { getSharedPreferences(KeyLayout.PREFS, MODE_PRIVATE) }
    private val settings by lazy { EditorSettings(this) }
    private lateinit var rows: List<MutableList<String>>

    /** The language whose keys are being edited, or null for the layout used by every language. */
    private var target: Lang? = null
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView

    /** The row whose add panel is open, or -1. */
    private var adding = -1
    private var pendingText = ""
    private var confirmReset = false

    /** The row whose "Return to default" is waiting for a second tap, or -1. */
    private var confirmRow = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        loadRows()
        buildUi()
        render()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Reads the rows of the language being edited (or the layout for all languages) from the saved settings. */
    private fun loadRows() {
        val t = target
        rows = KeyLayout.rowsFor(prefs, t, t != null)
    }

    /** What "return to default" gives for a row: the default keys, or the built-in keys of the language. */
    private fun defaultRow(i: Int): List<String> {
        val t = target ?: return if (i == 0) KeyLayout.DEFAULT_ROW1 else KeyLayout.DEFAULT_ROW2
        return KeyLayout.baseRows(prefs, t)[i]
    }

    /** Saves the layout. Any pending "return to default" question is dropped, since something else was done. */
    private fun persist() {
        val t = target
        if (t == null) KeyLayout.save(prefs, rows) else KeyLayout.saveFor(prefs, t, rows)
        confirmRow = -1
        confirmReset = false
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(colorText)
        AppTheme.button(this@KeyEditorActivity, this, palette)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorMuted)
        textSize = 12f
        letterSpacing = 0.1f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(10))
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
            textSize = 28f
            contentDescription = "Back"
            background = AppTheme.pressable(this@KeyEditorActivity, colorBar, palette.ripple, 12f)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(48), dp(44)))
        bar.addView(TextView(this).apply {
            text = "Key Editor"
            setTextColor(colorText)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }
        scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(list, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    // ------------------------------------------------------------------ drawing the page

    /** Redraws the whole page from [rows], keeping the scroll position. */
    private fun render() {
        val y = scroll.scrollY
        list.removeAllViews()

        renderLanguageSection()

        list.addView(
            muted(
                "These are the two rows of keys above the keyboard, in the order they appear. " +
                    "Use ↑ and ↓ to move a key (long-press to send it to the start or end) and × to remove it. " +
                    "Changes are saved straight away. Each row can be returned to its default keys on its own."
            )
        )

        for (i in 0..1) renderRow(i)

        list.addView(sectionTitle("DEFAULTS"))
        if (!confirmReset) {
            list.addView(wide("Return both rows to default") {
                confirmReset = true
                confirmRow = -1
                render()
            })
        } else {
            list.addView(muted("Put both rows back to the default keys?"))
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            line.addView(
                wide("Return to default") {
                    val t = target
                    if (t == null) {
                        rows[0].clear()
                        rows[0].addAll(KeyLayout.DEFAULT_ROW1)
                        rows[1].clear()
                        rows[1].addAll(KeyLayout.DEFAULT_ROW2)
                        persist()
                    } else {
                        // Forget what was saved for this language; it goes back to its built-in keys.
                        KeyLayout.clearFor(prefs, t)
                        loadRows()
                    }
                    confirmReset = false
                    adding = -1
                    toast("Keys returned to default")
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
            list.addView(line)
        }

        scroll.post { scroll.scrollTo(0, y) }
    }

    /** The switch for keys by language and, when it is on, the choice of which language's keys are shown below. */
    private fun renderLanguageSection() {
        list.addView(Switch(this).apply {
            text = "Change keys by language"
            setTextColor(colorText)
            textSize = 15f
            thumbTintList = AppTheme.switchTint(palette)
            trackTintList = AppTheme.switchTint(palette)
            background = AppTheme.card(this@KeyEditorActivity, palette, 14f)
            setPadding(dp(14), dp(10), dp(12), dp(10))
            isChecked = settings.keysByLanguage
            setOnCheckedChangeListener { _, on ->
                settings.keysByLanguage = on
                if (!on && target != null) {
                    target = null
                    loadRows()
                }
                adding = -1
                confirmRow = -1
                confirmReset = false
                pendingText = ""
                render()
            }
        })
        list.addView(
            muted(
                "When on, the rows switch to keys that suit the open file, such as := and <- for Go or </> for HTML. " +
                    "Pick a language to change its keys; the other languages keep theirs."
            )
        )
        if (!settings.keysByLanguage) return

        list.addView(sectionTitle("EDITING KEYS FOR"))
        list.addView(wide("${target?.name ?: "All languages"}  ▾") { pickTarget() })
        val t = target
        list.addView(
            muted(
                when {
                    t == null -> "The layout that every language without its own keys uses."
                    KeyLayout.hasSaved(prefs, t) ->
                        "You changed the keys for ${t.name}. Return to default gives back its built-in keys."
                    KeyLayout.hasPreset(t) -> "${t.name} has built-in keys. Change any key to make them your own."
                    else -> "${t.name} uses the same keys as all languages until you change something here."
                }
            ).apply { setPadding(0, dp(8), 0, 0) }
        )
    }

    private fun pickTarget() {
        val langs = Highlighter.languages
        val labels = arrayOf("All languages") + langs.map {
            it.name + when {
                KeyLayout.hasSaved(prefs, it) -> "  ·  changed"
                KeyLayout.hasPreset(it) -> "  ·  built-in keys"
                else -> ""
            }
        }
        AppTheme.dialogBuilder(this)
            .setTitle("Edit keys for")
            .setItems(labels) { _, which ->
                target = if (which == 0) null else langs[which - 1]
                loadRows()
                adding = -1
                confirmRow = -1
                confirmReset = false
                pendingText = ""
                render()
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, this) }
    }

    /** A full-width, left-aligned button. */
    private fun wide(label: String, onClick: () -> Unit): Button = button(label, onClick).apply {
        textSize = 15f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(16), 0, dp(16), 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
    }

    private fun renderRow(i: Int) {
        val row = rows[i]
        list.addView(sectionTitle("ROW ${i + 1}  ·  ${row.size} ${if (row.size == 1) "KEY" else "KEYS"}"))

        if (row.isEmpty()) list.addView(muted("No keys in this row."))

        row.forEachIndexed { pos, id ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = AppTheme.card(this@KeyEditorActivity, palette, 12f)
                setPadding(dp(6), 0, dp(6), 0)
            }
            line.addView(
                TextView(this).apply {
                    text = "${pos + 1}.  ${KeyLayout.nameOf(id)}"
                    setTextColor(colorText)
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(12), 0, dp(4), 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            line.addView(
                small("↑", "Move up", pos > 0,
                    { move(i, pos, pos - 1) }, { move(i, pos, 0) }),
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            line.addView(
                small("↓", "Move down", pos < row.size - 1,
                    { move(i, pos, pos + 1) }, { move(i, pos, row.size - 1) }),
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            line.addView(
                small("×", "Remove", true, {
                    row.removeAt(pos)
                    persist()
                    render()
                }).apply { textSize = 22f },
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            list.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { setMargins(0, 0, 0, dp(4)) })
        }

        // Add key (opens a panel under the list) and Return to default for just this row.
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            half(if (adding == i) "Close add panel" else "Add key") {
                adding = if (adding == i) -1 else i
                pendingText = ""
                confirmRow = -1
                confirmReset = false
                render()
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(8), 0) }
        )
        actions.addView(
            half(if (confirmRow == i) "Tap again to confirm" else "Return to default") {
                if (confirmRow == i) {
                    row.clear()
                    row.addAll(defaultRow(i))
                    adding = -1
                    persist()
                    toast("Row ${i + 1} returned to default")
                } else {
                    confirmRow = i
                    confirmReset = false
                }
                render()
            }.apply { if (confirmRow == i) setTextColor(colorAccent) },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        list.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, 0) })

        if (adding == i) renderAddPanel(i)
    }

    /** A centred button for two side by side. */
    private fun half(label: String, onClick: () -> Unit): Button = button(label, onClick).apply {
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
    }

    /** A small square button used inside a key's line. Disabled buttons are dimmed and do nothing. */
    private fun small(
        label: String,
        description: String,
        enabled: Boolean,
        onClick: () -> Unit,
        onLong: (() -> Unit)? = null,
    ): Button = button(label) { if (enabled) onClick() }.apply {
        textSize = 17f
        contentDescription = description
        background = AppTheme.pressable(this@KeyEditorActivity, colorKeyHi, palette.ripple, 9f, palette.outline)
        setPadding(0, 0, 0, 0)
        alpha = if (enabled) 1f else 0.3f
        if (enabled && onLong != null) setOnLongClickListener { onLong(); true }
    }

    private fun move(i: Int, from: Int, to: Int) {
        val row = rows[i]
        val key = row.removeAt(from)
        row.add(to.coerceIn(0, row.size), key)
        persist()
        render()
    }

    private fun renderAddPanel(i: Int) {
        val row = rows[i]
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        panel.addView(TextView(this).apply {
            text = "Add to row ${i + 1}"
            setTextColor(colorAccent)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        })

        // A key that types any text or symbol.
        val input = EditText(this).apply {
            hint = "Text or symbol, e.g. =>"
            setHintTextColor(colorMuted)
            setTextColor(colorText)
            textSize = 15f
            setSingleLine()
            setText(pendingText)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    pendingText = s?.toString() ?: ""
                }
            })
        }
        val textLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        textLine.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        textLine.addView(
            button("Add") {
                if (pendingText.isEmpty()) {
                    toast("Type what the key should insert")
                } else if (row.size >= KeyLayout.MAX_KEYS) {
                    toast("A row can have up to ${KeyLayout.MAX_KEYS} keys")
                } else {
                    row.add("t:$pendingText")
                    pendingText = ""
                    persist()
                    render()
                }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(dp(8), 0, 0, 0) }
        )
        panel.addView(textLine)

        panel.addView(TextView(this).apply {
            text = "Or pick a built-in key"
            setTextColor(colorMuted)
            textSize = 13f
            setPadding(0, dp(12), 0, dp(6))
        })
        for ((id, name) in KeyLayout.BUILTIN) {
            panel.addView(
                wide(name) {
                    if (row.size >= KeyLayout.MAX_KEYS) {
                        toast("A row can have up to ${KeyLayout.MAX_KEYS} keys")
                    } else {
                        row.add(id)
                        persist()
                        render()
                    }
                }.apply {
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { setMargins(0, 0, 0, dp(4)) }
                }
            )
        }
        panel.addView(
            button("Done") {
                adding = -1
                pendingText = ""
                render()
            }.apply {
                setTextColor(colorAccent)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { setMargins(0, dp(8), 0, 0) }
        )

        list.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, 0) })
    }
}
