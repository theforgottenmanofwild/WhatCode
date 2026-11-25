package com.whatcode.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * WCAG 2.x colour helpers: how readable one colour is on another, and small adjustments to reach a wanted ratio.
 * Colours are ARGB ints and are treated as opaque. Plain Kotlin on purpose, so it is tested without a device.
 */
object Contrast {
    /** Normal-size text should reach this ratio against its background (WCAG AA). */
    const val TEXT = 4.5

    /** The least that a colour chosen by the person may have against the editor background. */
    const val MINIMUM = 3.0

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** Relative luminance, 0 (black) to 1 (white). */
    fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Contrast ratio of two colours, from 1 (identical) to 21 (black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [from] moved [amount] (0..1) of the way towards [to], as an opaque colour. */
    fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return 0xFF000000.toInt() or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /**
     * [color] unchanged if it already has [min] contrast on [background]. Otherwise it is moved towards black or
     * white, whichever of those reads better on [background], in small steps until it does.
     */
    fun ensure(color: Int, background: Int, min: Double): Int {
        val opaque = color or 0xFF000000.toInt()
        if (ratio(opaque, background) >= min) return opaque
        val target = if (ratio(WHITE, background) >= ratio(BLACK, background)) WHITE else BLACK
        var step = 1
        while (step <= 20) {
            val candidate = blend(opaque, target, step / 20f)
            if (ratio(candidate, background) >= min) return candidate
            step++
        }
        return target
    }

    /** [dark] or white, whichever reads better as text on [fill]. */
    fun inkOn(fill: Int, dark: Int): Int = if (ratio(WHITE, fill) >= ratio(dark, fill)) WHITE else dark
}

/** Reading and writing colours as `#RRGGBB`, for the hex field of the colour picker. */
object Hex {
    /** `#RGB`, `#RRGGBB` or the same without the `#`, in any case. Returns an opaque colour, or null if it is not one. */
    fun parse(text: String): Int? {
        var s = text.trim()
        if (s.startsWith("#")) s = s.substring(1)
        if (s.length == 3) s = buildString { for (c in s) { append(c); append(c) } }
        if (s.length != 6 || !s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000.toInt() or s.toInt(16)
    }

    fun format(color: Int): String = "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
}
