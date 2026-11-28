package com.whatcode.bench

import java.io.OutputStream
import java.util.Random

/**
 * Makes text of a chosen kind and size for measuring the editor, in the same way every time. Nothing is stored in the
 * repository: the text is written to a stream (a temporary file, or memory) when it is needed. Debug builds only.
 */
object BenchData {

    enum class Kind(val label: String, val extension: String) {
        LOG("log", "log"),
        SOURCE("source code", "kt"),
        JSON_LINE("one-line JSON", "json"),
        CSV("CSV", "csv"),
    }

    /** The sizes the measurements use, in bytes. */
    val SIZES: List<Long> = listOf(1L, 5L, 20L, 100L).map { it * 1024 * 1024 }

    /** Writes about [bytes] bytes of [kind] to [out], and returns how many it wrote (never fewer than asked for). */
    fun write(kind: Kind, bytes: Long, out: OutputStream): Long {
        val random = Random(kind.ordinal * 7919L + 17)
        var written = 0L
        val buffer = StringBuilder(1 shl 16)
        fun flush() {
            val b = buffer.toString().toByteArray(Charsets.UTF_8)
            out.write(b)
            written += b.size
            buffer.setLength(0)
        }
        when (kind) {
            Kind.LOG -> {
                var n = 0
                while (written < bytes) {
                    buffer.append(logLine(random, n++))
                    if (buffer.length > 60_000) flush()
                }
            }
            Kind.SOURCE -> {
                var n = 0
                while (written < bytes) {
                    buffer.append(sourceBlock(random, n++))
                    if (buffer.length > 60_000) flush()
                }
            }
            Kind.JSON_LINE -> {
                buffer.append('[')
                var n = 0
                while (written + buffer.length < bytes) {
                    if (n > 0) buffer.append(',')
                    buffer.append(jsonItem(random, n++))
                    if (buffer.length > 60_000) flush()
                }
                buffer.append(']')
            }
            Kind.CSV -> {
                buffer.append("id,name,city,amount,date,note\n")
                var n = 0
                while (written + buffer.length < bytes) {
                    buffer.append(csvRow(random, n++))
                    if (buffer.length > 60_000) flush()
                }
            }
        }
        flush()
        out.flush()
        return written
    }

    /** The text as bytes in memory, for sizes that fit. */
    fun bytes(kind: Kind, bytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(bytes.toInt() + (1 shl 16))
        write(kind, bytes, out)
        return out.toByteArray()
    }

    private val LEVELS = arrayOf("INFO", "INFO", "INFO", "DEBUG", "WARN", "ERROR")
    private val WORDS = arrayOf("request", "cache", "session", "worker", "timeout", "retry", "queue", "update", "index", "stream", "d\u00E9j\u00E0", "caf\u00E9")
    private val CITIES = arrayOf("Oslo", "Lima", "Kyiv", "Pune", "Accra", "Hanoi", "Quito", "Z\u00FCrich")

    private fun word(r: Random) = WORDS[r.nextInt(WORDS.size)]

    private fun logLine(r: Random, n: Int): String {
        val t = 1_700_000_000L + n
        val level = LEVELS[r.nextInt(LEVELS.size)]
        val trace = if (level == "ERROR" && r.nextInt(4) == 0) "\n\tat com.example.${word(r)}.run(Worker.java:${r.nextInt(900)})" else ""
        return "$t [$level] ${word(r)}-${r.nextInt(64)} ${word(r)} ${word(r)} took ${r.nextInt(5000)}ms id=${Integer.toHexString(r.nextInt())}$trace\n"
    }

    private fun sourceBlock(r: Random, n: Int): String {
        val name = word(r).replaceFirstChar { it.uppercase() } + n
        return buildString {
            append("/**\n * Handles the ${word(r)} for $name.\n * It keeps a ${word(r)} and a ${word(r)}.\n */\n")
            append("class $name(private val limit: Int = ${r.nextInt(1000)}) {\n")
            append("    // ${word(r)} ${word(r)}\n")
            append("    fun ${word(r)}(input: String, count: Int): String {\n")
            append("        val text = \"${word(r)} \\\"${word(r)}\\\" ${r.nextInt(99)}\"\n")
            append("        if (count > limit) {\n")
            append("            /* ${word(r)}\n               ${word(r)} */\n")
            append("            return input.take(count) + text\n")
            append("        }\n")
            append("        for (i in 0 until count) println(\"\$i: \${input.length}\")\n")
            append("        return text\n")
            append("    }\n")
            append("}\n\n")
        }
    }

    private fun jsonItem(r: Random, n: Int): String =
        "{\"id\":$n,\"name\":\"${word(r)} ${word(r)}\",\"tags\":[\"${word(r)}\",\"${word(r)}\"],\"score\":${r.nextInt(1000)}.${r.nextInt(100)},\"ok\":${r.nextBoolean()},\"note\":null}"

    private fun csvRow(r: Random, n: Int): String =
        "$n,${word(r)} ${word(r)},${CITIES[r.nextInt(CITIES.size)]},${r.nextInt(100000)}.${r.nextInt(100)},2026-${1 + r.nextInt(12)}-${1 + r.nextInt(28)},\"${word(r)}, ${word(r)}\"\n"
}
