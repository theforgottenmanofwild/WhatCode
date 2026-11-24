package com.whatcodeeditor.files

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import java.util.Locale
import kotlin.math.min

/**
 * What one kind of file looks like: its [color], and either a short [label] written on the page ("GO", "JS", "{}")
 * or, when there is no label, a picture ([image] for a mountain and sun, otherwise lines of text).
 */
class FileLook(val color: Int, val label: String? = null, val image: Boolean = false)

/** The icons for files in the sidebar: a page with a folded corner, coloured and marked for the kind of file. */
object FileIcons {

    private val TEXT = FileLook(0xFF9DA5B0.toInt())

    private val byExt = HashMap<String, FileLook>()
    private val byName = HashMap<String, FileLook>()

    private fun add(exts: String, color: Long, label: String? = null, image: Boolean = false) {
        val look = FileLook(color.toInt(), label, image)
        for (e in exts.split(" ")) if (e.isNotEmpty()) byExt[e] = look
    }

    private fun addName(names: String, look: FileLook) {
        for (n in names.split(" ")) if (n.isNotEmpty()) byName[n] = look
    }

    init {
        add("go", 0xFF00ADD8, "GO")
        add("html htm", 0xFFE44D26, "<>")
        add("xml", 0xFFE9A23B, "XML")
        add("svg", 0xFFFFB13B, "SVG")
        add("css scss sass less", 0xFF42A5F5, "#")
        add("js jsx mjs cjs", 0xFFF7DF1E, "JS")
        add("ts tsx", 0xFF3178C6, "TS")
        add("json", 0xFFCBCB41, "{}")
        add("md markdown", 0xFF9CDCFE, "MD")
        add("py", 0xFF5EA2D6, "PY")
        add("kt kts", 0xFFB07CFF, "KT")
        add("java", 0xFFE76F00, "JV")
        add("gradle", 0xFF5AB68E, "GR")
        add("c", 0xFF6C9BD2, "C")
        add("h hpp", 0xFFA074C4, "H")
        add("cpp cc cxx", 0xFF659AD2, "C+")
        add("cs", 0xFF9B7BDB, "C#")
        add("rs", 0xFFDEA584, "RS")
        add("swift", 0xFFFA7343, "SW")
        add("dart", 0xFF40C4FF, "DT")
        add("php", 0xFF8892BF, "PHP")
        add("rb", 0xFFE0475B, "RB")
        add("scala", 0xFFDC322F, "SC")
        add("sh bash zsh", 0xFF89E051, ">_")
        add("yml yaml", 0xFFE5C07B, "YML")
        add("toml", 0xFFC678DD, "TML")
        add("ini conf cfg", 0xFF9DA5B0, "CFG")
        add("sql", 0xFFE38C00, "SQL")
        add("zip tar gz tgz 7z rar", 0xFFD19A66, "ZIP")
        add("apk", 0xFF3DDC84, "APK")
        add("png jpg jpeg gif webp bmp ico", 0xFFB48EAD, image = true)
        // Text files have no entry: they, and every kind not listed, get the grey page of text lines.

        addName("go.mod go.sum", byExt.getValue("go"))
        addName("dockerfile", FileLook(0xFF2496ED.toInt(), "DK"))
        addName("makefile", FileLook(0xFF89E051.toInt(), "MK"))
    }

    fun lookFor(fileName: String, fallbackColor: Int = TEXT.color): FileLook {
        val lower = fileName.lowercase(Locale.ROOT)
        byName[lower]?.let { return it }
        val ext = if ('.' in lower) lower.substringAfterLast('.') else ""
        return byExt[ext] ?: FileLook(fallbackColor)
    }

    /** The icon for a file called [fileName], [sizePx] square. It brings its own colours, so do not tint it. */
    fun drawable(fileName: String, sizePx: Int, fallbackColor: Int = TEXT.color): Drawable =
        FileIconDrawable(lookFor(fileName, fallbackColor), sizePx)
}

/** One file icon, drawn with paths on a 24 x 24 grid. */
private class FileIconDrawable(private val look: FileLook, private val sizePx: Int) : Drawable() {

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.6f
        color = look.color
    }
    private val detail = Paint(outline).apply { strokeWidth = 1.4f }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = look.color
        alpha = 40
    }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = look.color
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = look.color
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f)
        canvas.scale(s, s)

        val page = Path().apply {
            moveTo(6f, 3f)
            lineTo(14f, 3f)
            lineTo(19f, 8f)
            lineTo(19f, 21f)
            lineTo(6f, 21f)
            close()
        }
        canvas.drawPath(page, body)
        canvas.drawPath(page, outline)
        val fold = Path().apply {
            moveTo(14f, 3f)
            lineTo(14f, 8f)
            lineTo(19f, 8f)
        }
        canvas.drawPath(fold, outline)

        val label = look.label
        when {
            label != null -> {
                val size = when (label.length) {
                    1 -> 9f
                    2 -> 7f
                    else -> 5.4f
                }
                text.textSize = size
                canvas.drawText(label, 12.5f, 14.8f + 0.36f * size, text)
            }
            look.image -> {
                canvas.drawCircle(15f, 12.4f, 1.1f, sun)
                val hills = Path().apply {
                    moveTo(8f, 19f)
                    lineTo(11f, 14.8f)
                    lineTo(13.2f, 17.2f)
                    lineTo(14.6f, 15.8f)
                    lineTo(17f, 19f)
                }
                canvas.drawPath(hills, detail)
            }
            else -> {
                val lines = Path().apply {
                    moveTo(9f, 12f)
                    lineTo(16f, 12f)
                    moveTo(9f, 15f)
                    lineTo(16f, 15f)
                    moveTo(9f, 18f)
                    lineTo(13f, 18f)
                }
                canvas.drawPath(lines, detail)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        outline.alpha = alpha
        detail.alpha = alpha
        sun.alpha = alpha
        text.alpha = alpha
        body.alpha = alpha * 40 / 255
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // The icon keeps its own colours.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
