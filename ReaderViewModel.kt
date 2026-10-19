package com.novelreader.app.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.novelreader.app.R
import com.novelreader.app.core.ReadingMath
import com.novelreader.app.data.BgImageResult
import com.novelreader.app.data.BgImageMode
import com.novelreader.app.data.BookmarkEntity
import com.novelreader.app.data.CustomFont
import com.novelreader.app.data.FontImportError
import com.novelreader.app.data.FontImportResult
import com.novelreader.app.data.HighlightEntity
import com.novelreader.app.data.HistoryEntity
import com.novelreader.app.data.ReaderSettings
import com.novelreader.app.graph
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse

/**
 * Owns everything about one open book except the navigator fragment itself (which lives in the activity):
 * the Readium [Publication], table of contents, positions, progress, search, annotations and persistence.
 */
@OptIn(FlowPreview::class)
class ReaderViewModel(app: Application, private val bookId: Long) : AndroidViewModel(app) {

    private val graph = app.graph

    private val _load = MutableStateFlow<ReaderLoad>(ReaderLoad.Loading)
    val load: StateFlow<ReaderLoad> = _load.asStateFlow()

    val settings: StateFlow<ReaderSettings> = graph.settings.readerSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    // ---- Imported fonts ----
    /** Live registry (changes immediately on import/delete). */
    val customFonts: StateFlow<List<CustomFont>> = graph.fonts.fonts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val fontIdsAtOpen = MutableStateFlow<Set<String>>(emptySet())

