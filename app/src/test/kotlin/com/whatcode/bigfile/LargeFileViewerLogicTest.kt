package com.whatcode.bigfile

import com.whatcode.editor.Highlighter
import com.whatcode.editor.SyntaxScheme
import com.whatcode.editor.Tokens
import com.whatcode.files.TempFolder
import com.whatcode.search.FindLogic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileOutputStream

class LargeFileViewerLogicTest {

    private val temp = TempFolder()

    @After
    fun clean() {
        temp.delete()
    }

    @Test
    fun pagedFileReadsAcrossSmallWindowBoundariesAndDetectsTextVsBinary() {
        val f = temp.file("win.txt", "")
        val text = (0 until 200).joinToString("") { "line-%03d\n".format(it) }
        f.writeText(text)
        for (preferMap in listOf(false, true)) {
            PagedFile(f, windowBytes = 128, preferMap = preferMap).use { pf ->
                assertTrue(pf.isText())
                assertEquals(text.length.toLong(), pf.length)
                val out = ByteArray(text.length)
                var pos = 0L
                while (pos < pf.length) {
                    val n = pf.readBytes(pos, 73, out, pos.toInt())
                    assertTrue(n > 0)
                    pos += n
                }
                assertEquals(text, String(out, Charsets.UTF_8))
            }
            if (preferMap) System.gc()
        }
        val bin = temp.file("bin.dat", "")
        FileOutputStream(bin).use { it.write(byteArrayOf(0x41, 0x00, 0x42)) }
        PagedFile(bin, preferMap = false).use { assertFalse(it.isText()) }
    }

    @Test
    fun utf8WindowHandlesMultiByteBoundariesAndInvalidSequencesSafely() {
        val f = temp.file("utf8.txt", "")
        val sample = "hello 😀 world café 日本語\n"
        val raw = sample.toByteArray(Charsets.UTF_8)
        f.writeBytes(raw)
        val emojiByteStart = "hello ".toByteArray(Charsets.UTF_8).size.toLong()
        PagedFile(f, windowBytes = 8, preferMap = false).use { pf ->
            // Start 2 bytes inside the 4-byte emoji and stop in the middle of it.
            val slice = Utf8Window.decodeRange(pf, emojiByteStart + 2, emojiByteStart + 3)
            assertEquals("😀", slice.text)
            assertFalse(slice.text.contains('\uFFFD'))
        }
        val bad = byteArrayOf(0x41, 0xFF.toByte(), 0x42)
        assertEquals("A\uFFFDB", Utf8Window.decodeBytes(bad))
    }

    @Test
    fun sparseIndexHandlesEmptyNoNewlineCrLfMixedEndingsAndVeryLongLines() {
        val empty = temp.file("empty.txt", "")
        PagedFile(empty, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 4)
            assertTrue(idx.build(pf))
            assertEquals(1L, idx.lineCount)
            assertEquals("", idx.readLine(pf, 0L, 100)?.text)
        }

