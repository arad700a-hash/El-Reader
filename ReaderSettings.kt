package com.novelreader.app.data

/** App/reader theme. Dark is the default. */
enum class ThemeMode { DARK, LIGHT }

/** Paragraph alignment. ORIGINAL keeps whatever the EPUB specifies (the default - nothing is forced). */
enum class AlignMode { ORIGINAL, RIGHT, CENTER, LEFT }

/** Font choice. ORIGINAL keeps the book's own font. */
enum class FontChoice { ORIGINAL, SERIF, SANS_SERIF }

/** How a custom background image fills the reading area. */
enum class BgImageMode { COVER, FIT, TILE }

/** Page-turn direction: follow the book, or force LTR/RTL. */
enum class DirectionMode { AUTO, LTR, RTL }

enum class LibrarySort { LAST_READ, TITLE, ADDED }

/**
 * Every reader preference. All of these are persisted (DataStore) and each one is wired to the
 * navigator in [com.novelreader.app.reader.ReaderPreferencesMapper] - nothing here is cosmetic only.
 */
data class ReaderSettings(
    val theme: ThemeMode = ThemeMode.DARK,

    /** Percent, 50..300. 100 = the book's/engine default size. */
    val fontSizePercent: Int = 100,
    val fontChoice: FontChoice = FontChoice.ORIGINAL,

    /**
     * Imported fonts (see [CustomFont]). When either is set it takes priority over [fontChoice].
     * [faFontId] styles Persian/Arabic-script text, [latinFontId] everything else; null = the book's/default font
     * for that script. The same id in both = one font for everything.
     */
    val faFontId: String? = null,
    val latinFontId: String? = null,

    /**
     * When true the book's own CSS decides spacing/alignment/font (Readium "publisher styles").
     * Line height, paragraph spacing/indent, alignment and font choice only take effect when false.
     */
    val keepPublisherStyles: Boolean = true,
    val lineHeight: Double = 1.5,          // 1.0 .. 2.5
    val paragraphSpacing: Double = 0.5,    // 0.0 .. 3.0 (Readium relative units)
    val paragraphIndent: Double = 0.0,     // 0.0 .. 3.0
    val align: AlignMode = AlignMode.ORIGINAL,
    val pageMargins: Double = 1.0,         // 0.5 .. 3.0  (side margins / max text width)

    /** null = use the theme's colour. */
    val textColor: Int? = null,
    val backgroundColor: Int? = null,

    /** Absolute path of a validated copy inside app-private storage, or null. */
    val backgroundImagePath: String? = null,
    val backgroundImageMode: BgImageMode = BgImageMode.COVER,
    /** How visible the image is, 0..1 (the rest is the plain background colour). */
    val backgroundImageOpacity: Float = 0.35f,

    /** true = continuous vertical scroll, false = paginated. */
    val scroll: Boolean = false,
    val direction: DirectionMode = DirectionMode.AUTO,
    /** Last chosen fullscreen state. */
    val fullscreen: Boolean = false,
) {
    companion object {
        val FONT_SIZE_RANGE = 50..300
        val LINE_HEIGHT_RANGE = 1.0..2.5
        val PARAGRAPH_SPACING_RANGE = 0.0..3.0
        val PARAGRAPH_INDENT_RANGE = 0.0..3.0
        val PAGE_MARGINS_RANGE = 0.5..3.0
    }
}

/** Theme defaults used to decide readability when the user has not picked explicit colours. */
object ThemeColors {
    const val DARK_BG = 0xFF121212.toInt()
    const val DARK_TEXT = 0xFFE6E6E6.toInt()
    const val LIGHT_BG = 0xFFFFFFFF.toInt()
    const val LIGHT_TEXT = 0xFF1A1A1A.toInt()

    fun background(theme: ThemeMode) = if (theme == ThemeMode.DARK) DARK_BG else LIGHT_BG
    fun text(theme: ThemeMode) = if (theme == ThemeMode.DARK) DARK_TEXT else LIGHT_TEXT
}

/** Ready-made swatches shown next to the colour picker. */
object Swatches {
    val background = listOf(
        0xFF000000.toInt(), // black
        0xFF121212.toInt(), // very dark grey
        0xFF2B2B2B.toInt(), // dark grey
        0xFFFFFFFF.toInt(), // white
        0xFFFAF3E0.toInt(), // cream
        0xFFF4ECD8.toInt(), // sepia
        0xFFDDEBD6.toInt(), // soft green
    )
    val text = listOf(
        0xFF000000.toInt(),
        0xFF222222.toInt(),
        0xFFE6E6E6.toInt(),
        0xFFFFFFFF.toInt(),
        0xFF5B4636.toInt(), // sepia brown
        0xFFB8C7B0.toInt(), // soft green
        0xFFFFD27F.toInt(), // warm amber
    )
    val highlight = listOf(
        0xFFFFEB3B.toInt(), // yellow
        0xFF81C784.toInt(), // green
        0xFF64B5F6.toInt(), // blue
        0xFFF48FB1.toInt(), // pink
        0xFFFFB74D.toInt(), // orange
    )
}