    /** Imported fonts that exist now AND were declared when this publication was opened. */
    val availableFontIds: StateFlow<Set<String>> = combine(customFonts, fontIdsAtOpen) { live, atOpen ->
        live.map { it.id }.toSet().intersect(atOpen)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    // ---- Table of contents / positions ----
    private val _toc = MutableStateFlow<List<TocItem>>(emptyList())
    val toc: StateFlow<List<TocItem>> = _toc.asStateFlow()
    private var flatToc: List<TocItem> = emptyList()
    private var tocStartIndex: IntArray = IntArray(0)

    private var positions: List<Locator> = emptyList()
    private val _chapterMarkers = MutableStateFlow<List<Double>>(emptyList())
    val chapterMarkers: StateFlow<List<Double>> = _chapterMarkers.asStateFlow()

    // ---- Live reading info ----
    private val _info = MutableStateFlow(ReaderInfo())
    val info: StateFlow<ReaderInfo> = _info.asStateFlow()
    private var latestLocator: Locator? = null
    private val saveTrigger = MutableSharedFlow<Locator>(extraBufferCapacity = 1)
    private var lastHistoryChapter: String? = null

    // ---- Chrome / dialogs / commands ----
    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _tab = MutableStateFlow(ReaderTab.TEXT)
    val tab: StateFlow<ReaderTab> = _tab.asStateFlow()

    private val _dialog = MutableStateFlow<ReaderDialog?>(null)
    val dialog: StateFlow<ReaderDialog?> = _dialog.asStateFlow()

    private val _commands = MutableSharedFlow<ReaderCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<ReaderCommand> = _commands.asSharedFlow()

    // ---- Annotations ----
    val highlights: StateFlow<List<HighlightEntity>> = graph.annotations.highlights(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = graph.annotations.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history: StateFlow<List<HistoryEntity>> = graph.annotations.history(bookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _inLibrary = MutableStateFlow(true)
    val inLibrary: StateFlow<Boolean> = _inLibrary.asStateFlow()

    // ---- Search ----
    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { open() }
        viewModelScope.launch {
            saveTrigger.debounce(1_500).collect { persist(it) }
        }
    }

    // =====================================================================================
    // Opening
    // =====================================================================================

    private suspend fun open() {
        val book = graph.library.get(bookId)
        if (book == null) {
            _load.value = ReaderLoad.Failed(ReaderFailure.BOOK_NOT_FOUND, null); return
        }
        _inLibrary.value = book.inLibrary
        val file = graph.library.bookFile(book)
        if (!file.exists()) {
            _load.value = ReaderLoad.Failed(ReaderFailure.FILE_MISSING, null); return
        }
        val fonts = graph.settings.currentCustomFonts()
        when (val r = graph.engine.open(file, fonts, graph.fonts.directory)) {
            is EngineResult.Failed -> _load.value = ReaderLoad.Failed(
                when (r.reason) {
                    EpubFailure.NOT_EPUB -> ReaderFailure.NOT_EPUB
                    EpubFailure.ENCRYPTED -> ReaderFailure.ENCRYPTED
                    EpubFailure.CORRUPT -> ReaderFailure.CORRUPT
                    EpubFailure.UNSUPPORTED -> ReaderFailure.UNSUPPORTED
                },
                r.detail,
            )
            is EngineResult.Ok -> {
                val pub = r.value
                buildToc(pub)
                // Positions power the progress bar; computed once, off the UI path (suspend).
                positions = try { pub.positions() } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    emptyList()
                }
                computeChapterMarkers()
                val initial = book.lastLocatorJson?.let { json ->
                    try { Locator.fromJSON(JSONObject(json)) } catch (_: Throwable) { null }
                }
                initial?.let { onLocatorChanged(it, persist = false) }
                graph.library.touch(bookId) // keeps "last read" ordering right even before the first page turn
                val declared = fonts.map { it.id }.toSet()
                fontIdsAtOpen.value = declared
                _load.value = ReaderLoad.Ready(book, pub, initial, declared)
            }
        }
    }

    private fun buildToc(pub: Publication) {
        val untitled = getApplication<Application>().getString(R.string.untitled_chapter)
        fun map(links: List<Link>, depth: Int): List<TocItem> = links.map { l ->
            TocItem(
                title = l.title?.takeIf { it.isNotBlank() } ?: untitled,
                href = ReadingMath.stripFragment(l.url().toString()),
                link = l,
                depth = depth,
                children = map(l.children, depth + 1),
            )
        }
        val tree = map(pub.tableOfContents, 0)
        _toc.value = tree
        val flat = ArrayList<TocItem>()
        fun walk(items: List<TocItem>) { items.forEach { flat += it; walk(it.children) } }
        walk(tree)
        flatToc = flat
    }

    private fun computeChapterMarkers() {
        val posHrefs = positions.map { ReadingMath.stripFragment(it.href.toString()) }
        val firstIndex = HashMap<String, Int>()
        posHrefs.forEachIndexed { i, h -> firstIndex.putIfAbsent(h, i) }
        tocStartIndex = IntArray(flatToc.size) { firstIndex[flatToc[it].href] ?: -1 }
        _chapterMarkers.value = ReadingMath.chapterMarkers(posHrefs, flatToc.map { it.href })
    }

    // =====================================================================================
    // Position tracking
    // =====================================================================================

    /** Called by the activity for every navigator location change. */
    fun onLocatorChanged(locator: Locator, persist: Boolean = true) {
        latestLocator = locator
        val total = positions.size
        val pos = locator.locations.position
        val fraction = ReadingMath.clamp01(
            locator.locations.totalProgression
                ?: if (total > 0 && pos != null) (pos - 1).toDouble() / total else 0.0
        )
        val tocIdx = currentTocIndex(pos)
        val chapter = if (tocIdx >= 0) flatToc[tocIdx].title else locator.title?.takeIf { it.isNotBlank() }
        _info.value = ReaderInfo(
            position = ReadingMath.currentPosition(pos, total),
            totalPositions = total,
            percent = ReadingMath.percent(fraction),
            fraction = fraction,
            chapterTitle = chapter,
            currentTocIndex = tocIdx,
        )
        if (persist) {
            saveTrigger.tryEmit(locator)
            if (chapter != null && chapter != lastHistoryChapter) {
                lastHistoryChapter = chapter
                viewModelScope.launch {
                    graph.annotations.addHistory(bookId, locator.toJSON().toString(), chapter, fraction)
                }
            }
        }
    }

    private fun currentTocIndex(position: Int?): Int {
        if (flatToc.isEmpty() || position == null) return -1
        val p = position - 1
        var best = -1
        var bestStart = -1
        for (i in flatToc.indices) {
            val s = tocStartIndex.getOrElse(i) { -1 }
            if (s in 0..p && s >= bestStart) { best = i; bestStart = s }
        }
        return best
    }

    /** Writes the newest position immediately (called when the reader goes to the background / closes). */
    fun flushPosition() {
        val l = latestLocator ?: return
        viewModelScope.launch { persist(l) }
    }

    private suspend fun persist(locator: Locator) {
        val fraction = ReadingMath.clamp01(locator.locations.totalProgression ?: _info.value.fraction)
        graph.annotations.savePosition(bookId, fraction, locator.toJSON().toString())
    }

    // =====================================================================================
    // Chrome, dialogs, navigation
    // =====================================================================================

    fun setChromeVisible(v: Boolean) { _chromeVisible.value = v }
    fun toggleChrome() { _chromeVisible.value = !_chromeVisible.value }
    fun setSettingsOpen(v: Boolean) { _settingsOpen.value = v }
    fun openTab(tab: ReaderTab) { _tab.value = tab }
    fun showDialog(d: ReaderDialog?) { _dialog.value = d }

    fun go(locator: Locator) { _commands.tryEmit(ReaderCommand.GoLocator(locator)) }
    fun go(link: Link) { _commands.tryEmit(ReaderCommand.GoLink(link)) }
    fun next() { _commands.tryEmit(ReaderCommand.Next) }
    fun previous() { _commands.tryEmit(ReaderCommand.Previous) }

    /** Jumps to the start of approximate page [index] (0-based) from the progress bar. */
    fun goToPositionIndex(index: Int) {
        positions.getOrNull(index)?.let { go(it) }
    }

    fun goToFraction(fraction: Double) {
        val idx = ReadingMath.indexFromFraction(fraction, positions.size)
        if (idx >= 0) goToPositionIndex(idx)
    }

    fun goToLocatorJson(json: String) {
        try { Locator.fromJSON(JSONObject(json))?.let { go(it) } } catch (_: Throwable) { /* ignore corrupt row */ }
    }

    fun continueFromSaved() {
        val json = (_load.value as? ReaderLoad.Ready)?.book?.lastLocatorJson ?: return
        goToLocatorJson(json)
    }

    fun chapterTitleFor(locator: Locator): String? {
        val idx = currentTocIndex(locator.locations.position)
        return if (idx >= 0) flatToc[idx].title else locator.title?.takeIf { it.isNotBlank() }
    }

    // =====================================================================================
    // Settings
    // =====================================================================================

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { graph.settings.update(transform) }
    }

