package com.novelreader.app.core

/**
 * Pure colour math on ARGB Ints (no android.graphics dependency).
 * Used to stop the user from picking a text colour that is unreadable on the chosen background.
 */
object ColorMath {

    /** WCAG minimum for normal body text. */
    const val MIN_CONTRAST = 3.0

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    /** Relative luminance (WCAG) of an opaque colour given as 0xAARRGGBB or 0xRRGGBB. */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** WCAG contrast ratio, 1.0 (identical) .. 21.0 (black on white). */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun isReadable(text: Int, background: Int): Boolean = contrastRatio(text, background) >= MIN_CONTRAST

    /** Black or white, whichever reads better on [background]. */
    fun bestTextOn(background: Int): Int =
        if (contrastRatio(0xFFFFFFFF.toInt(), background) >= contrastRatio(0xFF000000.toInt(), background))
            0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    /** Parses "#RRGGBB", "RRGGBB", "#AARRGGBB" -> opaque ARGB Int, or null if invalid. */
    fun parseHex(input: String): Int? {
        val s = input.trim().removePrefix("#")
        if (s.length != 6 && s.length != 8) return null
        if (!s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val v = s.toLong(16)
        return if (s.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    }

    /** "#RRGGBB" (alpha dropped). */
    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** HSV (h 0..360, s/v 0..1) to opaque ARGB Int. */
    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1 - Math.abs((hh / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            hh < 60 -> Triple(c, x, 0f)
            hh < 120 -> Triple(x, c, 0f)
            hh < 180 -> Triple(0f, c, x)
            hh < 240 -> Triple(0f, x, c)
            hh < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun to255(f: Float) = Math.round((f + m) * 255f).coerceIn(0, 255)
        return (0xFF shl 24) or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)
    }

    /** ARGB Int to HSV triple (h 0..360, s/v 0..1). */
    fun argbToHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val s = if (max == 0f) 0f else d / max
        return floatArrayOf(h, s, max)
    }
}
