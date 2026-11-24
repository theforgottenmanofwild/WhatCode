package com.whatcodeeditor.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu
import com.whatcodeeditor.R

/** The appearance preference is deliberately independent from the editor's typing preferences. */
enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System");

    companion object {
        fun from(value: String?): ThemeMode = values().firstOrNull { it.key == value } ?: SYSTEM
    }
}

/** Shared colours and small drawing helpers used by every screen. */
object AppTheme {
    private const val PREFS = "appearance"
    private const val MODE_KEY = "color_mode"

    data class Palette(
        val isLight: Boolean,
        val background: Int,
        val surface: Int,
        val raised: Int,
        val rail: Int,
        val key: Int,
        val keyPressed: Int,
        val outline: Int,
        val text: Int,
        val muted: Int,
        val accent: Int,
        val accentSoft: Int,
        val onAccent: Int,
        val editor: Int,
        val status: Int,
        val code: Int,
        val ripple: Int,
        val hit: Int,
        val lockedText: Int,
    )

    val lightPalette = Palette(
        isLight = true,
        background = 0xFFF3F6FA.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        raised = 0xFFF9FBFD.toInt(),
        rail = 0xFFE9EFF5.toInt(),
        key = 0xFFE8EEF5.toInt(),
        keyPressed = 0xFFD6E2EE.toInt(),
        outline = 0xFFD9E2EB.toInt(),
        text = 0xFF1D2937.toInt(),
        muted = 0xFF627286.toInt(),
        accent = 0xFF176EB5.toInt(),
        accentSoft = 0xFFE4F0FC.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        editor = 0xFFFCFDFE.toInt(),
        status = 0xFF236DA8.toInt(),
        code = 0xFFF0F3F7.toInt(),
        ripple = 0x33266FAE,
        hit = 0x55F5C451,
        lockedText = 0xFFFFFFFF.toInt(),
    )

    val darkPalette = Palette(
        isLight = false,
        background = 0xFF181B20.toInt(),
        surface = 0xFF20242A.toInt(),
        raised = 0xFF272D35.toInt(),
        rail = 0xFF15181D.toInt(),
        key = 0xFF303741.toInt(),
        keyPressed = 0xFF3C4652.toInt(),
        outline = 0xFF343D48.toInt(),
        text = 0xFFE7EDF4.toInt(),
        muted = 0xFFA1ADBB.toInt(),
        accent = 0xFF65B2F7.toInt(),
        accentSoft = 0xFF1C354C.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        editor = 0xFF171A1F.toInt(),
        status = 0xFF286EA9.toInt(),
        code = 0xFF2B3038.toInt(),
        ripple = 0x447DBFFF,
        hit = 0x55FFC451,
        lockedText = 0xFF17212C.toInt(),
    )

    fun mode(context: Context): ThemeMode = ThemeMode.from(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODE_KEY, ThemeMode.SYSTEM.key)
    )

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE_KEY, mode.key).apply()
    }

    fun resolvedLight(context: Context, mode: ThemeMode = mode(context)): Boolean = when (mode) {
        ThemeMode.LIGHT -> true
        ThemeMode.DARK -> false
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
    }

    fun palette(context: Context): Palette = if (resolvedLight(context)) lightPalette else darkPalette

    /** Selects native widget resources before Activity.onCreate inflates any platform controls. */
    fun applyActivityTheme(activity: Activity) {
        activity.setTheme(
            if (resolvedLight(activity)) R.style.WhatCodeEditorTheme_Light
            else R.style.WhatCodeEditorTheme_Dark
        )
    }

    /** Keeps settings and secondary pages readable on tablets instead of stretching controls edge to edge. */
    fun scrollContentParams(context: Context, maxWidthDp: Int = 720): FrameLayout.LayoutParams {
        val width = if (context.resources.configuration.screenWidthDp >= 760) {
            (maxWidthDp * context.resources.displayMetrics.density).toInt()
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        return FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
    }

    fun applyWindow(activity: Activity) {
        val p = palette(activity)
        activity.window.statusBarColor = p.surface
        activity.window.navigationBarColor = p.background
        if (Build.VERSION.SDK_INT >= 26) {
            val flags = if (p.isLight) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                0
            }
            activity.window.decorView.systemUiVisibility = flags
        }
        if (Build.VERSION.SDK_INT >= 29) {
            activity.window.isNavigationBarContrastEnforced = false
            activity.window.isStatusBarContrastEnforced = false
        }
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun rounded(
        context: Context,
        color: Int,
        radiusDp: Float = 14f,
        strokeColor: Int? = null,
        strokeDp: Float = 1f,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        if (strokeColor != null) {
            setStroke((strokeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    fun pressable(
        context: Context,
        fill: Int,
        ripple: Int,
        radiusDp: Float = 14f,
        stroke: Int? = null,
    ): Drawable {
        val shape = rounded(context, fill, radiusDp, stroke)
        val mask = rounded(context, 0xFFFFFFFF.toInt(), radiusDp)
        return RippleDrawable(ColorStateList.valueOf(ripple), shape, mask)
    }

    fun card(context: Context, palette: Palette, radiusDp: Float = 16f): Drawable =
        rounded(context, palette.surface, radiusDp, palette.outline)

    fun button(context: Context, button: Button, palette: Palette, primary: Boolean = false) {
        button.isAllCaps = false
        button.minWidth = 0
        button.minimumWidth = 0
        button.minHeight = (44 * context.resources.displayMetrics.density).toInt()
        button.setPadding(
            (16 * context.resources.displayMetrics.density).toInt(),
            0,
            (16 * context.resources.displayMetrics.density).toInt(),
            0,
        )
        button.setTextColor(if (primary) palette.onAccent else palette.text)
        button.background = pressable(
            context,
            if (primary) palette.accent else palette.key,
            palette.ripple,
            12f,
            if (primary) null else palette.outline,
        )
        button.stateListAnimator = null
    }

    fun switchTint(palette: Palette): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(palette.accent, palette.muted),
    )

    fun dialogBuilder(context: Context): AlertDialog.Builder {
        val light = palette(context).isLight
        val theme = if (light) R.style.WhatCodeEditorDialog_Light else R.style.WhatCodeEditorDialog_Dark
        return AlertDialog.Builder(ContextThemeWrapper(context, theme))
    }

    fun popup(context: Context, anchor: View, gravity: Int = Gravity.NO_GRAVITY): PopupMenu {
        val theme = if (palette(context).isLight) R.style.WhatCodeEditorPopup_Light else R.style.WhatCodeEditorPopup_Dark
        return PopupMenu(ContextThemeWrapper(context, theme), anchor, gravity)
    }

    fun styleDialog(dialog: AlertDialog, context: Context) {
        val p = palette(context)
        dialog.window?.setBackgroundDrawable(rounded(context, p.surface, 18f, p.outline))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(p.accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(p.muted)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(p.accent)
    }

    fun applyCard(view: View, context: Context, palette: Palette, radiusDp: Float = 16f) {
        view.background = card(context, palette, radiusDp)
    }
}