    // =====================================================================================
    // Fonts and background image
    // =====================================================================================

    /** Selects imported fonts for Persian (and other Arabic-script) text and for everything else. null = default. */
    fun selectFonts(faId: String?, latinId: String?) {
        updateSettings { it.copy(faFontId = faId, latinFontId = latinId) }
    }

    /**
     * Imports a TTF/OTF chosen in the system picker. A new font must be declared in the publication's resources, so
     * after a successful new import the position is saved and the reader is reopened (see [ReaderEvent.Reopen]).
     */
    fun importFont(uri: Uri) {
        viewModelScope.launch {
            when (val r = graph.fonts.import(uri)) {
                is FontImportResult.Failure -> _events.tryEmit(ReaderEvent.Message(fontErrorRes(r.error)))
                is FontImportResult.Success -> {
                    val font = r.font
                    // Select it for the scripts it can actually render.
                    graph.settings.update {
                        it.copy(
                            faFontId = if (font.coversPersian) font.id else it.faFontId,
                            latinFontId = if (font.coversLatin) font.id else it.latinFontId,
                        )
                    }
                    if (!font.coversPersian) _events.tryEmit(ReaderEvent.Message(R.string.font_no_persian, font.displayName))
                    if (font.id in fontIdsAtOpen.value) {
                        _events.tryEmit(ReaderEvent.Message(R.string.font_applied, font.displayName))
                    } else {
                        latestLocator?.let { persist(it) }
                        _events.tryEmit(ReaderEvent.Message(R.string.font_imported_reopening, font.displayName))
                        _events.tryEmit(ReaderEvent.Reopen)
                    }
                }
            }
        }
    }

    fun deleteFont(id: String) {
        viewModelScope.launch {
            graph.fonts.delete(id)
            _events.tryEmit(ReaderEvent.Message(R.string.font_deleted))
        }
    }

    fun resetFonts() {
        updateSettings { it.copy(faFontId = null, latinFontId = null, fontChoice = com.novelreader.app.data.FontChoice.ORIGINAL) }
    }

    private fun fontErrorRes(e: FontImportError): Int = when (e) {
        FontImportError.NO_ACCESS -> R.string.font_err_no_access
        FontImportError.EMPTY, FontImportError.NOT_A_FONT, FontImportError.CORRUPT -> R.string.font_err_invalid
        FontImportError.TOO_LARGE -> R.string.font_err_too_large
        FontImportError.COLLECTION -> R.string.font_err_collection
        FontImportError.WEB_FONT -> R.string.font_err_webfont
        FontImportError.LIMIT_REACHED -> R.string.font_err_limit
        FontImportError.IO -> R.string.font_err_io
    }