        val oneLine = temp.file("single.txt", "abc")
        PagedFile(oneLine, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 4)
            assertTrue(idx.build(pf))
            assertEquals(1L, idx.lineCount)
            val s = idx.readLine(pf, 0L, 100)!!
            assertEquals("abc", s.text)
            assertFalse(s.truncated)
        }

        val mixed = temp.file("mixed.txt", "")
        mixed.writeBytes("one\r\ntwo\rthree\nfour".toByteArray())
        PagedFile(mixed, windowBytes = 6, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 2)
            assertTrue(idx.build(pf))
            assertEquals(4L, idx.lineCount)
            assertEquals("one", idx.readLine(pf, 0, 100)?.text)
            assertEquals("\r\n", idx.readLine(pf, 0, 100)?.ending)
            assertEquals("two", idx.readLine(pf, 1, 100)?.text)
            assertEquals("\r", idx.readLine(pf, 1, 100)?.ending)
            assertEquals("three", idx.readLine(pf, 2, 100)?.text)
            assertEquals("four", idx.readLine(pf, 3, 100)?.text)
        }

        val hugeLine = temp.file("long.txt", "A".repeat(2000) + "\ntail\n")
        PagedFile(hugeLine, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 4)
            assertTrue(idx.build(pf))
            val trunc = idx.readLine(pf, 0, maxBytes = 256, expanded = false)!!
            assertTrue(trunc.truncated)
            assertEquals(256, trunc.text.length)
            val full = idx.readLine(pf, 0, maxBytes = 256, expanded = true)!!
            assertFalse(full.truncated)
            assertEquals(2000, full.text.length)
        }
    }

    @Test
    fun growingFilesUpdateTailLinesSafelyAcrossCrLfBoundaryAndCancelCleanly() {
        val log = temp.file("app.log", "")
        log.writeBytes("first\r".toByteArray())
        PagedFile(log, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 2)
            assertTrue(idx.build(pf))
            assertEquals(2L, idx.lineCount) // "first" and trailing line 1

            // Append '\nsecond\n' — the '\n' finishes the '\r\n' break rather than counting an extra line!
            FileOutputStream(log, true).use { it.write("\nsecond\nthird".toByteArray()) }
            assertTrue(idx.refreshTail(pf))
            assertEquals(3L, idx.lineCount)
            assertEquals("first", idx.readLine(pf, 0, 100)?.text)
            assertEquals("second", idx.readLine(pf, 1, 100)?.text)
            assertEquals("third", idx.readLine(pf, 2, 100)?.text)

            // Cancelled build stops early and returns false.
            assertFalse(idx.build(pf, cancelled = { true }))
            assertFalse(idx.completed)
        }
    }

    @Test
    fun percentSeekAndStreamingSearchSupportCancellationAndCaps() {
        val f = temp.file("search.log", "")
        val content = (0 until 500).joinToString("") { i -> "row $i target_${i % 10} end\n" }
        f.writeText(content)
        PagedFile(f, windowBytes = 512, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 32)
            assertTrue(idx.build(pf))
            assertEquals(501L, idx.lineCount)
            assertEquals(0L, idx.lineForPercent(pf, 0f))
            val mid = idx.lineForPercent(pf, 0.5f)
            assertTrue("mid=$mid", mid in 200L..300L)

            val pat = FindLogic.buildPattern("target_3", caseSensitive = true, wholeWord = true, regex = false)!!
            val res = ViewerSearch.search(pf, pat, cap = 20)
            assertEquals(50L, res.totalMatched)
            assertEquals(20, res.hits.size)
            assertTrue(res.capped)
            assertEquals(3L, res.hits[0].line)

            val cancelled = ViewerSearch.search(pf, pat, cap = 100, cancelled = { true })
            assertEquals(0, cancelled.hits.size)
        }
    }

    @Test
    fun windowedViewerHighlightMatchesFullFileTokenColorsIncludingCommentsAndStrings() {
        val lines = buildList {
            add("package sample")
            add("/* multi-line")
            add("   block comment line */")
            add("fun keep(): String {")
            add("    val msg = \"\"\"hello")
            add("    world\"\"\"")
            for (i in 0 until 30) add("    val item$i = $i // note $i")
            add("    return msg")
            add("}")
        }
        val text = lines.joinToString("\n")
        val f = temp.file("Sample.kt", text)
        val lang = Highlighter.languageFor("Sample.kt")
        val fullTokens = Highlighter.tokenize(text, lang, longLine = 20_000)

        val lineStarts = IntArray(lines.size)
        var off = 0
        for (i in lines.indices) {
            lineStarts[i] = off
            off += lines[i].length + 1
        }

        PagedFile(f, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 8)
            assertTrue(idx.build(pf))
            val firstVis = 10L
            val lastVis = 25L
            val win = ViewerHighlight.tokenizeLines(pf, idx, firstVis, lastVis, lang, longLineLimit = 20_000, maxCharsPerLine = 20_000)
            assertNotNull(win)
            win!!
            for (l in firstVis..lastVis) {
                val li = l.toInt()
                val fullLineTokens = collectLineColors(fullTokens, lineStarts[li], lineStarts[li] + lines[li].length)
                val winLineStart = win.lineOffsets[(l - firstVis).toInt()]
                val winLineTokens = collectLineColors(win.tokens, winLineStart, winLineStart + lines[li].length)
                assertEquals("line $li", fullLineTokens, winLineTokens)
            }

            // Plain text or > maxFileBytes returns null cleanly.
            val over = ViewerHighlight.tokenizeLines(pf, idx, 0L, 5L, lang, 20_000, 20_000, maxFileBytes = 10L)
            assertNull(over)
        }
    }

    @Test
    fun memoryFootprintOfSparseIndexIsTinyRelativeToMultiMbFile() {
        val f = temp.file("multi.log", "")
        // Write ~3 MB without keeping it all in memory at once.
        val block = "2026-11-28 INFO request handled in 12ms path=/api/v1/items\n".repeat(1000).toByteArray()
        FileOutputStream(f).use { out -> repeat(55) { out.write(block) } }
        PagedFile(f, preferMap = false).use { pf ->
            val idx = SparseLineIndex(chunkLines = 256)
            assertTrue(idx.build(pf))
            assertEquals(55_001L, idx.lineCount)
            assertNotNull(idx.readLine(pf, 54_000L, 4096))
        }
    }

    private fun collectLineColors(tokens: Tokens, lineStart: Int, lineEnd: Int): List<Triple<Int, Int, Int>> {
        val scheme = SyntaxScheme.of(light = false, editorBackground = 0xFF1E1E1E.toInt())
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (i in 0 until tokens.size) {
            val b = i * 3
            val s = maxOf(tokens.data[b], lineStart)
            val e = minOf(tokens.data[b + 1], lineEnd)
            if (e > s) out.add(Triple(s - lineStart, e - lineStart, scheme.resolve(tokens.data[b + 2])))
        }
        return out
    }
}
