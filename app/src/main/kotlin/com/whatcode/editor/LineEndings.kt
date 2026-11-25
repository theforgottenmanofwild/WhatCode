package com.whatcode.editor

/**
 * Which line ending a text uses, for the status bar. The editor keeps the text exactly as it was read, with its
 * `\r` characters in place, so this only looks at the text and never changes it. Plain logic, no Android.
 */
object LineEndings {

    enum class Kind(val label: String) {
        /** A line break is `\n`, as on Linux, macOS and Android. */
        LF("LF"),

        /** A line break is `\r` then `\n`, as on Windows. */
        CRLF("CRLF"),

        /** A line break is a lone `\r`, as on very old Mac files. */
        CR("CR"),

        /** More than one of the above in the same text. */
        MIXED("Mixed")
    }

    /** Texts up to this many characters are scanned at once; a bigger one should be scanned off the main thread. */
    const val SYNC_LIMIT = 100_000

    // How often a long scan looks at [cancelled]: every 65,536 characters. Used as a mask on a step counter.
    private const val CHECK_MASK = 0xFFFF

    /**
     * The line ending of [text]. A text with no line break at all gives [Kind.LF], because that is what a line
     * typed into it will end with. Returns null if [cancelled] turned true before the scan was done.
     *
     * A `\r\n` counts as one CRLF break, not as a CR and an LF. The scan stops at the first sign of a second kind.
     */
    fun detect(text: CharSequence, cancelled: () -> Boolean = { false }): Kind? {
        var lf = false
        var crlf = false
        var cr = false
        val n = text.length
        var i = 0
        var steps = 0
        while (i < n) {
            val c = text[i]
            if (c == '\n') {
                lf = true
            } else if (c == '\r') {
                if (i + 1 < n && text[i + 1] == '\n') {
                    crlf = true
                    i++
                } else {
                    cr = true
                }
            }
            if ((lf && crlf) || (lf && cr) || (crlf && cr)) return Kind.MIXED
            steps++
            if ((steps and CHECK_MASK) == 0 && cancelled()) return null
            i++
        }
        return when {
            crlf -> Kind.CRLF
            cr -> Kind.CR
            else -> Kind.LF
        }
    }
}
