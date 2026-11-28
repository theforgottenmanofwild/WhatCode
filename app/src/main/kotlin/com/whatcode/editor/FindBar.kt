package com.whatcode.editor

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.os.Handler
import android.os.Looper
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
import com.whatcode.search.FindLogic
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * Find and replace for the open file. It is a bar above the key rows, not a dialog, so the matches
 * stay visible (highlighted in the text) while you step through them and edit.
 * Layout: search and replace fields on the left, icon buttons in columns on the right (previous, next, options, close
 * above; replace, replace all and the match counter below). The gear holds the options: match case, whole word, and
 * regular expressions (with $1 groups, \n and \t in the replacement); it is tinted while any option is on.
 *
 * A short text is searched at once. A long one (see [FindLogic.SYNC_BELOW]), or a search that takes too long, runs on
 * another thread: the counter shows the progress, a touch on it stops the search, and a result that is overtaken by
 * an edit is thrown away. The matching rules are in [FindLogic].
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

    private val matchColor = 0x66FFC107
    private val currentColor = 0xCCFF9800.toInt()
    private val errorColor = 0xFFF44747.toInt()
    private val palette by lazy { AppTheme.palette(activity) }
    private val maxHighlights = 3000
    private val inputWeight = 2.6f // the fields take about 40% of the width, the icon columns share the rest

    private val handler = Handler(Looper.getMainLooper())

    /** Shown in place of the count while a search runs on another thread, or after it was stopped. */
    private var note: String? = null
    private var stopFlag: AtomicBoolean? = null

    /** Goes up each time work is started or thrown away, so the result of work that was overtaken is ignored. */
    private var jobId = 0

    /** Goes up with each change to the text. */
    private var editSerial = 0

    /** True while this bar changes the text itself (Replace), so that change is not taken for the person's edit. */
    private var selfEdit = false
    private val refreshTask = Runnable { refresh(false) }

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
    private var hits: List<FindLogic.Span> = emptyList()
    private var capped = false
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

        countView.setOnClickListener {
            if (stopFlag != null) {
                cancelJob()
                note = "stopped"
                updateCount(findInput.text.toString())
            }
        }
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
                editSerial++
                if (isShown && !selfEdit) onTextEdited()
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
        cancelJob()
        note = null
        clearSpans()
        hits = emptyList()
        current = -1
        editor.navigation.jumps.endRun()
        view.visibility = View.GONE
        editor.requestFocus()
    }

    // ---------------------------------------------------------------------- matching

    private val isBig: Boolean get() = editor.text.length >= FindLogic.SYNC_BELOW

    /** Stops the work on another thread, if any, and ignores what it would have found. */
    private fun cancelJob() {
        stopFlag?.set(true)
        stopFlag = null
        jobId++
        handler.removeCallbacks(refreshTask)
    }

    /** The text was edited with the bar open. A long text is searched again a moment after typing stops. */
    private fun onTextEdited() {
        if (!isBig) {
            refresh(false)
            return
        }
        cancelJob()
        hits = emptyList()
        current = -1
        clearSpans()
        showNote(findInput.text.toString(), "…")
        handler.postDelayed(refreshTask, 300)
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

    private fun showNote(q: String, text: String) {
        note = text
        updateCount(q)
    }

    private fun updateCount(q: String) {
        val bad = q.isNotEmpty() && pattern == null
        countView.setTextColor(if (bad) errorColor else colorText)
        countView.text = when {
            bad -> "bad regex"
            note != null -> note
            hits.isEmpty() -> "0 of 0"
            else -> "${current + 1} of ${hits.size}${if (capped) "+" else ""}"
        }
    }

    private fun selectCurrent() {
        if (current !in hits.indices) return
        // A run of hits is one jump for Back, so the history keeps only where the cursor was before the first.
        editor.navigation.takeIf { editor.jumpHistoryOn }?.jumps?.record(editor.selectionEnd.coerceAtLeast(0), run = true)
        editor.setSelection(hits[current].start, hits[current].end)
        editor.bringPointIntoView(hits[current].start)
    }

    /** Searches again. With [select], the first match at or after the cursor is selected. */
    private fun refresh(select: Boolean) {
        val q = findInput.text.toString()
        cancelJob()
        note = null
        pattern = FindLogic.buildPattern(q, caseSensitive, wholeWord, useRegex)
        val p = pattern
        if (p == null) {
            showFound(q, FindLogic.Found(emptyList(), false), select)
            return
        }
        if (!isBig) {
            val deadline = System.nanoTime() + FindLogic.SYNC_BUDGET_MS * 1_000_000
            val found = try {
                FindLogic.findAll(editor.text, p, { System.nanoTime() > deadline })
            } catch (_: CancellationException) {
                null // too slow to wait for: it runs on another thread below
            }
            if (found != null) {
                showFound(q, found, select)
                return
            }
        }
        searchInBackground(q, p, select)
    }

    private fun showFound(q: String, found: FindLogic.Found, select: Boolean) {
        hits = found.hits
        capped = found.capped
        note = null
        val cursor = minOf(editor.selectionStart, editor.selectionEnd)
        current = if (hits.isEmpty()) -1 else hits.indexOfFirst { it.start >= cursor }.let { if (it < 0) 0 else it }
        paint()
        updateCount(q)
        if (select) selectCurrent()
    }

    /** Where a run of work on another thread gets to say how far it is: a percentage, and only when it changes. */
    private fun progressTo(id: Int, q: String, length: Int): (Int) -> Unit {
        var last = -1
        return { pos ->
            val pct = if (length == 0) 0 else (pos.toLong() * 100 / length).toInt().coerceIn(0, 99)
            if (pct != last) {
                last = pct
                handler.post { if (id == jobId) showNote(q, "$pct%") }
            }
        }
    }

    private fun searchInBackground(q: String, p: Pattern, select: Boolean) {
        val snapshot = editor.text.toString()
        val stop = AtomicBoolean(false)
        stopFlag = stop
        val id = ++jobId
        hits = emptyList()
        current = -1
        clearSpans()
        showNote(q, "0%")
        val progress = progressTo(id, q, snapshot.length)
        Thread {
            var found: FindLogic.Found? = null
            var failed = false
            try {
                found = FindLogic.findAll(snapshot, p, { stop.get() }, progress)
            } catch (_: CancellationException) {
            } catch (_: Throwable) {
                failed = true // for example a pattern that nests too deep for the stack
            }
            handler.post {
                if (id != jobId) return@post
                stopFlag = null
                if (found != null) showFound(q, found, select) else if (failed) showNote(q, "failed")
            }
        }.start()
    }

    private fun step(direction: Int) {
        if (hits.isEmpty()) return
        current = if (current < 0) 0 else (current + direction + hits.size) % hits.size
        markCurrent()
        updateCount(findInput.text.toString())
        selectCurrent()
    }

    // ---------------------------------------------------------------------- replacing

    private fun replaceOne() {
        val p = pattern ?: return
        if (hits.isEmpty() || stopFlag != null) return
        val at = current.coerceAtLeast(0)
        val h = hits[at]
        val m = p.matcher(editor.text)
        m.region(h.start, h.end)
        m.useTransparentBounds(true)
        m.useAnchoringBounds(false)
        if (!m.find()) return
        val rep = if (useRegex) FindLogic.expand(m, replaceInput.text.toString()) else replaceInput.text.toString()
        val big = isBig
        change { editor.replaceRaw(h.start, h.end, rep) }
        editor.setSelection((h.start + rep.length).coerceAtMost(editor.text.length))
        if (big) {
            // A new search of a long text would take long for each click: drop this match, move the others, go on.
            val delta = rep.length - (h.end - h.start)
            hits = hits.filterIndexed { i, _ -> i != at }.map { if (it.start > h.start) FindLogic.Span(it.start + delta, it.end + delta) else it }
            current = if (hits.isEmpty()) -1 else at.coerceAtMost(hits.size - 1)
            paint()
            updateCount(findInput.text.toString())
            selectCurrent()
        } else {
            refresh(true) // moves on to the next match
        }
    }

    /** Runs [edit], which changes the text, without taking the change for one the person made. */
    private fun change(edit: () -> Unit) {
        selfEdit = true
        try {
            edit()
        } finally {
            selfEdit = false
        }
    }

    private fun replaceAll() {
        val p = pattern ?: return
        val rep = replaceInput.text.toString()
        val regex = useRegex
        cancelJob()
        note = null
        if (!isBig) {
            val deadline = System.nanoTime() + FindLogic.SYNC_BUDGET_MS * 1_000_000
            var done = false
            var r: FindLogic.Replaced? = null
            try {
                r = FindLogic.replaceAll(editor.text.toString(), p, rep, regex, { System.nanoTime() > deadline })
                done = true
            } catch (_: CancellationException) {
                // too slow to wait for: it runs on another thread below
            }
            if (done) {
                applyReplaced(r)
                return
            }
        }
        replaceInBackground(p, rep, regex)
    }

    private fun replaceInBackground(p: Pattern, rep: String, regex: Boolean) {
        val q = findInput.text.toString()
        val snapshot = editor.text.toString()
        val serial = editSerial
        val stop = AtomicBoolean(false)
        stopFlag = stop
        val id = ++jobId
        showNote(q, "0%")
        val progress = progressTo(id, q, snapshot.length)
        Thread {
            var r: FindLogic.Replaced? = null
            var failed = false
            try {
                r = FindLogic.replaceAll(snapshot, p, rep, regex, { stop.get() }, progress)
            } catch (_: CancellationException) {
            } catch (_: Throwable) {
                failed = true
            }
            handler.post {
                if (id != jobId) return@post
                stopFlag = null
                when {
                    failed -> showNote(q, "failed")
                    serial != editSerial -> {
                        Toast.makeText(activity, "The text changed, so nothing was replaced", Toast.LENGTH_SHORT).show()
                        refresh(false)
                    }
                    else -> applyReplaced(r)
                }
            }
        }.start()
    }

    /** Puts a finished Replace all into the text as one change, so one Undo takes it back. */
    private fun applyReplaced(r: FindLogic.Replaced?) {
        if (r == null) {
            refresh(false)
            return
        }
        change { editor.replaceRaw(r.from, r.to, r.text) }
        editor.setSelection((r.from + r.text.length).coerceAtMost(editor.text.length))
        refresh(false)
        Toast.makeText(activity, "Replaced ${r.count}", Toast.LENGTH_SHORT).show()
    }
}
