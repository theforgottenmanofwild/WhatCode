package com.whatcode.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.app.AlertDialog
import com.whatcode.files.IoPlan

/**
 * A box with a message, a bar and, if [onCancel] is given, a Cancel button, for a job that runs in the background.
 * It shows only after [IoPlan.SHOW_AFTER_MS], so a job that ends at once never flashes it. Every method is for the
 * main thread. The box cannot be dismissed by touching outside it or by Back, only by Cancel or when the job ends.
 */
internal class BusyDialog(
    private val activity: Activity,
    private val handler: Handler,
    private val title: String,
    private val onCancel: (() -> Unit)?,
) {
    private val palette = AppTheme.palette(activity)
    private val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        isIndeterminate = true
        progressTintList = ColorStateList.valueOf(palette.accent)
        indeterminateTintList = ColorStateList.valueOf(palette.accent)
    }
    private val detail = TextView(activity).apply {
        setTextColor(palette.muted)
        textSize = 13f
        gravity = Gravity.START
    }
    private var dialog: AlertDialog? = null
    private var ended = false
    private val showTask = Runnable { if (!ended) show() }

    /** Starts the wait before the box shows. */
    fun start() {
        handler.postDelayed(showTask, IoPlan.SHOW_AFTER_MS)
    }

    private fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad / 2 })
        }
        val b = AppTheme.dialogBuilder(activity).setTitle(title).setView(body).setCancelable(false)
        if (onCancel != null) b.setNegativeButton("Cancel") { _, _ -> onCancel.invoke() }
        dialog = b.show().also { AppTheme.styleDialog(it, activity) }
    }

    /** Moves the bar to [done] of [total] and says so. A [total] that is not known leaves the bar moving. */
    fun update(done: Long, total: Long) {
        val pct = IoPlan.percent(done, total)
        bar.isIndeterminate = pct < 0
        if (pct >= 0) bar.progress = pct
        detail.text = IoPlan.describe(done, total)
    }

    /** Takes the box away, or stops it from ever showing. */
    fun end() {
        ended = true
        handler.removeCallbacks(showTask)
        runCatching { dialog?.dismiss() }
        dialog = null
    }
}
