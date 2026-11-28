package com.whatcode.settings

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.ui.AppTheme
import com.whatcode.ui.SettingsPage

/**
 * A settings page that is a list of other pages: rows with a glyph, a title and a line under it, grouped in rounded
 * cards under small headings. The Settings hub and the File explorer page are built on it.
 */
abstract class SettingsHubPage(title: String) : SettingsPage(title) {

    protected fun groupTitle(label: String) = TextView(this).apply {
        text = label
        setTextColor(colorMuted)
        textSize = 12f
        letterSpacing = 0.12f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(22), 0, dp(9))
    }

    /** Rows that belong together share one rounded card, with a thin line between them. */
    protected fun group(vararg rows: View): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AppTheme.card(this@SettingsHubPage, palette, 18f)
            clipToOutline = true
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) {
                card.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    setMargins(dp(70), 0, 0, 0)
                })
            }
            card.addView(row)
        }
        return card
    }

    protected fun settingRow(
        glyph: SettingsGlyph,
        title: String,
        subtitle: String,
        keepTitle: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val p = palette
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = RippleDrawable(ColorStateList.valueOf(p.ripple), null, ColorDrawable(0xFFFFFFFF.toInt()))
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
        row.addView(SettingsGlyphView(this, glyph, p.accent, p.accentSoft), LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            setMargins(0, 0, dp(14), 0)
        })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val heading = TextView(this).apply {
            text = title
            setTextColor(p.text)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        labels.addView(heading)
        labels.addView(TextView(this).apply {
            text = subtitle
            setTextColor(p.muted)
            textSize = 13f
            setPadding(0, dp(2), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(p.muted)
        }, LinearLayout.LayoutParams(dp(28), dp(36)))
        row.minimumHeight = dp(68)
        row.tag = if (keepTitle) heading else null
        return row
    }
}
