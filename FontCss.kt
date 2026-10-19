package com.novelreader.app.core

/** Minimal description of an imported font, as needed to build CSS. (The persisted model adds display data.) */
data class FontFaceSpec(
    /** Short lowercase-hex id; also used in the served file name. */
    val id: String,
    val extension: String,          // "ttf" | "otf"
    val weight: Int,                // static weight (100..900)
    val italic: Boolean,
    val variableWeightRange: IntRange?,
)

/**
 * Builds the @font-face CSS that makes imported fonts usable inside the EPUB's WebView, and the family names
 * the navigator is told to use. Pure Kotlin (unit-testable).
 *
 * Why this exists: Readium's documented font mechanism serves fonts from the app's *assets* only. Imported
 * fonts live in private storage, so the app serves them from an extra container and declares them with CSS
 * injected into each XHTML resource (see EpubEngine / FontInjection).
 *
 * Family naming (all identifiers are [a-zA-Z0-9_], so they are valid quoted or unquoted):
 *   NRFont_<id>            one font for every script
 *   NRPair_<faId>_<latId>  Persian/Arabic-script text uses font faId, everything else uses font latId
 */
object FontCss {

    const val SERVE_DIR = "novelreader-fonts"
    const val MAX_FONTS = 6

    private val ID_REGEX = Regex("^[a-z0-9]{4,16}$")

    /** Arabic-script ranges (includes ZWNJ/ZWJ so half-spaces use the Persian font). Declared AFTER Latin so it wins overlaps. */
    private const val RANGE_ARABIC =
        "U+0600-06FF,U+0750-077F,U+08A0-08FF,U+FB50-FDFF,U+FE70-FEFF,U+200C-200D"

    /** Everything else that books commonly contain: Latin, punctuation, digits, symbols. */
    private const val RANGE_OTHER =
        "U+0000-05FF,U+0700-074F,U+0780-089F,U+0900-FB4F,U+FE00-FE6F,U+FF00-10FFFF"

    fun isValidId(id: String?): Boolean = id != null && ID_REGEX.matches(id)

    fun singleFamily(id: String) = "NRFont_$id"
    fun pairFamily(faId: String, latinId: String) = "NRPair_${faId}_$latinId"

    /**
     * The family to hand to the navigator for the user's selection, or null when no imported font is selected.
     * Only ids present in [available] count; unknown ids are ignored (e.g. a font that was deleted).
     */
    fun familyFor(faId: String?, latinId: String?, available: Set<String>): String? {
        val fa = faId?.takeIf { it in available && isValidId(it) }
        val lat = latinId?.takeIf { it in available && isValidId(it) }
        return when {
            fa == null && lat == null -> null
            fa != null && lat != null && fa != lat -> pairFamily(fa, lat)
            fa != null && lat != null -> singleFamily(fa)
            fa != null -> pairFamilyArabicOnly(fa)
            else -> pairFamilyLatinOnly(lat!!)
        }
    }

    // One-sided selections: the unselected script keeps the book's / system default, because the family has
    // no face for it and the browser falls through to the next family in the stack.
    fun pairFamilyArabicOnly(faId: String) = "NRFa_$faId"
    fun pairFamilyLatinOnly(latId: String) = "NRLat_$latId"

    /** Path of the served font file inside the publication container (never starts with '/'). */
    fun servedPath(font: FontFaceSpec) = "$SERVE_DIR/${font.id}.${font.extension}"

    /**
     * Relative URL from the XHTML at [resourcePath] (e.g. "OEBPS/text/ch1.xhtml") to the served font at
     * [targetPath] (e.g. "novelreader-fonts/ab12.ttf"): "../../novelreader-fonts/ab12.ttf".
     * Both paths are container-relative, percent-decoded, with '/' separators.
     */
    fun relativePath(resourcePath: String, targetPath: String): String {
        val from = resourcePath.trimStart('/').split('/').dropLast(1).filter { it.isNotEmpty() && it != "." }
        val up = from.size
        return "../".repeat(up) + targetPath.trimStart('/')
    }

    private fun format(ext: String) = if (ext == "otf") "opentype" else "truetype"