    fun importBackground(uri: Uri) {
        viewModelScope.launch {
            val previous = settings.value.backgroundImagePath
            when (val r = graph.backgrounds.import(uri)) {
                is BgImageResult.Ok -> {
                    updateSettings { it.copy(backgroundImagePath = r.path) }
                    graph.backgrounds.delete(previous)
                }
                BgImageResult.Invalid -> _events.tryEmit(ReaderEvent.Message(R.string.bg_err_invalid))
                BgImageResult.TooLarge -> _events.tryEmit(ReaderEvent.Message(R.string.bg_err_too_large))
                BgImageResult.IoError -> _events.tryEmit(ReaderEvent.Message(R.string.bg_err_io))
            }
        }
    }

    fun removeBackgroundImage() {
        val previous = settings.value.backgroundImagePath
        updateSettings { it.copy(backgroundImagePath = null) }
        graph.backgrounds.delete(previous)
    }

    // =====================================================================================
    // Annotations
    // =====================================================================================

    fun createHighlight(json: String, text: String, color: Int, note: String?, chapter: String?) {
        viewModelScope.launch {
            val id = graph.annotations.addHighlight(bookId, json, text, color, chapter)
            if (!note.isNullOrBlank()) {
                graph.annotations.highlight(id)?.let { graph.annotations.updateHighlight(it.copy(note = note.trim())) }
            }
        }
    }

    fun updateHighlight(h: HighlightEntity) { viewModelScope.launch { graph.annotations.updateHighlight(h) } }
    fun deleteHighlight(id: Long) { viewModelScope.launch { graph.annotations.deleteHighlight(id) } }

    fun openHighlight(id: Long) {
        viewModelScope.launch {
            graph.annotations.highlight(id)?.let { _dialog.value = ReaderDialog.EditHighlight(it) }
        }
    }

    fun addBookmark(title: String?) {
        val l = latestLocator ?: return
        viewModelScope.launch {
            graph.annotations.addBookmark(
                bookId, l.toJSON().toString(), title, _info.value.chapterTitle, _info.value.fraction,
            )
        }
    }

    fun updateBookmark(b: BookmarkEntity) { viewModelScope.launch { graph.annotations.updateBookmark(b) } }
    fun deleteBookmark(id: Long) { viewModelScope.launch { graph.annotations.deleteBookmark(id) } }

    fun addToLibrary() {
        viewModelScope.launch {
            graph.library.addToLibrary(bookId)
            _inLibrary.value = true
        }
    }

    // =====================================================================================
    // In-book search (Readium search service over the real EPUB text)
    // =====================================================================================

    fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        if (query.isEmpty()) { clearSearch(); return }
        val pub = (_load.value as? ReaderLoad.Ready)?.publication ?: return
        _search.value = SearchState(query = query, running = true)
        searchJob = viewModelScope.launch {
            val hits = ArrayList<SearchHit>()
            var truncated = false
            try {
                val iterator = pub.search(query)
                if (iterator == null) {
                    _search.value = SearchState(query = query, unsupported = true, done = true); return@launch
                }
                try {
                    var failed = false
                    loop@ while (true) {
                        val collection = iterator.next().getOrElse { failed = true; null }
                        if (failed) break
                        if (collection == null) break
                        for (loc in collection.locators) {
                            hits += SearchHit(
                                locator = loc,
                                before = loc.text.before.orEmpty(),
                                match = loc.text.highlight.orEmpty(),
                                after = loc.text.after.orEmpty(),
                                chapter = chapterTitleFor(loc),
                            )
                            if (hits.size >= MAX_SEARCH_HITS) { truncated = true; break@loop }
                        }
                        _search.value = _search.value.copy(hits = hits.toList())
                    }
                    _search.value = SearchState(
                        query = query, hits = hits.toList(), truncated = truncated, failed = failed && hits.isEmpty(),
                        activeIndex = -1, done = true,
                    )
                } finally {
                    iterator.close()
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _search.value = SearchState(query = query, hits = hits.toList(), failed = true, done = true)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    fun goToSearchHit(index: Int) {
        val hits = _search.value.hits
        if (index !in hits.indices) return
        _search.value = _search.value.copy(activeIndex = index)
        go(hits[index].locator)
    }

    fun nextSearchHit() {
        val s = _search.value
        if (s.hits.isEmpty()) return
        goToSearchHit((s.activeIndex + 1).mod(s.hits.size))
    }

    fun previousSearchHit() {
        val s = _search.value
        if (s.hits.isEmpty()) return
        goToSearchHit((s.activeIndex - 1).mod(s.hits.size))
    }

    override fun onCleared() {
        (_load.value as? ReaderLoad.Ready)?.publication?.close()
        super.onCleared()
    }

    companion object {
        const val MAX_SEARCH_HITS = 300

        fun factory(app: Application, bookId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(app, bookId) as T
        }
    }
}
