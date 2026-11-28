package com.whatcode.editor

/**
 * Pure line-by-line diff utility comparing two snapshots of a file (e.g. unsaved buffer vs saved file on disk).
 * Computes exact LCS line diff within [MAX_DP_CELLS] and formats unified-style output cleanly. No Android here.
 */
object LineDiff {

    enum class Kind { SAME, REMOVED, ADDED }

    data class Line(
        val kind: Kind,
        val oldLine: Int,
        val newLine: Int,
        val text: String,
    )

    data class Result(
        val lines: List<Line>,
        val addedCount: Int,
        val removedCount: Int,
    ) {
        val identical: Boolean get() = addedCount == 0 && removedCount == 0
    }

    private const val MAX_DP_CELLS = 4_000_000L

    fun diff(oldText: String, newText: String): Result {
        if (oldText == newText) return Result(emptyList(), 0, 0)
        val a = splitLines(oldText)
        val b = splitLines(newText)
        // Trim shared prefix & suffix to keep DP matrix tiny on typical edits.
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++

        val midA = a.subList(prefix, a.size - suffix)
        val midB = b.subList(prefix, b.size - suffix)

        val out = ArrayList<Line>()
        for (i in 0 until prefix) {
            out.add(Line(Kind.SAME, i + 1, i + 1, a[i]))
        }

        val cells = midA.size.toLong() * midB.size.toLong()
        if (cells <= MAX_DP_CELLS && midA.isNotEmpty() && midB.isNotEmpty()) {
            val n = midA.size
            val m = midB.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) {
                val ai = midA[i]
                val row = dp[i]
                val next = dp[i + 1]
                for (j in m - 1 downTo 0) {
                    row[j] = if (ai == midB[j]) next[j + 1] + 1 else maxOf(next[j], row[j + 1])
                }
            }
            var i = 0
            var j = 0
            while (i < n || j < m) {
                when {
                    i < n && j < m && midA[i] == midB[j] -> {
                        out.add(Line(Kind.SAME, prefix + i + 1, prefix + j + 1, midA[i]))
                        i++; j++
                    }
                    j < m && (i == n || dp[i][j + 1] >= dp[i + 1][j]) -> {
                        out.add(Line(Kind.ADDED, -1, prefix + j + 1, midB[j]))
                        j++
                    }
                    else -> {
                        out.add(Line(Kind.REMOVED, prefix + i + 1, -1, midA[i]))
                        i++
                    }
                }
            }
        } else {
            for (i in midA.indices) out.add(Line(Kind.REMOVED, prefix + i + 1, -1, midA[i]))
            for (j in midB.indices) out.add(Line(Kind.ADDED, -1, prefix + j + 1, midB[j]))
        }

        for (s in 0 until suffix) {
            val oa = a.size - suffix + s
            val ob = b.size - suffix + s
            out.add(Line(Kind.SAME, oa + 1, ob + 1, a[oa]))
        }

        val added = out.count { it.kind == Kind.ADDED }
        val removed = out.count { it.kind == Kind.REMOVED }
        return Result(out, added, removed)
    }

    /** Formats [result] cleanly as readable unified diff text with [context] lines around each change. */
    fun formatUnified(result: Result, oldName: String = "saved", newName: String = "editor", context: Int = 3): String {
        if (result.identical) return "No differences between $oldName and $newName."
        val sb = StringBuilder()
        sb.append("--- ").append(oldName).append('\n')
        sb.append("+++ ").append(newName).append('\n')
        sb.append("@@ +").append(result.addedCount).append(" / -").append(result.removedCount).append(" lines @@\n")
        val changedIndices = ArrayList<Int>()
        result.lines.forEachIndexed { idx, line -> if (line.kind != Kind.SAME) changedIndices.add(idx) }
        val keep = BooleanArray(result.lines.size)
        for (idx in changedIndices) {
            for (k in maxOf(0, idx - context)..minOf(result.lines.lastIndex, idx + context)) {
                keep[k] = true
            }
        }
        var skipping = false
        for (i in result.lines.indices) {
            if (!keep[i]) {
                if (!skipping && i > 0) sb.append("...\n")
                skipping = true
                continue
            }
            skipping = false
            val line = result.lines[i]
            when (line.kind) {
                Kind.SAME -> sb.append("  ").append(line.text).append('\n')
                Kind.REMOVED -> sb.append("- ").append(line.text).append('\n')
                Kind.ADDED -> sb.append("+ ").append(line.text).append('\n')
            }
        }
        return sb.toString()
    }

    private fun splitLines(s: String): List<String> =
        if (s.isEmpty()) emptyList() else s.replace("\r\n", "\n").replace('\r', '\n').split('\n')
}
