package com.whatcode.bigfile

/**
 * What a big text turns off in the editor, so that the rest of it stays quick. Each one has a [bit] in the saved
 * choices of Settings > Large files, so the order and the bits of the existing ones must never change.
 */
enum class Saving(val bit: Int, val label: String) {
    WRAP(1, "Turn off word wrap"),
    MATCHING(2, "Match brackets and tags only on the screen"),
    WHITESPACE(4, "Turn off whitespace marks"),
    UNDO(8, "Keep fewer undo steps"),
}

/** How big the text is: [NONE] is an ordinary file, [LARGE] and [VERY_LARGE] switch off more and more. */
enum class Tier { NONE, LARGE, VERY_LARGE }

/** Whether a file at or above Tier 3 ([LargeFileConfig.DEFAULT_VIEWER]) opens in the read-only large-file viewer. */
enum class ViewerOpen(val label: String) {
    ASK("Ask (recommend the viewer, allow the editor)"),
    ALWAYS("Always open in the large-file viewer"),
    NEVER("Always open in the editor"),
}

/** What the editor does for the text it holds right now: how big it is, what is off, and how many undo steps it keeps. */
data class Plan(val tier: Tier, val off: Int, val undoSteps: Int) {
    val active: Boolean get() = tier != Tier.NONE

    fun isOff(s: Saving): Boolean = off and s.bit != 0

    companion object {
        /** The plan for an ordinary file: nothing is off. */
        val NONE = Plan(Tier.NONE, 0, LargeFileConfig.NORMAL_UNDO_STEPS)
    }
}

/**
 * The choices of Settings > Large files that decide the [Plan] for a text of a given length. The sizes are numbers of
 * characters, which is what the editor holds (a file of plain English is about one byte for each).
 *
 * [tier1Off] and [tier2Off] are sets of [Saving] bits: what a large text turns off, and what a very large one turns off
 * in addition. Plain logic, no Android.
 */
data class LargeFileConfig(
    val enabled: Boolean = DEFAULT_ENABLED,
    val largeAbove: Int = DEFAULT_LARGE,
    val veryLargeAbove: Int = DEFAULT_VERY_LARGE,
    val tier1Off: Int = DEFAULT_TIER1_OFF,
    val tier2Off: Int = DEFAULT_TIER2_OFF,
    val undoSteps: Int = DEFAULT_UNDO_STEPS,
) {
    /** A text with [chars] characters is large when it has more than [largeAbove], and a text exactly that long is not. */
    fun tierOf(chars: Int): Tier {
        if (!enabled) return Tier.NONE
        val very = maxOf(veryLargeAbove, largeAbove) // a very large size below the large one is read as the same size
        return when {
            chars > very -> Tier.VERY_LARGE
            chars > largeAbove -> Tier.LARGE
            else -> Tier.NONE
        }
    }

    fun planFor(chars: Int): Plan {
        val tier = tierOf(chars)
        val off = when (tier) {
            Tier.NONE -> return Plan.NONE
            Tier.LARGE -> tier1Off
            Tier.VERY_LARGE -> tier1Off or tier2Off
        } and ALL_BITS
        val undo = if (off and Saving.UNDO.bit != 0) undoSteps.coerceIn(MIN_UNDO_STEPS, NORMAL_UNDO_STEPS) else NORMAL_UNDO_STEPS
        return Plan(tier, off, undo)
    }

    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_LARGE = 300_000
        const val DEFAULT_VERY_LARGE = 2_000_000
        const val DEFAULT_VIEWER = 8_000_000
        const val MIN_SIZE = 20_000
        const val MAX_SIZE = 100_000_000

        /** True when a file of [byteSize] crosses Tier 3 ([viewerAbove]) while large-file mode is [enabled]. */
        fun isViewerTier(enabled: Boolean, byteSize: Long, viewerAbove: Int): Boolean =
            enabled && byteSize > viewerAbove.toLong()

        val DEFAULT_TIER1_OFF = Saving.WRAP.bit or Saving.MATCHING.bit
        val DEFAULT_TIER2_OFF = Saving.WHITESPACE.bit or Saving.UNDO.bit

        /** How many undo steps an ordinary file keeps (the same as the editor's own limit). */
        const val NORMAL_UNDO_STEPS = 1000
        const val DEFAULT_UNDO_STEPS = 200
        const val MIN_UNDO_STEPS = 10

        /** Every bit a saved choice may hold. */
        val ALL_BITS: Int = Saving.entries.fold(0) { acc, s -> acc or s.bit }
    }
}

/** The words that tell a person what the large file mode did to the text in front of them. */
object LargeFileText {

    /** The sentence for the status bar. */
    const val STATUS = "Large file mode"

    /** An explanation of [plan] for a text of [chars] characters, made to be read in a dialog. */
    fun explain(plan: Plan, chars: Int, config: LargeFileConfig): String {
        if (!plan.active) return "This file is not large, so nothing is turned off."
        val sb = StringBuilder()
        val size = "%,d".format(chars)
        if (plan.tier == Tier.VERY_LARGE) {
            sb.append("This file has $size characters, more than the %,d where very large files start.".format(config.veryLargeAbove))
        } else {
            sb.append("This file has $size characters, more than the %,d where large files start.".format(config.largeAbove))
        }
        sb.append(" So that it stays quick to edit:\n")
        sb.append("\n\u2022 Colors cover only the lines you see and some around them.")
        if (plan.isOff(Saving.WRAP)) sb.append("\n\u2022 Word wrap is off, because laying out every wrapped line takes too long.")
        if (plan.isOff(Saving.MATCHING)) sb.append("\n\u2022 A bracket or tag is matched only when both ends are on the screen.")
        if (plan.isOff(Saving.WHITESPACE)) sb.append("\n\u2022 Whitespace marks are not drawn.")
        if (plan.isOff(Saving.UNDO)) sb.append("\n\u2022 Undo keeps the last ${plan.undoSteps} steps.")
        sb.append("\n\nChoose what is turned off in Settings > Large files.")
        return sb.toString()
    }
}

/**
 * A stretch of another text, [from] to [to], that reads like a text of its own whose first character is number 0.
 * Nothing is copied, so the matching of brackets can be held to the lines on the screen of a huge text.
 */
class TextWindow(private val base: CharSequence, val from: Int, val to: Int) : CharSequence {

    init {
        require(from in 0..to && to <= base.length) { "window $from..$to is outside a text of ${base.length}" }
    }

    override val length: Int get() = to - from

    override fun get(index: Int): Char {
        if (index < 0 || index >= length) throw IndexOutOfBoundsException("$index of $length")
        return base[from + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex) throw IndexOutOfBoundsException("$startIndex..$endIndex of $length")
        return TextWindow(base, from + startIndex, from + endIndex)
    }

    override fun toString(): String = base.subSequence(from, to).toString()
}
