package com.novelreader.app.reader

import com.novelreader.app.core.FontCss
import com.novelreader.app.data.AlignMode
import com.novelreader.app.data.DirectionMode
import com.novelreader.app.data.FontChoice
import com.novelreader.app.data.ReaderSettings
import com.novelreader.app.data.ThemeMode
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

/**
 * Single translation point from the app's persisted [ReaderSettings] to Readium's [EpubPreferences].
 * Styling is applied through the navigator's presentation CSS (ReadiumCSS user properties); the EPUB file
 * itself is never modified.
 *
 * Notes on semantics (documented Readium behaviour):
 *  - keepPublisherStyles = true  -> the book's own CSS wins for spacing/alignment; custom values are ignored
 *    by the engine until it is switched off.
 *  - text/background colours and font size apply in both modes.
 *  - A custom background *image* is drawn by the app in a layer BEHIND the navigator (ReaderBackground) and the
 *    page colour is sent as fully transparent. Whether Readium honours alpha 0 is to be verified on a device.
 */
object ReaderPreferencesMapper {

    /**
     * @param availableFontIds ids of imported fonts that are actually declared in the open publication
     * (see [FontInjection]); selections pointing anywhere else are ignored instead of producing a dead family name.
     */
    fun toEpubPreferences(s: ReaderSettings, availableFontIds: Set<String> = emptySet()): EpubPreferences = EpubPreferences(
        theme = if (s.theme == ThemeMode.DARK) Theme.DARK else Theme.LIGHT,
        // With a background image the page must be transparent so the image layer behind it shows through.
        backgroundColor = if (s.backgroundImagePath != null) Color(0x00000000) else s.backgroundColor?.let { Color(it) },
        textColor = s.textColor?.let { Color(it) },
        fontSize = s.fontSizePercent / 100.0,
        publisherStyles = s.keepPublisherStyles,
        fontFamily = FontCss.familyFor(s.faFontId, s.latinFontId, availableFontIds)?.let { FontFamily(it) }
            ?: when (s.fontChoice) {
                FontChoice.ORIGINAL -> null
                FontChoice.SERIF -> FontFamily.SERIF
                FontChoice.SANS_SERIF -> FontFamily.SANS_SERIF
            },
        lineHeight = s.lineHeight,
        paragraphSpacing = s.paragraphSpacing,
        paragraphIndent = s.paragraphIndent,
        textAlign = when (s.align) {
            AlignMode.ORIGINAL -> null
            AlignMode.RIGHT -> TextAlign.RIGHT
            AlignMode.CENTER -> TextAlign.CENTER
            AlignMode.LEFT -> TextAlign.LEFT
        },
        pageMargins = s.pageMargins,
        scroll = s.scroll,
        readingProgression = when (s.direction) {
            DirectionMode.AUTO -> null
            DirectionMode.LTR -> ReadingProgression.LTR
            DirectionMode.RTL -> ReadingProgression.RTL
        },
    )
}
