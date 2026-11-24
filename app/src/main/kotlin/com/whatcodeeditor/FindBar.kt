package com.whatcodeeditor

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Find and replace for the open file. It is a bar above the key rows, not a dialog, so the matches
 * stay visible (highlighted in the text) while you step through them and edit.
 * Layout: search and replace fields on the left, icon buttons in columns on the right (previous, next, options, close
 * above; replace, replace all and the match counter below). The gear holds the options: match case, whole word, and
 * regular expressions (with $1 groups, \n and \t in the replacement); it is tinted while any option is on.
 */
class FindBar(
    private val activity: Activity,
    private val editor: CodeEditText,
    private val colorBar: Int,
    private val colorBg: Int,
    private val colorText: Int,
    private val colorAccent: Int,
) {
    private class FindSpan(color: Int) : BackgroundColorSpan(color)
    private class Hit(val start: Int, val end: Int)

    private val matchColor = 0x66FFC107
    private val currentColor = 0xCCFF9800.toInt()
    private val errorColor = 0xFFF44747.toInt()
    private val palette by lazy { AppTheme.palette(activity) }
    private val maxHighlights = 3000
    private val inputWeight = 2.6f // the fields take about 40% of the width, the icon columns share the rest

    private val findInput = field("Search")
    private val replaceInput = field("Replace")
    private val countView = TextView(activity).apply {
        setTextColor(colorText)
        textSize = 15f
        gravity = Gravity.CENTER
    }
    private lateinit var settingsButton: ImageView

    private var caseSensitive = false
    private var wholeWord = false
    private var useRegex = false
    private var pattern: Pattern? = null
    private var hits: List<Hit> = emptyList()
    private var current = -1
    private var currentSpan: FindSpan? = null

    val view: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = AppTheme.rounded(activity, colorBar, 0f, palette.outline)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        visibility = View.GONE
    }

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    init {
        settingsButton = icon(Icon.SETTINGS, "Search options") { showOptions() }
        paintToggles()

        val top = row().apply {
            addView(findInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, inputWeight))
            addView(icon(Icon.PREVIOUS, "Previous match") { step(-1) }, cell())
            addView(icon(Icon.NEXT, "Next match") { step(1) }, cell())
            addView(settingsButton, cell())
            addView(icon(Icon.CLOSE, "Close") { hide() }, cell())
        }
        val bottom = row().apply {
            addView(replaceInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, inputWeight))
            addView(icon(Icon.REPLACE, "Replace") { replaceOne() }, cell())
            addView(icon(Icon.REPLACE_ALL, "Replace all") { replaceAll() }, cell())
            addView(countView, LinearLayout.LayoutParams(0, dp(44), 2f))
        }
        view.addView(top)
        view.addView(bottom)

        findInput.imeOptions = EditorInfo.IME_ACTION_SEARCH
        findInput.setOnEditorActionListener { _, _, _ ->
            step(1)
            true
        }
        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isShown) refresh(true)
            }
        })
        // Keep the match list right while the text is edited with the bar open.
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isShown) refresh(false)
            }
        })
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun colorMuted(): Int = palette.muted

    private fun field(hint: String): EditText = EditText(activity).apply {
        this.hint = hint
        setHintTextColor(colorMuted())
        setTextColor(colorText)
        background = AppTheme.rounded(activity, colorBg, 9f, palette.outline)
        // Keep the default underline, light normally and in the accent colour while the field has focus.
        backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(colorAccent, colorText)
        )
        textSize = 14f
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    /** The icons are drawn in Icons.kt; this tints them to match the bar. */
    private fun icon(kind: Icon, description: String, onClick: () -> Unit): ImageView =
        Icons.button(activity, kind, description, colorText, onClick)

    private fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** One icon column: every button on the right of the bar shares the same width, so the two rows line up. */
    private fun cell(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)

    /** The gear is tinted while any option is on, and the search field says when it is a regex. */
    private fun paintToggles() {
        settingsButton.setColorFilter(if (caseSensitive || wholeWord || useRegex) colorAccent else colorText)
        findInput.hint = if (useRegex) "Search (regex)" else "Search"
    }

    /** The gear's menu: each option flips its flag, repaints, and searches again. */
    private fun showOptions() {
        val popup = AppTheme.popup(activity, settingsButton, Gravity.END)
        fun option(id: Int, title: String, on: Boolean) {
            popup.menu.add(0, id, id, title).apply {
                isCheckable = true
                isChecked = on
            }
        }
        option(1, "Case sensitive", caseSensitive)
        option(2, "Whole word", wholeWord)
        option(3, "Regular expression", useRegex)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> caseSensitive = !caseSensitive
                2 -> wholeWord = !wholeWord
                else -> useRegex = !useRegex
            }
            paintToggles()
            refresh(true)
            true
        }
        popup.show()
    }

    // ------------------------------------------------------------------ show / hide

    fun show() {
        view.visibility = View.VISIBLE
        val s = minOf(editor.selectionStart, editor.selectionEnd)
        val e = maxOf(editor.selectionStart, editor.selectionEnd)
        if (e > s && e - s <= 100) {
            val sel = editor.text.subSequence(s, e).toString()
            if (!sel.contains('\n')) findInput.setText(sel)
        }
        findInput.requestFocus()
        findInput.selectAll()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
        refresh(true)
    }

    fun hide() {
        clearSpans()
        hits = emptyList()
        current = -1
        view.visibility = View.GONE
        editor.requestFocus()
    }

    // ---------------------------------------------------------------------- matching

    /** Builds the search pattern from the text and options, or null if it is empty or not a valid regex. */
    private fun buildPattern(q: String): Pattern? {
        if (q.isEmpty()) return null
        return try {
            val core = if (useRegex) q else Pattern.quote(q)
            val full = if (wholeWord) "(?<!\\w)(?:$core)(?!\\w)" else core
            var flags = Pattern.MULTILINE
            if (!caseSensitive) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            Pattern.compile(full, flags)
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    private fun findAll(p: Pattern?): List<Hit> {
        if (p == null) return emptyList()
        val out = ArrayList<Hit>()
        val m = p.matcher(editor.text)
        while (m.find() && out.size < 100_000) {
            if (m.end() > m.start()) out.add(Hit(m.start(), m.end())) // empty matches have nothing to highlight
        }
        return out
    }

    private fun clearSpans() {
        val t = editor.text
        for (sp in t.getSpans(0, t.length, FindSpan::class.java)) t.removeSpan(sp)
        currentSpan = null
    }

    private fun paint() {
        clearSpans()
        val t = editor.text
        for ((n, h) in hits.withIndex()) {
            if (n >= maxHighlights) break
            t.setSpan(FindSpan(matchColor), h.start, h.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        markCurrent()
    }

    private fun markCurrent() {
        val t = editor.text
        currentSpan?.let { t.removeSpan(it) }
        currentSpan = null
        if (current in hits.indices) {
            val sp = FindSpan(currentColor)
            t.setSpan(sp, hits[current].start, hits[current].end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            currentSpan = sp
        }
    }

    private fun updateCount(q: String) {
        val bad = q.isNotEmpty() && pattern == null
        countView.setTextColor(if (bad) errorColor else colorText)
        countView.text = when {
            bad -> "bad regex"
            hits.isEmpty() -> "0 of 0"
            else -> "${current + 1} of ${hits.size}"
        }
    }

    private fun selectCurrent() {
        if (current !in hits.indices) return
        editor.setSelection(hits[current].start, hits[current].end)
        editor.bringPointIntoView(hits[current].start)
    }

    /** Recomputes the matches. With [select], the first match at or after the cursor is selected. */
    private fun refresh(select: Boolean) {
        val q = findInput.text.toString()
        pattern = buildPattern(q)
        hits = findAll(pattern)
        val cursor = minOf(editor.selectionStart, editor.selectionEnd)
        current = if (hits.isEmpty()) -1 else hits.indexOfFirst { it.start >= cursor }.let { if (it < 0) 0 else it }
        paint()
        updateCount(q)
        if (select) selectCurrent()
    }

    private fun step(direction: Int) {
        if (hits.isEmpty()) return
        current = if (current < 0) 0 else (current + direction + hits.size) % hits.size
        markCurrent()
        updateCount(findInput.text.toString())
        selectCurrent()
    }

    // ---------------------------------------------------------------------- replacing

    /** Expands $1-style groups and \n, \t in a regex replacement. */
    private fun expand(m: Matcher, rep: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < rep.length) {
            val c = rep[i]
            if (c == '\\' && i + 1 < rep.length) {
                when (val n = rep[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> sb.append(n)
                }
                i += 2
            } else if (c == '$' && i + 1 < rep.length && rep[i + 1].isDigit()) {
                var g = rep[i + 1] - '0'
                i += 2
                while (i < rep.length && rep[i].isDigit() && g * 10 + (rep[i] - '0') <= m.groupCount()) {
                    g = g * 10 + (rep[i] - '0')
                    i++
                }
                if (g <= m.groupCount()) sb.append(m.group(g) ?: "")
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun replacementFor(m: Matcher, rep: String): String = if (useRegex) expand(m, rep) else rep

    private fun replaceOne() {
        val p = pattern ?: return
        if (hits.isEmpty()) return
        val h = hits[current.coerceAtLeast(0)]
        val src = editor.text.toString()
        val m = p.matcher(src)
        m.region(h.start, h.end)
        m.useTransparentBounds(true)
        m.useAnchoringBounds(false)
        if (!m.find()) return
        val rep = replacementFor(m, replaceInput.text.toString())
        editor.replaceRaw(h.start, h.end, rep)
        editor.setSelection((h.start + rep.length).coerceAtMost(editor.text.length))
        refresh(true) // moves on to the next match
    }

    private fun replaceAll() {
        val p = pattern ?: return
        val all = hits
        if (all.isEmpty()) return
        val repText = replaceInput.text.toString()
        val src = editor.text.toString()
        val from = all.first().start
        val to = all.last().end
        val m = p.matcher(src)
        m.region(from, to)
        m.useTransparentBounds(true)
        m.useAnchoringBounds(false)
        val sb = StringBuilder()
        var last = from
        var n = 0
        while (m.find()) {
            if (m.end() == m.start()) continue
            sb.append(src, last, m.start()).append(replacementFor(m, repText))
            last = m.end()
            n++
        }
        sb.append(src, last, to)
        editor.replaceRaw(from, to, sb)
        editor.setSelection((from + sb.length).coerceAtMost(editor.text.length))
        refresh(false)
        Toast.makeText(activity, "Replaced $n", Toast.LENGTH_SHORT).show()
    }
}
