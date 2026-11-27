package com.whatcode.ui

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
import com.whatcode.R
import com.whatcode.settings.AppearanceSettings
import java.util.concurrent.ConcurrentHashMap

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
    private const val PREFS = AppearanceSettings.FILE
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
        /** Text on a button, key or tag that is filled with [accent]; chosen so it is readable on any accent. */
        val onAccent: Int,
        val editor: Int,
        val status: Int,
        /** Text on the status bar's [status] fill. */
        val onStatus: Int,
        val code: Int,
        val ripple: Int,
        val hit: Int,
        val lockedText: Int,
        /** The editor's selection; see TextView.setHighlightColor. Translucent, so the text stays readable. */
        val selection: Int,
        /** The soft background of the line with the cursor (Settings > Appearance > Highlight the current line). */
        val currentLine: Int,
    )

    /** The ink used on a filled accent in the dark theme, where the accents are light. */
    private const val DARK_INK = 0xFF17212C.toInt()

    private val cache = ConcurrentHashMap<String, Palette>()

    /**
     * The colours of one theme. [pureBlack] only matters for the dark theme: it swaps the dark grey backgrounds for
     * black. Plain Kotlin on purpose, so ThemeContrastTest can check every combination without a device.
     */
    fun buildPalette(light: Boolean, accent: AccentColor, pureBlack: Boolean): Palette {
        val key = "$light|${accent.key}|${pureBlack && !light}"
        return cache.getOrPut(key) { if (light) buildLight(accent) else buildDark(accent, pureBlack) }
    }

    private fun buildLight(a: AccentColor): Palette {
        val accent = a.light
        val surface = 0xFFFFFFFF.toInt()
        val editor = 0xFFFCFDFE.toInt()
        val onAccent = Contrast.inkOn(accent, DARK_INK)
        return Palette(
            isLight = true,
            background = 0xFFF3F6FA.toInt(),
            surface = surface,
            raised = 0xFFF9FBFD.toInt(),
            rail = 0xFFE9EFF5.toInt(),
            key = 0xFFE8EEF5.toInt(),
            keyPressed = 0xFFD6E2EE.toInt(),
            outline = 0xFFD9E2EB.toInt(),
            text = 0xFF1D2937.toInt(),
            muted = 0xFF5A6A7E.toInt(),
            accent = accent,
            accentSoft = Contrast.blend(surface, accent, 0.12f),
            onAccent = onAccent,
            editor = editor,
            status = a.statusLight,
            onStatus = 0xFFFFFFFF.toInt(),
            code = 0xFFF0F3F7.toInt(),
            ripple = withAlpha(accent, 0x33),
            hit = 0x55F5C451,
            lockedText = onAccent,
            selection = withAlpha(accent, 0x40),
            currentLine = Contrast.blend(editor, accent, 0.07f),
        )
    }

    private fun buildDark(a: AccentColor, pureBlack: Boolean): Palette {
        val accent = a.dark
        val surface = if (pureBlack) 0xFF0B0B0D.toInt() else 0xFF20242A.toInt()
        val editor = if (pureBlack) 0xFF000000.toInt() else 0xFF171A1F.toInt()
        val onAccent = Contrast.inkOn(accent, DARK_INK)
        return Palette(
            isLight = false,
            background = if (pureBlack) 0xFF000000.toInt() else 0xFF181B20.toInt(),
            surface = surface,
            raised = if (pureBlack) 0xFF141417.toInt() else 0xFF272D35.toInt(),
            rail = if (pureBlack) 0xFF000000.toInt() else 0xFF15181D.toInt(),
            key = if (pureBlack) 0xFF1C1C20.toInt() else 0xFF303741.toInt(),
            keyPressed = if (pureBlack) 0xFF28282D.toInt() else 0xFF3C4652.toInt(),
            outline = if (pureBlack) 0xFF2A2A30.toInt() else 0xFF343D48.toInt(),
            text = 0xFFE7EDF4.toInt(),
            muted = 0xFFA1ADBB.toInt(),
            accent = accent,
            accentSoft = Contrast.blend(surface, accent, 0.16f),
            onAccent = onAccent,
            editor = editor,
            status = a.statusDark,
            onStatus = 0xFFFFFFFF.toInt(),
            code = if (pureBlack) 0xFF121214.toInt() else 0xFF2B3038.toInt(),
            ripple = withAlpha(accent, 0x44),
            hit = 0x55FFC451,
            lockedText = onAccent,
            selection = withAlpha(accent, 0x55),
            currentLine = Contrast.blend(editor, accent, 0.10f),
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)

    /** The standard blue themes, which are what the app looked like before accents and true black existed. */
    val lightPalette: Palette get() = buildPalette(true, AccentColor.BLUE, false)
    val darkPalette: Palette get() = buildPalette(false, AccentColor.BLUE, false)

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

    /** The palette the app shows right now: the chosen mode, accent and dark style. */
    fun palette(context: Context): Palette = palette(context, resolvedLight(context))

    /** The light or the dark palette with the chosen accent and dark style, whichever mode is in use. */
    fun palette(context: Context, light: Boolean): Palette {
        val settings = AppearanceSettings(context)
        return buildPalette(light, settings.accent, settings.pureBlack)
    }

    /** Selects native widget resources before Activity.onCreate inflates any platform controls. */
    fun applyActivityTheme(activity: Activity) {
        activity.setTheme(
            if (resolvedLight(activity)) R.style.WhatCodeTheme_Light
            else R.style.WhatCodeTheme_Dark
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
        val theme = if (light) R.style.WhatCodeDialog_Light else R.style.WhatCodeDialog_Dark
        return AlertDialog.Builder(ContextThemeWrapper(context, theme))
    }

    /** A menu drawn in the colours of the current theme; see [ThemedPopup]. */
    fun popup(context: Context, anchor: View, gravity: Int = Gravity.NO_GRAVITY): ThemedPopup =
        ThemedPopup(context, anchor, gravity)

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
