package com.whatcode.bigfile

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Pure Kotlin piece-table overlay for editing multi-gigabyte files without loading the whole file into memory.
 * Keeps the original file immutable on disk ([originalLength]), appends new bytes to an in-memory [addBuffer],
 * maintains an undo/redo stack of piece-list snapshots, and writes changes via an atomic sibling temp-file save.
 */
class PieceTable(val originalLength: Long) {

    enum class Source { ORIGINAL, ADD }

    data class Piece(val source: Source, val start: Long, val length: Long)

    private class UndoState(val pieces: List<Piece>, val cursorOffset: Long)

    private val addBuffer = ByteArrayOutputStream()
    private var pieces: MutableList<Piece> = if (originalLength > 0L) {
        mutableListOf(Piece(Source.ORIGINAL, 0L, originalLength))
    } else {
        mutableListOf()
    }

    private val undoStack = ArrayDeque<UndoState>()
    private val redoStack = ArrayDeque<UndoState>()
    private var editVersion = 0

    val isDirty: Boolean get() = editVersion != 0
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val totalLength: Long
        get() {
            var sum = 0L
            for (p in pieces) sum += p.length
            return sum
        }

    fun snapshotPieces(): List<Piece> = pieces.toList()

    /** Inserts [bytes] at byte [offset], recording one undo step. */
    fun insert(offset: Long, bytes: ByteArray, cursorAfter: Long = offset + bytes.size) {
        if (bytes.isEmpty()) return
        replace(offset, 0L, bytes, cursorAfter)
    }

    /** Deletes [len] bytes starting at [offset], recording one undo step. */
    fun delete(offset: Long, len: Long, cursorAfter: Long = offset) {
        if (len <= 0L) return
        replace(offset, len, ByteArray(0), cursorAfter)
    }

    /** Replaces [deleteLen] bytes at [offset] with [insertBytes] as a single undoable step. */
    fun replace(offset: Long, deleteLen: Long, insertBytes: ByteArray, cursorAfter: Long = offset + insertBytes.size) {
        val total = totalLength
        val safeOff = offset.coerceIn(0L, total)
        val safeDel = deleteLen.coerceIn(0L, total - safeOff)
        if (safeDel == 0L && insertBytes.isEmpty()) return

        undoStack.addLast(UndoState(pieces.toList(), cursorAfter))
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()

        val next = ArrayList<Piece>(pieces.size + 2)
        val delEnd = safeOff + safeDel
        var pos = 0L
        var inserted = false

        fun emitInserted() {
            if (!inserted && insertBytes.isNotEmpty()) {
                val addStart = addBuffer.size().toLong()
                addBuffer.write(insertBytes)
                next.add(Piece(Source.ADD, addStart, insertBytes.size.toLong()))
                inserted = true
            }
        }

        for (p in pieces) {
            val pEnd = pos + p.length
            if (pEnd <= safeOff) {
                next.add(p)
            } else if (pos >= delEnd) {
                emitInserted()
                next.add(p)
            } else {
                if (pos < safeOff) {
                    next.add(Piece(p.source, p.start, safeOff - pos))
                }
                emitInserted()
                if (pEnd > delEnd) {
                    val shift = delEnd - pos
                    next.add(Piece(p.source, p.start + shift, pEnd - delEnd))
                }
            }
            pos = pEnd
        }
        emitInserted()

        pieces = coalesce(next)
        editVersion++
    }

    fun undo(): Long? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(UndoState(pieces.toList(), prev.cursorOffset))
        pieces = prev.pieces.toMutableList()
        editVersion--
        return prev.cursorOffset
    }

    fun redo(): Long? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(UndoState(pieces.toList(), next.cursorOffset))
        pieces = next.pieces.toMutableList()
        editVersion++
        return next.cursorOffset
    }

    /** Reads up to [len] bytes starting at logical byte [offset] into [dst], spanning ORIGINAL and ADD pieces. */
    fun readBytes(paged: PagedFile, offset: Long, len: Int, dst: ByteArray): Int {
        if (offset < 0L || len <= 0 || dst.isEmpty()) return 0
        val want = minOf(len, dst.size)
        val addBytes by lazy(LazyThreadSafetyMode.NONE) { addBuffer.toByteArray() }
        var pos = 0L
        var written = 0
        for (p in pieces) {
            if (written >= want) break
            val pEnd = pos + p.length
            if (pEnd <= offset) {
                pos = pEnd
                continue
            }
            val inPiece = maxOf(0L, offset + written - pos)
            val avail = p.length - inPiece
            val take = minOf(avail, (want - written).toLong()).toInt()
            if (take > 0) {
                if (p.source == Source.ORIGINAL) {
                    val got = paged.readBytes(p.start + inPiece, take, sliceTmp(take))
                    if (got > 0) {
                        System.arraycopy(tmpBuf, 0, dst, written, got)
                        written += got
                    }
                    if (got < take) break
                } else {
                    System.arraycopy(addBytes, (p.start + inPiece).toInt(), dst, written, take)
                    written += take
                }
            }
            pos = pEnd
        }
        return written
    }

    private var tmpBuf = ByteArray(4096)
    private fun sliceTmp(need: Int): ByteArray {
        if (tmpBuf.size < need) tmpBuf = ByteArray(need)
        return tmpBuf
    }

    /**
     * Streams the edited document to a sibling temp file and atomically replaces [targetFile]. Returns total bytes
     * written, or throws if cancelled or on I/O failure (cleaning up the temp file).
     */
    fun saveAtomic(
        paged: PagedFile,
        targetFile: File,
        cancelled: () -> Boolean = { false },
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Long {
        val parent = targetFile.parentFile ?: throw IllegalArgumentException("Missing parent directory")
        val tmp = File(parent, ".${targetFile.name}.whatcode.tmp.${System.nanoTime()}")
        val total = totalLength
        val addBytes = addBuffer.toByteArray()
        val buf = ByteArray(64 * 1024)
        var written = 0L
        var completed = false
        try {
            FileOutputStream(tmp).use { out ->
                for (p in pieces) {
                    if (cancelled()) throw IllegalStateException("Save cancelled")
                    if (p.source == Source.ORIGINAL) {
                        var rem = p.length
                        var srcOff = p.start
                        while (rem > 0L) {
                            if (cancelled()) throw IllegalStateException("Save cancelled")
                            val chunk = minOf(buf.size.toLong(), rem).toInt()
                            val n = paged.readBytes(srcOff, chunk, buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            srcOff += n
                            rem -= n
                            written += n
                            onProgress(written, total)
                        }
                    } else {
                        var rem = p.length.toInt()
                        var srcOff = p.start.toInt()
                        while (rem > 0) {
                            if (cancelled()) throw IllegalStateException("Save cancelled")
                            val chunk = minOf(buf.size, rem)
                            out.write(addBytes, srcOff, chunk)
                            srcOff += chunk
                            rem -= chunk
                            written += chunk
                            onProgress(written, total)
                        }
                    }
                }
                out.fd.sync()
            }
            paged.close()
            try {
                Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            completed = true
        } finally {
            if (!completed) tmp.delete()
        }
        return written
    }

    private fun coalesce(list: List<Piece>): MutableList<Piece> {
        val out = ArrayList<Piece>(list.size)
        for (p in list) {
            if (p.length <= 0L) continue
            val last = out.lastOrNull()
            if (last != null && last.source == p.source && last.start + last.length == p.start) {
                out[out.lastIndex] = Piece(last.source, last.start, last.length + p.length)
            } else {
                out.add(p)
            }
        }
        return out
    }

    companion object {
        private const val MAX_UNDO = 200
    }
}
