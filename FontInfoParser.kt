package com.novelreader.app.core

/** What the importer learned from a font file's own tables. */
data class FontInfo(
    val family: String,
    val subfamily: String?,
    /** 100..900 (OS/2 usWeightClass, clamped); 400 when the table is missing. */
    val weight: Int,
    val italic: Boolean,
    /** Variable font with a 'wght' axis: the usable weight range (e.g. 200..900). */
    val variableWeightRange: IntRange?,
    /** TrueType outlines (glyf) -> "ttf"; CFF outlines ('OTTO') -> "otf". */
    val extension: String,
    /** The font maps the core Arabic-script letters used by Persian (alef, beh, peh, yeh, keheh, gaf, ...). */
    val coversPersian: Boolean,
    /** The font maps basic Latin letters. */
    val coversLatin: Boolean,
) {
    val isVariable: Boolean get() = variableWeightRange != null
}

sealed interface FontParseResult {
    data class Ok(val info: FontInfo) : FontParseResult
    data class Rejected(val reason: FontReject) : FontParseResult
}

enum class FontReject { EMPTY, TOO_LARGE, NOT_A_FONT, COLLECTION, WEB_FONT, CORRUPT }

/**
 * Minimal, defensive OpenType/TrueType (sfnt) reader. Pure Kotlin so it is unit-testable on the JVM.
 *
 * The input is untrusted: every read is bounds-checked, table counts are capped, and any inconsistency yields
 * [FontReject.CORRUPT] instead of an exception. It reads only what the app needs: the 'name', 'OS/2', 'fvar',
 * 'cmap' and 'head'-free metadata. It never executes or interprets glyph programs.
 */
object FontInfoParser {

    const val MAX_FONT_BYTES = 30 * 1024 * 1024
    private const val MAX_TABLES = 128

    private const val TAG_TRUETYPE = 0x00010000
    private const val TAG_TRUE = 0x74727565      // 'true' (old Apple TrueType)
    private const val TAG_OTTO = 0x4F54544F      // 'OTTO' (CFF)
    private const val TAG_TTCF = 0x74746366      // 'ttcf'
    private const val TAG_WOFF = 0x774F4646      // 'wOFF'
    private const val TAG_WOF2 = 0x774F4632      // 'wOF2'

    fun parse(data: ByteArray): FontParseResult {
        if (data.isEmpty()) return FontParseResult.Rejected(FontReject.EMPTY)
        if (data.size > MAX_FONT_BYTES) return FontParseResult.Rejected(FontReject.TOO_LARGE)
        if (data.size < 12) return FontParseResult.Rejected(FontReject.NOT_A_FONT)
        return try {
            val r = Reader(data)
            val tag = r.u32(0)
            val ext = when (tag) {
                TAG_TRUETYPE, TAG_TRUE -> "ttf"
                TAG_OTTO -> "otf"
                TAG_TTCF -> return FontParseResult.Rejected(FontReject.COLLECTION)
                TAG_WOFF, TAG_WOF2 -> return FontParseResult.Rejected(FontReject.WEB_FONT)
                else -> return FontParseResult.Rejected(FontReject.NOT_A_FONT)
            }
            val numTables = r.u16(4)
            if (numTables == 0 || numTables > MAX_TABLES) return FontParseResult.Rejected(FontReject.CORRUPT)
            val tables = HashMap<Int, Pair<Int, Int>>() // tag -> (offset, length)
            for (i in 0 until numTables) {
                val rec = 12 + i * 16
                val t = r.u32(rec)
                val off = r.u32(rec + 8)
                val len = r.u32(rec + 12)
                if (off < 0 || len < 0 || off.toLong() + len.toLong() > data.size) {
                    return FontParseResult.Rejected(FontReject.CORRUPT)
                }
                tables[t] = off to len
            }
            // A usable font must at least have a glyph source and a character map.
            val hasOutlines = tables.containsKey(tag("glyf")) || tables.containsKey(tag("CFF ")) || tables.containsKey(tag("CFF2"))
            val cmapT = tables[tag("cmap")]
            if (!hasOutlines || cmapT == null) return FontParseResult.Rejected(FontReject.CORRUPT)

            val names = tables[tag("name")]?.let { readNames(r, it.first, it.second) } ?: emptyMap()
            val family = (names[16] ?: names[1])?.trim().takeUnless { it.isNullOrEmpty() }
                ?: (names[4] ?: "Font").trim().ifEmpty { "Font" }
            val subfamily = (names[17] ?: names[2])?.trim()?.takeIf { it.isNotEmpty() }

            var weight = 400
            var italic = false
            tables[tag("OS/2")]?.let { (off, len) ->
                if (len >= 64) {
                    weight = r.u16(off + 4).coerceIn(100, 900)
                    italic = (r.u16(off + 62) and 0x01) != 0
                }
            }
            val variableRange = tables[tag("fvar")]?.let { readWeightAxis(r, it.first, it.second) }

            val coversPersian = PERSIAN_PROBE.all { cmapHas(r, cmapT.first, cmapT.second, it) }
            val coversLatin = LATIN_PROBE.all { cmapHas(r, cmapT.first, cmapT.second, it) }

            FontParseResult.Ok(
                FontInfo(
                    family = family.take(80),
                    subfamily = subfamily?.take(60),
                    weight = weight,
                    italic = italic,
                    variableWeightRange = variableRange,
                    extension = ext,
                    coversPersian = coversPersian,
                    coversLatin = coversLatin,
                )
            )
        } catch (_: IndexOutOfBoundsException) {
            FontParseResult.Rejected(FontReject.CORRUPT)
        } catch (_: IllegalArgumentException) {
            FontParseResult.Rejected(FontReject.CORRUPT)
        }
    }

