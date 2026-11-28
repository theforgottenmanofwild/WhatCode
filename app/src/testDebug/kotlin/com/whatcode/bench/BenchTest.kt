package com.whatcode.bench

import com.whatcode.editor.DirtyRange
import com.whatcode.editor.Highlighter
import com.whatcode.editor.LineIndex
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Measures the parts of the editor that are plain logic, on generated files of 1, 5, 20 and 100 MB: reading the file,
 * finding its lines, and colouring it the way the editor does today. It only runs when -Dbench=1 is passed to
 * Gradle, so the normal test run stays quick:
 *
 *     ./gradlew --offline -Dbench=1 testDebugUnitTest --tests '*BenchTest*' -i
 *
 * The report is printed and written to build/bench-report.txt (never into the repository). What needs the real
 * editor view (open time, keystroke latency, scrolling) is measured by the debug build's "WhatCode bench" screen.
 */
class BenchTest {

    private fun used(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val t = System.nanoTime()
        val v = block()
        return v to (System.nanoTime() - t) / 1_000_000
    }

    @Test
    fun measure() {
        assumeTrue("pass -Dbench=1 to run the measurements", System.getProperty("bench").orEmpty().isNotEmpty())
        val report = StringWriter()
        val out = PrintWriter(report)
        out.println("Plain-logic measurements (JVM, ${Runtime.getRuntime().availableProcessors()} cores, max heap ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB)")
        out.println("%-14s %6s %9s %9s %9s %12s %14s".format("kind", "MB", "read ms", "lines ms", "lines", "longest line", "colour ms"))
        val only = System.getProperty("benchKind")
        for (kind in BenchData.Kind.values()) {
            if (only != null && only != kind.name) continue
            for (size in BenchData.SIZES) {
                val file = File.createTempFile("bench", "." + kind.extension, File("build/bench-tmp").also { it.mkdirs() })
                try {
                    file.outputStream().buffered().use { BenchData.write(kind, size, it) }
                    System.gc()
                    val before = used()
                    val (text, readMs) = try {
                        timed { file.readText(Charsets.UTF_8) }
                    } catch (e: OutOfMemoryError) {
                        out.println("%-14s %6d  out of memory while reading".format(kind.label, size shr 20))
                        continue
                    }
                    val (stats, lineMs) = timed {
                        var lines = 1
                        var longest = 0
                        var start = 0
                        for (i in text.indices) {
                            if (text[i] == '\n') {
                                lines++
                                if (i - start > longest) longest = i - start
                                start = i + 1
                            }
                        }
                        if (text.length - start > longest) longest = text.length - start
                        lines to longest
                    }
                    val lang = Highlighter.languageFor("x." + kind.extension)
                    val colour = if (text.length > Highlighter.MAX_CHARS) {
                        "off (>${Highlighter.MAX_CHARS / 1000}k)"
                    } else {
                        val runs = (1..3).map { timed { Highlighter.tokenize(text, lang) }.second }
                        "${runs.sorted()[1]}"
                    }
                    out.println("%-14s %6d %9d %9d %9d %12d %14s".format(kind.label, size shr 20, readMs, lineMs, stats.first, stats.second, colour))
                    out.println("               memory held by the text: ~${(used() - before) / (1024 * 1024)} MB")
                } finally {
                    file.delete()
                }
            }
        }
        // What every keystroke costs today in a file the editor still colours: the whole text is read again.
        out.println()
        out.println("One re-colouring of the whole text (what each pause in typing costs today), median of 5:")
        for (kind in BenchData.Kind.values()) {
            val text = String(BenchData.bytes(kind, 900_000), Charsets.UTF_8)
            val lang = Highlighter.languageFor("x." + kind.extension)
            Highlighter.tokenize(text, lang)
            val runs = (1..5).map { timed { Highlighter.tokenize(text, lang) }.second }.sorted()
            out.println("%-14s %7d chars  %5d ms".format(kind.label, text.length, runs[2]))
        }
        // The same files with the windowed colouring: the lines on the screen only, from the checkpoints.
        out.println()
        out.println("Windowed colouring (files up to ${Highlighter.MAX_CHARS / 1_000_000} M characters; median of 15 edits in the middle of the text, 3 for the one-line JSON):")
        out.println("%-14s %6s %12s %12s %12s".format("kind", "MB", "build ms", "edit+colour", "scroll ms"))
        for (kind in BenchData.Kind.values()) {
            for (size in BenchData.SIZES) {
                if (size > Highlighter.MAX_CHARS) {
                    out.println("%-14s %6d %12s".format(kind.label, size shr 20, "over the limit, large-file viewer"))
                    continue
                }
                val sb = StringBuilder(String(BenchData.bytes(kind, size), Charsets.UTF_8))
                val lang = Highlighter.languageFor("x." + kind.extension)
                val index = LineIndex()
                val states = com.whatcode.editor.highlight.ScanStates(lang)
                val (_, buildMs) = timed {
                    val text = sb.toString()
                    index.build(text)
                    states.build(text, index)
                }
                val edits = ArrayList<Long>()
                val scrolls = ArrayList<Long>()
                // One huge line has to be read whole for every edit, so fewer runs are taken of it.
                val runs = if (kind == BenchData.Kind.JSON_LINE) 3 else 15
                repeat(runs) { i ->
                    val at = sb.length / 2 + i * 7
                    val (_, ms) = timed {
                        sb.insert(at, 'x')
                        val d = DirtyRange().also { it.add(at, 0, 1) }
                        val text = sb.toString()
                        val splice = index.replace(d.lo, d.oldLength, text.subSequence(d.lo, d.hi))
                        states.replace(splice, text, index)
                        val line = index.lineOf(at)
                        Highlighter.tokenizeWindow(text, lang, index, states, maxOf(0, line - 60), minOf(index.lineCount - 1, line + 120))
                    }
                    edits.add(ms)
                    val text = sb.toString()
                    val (_, sm) = timed {
                        val line = index.lineOf(at / 3 + i * 1000)
                        Highlighter.tokenizeWindow(text, lang, index, states, maxOf(0, line - 60), minOf(index.lineCount - 1, line + 120))
                    }
                    scrolls.add(sm)
                }
                out.println("%-14s %6d %12d %12d %12d".format(kind.label, size shr 20, buildMs, edits.sorted()[edits.size / 2], scrolls.sorted()[scrolls.size / 2]))
            }
        }
        out.println("(edit+colour includes copying the text to one string, which the editor does once per pause)")
        out.flush()
        val text = report.toString()
        println(text)
        File("build").also { it.mkdirs() }
        File("build/bench-report.txt").writeText(text)
    }
}
