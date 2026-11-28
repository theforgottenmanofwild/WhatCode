package com.whatcode.explorer

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import com.whatcode.files.FileNames
import com.whatcode.ui.AppTheme

    /** Asks for a name in a dialog. A name that can't be used is explained under the field, and the dialog stays. */
internal fun askName(activity: Activity, font: Typeface, title: String, initial: String, okLabel: String, onName: (String) -> Unit) {
    fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
    val input = EditText(activity).apply {
        setText(initial)
        typeface = font
        setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val dot = initial.lastIndexOf('.')
        setSelection(0, if (dot > 0) dot else initial.length)
    }
    val box = FrameLayout(activity).apply {
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    val dialog = AppTheme.dialogBuilder(activity)
        .setTitle(title)
        .setView(box)
        .setPositiveButton(okLabel, null)
        .setNegativeButton("Cancel", null)
        .create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    dialog.show()
    AppTheme.styleDialog(dialog, activity)
    // The button must not close the dialog when the name is no good, so its click is set after show().
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val name = input.text.toString().trim()
        val problem = FileNames.problem(name)
        if (problem != null) {
            input.error = problem
        } else {
            dialog.dismiss()
            onName(name)
        }
    }
    input.requestFocus()
}