    /** Alef, Beh, Peh, Yeh (Farsi), Keheh, Gaf, Zheh, Tcheh, Persian digit one. */
    private val PERSIAN_PROBE = intArrayOf(0x0627, 0x0628, 0x067E, 0x06CC, 0x06A9, 0x06AF, 0x0698, 0x0686, 0x06F1)
    private val LATIN_PROBE = intArrayOf('A'.code, 'a'.code, 'e'.code, 'z'.code, '0'.code)

    private fun tag(s: String): Int =
        (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code

    // ---- 'name' ---------------------------------------------------------------------------------

    private fun readNames(r: Reader, off: Int, len: Int): Map<Int, String> {
        if (len < 6) return emptyMap()
        val count = r.u16(off + 2).coerceAtMost(2000)
        val stringOffset = off + r.u16(off + 4)
        val out = HashMap<Int, String>()
        val score = HashMap<Int, Int>()
        for (i in 0 until count) {
            val rec = off + 6 + i * 12
            if (rec + 12 > off + len) break
            val platform = r.u16(rec)
            val encoding = r.u16(rec + 2)
            val language = r.u16(rec + 4)
            val nameId = r.u16(rec + 6)
            if (nameId !in intArrayOf(1, 2, 4, 16, 17)) continue
            val length = r.u16(rec + 8)
            val start = stringOffset + r.u16(rec + 10)
            if (start < 0 || start + length > off + len || length > 512) continue
            val raw = r.bytes(start, length)
            val text = when {
                platform == 3 && (encoding == 1 || encoding == 10) -> String(raw, Charsets.UTF_16BE)
                platform == 0 -> String(raw, Charsets.UTF_16BE)
                platform == 1 && encoding == 0 -> String(raw, Charsets.ISO_8859_1)
                else -> continue
            }
            // Prefer Windows/English-US, then any Windows, then Unicode, then Mac.
            val s = when {
                platform == 3 && language == 0x0409 -> 4
                platform == 3 -> 3
                platform == 0 -> 2
                else -> 1
            }
            if (text.isNotBlank() && s > (score[nameId] ?: 0)) {
                out[nameId] = text
                score[nameId] = s
            }
        }
        return out
    }

    // ---- 'fvar' ---------------------------------------------------------------------------------

    private fun readWeightAxis(r: Reader, off: Int, len: Int): IntRange? {
        if (len < 16) return null
        val axesOffset = r.u16(off + 4)
        val axisCount = r.u16(off + 8).coerceAtMost(64)
        val axisSize = r.u16(off + 10)
        if (axisSize < 20) return null
        for (i in 0 until axisCount) {
            val a = off + axesOffset + i * axisSize
            if (a + 20 > off + len) break
            if (r.u32(a) == tag("wght")) {
                val min = fixedToInt(r.u32(a + 4))
                val max = fixedToInt(r.u32(a + 12))
                if (min in 1..1000 && max in min..1000) return min..max
            }
        }
        return null
    }

    /** 16.16 fixed point -> rounded Int. */
    private fun fixedToInt(v: Int): Int = Math.round(v / 65536.0).toInt()

    // ---- 'cmap' ---------------------------------------------------------------------------------

    private fun cmapHas(r: Reader, off: Int, len: Int, codePoint: Int): Boolean {
        if (len < 4) return false
        val n = r.u16(off + 2).coerceAtMost(64)
        for (i in 0 until n) {
            val rec = off + 4 + i * 8
            if (rec + 8 > off + len) break
            val platform = r.u16(rec)
            val encoding = r.u16(rec + 2)
            val sub = off + r.u32(rec + 4)
            val usable = (platform == 3 && (encoding == 1 || encoding == 10)) || platform == 0
            if (!usable || sub < 0 || sub + 4 > r.size) continue
            val found = when (r.u16(sub)) {
                4 -> format4Has(r, sub, codePoint)
                12 -> format12Has(r, sub, codePoint)
                else -> false
            }
            if (found) return true
        }
        return false
    }

    private fun format4Has(r: Reader, sub: Int, cp: Int): Boolean {
        if (cp > 0xFFFF) return false
        val segX2 = r.u16(sub + 6)
        val segCount = segX2 / 2
        if (segCount == 0 || segCount > 4096) return false
        val endBase = sub + 14
        val startBase = endBase + segX2 + 2
        val deltaBase = startBase + segX2
        val rangeBase = deltaBase + segX2
        for (i in 0 until segCount) {
            val end = r.u16(endBase + i * 2)
            if (cp > end) continue
            val start = r.u16(startBase + i * 2)
            if (cp < start) return false
            val rangeOffset = r.u16(rangeBase + i * 2)
            val glyph = if (rangeOffset == 0) {
                (cp + r.s16(deltaBase + i * 2)) and 0xFFFF
            } else {
                val addr = rangeBase + i * 2 + rangeOffset + (cp - start) * 2
                val g = r.u16(addr)
                if (g == 0) 0 else (g + r.s16(deltaBase + i * 2)) and 0xFFFF
            }
            return glyph != 0
        }
        return false
    }

    private fun format12Has(r: Reader, sub: Int, cp: Int): Boolean {
        val groups = r.u32(sub + 12)
        if (groups < 0 || groups > 100_000) return false
        for (i in 0 until groups) {
            val g = sub + 16 + i * 12
            val start = r.u32(g)
            val end = r.u32(g + 4)
            if (cp < start) return false
            if (cp <= end) return r.u32(g + 8) + (cp - start) != 0
        }
        return false
    }

    // ---- bounds-checked big-endian reader --------------------------------------------------------

    private class Reader(private val d: ByteArray) {
        val size get() = d.size

        private fun check(off: Int, n: Int) {
            if (off < 0 || n < 0 || off.toLong() + n > d.size) throw IndexOutOfBoundsException("font read out of range")
        }

        fun u16(off: Int): Int { check(off, 2); return ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF) }
        fun s16(off: Int): Int = u16(off).toShort().toInt()
        fun u32(off: Int): Int {
            check(off, 4)
            return ((d[off].toInt() and 0xFF) shl 24) or ((d[off + 1].toInt() and 0xFF) shl 16) or
                ((d[off + 2].toInt() and 0xFF) shl 8) or (d[off + 3].toInt() and 0xFF)
        }
        fun bytes(off: Int, n: Int): ByteArray { check(off, n); return d.copyOfRange(off, off + n) }
    }
}
