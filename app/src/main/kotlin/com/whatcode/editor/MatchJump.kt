package com.whatcode.editor

/**
 * Where the "jump to matching" key goes: from a tag to its partner, or from a bracket to its partner. Like
 * [BracketMatch] and [TagMatch] it only deals in text and numbers, so it can be tested without a device.
 */
object MatchJump {

    private val NOT_SKIPPED: (Int) -> Boolean = { false }

    /**
     * The place to move the cursor to from [cursor], or null if there is nothing to jump to.
     *
     * In HTML, XML and SVG a cursor in a tag goes to the start of the partner tag (the `<` of `</div>`, or of the
     * opening tag when it is in the closing one). Otherwise a cursor next to a bracket goes next to its partner, on the
     * same side: just after an opening bracket goes to just after the closing one, just before one to just before the
     * other. Pressing it again goes back. Angle brackets are only paired in the ways [BracketMatch] pairs them, and
     * the two ends of a tag are not offered as a pair of brackets in markup.
     */
    fun target(text: CharSequence, cursor: Int, lang: Lang, skipped: (Int) -> Boolean = NOT_SKIPPED): Int? {
        if (cursor < 0 || cursor > text.length) return null
        TagMatch.find(text, cursor, lang, skipped)?.let { tags ->
            return if (cursor >= tags.closeStart) tags.openStart else tags.closeStart
        }
        val pair = BracketMatch.find(text, cursor, lang, skipped) ?: return null
        if (lang.family == Family.MARKUP && text[pair.open] == '<') return null
        val after = cursor > 0 && (cursor - 1 == pair.open || cursor - 1 == pair.close)
        val touching = if (after) cursor - 1 else cursor
        val opening = touching == pair.open
        return if (after) (if (opening) pair.close + 1 else pair.open + 1) else (if (opening) pair.close else pair.open)
    }
}
