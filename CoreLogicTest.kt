package com.novelreader.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test fun arabicVariantsEqualPersian() =
        assertEquals(TextNormalizer.normalize("كتاب علي"), TextNormalizer.normalize("کتاب علی"))

    @Test fun zwnjRemoved() = assertEquals("میخواهم", TextNormalizer.normalize("می‌خواهم"))
    @Test fun persianDigits() = assertEquals("فصل 12", TextNormalizer.normalize("فصل ۱۲"))
    @Test fun latinCaseAndSpaces() = assertEquals("hello world", TextNormalizer.normalize("  HeLLo   World "))
    @Test fun diacriticsAndTatweel() = assertEquals("محمد", TextNormalizer.normalize("مُحَمَّـد"))
    @Test fun containsNormalized() {
        assertTrue(TextNormalizer.containsNormalized("رمان كتاب جدید", "کتاب"))
        assertFalse(TextNormalizer.containsNormalized("رمان", "کتاب"))
        assertTrue(TextNormalizer.containsNormalized("x", "  "))
        assertFalse(TextNormalizer.containsNormalized(null, "a"))
    }
}

class ReadingMathTest {
    @Test fun clampAndPercent() {
        assertEquals(0.0, ReadingMath.clamp01(Double.NaN), 0.0)
        assertEquals(1.0, ReadingMath.clamp01(1.7), 0.0)
        assertEquals(0, ReadingMath.percent(null))
        assertEquals(46, ReadingMath.percent(0.456))
    }

    @Test fun indexMapping() {
        assertEquals(0, ReadingMath.indexFromFraction(0.0, 100))
        assertEquals(99, ReadingMath.indexFromFraction(1.0, 100))
        assertEquals(50, ReadingMath.indexFromFraction(0.5, 100))
        assertEquals(-1, ReadingMath.indexFromFraction(0.5, 0))
        assertEquals(0.25, ReadingMath.fractionFromIndex(25, 100), 0.0)
    }

    @Test fun positionsAndRemaining() {
        assertEquals(0, ReadingMath.currentPosition(null, 10))
        assertEquals(10, ReadingMath.currentPosition(99, 10))
        assertEquals(6, ReadingMath.remaining(4, 10))
        assertEquals(0, ReadingMath.remaining(10, 10))
    }

    @Test fun chapterMarkers() {
        val pos = listOf("c1", "c1", "c2", "c2", "c3", "c3", "c3", "c4")
        val m = ReadingMath.chapterMarkers(pos, listOf("c1", "c2", "c3", "missing", "c4", "c2"))
        assertEquals(listOf(2 / 8.0, 4 / 8.0, 7 / 8.0), m)
        assertTrue(ReadingMath.chapterMarkers(listOf("a"), listOf("a")).isEmpty())
        assertEquals("OEBPS/ch1.xhtml", ReadingMath.stripFragment("OEBPS/ch1.xhtml#sec2"))
    }
}

class ColorMathTest {
    @Test fun contrast() {
        assertEquals(21.0, ColorMath.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertFalse(ColorMath.isReadable(0xFF222222.toInt(), 0xFF222222.toInt()))
        assertFalse(ColorMath.isReadable(0xFF303030.toInt(), 0xFF121212.toInt()))
        assertTrue(ColorMath.isReadable(0xFFEEEEEE.toInt(), 0xFF121212.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.bestTextOn(0xFF101010.toInt()))
        assertEquals(0xFF000000.toInt(), ColorMath.bestTextOn(0xFFFAF3E0.toInt()))
    }

    @Test fun hex() {
        assertEquals(0xFFFF8800.toInt(), ColorMath.parseHex("#FF8800"))
        assertEquals(0x80FF8800.toInt(), ColorMath.parseHex("80FF8800"))
        assertNull(ColorMath.parseHex("#GG0000"))
        assertNull(ColorMath.parseHex("#12345"))
        assertEquals("#FF8800", ColorMath.toHex(0xFFFF8800.toInt()))
    }

    @Test fun hsvRoundTrip() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), ColorMath.hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), ColorMath.hsvToArgb(240f, 1f, 1f))
        val hsv = ColorMath.argbToHsv(0xFF3366CC.toInt())
        assertEquals(0xFF3366CC.toInt(), ColorMath.hsvToArgb(hsv[0], hsv[1], hsv[2]))
    }
}

class EpubFileCheckTest {
    @Test fun magicAndExtension() {
        assertTrue(EpubFileCheck.hasZipMagic(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0)))
        assertFalse(EpubFileCheck.hasZipMagic(byteArrayOf(0x25, 0x50, 0x44, 0x46)))
        assertFalse(EpubFileCheck.hasZipMagic(byteArrayOf(0x50)))
        assertTrue(EpubFileCheck.hasEpubExtension("Book.EPUB"))
        assertFalse(EpubFileCheck.hasEpubExtension("book.pdf"))
        assertFalse(EpubFileCheck.hasEpubExtension(null))
    }

    @Test fun safeNames() {
        assertEquals("passwd.epub", EpubFileCheck.safeDisplayName("../../etc/passwd.epub"))
        assertEquals("book.epub", EpubFileCheck.safeDisplayName(null))
    }
}
