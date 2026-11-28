package com.whatcode.editor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Go to symbol: the functions, classes and headings of the open file in a list with a filter box; a tap goes there. */
internal object SymbolDialog {

    fun show(activity: Activity, editor: CodeEditText, lang: Lang) {
        val ui = Handler(Looper.getMainLooper())
        val length = editor.text.length
        if (length < Outline.SYNC_BELOW) {
            list(activity, editor, Outline.find(editor.text, lang))
            return
        }
        // A long text is read on another thread, with a box that can stop it.
        val snapshot = editor.text.toString()
        val stop = AtomicBoolean(false)
        val box = BusyDialog(activity, ui, "Finding symbols") { stop.set(true) }
        box.start()
        Thread {
            var found: List<Outline.Symbol>? = null
            try {
                found = Outline.find(snapshot, lang, { stop.get() })
            } catch (_: CancellationException) {
            } catch (_: Throwable) {
            }
            ui.post {
                box.end()
                if (activity.isFinishing || activity.isDestroyed) return@post
                found?.let { list(activity, editor, it) }
            }
        }.start()
    }

    private fun list(activity: Activity, editor: CodeEditText, all: List<Outline.Symbol>) {
        if (all.isEmpty()) {
            Toast.makeText(activity, "No functions, classes or headings found", Toast.LENGTH_SHORT).show()
            return
        }
        val palette = AppTheme.palette(activity)
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        var shown: List<Outline.Symbol> = all
        var dialog: android.app.AlertDialog? = null

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
                val s = shown[i]
                val row = (convert as? TextView) ?: TextView(activity).apply {
                    setTextColor(palette.text)
                    textSize = 15f
                    setPadding(dp(8), dp(10), dp(8), dp(10))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val mark = when (s.kind) {
                    Outline.Kind.FUNCTION -> "ƒ "
                    Outline.Kind.TYPE -> "◆ "
                    Outline.Kind.HEADING -> "§ "
                }
                row.text = "\u2007".repeat((s.indent / 2).coerceAtMost(8)) + mark + s.name + "   ${s.line}"
                return row
            }
        }
        val filter = EditText(activity).apply {
            hint = "Filter ${all.size} symbols"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim().orEmpty()
                    shown = if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) }
                    adapter.notifyDataSetChanged()
                }
            })
        }
        val listView = ListView(activity).apply {
            this.adapter = adapter
            divider = null
            setOnItemClickListener { _, _, i, _ ->
                val s = shown[i]
                dialog?.dismiss()
                editor.setSelection(s.offset.coerceIn(0, editor.text.length))
                editor.bringPointIntoView(s.offset.coerceIn(0, editor.text.length))
                editor.requestFocus()
            }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            gravity = Gravity.START
            addView(filter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        }
        dialog = AppTheme.dialogBuilder(activity)
            .setTitle("Go to symbol")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        dialog.show()
        AppTheme.styleDialog(dialog, activity)
    }
}
