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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.whatcode.explorer.logic.ExplorerFind
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.ui.AppTheme
import java.io.File

/** Find file: a box to type part of a name in, and the files and folders below a folder that match. */
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
            addView(status)
            addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (activity.resources.displayMetrics.heightPixels * 0.4).toInt()))
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
            val mine = ++run
            if (query.isBlank()) {
                showResults(emptyList())
                status.text = "Type part of a name. This folder and every folder below it are searched."
                return@Runnable
            }
            status.text = "Searching\u2026"
            Thread {
                val found = ExplorerFind.search(from, query, 200) { mine != run || !alive() }
                ui.post {
                    if (mine != run || !alive()) return@post
                    showResults(found.files)
                    status.text = when {
                        found.files.isEmpty() -> "Nothing found."
                        found.truncated -> "First ${found.files.size} found. Keep typing to narrow it down."
                        else -> "${found.files.size} found."
                    }
                }
            }.start()
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                ui.removeCallbacks(search)
                ui.postDelayed(search, 250)
            }
        })
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
