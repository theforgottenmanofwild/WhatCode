package com.whatcode.editor

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import java.util.concurrent.atomic.AtomicBoolean

/** The Text tools menu, and running one of its entries on the editor as a single undo step. */
internal object TextToolRunner {

    /** Lists [tools] under [title] and runs the one that is tapped. */
    fun menu(
        activity: Activity,
        editor: CodeEditText,
        options: TextTools.Options,
        title: String = "Text tools",
        tools: List<TextTool> = TextTool.values().toList(),
    ) {
        AppTheme.dialogBuilder(activity)
            .setTitle(title)
            .setItems(tools.map { it.label }.toTypedArray()) { _, i -> run(activity, editor, tools[i], options) }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    /**
     * Runs [tool]. A long text is worked on off the main thread, with a box that can cancel it; the result is dropped
     * if the text changed in the meantime.
     */
    fun run(activity: Activity, editor: CodeEditText, tool: TextTool, options: TextTools.Options) {
        val a = editor.selectionStart
        val b = editor.selectionEnd
        if (a < 0 || b < 0) return
        if (editor.text.length < TextTools.BACKGROUND_FROM) {
            try {
                apply(activity, editor, tool, TextTools.edit(tool, editor.text, a, b, options))
            } catch (e: TextToolFailure) {
                toast(activity, e.message ?: "That did not work", long = true)
            }
            return
        }
        val ui = Handler(Looper.getMainLooper())
        val snapshot = editor.text.toString()
        var changed = false
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                changed = true
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        editor.addTextChangedListener(watcher)
        val stop = AtomicBoolean(false)
        val box = BusyDialog(activity, ui, tool.label) { stop.set(true) }
        box.start()
        Thread {
            var edit: TextEdit? = null
            var failed = false
            var refused: String? = null
            try {
                edit = TextTools.edit(tool, snapshot, a, b, options)
            } catch (e: TextToolFailure) {
                refused = e.message ?: "That did not work"
            } catch (_: OutOfMemoryError) {
                failed = true
            }
            ui.post {
                box.end()
                editor.removeTextChangedListener(watcher)
                if (activity.isFinishing || activity.isDestroyed || stop.get() || changed) return@post
                if (failed) toast(activity, "There is not enough memory to do that to this text")
                else if (refused != null) toast(activity, refused, long = true)
                else apply(activity, editor, tool, edit)
            }
        }.start()
    }

    private fun apply(activity: Activity, editor: CodeEditText, tool: TextTool, edit: TextEdit?) {
        if (edit == null) {
            toast(activity, TextTools.nothingToDo(tool))
            return
        }
        val marks = editor.bookmarkOffsets()
        val lines = if (tool.keepsLines && marks.isNotEmpty()) TextTools.lineNumbers(editor.text, marks) else null
        editor.applyStep(edit)
        if (lines != null) editor.setBookmarkOffsets(TextTools.lineOffsets(editor.text, lines))
        editor.bringPointIntoView(editor.selectionEnd.coerceAtLeast(0))
    }

    private fun toast(activity: Activity, message: String, long: Boolean = false) =
        Toast.makeText(activity, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
