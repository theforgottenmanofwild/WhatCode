package com.whatcode.editor

import android.graphics.Canvas
import android.widget.TextView

/** Scrolled text is cut off at the top and bottom padding, and what is drawn over it must be cut off with it. */
internal fun TextView.clipToText(canvas: Canvas) {
    canvas.clipRect(
        scrollX, if (scrollY == 0) 0 else scrollY + extendedPaddingTop,
        scrollX + width, scrollY + height - extendedPaddingBottom,
    )
}