    private fun face(family: String, font: FontFaceSpec, url: String, unicodeRange: String?): String {
        val weight = font.variableWeightRange?.let { "${it.first} ${it.last}" } ?: font.weight.toString()
        val style = if (font.italic) "italic" else "normal"
        return buildString {
            append("@font-face{font-family:\"").append(family).append("\";")
            append("src:url(\"").append(url).append("\") format(\"").append(format(font.extension)).append("\");")
            append("font-weight:").append(weight).append(";font-style:").append(style).append(";")
            if (unicodeRange != null) append("unicode-range:").append(unicodeRange).append(";")
            append("font-display:swap;}")
        }
    }

    /**
     * All @font-face rules for [fonts], ready to place in a <style> element. [urlFor] maps a font to the URL
     * relative to the current XHTML resource. Declares: a single-script-agnostic family per font, a pair family
     * per ordered pair of different fonts, and one-sided families per font.
     */
    fun build(fonts: List<FontFaceSpec>, urlFor: (FontFaceSpec) -> String): String {
        val valid = fonts.filter { isValidId(it.id) }.distinctBy { it.id }.take(MAX_FONTS)
        val sb = StringBuilder()
        for (f in valid) {
            val url = urlFor(f)
            sb.append(face(singleFamily(f.id), f, url, null))
            // Latin-range face first, Arabic-range face second: later rules win where ranges overlap.
            sb.append(face(pairFamilyLatinOnly(f.id), f, url, RANGE_OTHER))
            sb.append(face(pairFamilyArabicOnly(f.id), f, url, RANGE_ARABIC))
        }
        for (fa in valid) for (lat in valid) {
            if (fa.id == lat.id) continue
            val family = pairFamily(fa.id, lat.id)
            sb.append(face(family, lat, urlFor(lat), RANGE_OTHER))
            sb.append(face(family, fa, urlFor(fa), RANGE_ARABIC))
        }
        return sb.toString()
    }
}

/** Inserts a <style> block into (X)HTML bytes without decoding the document. */
object HtmlStyleInjector {

    const val STYLE_ID = "novelreader-fonts"

    /**
     * Returns [html] with `<style id="novelreader-fonts">[css]</style>` inserted just before `</head>`
     * (or after `<head ...>`/before `<body` when there is no closing head tag). The bytes are treated as
     * ASCII-compatible (UTF-8, ISO-8859-x, ...), which covers EPUB content documents. UTF-16/32 documents and
     * documents that were already injected are returned unchanged.
     */
    fun inject(html: ByteArray, css: String): ByteArray {
        if (css.isEmpty() || html.size < 8) return html
        if (looksLikeUtf16Or32(html)) return html
        // ISO-8859-1 maps every byte to one char, so indices are byte offsets and nothing is corrupted.
        val text = String(html, Charsets.ISO_8859_1)
        val lower = text.lowercase()
        if (lower.contains("id=\"$STYLE_ID\"")) return html

        val block = "<style id=\"$STYLE_ID\">$css</style>"
        val insertAt = headClose(lower) ?: afterHeadOpen(lower) ?: bodyOpen(lower) ?: return html

        val blockBytes = block.toByteArray(Charsets.ISO_8859_1) // css is ASCII by construction
        val out = ByteArray(html.size + blockBytes.size)
        System.arraycopy(html, 0, out, 0, insertAt)
        System.arraycopy(blockBytes, 0, out, insertAt, blockBytes.size)
        System.arraycopy(html, insertAt, out, insertAt + blockBytes.size, html.size - insertAt)
        return out
    }

    private fun looksLikeUtf16Or32(b: ByteArray): Boolean {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) return true
        // No BOM but NUL bytes in the first characters also means a wide encoding.
        return b[1].toInt() == 0 || b[0].toInt() == 0
    }

    private fun headClose(lower: String): Int? = lower.indexOf("</head").takeIf { it >= 0 }

    private fun afterHeadOpen(lower: String): Int? {
        val i = Regex("<head(\\s[^>]*)?>").find(lower) ?: return null
        return i.range.last + 1
    }

    private fun bodyOpen(lower: String): Int? = Regex("<body[\\s>]").find(lower)?.range?.first

    /** True for container paths that are HTML/XHTML content documents. */
    fun isHtmlPath(path: String?): Boolean {
        val ext = path?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext == "xhtml" || ext == "html" || ext == "htm" || ext == "xht"
    }
}
