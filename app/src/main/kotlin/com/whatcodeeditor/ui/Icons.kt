package com.whatcodeeditor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The icons the app draws itself (there are no image resources in the app). */
enum class Icon {
    // find bar
    PREVIOUS, NEXT, CLOSE, SETTINGS, REPLACE, REPLACE_ALL,

    // key rows (the left and right arrow keys use PREVIOUS and NEXT)
    UP, DOWN, TAB, FIND, UNDO, REDO, SAVE, MOVE_UP, MOVE_DOWN, COPY_DOWN, DELETE_LINE,

    // sidebar
    MENU, FILES, FOLDER, FILE,
}

/**
 * One [Icon], drawn with paths on a 24 x 24 grid and scaled to whatever size it is given.
 * It is drawn in white; tint it with a colour filter (see [Icons.button] and [KeyView.setIcon]) to give it its colour.
 */
class VectorIcon(private val icon: Icon, private val sizePx: Int) : Drawable() {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.7f
        color = Color.WHITE
    }
    private val fine = Paint(line).apply { strokeWidth = 1.2f }
    private val dashed = Paint(line).apply { pathEffect = DashPathEffect(floatArrayOf(2.2f, 1.8f), 0f) }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f)
        canvas.scale(s, s)
        when (icon) {
            Icon.PREVIOUS -> arrow(canvas, 180f)
            Icon.NEXT -> arrow(canvas, 0f)
            Icon.UP -> arrow(canvas, 270f)
            Icon.DOWN -> arrow(canvas, 90f)
            Icon.CLOSE -> close(canvas)
            Icon.SETTINGS -> gear(canvas)
            Icon.REPLACE -> replace(canvas, all = false)
            Icon.REPLACE_ALL -> replace(canvas, all = true)
            Icon.TAB -> tab(canvas)
            Icon.FIND -> find(canvas)
            Icon.UNDO -> undo(canvas, mirror = false)
            Icon.REDO -> undo(canvas, mirror = true)
            Icon.SAVE -> save(canvas)
            Icon.MOVE_UP -> moveLines(canvas, up = true)
            Icon.MOVE_DOWN -> moveLines(canvas, up = false)
            Icon.COPY_DOWN -> copyDown(canvas)
            Icon.DELETE_LINE -> trash(canvas)
            Icon.MENU -> menu(canvas)
            Icon.FILES -> files(canvas)
            Icon.FOLDER -> folder(canvas)
            Icon.FILE -> page(canvas, 0f, 0f)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        line.alpha = alpha
        fine.alpha = alpha
        dashed.alpha = alpha
        solid.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        line.colorFilter = colorFilter
        fine.colorFilter = colorFilter
        dashed.colorFilter = colorFilter
        solid.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // ------------------------------------------------------------------ the shapes

    /** A shaft with a chevron head, drawn pointing right and turned by [degrees] (0 right, 90 down, 180 left, 270 up). */
    private fun arrow(c: Canvas, degrees: Float) {
        c.save()
        c.rotate(degrees, 12f, 12f)
        val p = Path().apply {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
        c.drawPath(p, line)
        c.restore()
    }

    /** Three horizontal bars. */
    private fun menu(c: Canvas) {
        val p = Path().apply {
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17.5f)
            lineTo(20f, 17.5f)
        }
        c.drawPath(p, line)
    }

    /** A folder with a tab. */
    private fun folder(c: Canvas) {
        val p = Path().apply {
            moveTo(3f, 6f)
            lineTo(9.5f, 6f)
            lineTo(11.5f, 8.5f)
            lineTo(21f, 8.5f)
            lineTo(21f, 19f)
            lineTo(3f, 19f)
            close()
        }
        c.drawPath(p, line)
    }

    /** One page with a folded corner, moved by ([dx], [dy]). */
    private fun page(c: Canvas, dx: Float, dy: Float) {
        val p = Path().apply {
            moveTo(6f + dx, 3f + dy)
            lineTo(14f + dx, 3f + dy)
            lineTo(19f + dx, 8f + dy)
            lineTo(19f + dx, 21f + dy)
            lineTo(6f + dx, 21f + dy)
            close()
            moveTo(14f + dx, 3f + dy)
            lineTo(14f + dx, 8f + dy)
            lineTo(19f + dx, 8f + dy)
        }
        c.drawPath(p, line)
    }

    /** Two pages, one behind the other. */
    private fun files(c: Canvas) {
        c.save()
        c.translate(7f, 0f)
        c.scale(0.75f, 0.75f)
        page(c, 0f, 0f)
        c.restore()
        c.save()
        c.translate(0f, 4f)
        c.scale(0.75f, 0.75f)
        page(c, 0f, 0f)
        c.restore()
    }

    private fun close(c: Canvas) {
        val p = Path().apply {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
        c.drawPath(p, line)
    }

    /** A cog: eight teeth round a hub with a hole in the middle. */
    private fun gear(c: Canvas) {
        fun at(r: Float, deg: Float): Pair<Float, Float> {
            val a = Math.toRadians(deg.toDouble())
            return (12 + r * cos(a)).toFloat() to (12 + r * sin(a)).toFloat()
        }
        val p = Path().apply { fillType = Path.FillType.EVEN_ODD }
        for (i in 0 until 8) {
            val a = i * 45f
            val pts = listOf(at(7.6f, a - 15f), at(10.4f, a - 9f), at(10.4f, a + 9f), at(7.6f, a + 15f))
            for ((n, pt) in pts.withIndex()) {
                if (i == 0 && n == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second)
            }
        }
        p.close()
        p.addCircle(12f, 12f, 3.2f, Path.Direction.CW)
        c.drawPath(p, solid)
    }

    /**
     * A dashed selection box, an arrow bending down and across from it, and what it lands on:
     * one box for a single replacement, a row of three small ones for replace all.
     */
    private fun replace(c: Canvas, all: Boolean) {
        c.drawRect(RectF(2.5f, 3.5f, 12f, 9.5f), dashed)
        val arrow = Path().apply {
            moveTo(7f, 9.5f)
            lineTo(7f, 16f)
            lineTo(11f, 16f)
            moveTo(9f, 14f)
            lineTo(11f, 16f)
            lineTo(9f, 18f)
        }
        c.drawPath(arrow, line)
        if (all) {
            for (i in 0 until 3) {
                val x = 12.4f + i * 3.9f
                c.drawRect(RectF(x, 14.4f, x + 3.2f, 17.6f), fine)
            }
        } else {
            c.drawRect(RectF(13.5f, 12f, 21.5f, 20f), line)
        }
    }

    /** An arrow running into a bar: the Tab key. */
    private fun tab(c: Canvas) {
        val p = Path().apply {
            moveTo(4f, 12f)
            lineTo(16f, 12f)
            moveTo(11f, 7f)
            lineTo(16f, 12f)
            lineTo(11f, 17f)
            moveTo(20f, 6f)
            lineTo(20f, 18f)
        }
        c.drawPath(p, line)
    }

    /** A magnifying glass. */
    private fun find(c: Canvas) {
        c.drawCircle(10.5f, 10.5f, 6f, line)
        c.drawLine(15f, 15f, 20.5f, 20.5f, line)
    }

    /** A line that turns back on itself with its head on the left (undo); mirrored it is redo. */
    private fun undo(c: Canvas, mirror: Boolean) {
        c.save()
        if (mirror) {
            c.translate(24f, 0f)
            c.scale(-1f, 1f)
        }
        val p = Path().apply {
            moveTo(5f, 9f)
            lineTo(13f, 9f)
            arcTo(RectF(7f, 9f, 19f, 21f), -90f, 180f)
            lineTo(8f, 21f)
            moveTo(9f, 5f)
            lineTo(5f, 9f)
            lineTo(9f, 13f)
        }
        c.drawPath(p, line)
        c.restore()
    }

    /** A floppy disk. */
    private fun save(c: Canvas) {
        val p = Path().apply {
            moveTo(4f, 4f)
            lineTo(17f, 4f)
            lineTo(20f, 7f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(8f, 4f)
            lineTo(8f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 4f)
            moveTo(7f, 20f)
            lineTo(7f, 13f)
            lineTo(17f, 13f)
            lineTo(17f, 20f)
        }
        c.drawPath(p, line)
    }

    /** An arrow on the right, up or down, and text lines on the left. */
    private fun lineArrow(c: Canvas, up: Boolean) {
        val end = if (up) 6f else 18f
        val start = if (up) 18f else 6f
        val back = end + if (up) 4f else -4f
        val p = Path().apply {
            moveTo(19f, start)
            lineTo(19f, end)
            moveTo(15.5f, back)
            lineTo(19f, end)
            lineTo(22.5f, back)
        }
        c.drawPath(p, line)
    }

    /** Move line up or down: three lines of text and an arrow. */
    private fun moveLines(c: Canvas, up: Boolean) {
        for (y in listOf(6f, 12f, 18f)) c.drawLine(3.5f, y, 13f, y, line)
        lineArrow(c, up)
    }

    /** Copy line down: a line, a dashed copy of it below, and a down arrow. */
    private fun copyDown(c: Canvas) {
        c.drawLine(3.5f, 7f, 13f, 7f, line)
        c.drawLine(3.5f, 17f, 13f, 17f, dashed)
        lineArrow(c, up = false)
    }

    /** A bin: the Delete line key. */
    private fun trash(c: Canvas) {
        val p = Path().apply {
            moveTo(5f, 7f)
            lineTo(19f, 7f)
            moveTo(9f, 7f)
            lineTo(9f, 4.5f)
            lineTo(15f, 4.5f)
            lineTo(15f, 7f)
            moveTo(7f, 7f)
            lineTo(8f, 20f)
            lineTo(16f, 20f)
            lineTo(17f, 7f)
        }
        c.drawPath(p, line)
    }
}

/** A key that can show a drawn [Icon] centred in it instead of (or as well as) text. Used by the key rows. */
class KeyView(context: Context) : TextView(context) {
    private var icon: VectorIcon? = null

    fun setIcon(kind: Icon, color: Int, sizeDp: Int = 22) {
        val px = (sizeDp * resources.displayMetrics.density).toInt()
        icon = VectorIcon(kind, px).also { it.setColorFilter(color, PorterDuff.Mode.SRC_ATOP) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = icon ?: return
        val l = (width - d.intrinsicWidth) / 2
        val t = (height - d.intrinsicHeight) / 2
        d.setBounds(l, t, l + d.intrinsicWidth, t + d.intrinsicHeight)
        d.draw(canvas)
    }
}

object Icons {
    /** A tappable icon: [icon] drawn at 22 dp and tinted with [color], centred in whatever room the layout gives it. */
    fun button(context: Context, icon: Icon, description: String, color: Int, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            val density = context.resources.displayMetrics.density
            setImageDrawable(VectorIcon(icon, (22 * density).toInt()))
            scaleType = ImageView.ScaleType.CENTER
            setColorFilter(color)
            contentDescription = description
            setOnClickListener { onClick() }
        }
}
