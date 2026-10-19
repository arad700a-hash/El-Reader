package com.novelreader.app.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.app.R
import com.novelreader.app.core.TextNormalizer
import com.novelreader.app.data.BookEntity
import com.novelreader.app.data.ImportError
import com.novelreader.app.data.ImportResult
import com.novelreader.app.data.LibrarySort
import com.novelreader.app.graph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryEvent {
    data class OpenBook(val bookId: Long) : LibraryEvent
    data class Message(val resId: Int, val arg: String? = null) : LibraryEvent
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.graph

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    val sort: StateFlow<LibrarySort> = graph.settings.librarySort
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySort.LAST_READ)
    val grid: StateFlow<Boolean> = graph.settings.libraryGrid
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    /** All library books (unfiltered) - used to tell "empty library" apart from "no search results". */
    val total: StateFlow<Int> = graph.library.observeLibrary()
        .let { flow -> combine(flow, _loaded) { list, _ -> list.size } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Filtered + sorted books for display. */
    val books: StateFlow<List<BookEntity>> =
        combine(graph.library.observeLibrary(), query, sort) { list, q, s ->
            _loaded.value = true
            list.filter { TextNormalizer.containsNormalized(it.title, q) || TextNormalizer.containsNormalized(it.author, q) }
                .let { sorted(it, s) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun sorted(list: List<BookEntity>, s: LibrarySort): List<BookEntity> = when (s) {
        LibrarySort.LAST_READ -> list.sortedWith(
            compareByDescending<BookEntity> { it.lastReadAt ?: Long.MIN_VALUE }.thenByDescending { it.addedAt }
        )
        LibrarySort.TITLE -> list.sortedBy { TextNormalizer.normalize(it.title) }
        LibrarySort.ADDED -> list.sortedByDescending { it.addedAt }
    }

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: LibrarySort) { viewModelScope.launch { graph.settings.setLibrarySort(s) } }
    fun setGrid(g: Boolean) { viewModelScope.launch { graph.settings.setLibraryGrid(g) } }

    /** Books picked in the system file picker: copied into private storage, validated, added to the library. */
    fun importPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            try {
                var added = 0
                for (uri in uris) {
                    when (val r = graph.library.importFromUri(uri, addToLibrary = true)) {
                        is ImportResult.Success -> if (r.alreadyPresent) {
                            _events.tryEmit(LibraryEvent.Message(R.string.import_exists))
                        } else added++
                        is ImportResult.Failure -> _events.tryEmit(LibraryEvent.Message(errorRes(r.error)))
                    }
                }
                if (added > 0) _events.tryEmit(LibraryEvent.Message(R.string.import_ok, added.toString()))
            } finally {
                _importing.value = false
            }
        }
    }

    /**
     * A file handed over by another app ("Open with" / "Share"). It is copied privately (so access cannot be
     * revoked), kept out of the library until the user confirms in the reader, and opened right away.
     * A book that is already in the library is simply opened.
     */
    fun openExternal(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                when (val r = graph.library.importFromUri(uri, addToLibrary = false)) {
                    is ImportResult.Success -> _events.tryEmit(LibraryEvent.OpenBook(r.bookId))
                    is ImportResult.Failure -> _events.tryEmit(LibraryEvent.Message(errorRes(r.error)))
                }
            } finally {
                _importing.value = false
            }
        }
    }

    /** Hides the book from the library but keeps the private copy, highlights, bookmarks and position. */
    fun removeFromLibrary(id: Long) { viewModelScope.launch { graph.library.removeFromLibrary(id) } }

    /** Deletes the private copy and every annotation. The user's original file is never touched. */
    fun deleteCompletely(id: Long) { viewModelScope.launch { graph.library.deleteCompletely(id) } }

    fun coverFile(book: BookEntity) = graph.library.coverFile(book)

    private fun errorRes(e: ImportError): Int = when (e) {
        ImportError.NO_ACCESS -> R.string.import_err_no_access
        ImportError.NOT_EPUB -> R.string.import_err_not_epub
        ImportError.CORRUPT -> R.string.import_err_corrupt
        ImportError.ENCRYPTED -> R.string.import_err_encrypted
        ImportError.UNSUPPORTED -> R.string.import_err_unsupported
        ImportError.IO -> R.string.import_err_io
    }
}
