package com.whatcode.explorer

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.whatcode.files.Workspace
import com.whatcode.search.FileHits
import com.whatcode.search.FileSearch
import com.whatcode.ui.AppTheme

/**
 * The sidebar's Search panel: the text to look for, the options (match case, whole word, regular expression) and
 * the files that match, each with its matching lines. The options are kept in [prefs]. [close] closes the sidebar
 * when a result is opened; [hideKeyboard] puts the keyboard away when a search starts.
 */
internal class SearchPanel(
    private val activity: Activity,
    private val workspace: Workspace,
    private val host: SidebarHost,
    private val palette: AppTheme.Palette,
    private val prefs: SharedPreferences,
    private val close: () -> Unit,
    private val hideKeyboard: () -> Unit,
) {
    val view = LinearLayout(activity)
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var resultsBox: LinearLayout
    private lateinit var resultsScroll: ScrollView
    private val toggles = HashMap<String, TextView>()
    private var search: FileSearch? = null
    private lateinit var searchButton: Button
    private var searching = false

    private val colorKey get() = palette.key
    private val colorText get() = palette.text
    private val colorMuted get() = palette.muted
    private val colorAccent get() = palette.accent
    private val colorHit get() = palette.hit

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    init {
        view.orientation = LinearLayout.VERTICAL

        val title = TextView(activity).apply {
            text = "Search in files"
            setTextColor(colorText)
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(palette.surface)
        }
        view.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        // Options: match case, whole word, regular expression.
        val options = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), 0)
        }
        fun toggle(key: String, label: String, description: String) {
            val t = TextView(activity).apply {
                text = label
                textSize = 14f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                contentDescription = description
                setOnClickListener {
                    prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply()
                    paintToggles()
                }
            }
            toggles[key] = t
            options.addView(t, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        toggle("case", "aA", "Match case")
        toggle("word", "a-z", "Whole word")
        toggle("regex", ".*", "Regular expression")
        paintToggles()
        view.addView(options, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The text to look for, and the button that starts the search.
        val line = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        input = EditText(activity).apply {
            hint = "File names and text"
            setHintTextColor(colorMuted)
            setTextColor(colorText)
            background = AppTheme.rounded(activity, palette.background, 10f, palette.outline)
            backgroundTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(colorAccent, palette.outline)
            )
            textSize = 15f
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch()
                    true
                } else {
                    false
                }
            }
        }
        line.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchButton = Button(activity).apply {
            text = "Search"
            isAllCaps = false
            textSize = 13f
            AppTheme.button(activity, this, palette, primary = true)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(14), 0, dp(14), 0)
            // While a search is going the button stops it.
            setOnClickListener { if (searching) stopSearch() else runSearch() }
        }
        line.addView(
            searchButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(dp(8), 0, 0, 0) }
        )
        view.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        status = TextView(activity).apply {
            setTextColor(colorMuted)
            textSize = 12f
            setPadding(dp(14), dp(4), dp(14), dp(6))
        }
        view.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        resultsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        resultsScroll = ScrollView(activity)
        resultsScroll.addView(resultsBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(resultsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Puts the cursor in the text to look for and brings the keyboard up. */
    fun focusInput() {
        input.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.post { imm.showSoftInput(input, 0) }
    }

    /** Another folder was opened: stop any search and forget the old results. */
    fun reset() {
        search?.cancel()
        setSearching(false)
        resultsBox.removeAllViews()
        status.text = ""
    }

    private fun paintToggles() {
        for ((key, t) in toggles) {
            val on = prefs.getBoolean(key, false)
            t.background = AppTheme.pressable(activity, if (on) colorAccent else colorKey, palette.ripple, 9f, palette.outline)
            t.setTextColor(if (on) palette.lockedText else colorText)
        }
    }

    private fun setSearching(on: Boolean) {
        searching = on
        searchButton.text = if (on) "Stop" else "Search"
    }

    /** Cancels the running search and keeps what it found so far. */
    private fun stopSearch() {
        search?.cancel()
        setSearching(false)
        status.text = "Stopped.  ${status.text.toString().removePrefix("Searching…").trim()}".trimEnd()
    }

    private fun runSearch() {
        search?.cancel()
        setSearching(false)
        resultsBox.removeAllViews()
        hideKeyboard()
        val q = input.text.toString()
        if (!workspace.hasFolder) {
            status.text = ""
            resultsBox.addView(noFolderView(activity, palette, workspace, "Open a folder first to search its files."))
            return
        }
        if (q.isEmpty()) {
            status.text = "Type what to look for."
            return
        }
        status.text = "Searching…"
        setSearching(true)
        val s = FileSearch(activity, workspace)
        search = s
        s.start(
            q,
            prefs.getBoolean("case", false),
            prefs.getBoolean("word", false),
            prefs.getBoolean("regex", false),
            object : FileSearch.Listener {
                override fun onFile(hit: FileHits, files: Int, matches: Int) {
                    if (search !== s) return
                    addResult(hit)
                    status.text = "Searching…  ${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}"
                }

                override fun onProgress(scanned: Int, files: Int, matches: Int) {
                    if (search !== s) return
                    status.text = "Searching…  $scanned files looked at  ·  ${plural(matches, "match", "matches")}"
                }

                override fun onDone(scanned: Int, files: Int, matches: Int, truncated: Boolean) {
                    if (search !== s) return
                    setSearching(false)
                    status.text = when {
                        matches == 0 -> "No matches in $scanned files."
                        truncated -> "First ${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}. Search for something more specific to see the rest."
                        else -> "${plural(matches, "match", "matches")} in ${plural(files, "file", "files")}."
                    }
                }

                override fun onError(message: String) {
                    if (search !== s) return
                    setSearching(false)
                    status.text = message
                }
            }
        )
    }

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

    /** A file's name with its matching lines under it. Tapping the name folds or unfolds the lines. */
    private fun addResult(hit: FileHits) {
        val uri = workspace.uriFor(hit.id) ?: return
        val group = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val lines = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val name = hit.path.substringAfterLast('/')
        val parent = hit.path.substringBeforeLast('/', "")
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(palette.surface)
        }
        header.addView(
            ImageView(activity).apply { setImageDrawable(FileIcons.drawable(name, dp(22), colorMuted)) },
            LinearLayout.LayoutParams(dp(22), dp(22)).apply { setMargins(0, 0, dp(8), 0) }
        )
        header.addView(
            TextView(activity).apply {
                text = if (parent.isEmpty()) name else "$name   $parent"
                val s = SpannableString(text)
                s.setSpan(android.text.style.ForegroundColorSpan(colorMuted), name.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text = s
                setTextColor(colorText)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(TextView(activity).apply {
            text = when {
                hit.matches.isEmpty() -> "name"
                hit.nameMatch -> "name · ${hit.matches.size}"
                else -> hit.matches.size.toString()
            }
            setTextColor(colorAccent)
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
        })
        header.setOnClickListener {
            lines.visibility = if (lines.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        group.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(2), 0, 0) })

        if (hit.nameMatch) {
            // The name matched: a row that opens the file from its start.
            lines.addView(
                TextView(activity).apply {
                    text = "File name matches"
                    setTextColor(colorMuted)
                    textSize = 13f
                    setPadding(dp(14), dp(7), dp(12), dp(7))
                    setOnClickListener {
                        close()
                        host.openFile(uri)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        for (m in hit.matches) {
            val label = "${m.line}  "
            val s = SpannableString(label + m.text)
            s.setSpan(android.text.style.ForegroundColorSpan(colorMuted), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (m.textLen > 0) {
                s.setSpan(
                    BackgroundColorSpan(colorHit),
                    label.length + m.textCol,
                    label.length + m.textCol + m.textLen,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            lines.addView(
                TextView(activity).apply {
                    text = s
                    setTextColor(colorText)
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(14), dp(7), dp(12), dp(7))
                    setOnClickListener {
                        close()
                        host.openFileAt(uri, m.line, m.col, m.length)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        group.addView(lines)
        resultsBox.addView(group)
    }
}
