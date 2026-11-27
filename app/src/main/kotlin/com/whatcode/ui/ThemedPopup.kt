package com.whatcode.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import android.widget.TextView

/**
 * A pop-up menu drawn in the app's own colours (surface, outline, text and accent of the chosen theme, accent and
 * true black), which the platform's PopupMenu cannot do: it only follows a fixed style.
 *
 * It is used like a PopupMenu: add items to [menu], set a click listener, call [show]. The platform PopupMenu is
 * kept only as the container of the items (titles, ids, checkable and checked), and is never shown.
 */
class ThemedPopup(
    private val context: Context,
    private val anchor: View,
    private val gravity: Int = Gravity.NO_GRAVITY,
) {
    private val holder = PopupMenu(context, anchor)
    private var listener: PopupMenu.OnMenuItemClickListener? = null

    val menu: Menu get() = holder.menu

    fun setOnMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener?) {
        this.listener = listener
    }

    fun show() {
        val items = (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible }
        if (items.isEmpty()) return
        val palette = AppTheme.palette(context)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val anyCheckable = items.any { it.isCheckable }
        val last = items.lastIndex

        fun row(position: Int): View {
            val item = items[position]
            val title = TextView(context).apply {
                text = item.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(if (item.isEnabled) palette.text else palette.muted)
                maxLines = 1
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(18), if (position == 0) dp(4) else 0, dp(18), if (position == last) dp(4) else 0)
                background = AppTheme.pressable(context, Color.TRANSPARENT, palette.ripple, 0f)
                addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (anyCheckable) {
                    val mark = TextView(context).apply {
                        text = "\u2713"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(palette.accent)
                        visibility = if (item.isCheckable && item.isChecked) View.VISIBLE else View.INVISIBLE
                    }
                    addView(
                        mark,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            .apply { marginStart = dp(16) },
                    )
                }
            }
        }

        // The widest row decides the width, within what the screen allows.
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val widest = items.indices.maxOf { position ->
            row(position).also { it.measure(unspecified, unspecified) }.measuredWidth
        }
        val screen = context.resources.displayMetrics.widthPixels
        val width = widest.coerceIn(dp(176), maxOf(dp(176), minOf(dp(300), screen - dp(32))))

        val window = ListPopupWindow(context)
        window.anchorView = anchor
        window.width = width
        window.isModal = true
        if (gravity != Gravity.NO_GRAVITY) window.setDropDownGravity(gravity)
        window.setBackgroundDrawable(AppTheme.rounded(context, palette.surface, 12f, palette.outline))
        window.setAdapter(object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int): MenuItem = items[position]
            override fun getItemId(position: Int) = items[position].itemId.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = row(position)
            override fun isEnabled(position: Int) = items[position].isEnabled
        })
        window.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            window.dismiss()
            listener?.onMenuItemClick(item)
        }
        window.show()
        window.listView?.apply {
            selector = ColorDrawable(Color.TRANSPARENT)
            divider = null
            dividerHeight = 0
        }
    }
}
