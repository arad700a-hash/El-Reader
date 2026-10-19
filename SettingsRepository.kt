package com.novelreader.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [ReaderSettings], the imported-font registry and library UI preferences in DataStore. */
class SettingsRepository(private val context: Context) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val fontSize = intPreferencesKey("font_size_pct")
        val fontChoice = stringPreferencesKey("font_choice")
        val faFont = stringPreferencesKey("fa_font_id")
        val latinFont = stringPreferencesKey("latin_font_id")
        val customFonts = stringPreferencesKey("custom_fonts_json")
        val keepPublisher = booleanPreferencesKey("keep_publisher_styles")
        val lineHeight = doublePreferencesKey("line_height")
        val paraSpacing = doublePreferencesKey("paragraph_spacing")
        val paraIndent = doublePreferencesKey("paragraph_indent")
        val align = stringPreferencesKey("align")
        val margins = doublePreferencesKey("page_margins")
        val textColor = intPreferencesKey("text_color")
        val bgColor = intPreferencesKey("background_color")
        val bgImage = stringPreferencesKey("bg_image_path")
        val bgImageMode = stringPreferencesKey("bg_image_mode")
        val bgImageOpacity = floatPreferencesKey("bg_image_opacity")
        val scroll = booleanPreferencesKey("scroll")
        val direction = stringPreferencesKey("direction")
        val fullscreen = booleanPreferencesKey("fullscreen")

        val librarySort = stringPreferencesKey("library_sort")
        val libraryGrid = booleanPreferencesKey("library_grid")
    }

    private val store get() = context.settingsStore

    private val safeData: Flow<Preferences> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    /** Single place that turns stored preferences into a (range-clamped) [ReaderSettings]. */
    private fun Preferences.toSettings(): ReaderSettings {
        val p = this
        val d = ReaderSettings()
        return ReaderSettings(
            theme = p.enum(K.theme, d.theme),
            fontSizePercent = (p[K.fontSize] ?: d.fontSizePercent).coerceIn(ReaderSettings.FONT_SIZE_RANGE),
            fontChoice = p.enum(K.fontChoice, d.fontChoice),
            faFontId = p[K.faFont],
            latinFontId = p[K.latinFont],
            keepPublisherStyles = p[K.keepPublisher] ?: d.keepPublisherStyles,
            lineHeight = (p[K.lineHeight] ?: d.lineHeight).coerceIn(ReaderSettings.LINE_HEIGHT_RANGE),
            paragraphSpacing = (p[K.paraSpacing] ?: d.paragraphSpacing).coerceIn(ReaderSettings.PARAGRAPH_SPACING_RANGE),
            paragraphIndent = (p[K.paraIndent] ?: d.paragraphIndent).coerceIn(ReaderSettings.PARAGRAPH_INDENT_RANGE),
            align = p.enum(K.align, d.align),
            pageMargins = (p[K.margins] ?: d.pageMargins).coerceIn(ReaderSettings.PAGE_MARGINS_RANGE),
            textColor = p[K.textColor],
            backgroundColor = p[K.bgColor],
            backgroundImagePath = p[K.bgImage],
            backgroundImageMode = p.enum(K.bgImageMode, d.backgroundImageMode),
            backgroundImageOpacity = (p[K.bgImageOpacity] ?: d.backgroundImageOpacity).coerceIn(0f, 1f),
            scroll = p[K.scroll] ?: d.scroll,
            direction = p.enum(K.direction, d.direction),
            fullscreen = p[K.fullscreen] ?: d.fullscreen,
        )
    }

    val readerSettings: Flow<ReaderSettings> = safeData.map { it.toSettings() }

    /** Applies [transform] to the current settings and persists the result (null values remove their key). */
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { p ->
            val s = transform(p.toSettings())
            p[K.theme] = s.theme.name
            p[K.fontSize] = s.fontSizePercent.coerceIn(ReaderSettings.FONT_SIZE_RANGE)
            p[K.fontChoice] = s.fontChoice.name
            if (s.faFontId != null) p[K.faFont] = s.faFontId else p.remove(K.faFont)
            if (s.latinFontId != null) p[K.latinFont] = s.latinFontId else p.remove(K.latinFont)
            p[K.keepPublisher] = s.keepPublisherStyles
            p[K.lineHeight] = s.lineHeight.coerceIn(ReaderSettings.LINE_HEIGHT_RANGE)
            p[K.paraSpacing] = s.paragraphSpacing.coerceIn(ReaderSettings.PARAGRAPH_SPACING_RANGE)
            p[K.paraIndent] = s.paragraphIndent.coerceIn(ReaderSettings.PARAGRAPH_INDENT_RANGE)
            p[K.align] = s.align.name
            p[K.margins] = s.pageMargins.coerceIn(ReaderSettings.PAGE_MARGINS_RANGE)
            if (s.textColor != null) p[K.textColor] = s.textColor else p.remove(K.textColor)
            if (s.backgroundColor != null) p[K.bgColor] = s.backgroundColor else p.remove(K.bgColor)
            if (s.backgroundImagePath != null) p[K.bgImage] = s.backgroundImagePath else p.remove(K.bgImage)
            p[K.bgImageMode] = s.backgroundImageMode.name
            p[K.bgImageOpacity] = s.backgroundImageOpacity.coerceIn(0f, 1f)
            p[K.scroll] = s.scroll
            p[K.direction] = s.direction.name
            p[K.fullscreen] = s.fullscreen
        }
    }

    // ---- Imported fonts registry ----

    val customFonts: Flow<List<CustomFont>> = safeData.map { CustomFont.listFromJson(it[K.customFonts]) }

    suspend fun currentCustomFonts(): List<CustomFont> = customFonts.first()

    /**
     * Atomically rewrites the font registry. [transform] receives the current list; if it removes a font that
     * is selected, the selection is cleared in the same edit so settings never point at a missing font.
     */
    suspend fun updateCustomFonts(transform: (List<CustomFont>) -> List<CustomFont>) {
        store.edit { p ->
            val next = transform(CustomFont.listFromJson(p[K.customFonts]))
            p[K.customFonts] = CustomFont.listToJson(next)
            val ids = next.map { it.id }.toSet()
            p[K.faFont]?.let { if (it !in ids) p.remove(K.faFont) }
            p[K.latinFont]?.let { if (it !in ids) p.remove(K.latinFont) }
        }
    }

    // ---- Library UI preferences ----

    val librarySort: Flow<LibrarySort> = safeData.map { it.enum(K.librarySort, LibrarySort.LAST_READ) }
    val libraryGrid: Flow<Boolean> = safeData.map { it[K.libraryGrid] ?: true }

    suspend fun setLibrarySort(sort: LibrarySort) = store.edit { it[K.librarySort] = sort.name }
    suspend fun setLibraryGrid(grid: Boolean) = store.edit { it[K.libraryGrid] = grid }
}
