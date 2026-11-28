package com.whatcode.files

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Text character-encoding detection (BOM and strict UTF-8 validation), explicit re-decoding, exact-save checks
 * (lossy-save guard), and encoding. Pure JVM (unit tested).
 */
object TextEncoding {

    enum class Spec(
        val id: String,
        val charsetName: String,
        val label: String,
        val shortLabel: String,
        val bom: ByteArray = ByteArray(0),
    ) {
        UTF_8("UTF-8", "UTF-8", "UTF-8", "UTF-8"),
        UTF_8_BOM("UTF-8 BOM", "UTF-8", "UTF-8 with BOM", "UTF-8 BOM", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
        UTF_16LE("UTF-16LE", "UTF-16LE", "UTF-16 LE", "UTF-16LE", byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
        UTF_16BE("UTF-16BE", "UTF-16BE", "UTF-16 BE", "UTF-16BE", byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
        US_ASCII("US-ASCII", "US-ASCII", "ASCII", "ASCII"),
        ISO_8859_1("ISO-8859-1", "ISO-8859-1", "ISO-8859-1 (Latin-1)", "Latin-1"),
        WINDOWS_1252("windows-1252", "windows-1252", "Windows-1252", "Win-1252"),
        SHIFT_JIS("Shift_JIS", "Shift_JIS", "Shift-JIS", "Shift-JIS"),
        EUC_KR("EUC-KR", "EUC-KR", "EUC-KR", "EUC-KR"),
        GB18030("GB18030", "GB18030", "GB18030", "GB18030"),
        ;

        fun charset(): Charset = try {
            Charset.forName(charsetName)
        } catch (_: Exception) {
            Charsets.UTF_8
        }

        companion object {
            fun fromId(id: String?): Spec = values().firstOrNull { it.id == id } ?: UTF_8
        }
    }

    class Decoded(
        val text: String,
        val spec: Spec,
        val invalidUtf8: Boolean,
    )

    fun detectBom(bytes: ByteArray, len: Int = bytes.size): Spec? {
        if (len >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Spec.UTF_8_BOM
        }
        if (len >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Spec.UTF_16LE
        }
        if (len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Spec.UTF_16BE
        }
        return null
    }

    /** Detects BOM or validates UTF-8 bytes strictly, falling back to Windows-1252 when invalid UTF-8 sequences exist. */
    fun detectAndDecode(bytes: ByteArray): Decoded {
        val bomSpec = detectBom(bytes)
        if (bomSpec != null) {
            val offset = bomSpec.bom.size
            val decoder = bomSpec.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            val text = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            return Decoded(text, bomSpec, invalidUtf8 = false)
        }
        val strictUtf8 = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val text = strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
            Decoded(text, Spec.UTF_8, invalidUtf8 = false)
        } catch (_: Exception) {
            val win = Spec.WINDOWS_1252.charset().newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes)).toString()
            Decoded(win, Spec.WINDOWS_1252, invalidUtf8 = true)
        }
    }

    /** Decodes raw file bytes strictly/explicitly as [spec], stripping its BOM if matching. */
    fun decodeWith(bytes: ByteArray, spec: Spec): String {
        val offset = if (spec.bom.isNotEmpty() && bytes.size >= spec.bom.size &&
            spec.bom.indices.all { bytes[it] == spec.bom[it] }
        ) spec.bom.size else 0
        val decoder = spec.charset().newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    }

    /** Returns true when every character of [text] can be represented in [spec] without replacement loss. */
    fun canEncodeExactly(text: String, spec: Spec): Boolean {
        if (spec == Spec.UTF_8 || spec == Spec.UTF_8_BOM || spec == Spec.UTF_16LE || spec == Spec.UTF_16BE) return true
        val enc = spec.charset().newEncoder()
        return enc.canEncode(CharBuffer.wrap(text))
    }

    fun encode(text: String, spec: Spec): ByteArray {
        val body = text.toByteArray(spec.charset())
        if (spec.bom.isEmpty()) return body
        val out = ByteArray(spec.bom.size + body.size)
        System.arraycopy(spec.bom, 0, out, 0, spec.bom.size)
        System.arraycopy(body, 0, out, spec.bom.size, body.size)
        return out
    }
}
