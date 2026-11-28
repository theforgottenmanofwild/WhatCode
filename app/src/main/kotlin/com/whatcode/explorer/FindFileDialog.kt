package com.whatcode.explorer

import android.app.Activity
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.whatcode.explorer.logic.ExplorerFind
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.ui.AppTheme
import java.io.File

/** Find file: name query plus extension, size and date filters over the folder tree. */
internal class FindFileDialog(
    private val activity: Activity,
    private val palette: AppTheme.Palette,
    private val font: Typeface,
) {
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun alive(): Boolean = !activity.isFinishing && !activity.isDestroyed

    /** Opens the dialog for the folder [from]. [onPick] is called with the file or folder the person taps. */
    fun show(from: File, onPick: (File) -> Unit) {
        val input = EditText(activity).apply {
            hint = "Part of a file or folder name"
            typeface = font
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val extInput = EditText(activity).apply {
            hint = "Extensions (e.g. kt, xml)"
            typeface = font
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val sizePresets = listOf("Any size" to 0L, "> 10 KB" to 10_240L, "> 1 MB" to 1_048_576L, "> 10 MB" to 10_485_760L)
        val datePresets = listOf("Any date" to 0L, "Last 24h" to 86_400_000L, "Last 7d" to 7 * 86_400_000L, "Last 30d" to 30L * 86_400_000L)
        var sizeIdx = 0
        var dateIdx = 0

        lateinit var sizeChip: TextView
        lateinit var dateChip: TextView
        lateinit var triggerSearch: () -> Unit

        fun filterChip(label: String, onClick: () -> Unit) = TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(palette.text)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = AppTheme.card(activity, palette, 8f)
            setOnClickListener { onClick() }
        }
        sizeChip = filterChip(sizePresets[0].first) {
            sizeIdx = (sizeIdx + 1) % sizePresets.size
            sizeChip.text = sizePresets[sizeIdx].first
            sizeChip.setTextColor(if (sizeIdx > 0) palette.accent else palette.text)
            triggerSearch()
        }
        dateChip = filterChip(datePresets[0].first) {
            dateIdx = (dateIdx + 1) % datePresets.size
            dateChip.text = datePresets[dateIdx].first
            dateChip.setTextColor(if (dateIdx > 0) palette.accent else palette.text)
            triggerSearch()
        }
        val filtersRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
            addView(extInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sizeChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
            addView(dateChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
        }

        val status = TextView(activity).apply {
            textSize = 12f
            setTextColor(palette.muted)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val results = ListView(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(input)
            addView(filtersRow)
            addView(status)
            addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.36).toInt()))
        }
        val dialog = AppTheme.dialogBuilder(activity)
            .setTitle("Find file in ${ExplorerPaths.name(from.path)}")
            .setView(box)
            .setNegativeButton("Close", null)
            .create()

        var shown: List<File> = emptyList()
        var run = 0

        fun showResults(found: List<File>) {
            shown = found
            results.adapter = object : ArrayAdapter<String>(
                activity, android.R.layout.simple_list_item_1,
                found.map { f ->
                    val parent = (f.parent ?: "").removePrefix(from.path).trim('/')
                    (if (f.isDirectory) f.name + "/" else f.name) + if (parent.isEmpty()) "" else "   \u00B7   $parent"
                }
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = super.getView(position, convertView, parent) as TextView
                    row.setTextColor(palette.text)
                    row.typeface = font
                    row.textSize = 14f
                    return row
                }
            }
        }

        val search = Runnable {
            val query = input.text.toString()
            val extSet = ExplorerFind.Filter.parseExtensions(extInput.text.toString())
            val minSize = sizePresets[sizeIdx].second
            val ageMs = datePresets[dateIdx].second
            val afterMs = if (ageMs > 0L) System.currentTimeMillis() - ageMs else 0L
            val filter = ExplorerFind.Filter(extensions = extSet, minBytes = minSize, modifiedAfterMs = afterMs)
            val mine = ++run
            if (query.isBlank() && !filter.active) {
                showResults(emptyList())
                status.text = "Type part of a name or set a filter above."
                return@Runnable
            }
            status.text = "Searching\u2026"
            Thread {
                val found = ExplorerFind.search(from, query, 200, filter) { mine != run || !alive() }
                ui.post {
                    if (mine != run || !alive()) return@post
                    showResults(found.files)
                    status.text = when {
                        found.files.isEmpty() -> "Nothing found."
                        found.truncated -> "First ${found.files.size} found. Narrow your query or filters."
                        else -> "${found.files.size} found."
                    }
                }
            }.start()
        }
        triggerSearch = {
            ui.removeCallbacks(search)
            ui.postDelayed(search, 120)
        }
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                ui.removeCallbacks(search)
                ui.postDelayed(search, 250)
            }
        }
        input.addTextChangedListener(watcher)
        extInput.addTextChangedListener(watcher)
        status.text = "Type part of a name or set a filter above."
        results.setOnItemClickListener { _, _, position, _ ->
            val file = shown.getOrNull(position) ?: return@setOnItemClickListener
            dialog.dismiss()
            onPick(file)
        }
        dialog.setOnDismissListener {
            run++
            ui.removeCallbacks(search)
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        AppTheme.styleDialog(dialog, activity)
        input.requestFocus()
        search.run()
    }
}
