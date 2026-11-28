package com.whatcode.editor.completion

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.whatcode.editor.CodeEditText
import com.whatcode.ui.AppTheme

/**
 * The small list of suggested words under the cursor. It never takes the focus, so the keyboard stays up and typing
 * goes on into the editor while it shows. Tapping a word calls [onPick].
 */
internal class CompletionPopup(private val editor: CodeEditText, private val onPick: (String) -> Unit) {

    private val context = editor.context
    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(4))
    }

    private val window = PopupWindow(column, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
        isTouchable = true
        isOutsideTouchable = false
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        elevation = dp(6).toFloat()
    }

    private var words: List<String> = emptyList()
    private var prefixLength = 0

    /** The word that Tab or Enter would take. */
    var selected = 0
        private set

    val showing: Boolean get() = window.isShowing

    val selectedWord: String? get() = words.getOrNull(selected)

    fun dismiss() {
        if (window.isShowing) window.dismiss()
    }

    /** Moves the choice up or down the list. */
    fun move(by: Int) {
        if (words.isEmpty()) return
        selected = (selected + by).mod(words.size)
        paint()
    }

    /** Shows [list] for a word of [prefixLength] letters that ends at [cursor]. */
    fun show(list: List<String>, prefixLength: Int, cursor: Int) {
        if (list.isEmpty()) {
            dismiss()
            return
        }
        words = list
        this.prefixLength = prefixLength
        selected = 0
        paint()
        place(cursor)
    }

    private fun paint() {
        val p = AppTheme.palette(context)
        column.background = GradientDrawable().apply {
            setColor(p.raised)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), p.outline)
        }
        column.removeAllViews()
        for ((i, w) in words.withIndex()) {
            val label = SpannableString(w)
            val n = prefixLength.coerceAtMost(w.length)
            label.setSpan(StyleSpan(Typeface.BOLD), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            label.setSpan(ForegroundColorSpan(p.accent), 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val row = TextView(context).apply {
                text = label
                typeface = editor.typeface
                textSize = 15f
                setTextColor(p.text)
                setSingleLine()
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(18), 0)
                minWidth = dp(140)
                if (i == selected) setBackgroundColor(p.accentSoft)
                setOnClickListener { onPick(w) }
            }
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        }
    }

    /** Puts the list under the line of [cursor], or above it when there is no room below. */
    private fun place(cursor: Int) {
        val layout = editor.layout ?: return
        val at = cursor.coerceIn(0, editor.text.length)
        val line = layout.getLineForOffset(at)
        val x = (layout.getPrimaryHorizontal(at) + editor.totalPaddingLeft - editor.scrollX).toInt()
        val below = layout.getLineBottom(line) + editor.totalPaddingTop - editor.scrollY
        val above = layout.getLineTop(line) + editor.totalPaddingTop - editor.scrollY
        column.measure(
            View.MeasureSpec.makeMeasureSpec(editor.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val w = column.measuredWidth
        val h = column.measuredHeight
        val loc = IntArray(2)
        editor.getLocationInWindow(loc)
        val left = loc[0] + x.coerceIn(0, maxOf(0, editor.width - w))
        val visible = android.graphics.Rect()
        editor.getWindowVisibleDisplayFrame(visible)
        var top = loc[1] + below
        if (top + h > visible.bottom && loc[1] + above - h >= visible.top) top = loc[1] + above - h
        if (window.isShowing) window.update(left, top, w, h) else {
            window.width = w
            window.height = h
            window.showAtLocation(editor, Gravity.NO_GRAVITY, left, top)
        }
    }
}
