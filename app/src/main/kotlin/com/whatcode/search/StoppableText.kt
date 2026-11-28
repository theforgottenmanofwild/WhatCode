package com.whatcode.search

import java.util.concurrent.CancellationException

/**
 * A text that asks [stop] now and then while it is read, so a pattern working on it can be cut short with a
 * [CancellationException], and tells [tick] how far (as a position) the reading has got. A matcher reads a text from
 * the front, so the position is also how far through the text it is. No Android here.
 */
class StoppableText(
    private val s: CharSequence,
    private val stop: () -> Boolean,
    private val tick: (Int) -> Unit = {},
) : CharSequence {
    private var reads = 0
    override val length: Int get() = s.length
    override fun get(index: Int): Char {
        if ((++reads and MASK) == 0) {
            if (stop()) throw CancellationException()
            tick(index)
        }
        return s[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = s.subSequence(startIndex, endIndex)
    override fun toString(): String = s.toString()

    private companion object {
        const val MASK = 0x3FF
    }
}
