package com.novelreader.app.core

/**
 * Pure-Kotlin text helpers (no Android dependencies) so they can be unit-tested on the JVM.
 *
 * Normalization is used for library search and any app-side text matching so that
 * common Persian/Arabic spelling variants compare equal.
 */
object TextNormalizer {

    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'
    private const val TATWEEL = 'ـ'

    /** Strips Arabic diacritics (harakat, shadda, sukun, dagger alif). */
    private fun isDiacritic(c: Char): Boolean =
        c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ'

    /**
     * Normalizes [input] for case/variant-insensitive comparison:
     *  - Arabic Yeh/Alef Maksura -> Persian Yeh, Arabic Kaf -> Persian Kaf
     *  - Persian/Arabic-Indic digits -> ASCII digits
     *  - removes ZWNJ/ZWJ (half-space), tatweel and diacritics
     *  - collapses whitespace, lower-cases Latin letters
     */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input) {
            val mapped: Char? = when (ch) {
                'ي', 'ى', 'ی' -> 'ی'            // ي ى ی -> ی
                'ك', 'ک' -> 'ک'                      // ك ک -> ک
                'أ', 'إ', 'ٱ' -> 'ا'            // أ إ ٱ -> ا
                'ۀ', 'ة' -> 'ه'                      // ۀ ة -> ه
                in '۰'..'۹' -> '0' + (ch - '۰')      // Persian digits
                in '٠'..'٩' -> '0' + (ch - '٠')      // Arabic-Indic digits
                else -> ch
            }
            val c = mapped ?: continue
            if (c == ZWNJ || c == ZWJ || c == TATWEEL || isDiacritic(c)) continue
            if (c.isWhitespace()) {
                if (!lastWasSpace) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(c.lowercaseChar())
                lastWasSpace = false
            }
        }
        if (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    /** True if [haystack] contains [needle] after normalizing both. Blank needle matches everything. */
    fun containsNormalized(haystack: String?, needle: String): Boolean {
        val n = normalize(needle)
        if (n.isEmpty()) return true
        if (haystack == null) return false
        return normalize(haystack).contains(n)
    }
}
