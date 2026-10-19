package com.novelreader.app.core

/**
 * Pure progress/position math. EPUB has no fixed page count, so the app works with
 * Readium "positions" (a stable list of ~1000-character units, independent of font size).
 * These are shown to the user as approximate pages ("صفحه‌های تقریبی"); nothing here is invented.
 */
object ReadingMath {

    /** Clamps to [0, 1]; NaN becomes 0. */
    fun clamp01(v: Double): Double = if (v.isNaN()) 0.0 else v.coerceIn(0.0, 1.0)

    /** Percent 0..100 rounded to an Int. */
    fun percent(fraction: Double?): Int = (clamp01(fraction ?: 0.0) * 100.0).let { Math.round(it).toInt() }

    /**
     * Maps a slider fraction (0..1) onto a 0-based index in a list of [total] positions.
     * Returns -1 when [total] <= 0.
     */
    fun indexFromFraction(fraction: Double, total: Int): Int {
        if (total <= 0) return -1
        val idx = Math.floor(clamp01(fraction) * total).toInt()
        return idx.coerceIn(0, total - 1)
    }

    /** Inverse of [indexFromFraction]: fraction at the start of position [index]. */
    fun fractionFromIndex(index: Int, total: Int): Double {
        if (total <= 0) return 0.0
        return clamp01(index.toDouble() / total.toDouble())
    }

    /** 1-based current position clamped to [1, total]; 0 when unknown. */
    fun currentPosition(position: Int?, total: Int): Int =
        if (position == null || total <= 0) 0 else position.coerceIn(1, total)

    /** Positions left after the current one. */
    fun remaining(position: Int?, total: Int): Int =
        if (total <= 0) 0 else (total - currentPosition(position, total)).coerceAtLeast(0)

    /**
     * Fractions (0..1) of chapter starts on the progress bar.
     *
     * [positionHrefs] is the href (fragment already stripped) of every position, in order.
     * [chapterHrefs] is the href of every table-of-contents entry (fragment stripped).
     * A chapter's marker is placed at the first position that belongs to its resource.
     * Chapters that cannot be found are skipped; duplicates are removed. No chapters are invented.
     */
    fun chapterMarkers(positionHrefs: List<String>, chapterHrefs: List<String>): List<Double> {
        val total = positionHrefs.size
        if (total <= 1) return emptyList()
        val firstIndexByHref = HashMap<String, Int>()
        positionHrefs.forEachIndexed { i, h -> firstIndexByHref.putIfAbsent(h, i) }
        val out = LinkedHashSet<Double>()
        for (h in chapterHrefs) {
            val i = firstIndexByHref[h] ?: continue
            if (i == 0) continue // book start is not a useful marker
            out += fractionFromIndex(i, total)
        }
        return out.toList().sorted()
    }

    /** Removes a URL fragment and query: "ch1.xhtml#a" -> "ch1.xhtml". */
    fun stripFragment(href: String): String = href.substringBefore('#').substringBefore('?')
}
