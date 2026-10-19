package com.novelreader.app.reader

import com.novelreader.app.data.BookEntity
import com.novelreader.app.data.BookmarkEntity
import com.novelreader.app.data.HighlightEntity
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** One table-of-contents entry taken from the EPUB itself (nothing is synthesised). */
data class TocItem(
    val title: String,
    /** Resource href without fragment, comparable with position hrefs. */
    val href: String,
    val link: Link,
    val depth: Int,
    val children: List<TocItem>,
)

sealed interface ReaderLoad {
    data object Loading : ReaderLoad
    data class Failed(val reason: ReaderFailure, val detail: String?) : ReaderLoad
    /** [fontIds] = imported fonts that were declared in this open publication (see FontInjection). */
    data class Ready(
        val book: BookEntity,
        val publication: Publication,
        val initialLocator: Locator?,
        val fontIds: Set<String> = emptySet(),
    ) : ReaderLoad
}

enum class ReaderFailure { BOOK_NOT_FOUND, FILE_MISSING, NOT_EPUB, ENCRYPTED, CORRUPT, UNSUPPORTED }

/** Live reading position shown in the progress bar / panels. Pages are Readium "positions" (approximate pages). */
data class ReaderInfo(
    val position: Int = 0,
    val totalPositions: Int = 0,
    val percent: Int = 0,
    val fraction: Double = 0.0,
    val chapterTitle: String? = null,
    val currentTocIndex: Int = -1,
) {
    val remaining: Int get() = if (totalPositions <= 0) 0 else (totalPositions - position).coerceAtLeast(0)
}

/** One in-book search hit. */
data class SearchHit(
    val locator: Locator,
    val before: String,
    val match: String,
    val after: String,
    val chapter: String?,
)

data class SearchState(
    val query: String = "",
    val running: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val activeIndex: Int = -1,
    val truncated: Boolean = false,
    val unsupported: Boolean = false,
    val failed: Boolean = false,
    val done: Boolean = false,
)

/** Navigation requests emitted by Compose / view-model and executed against the navigator by the activity. */
sealed interface ReaderCommand {
    data class GoLocator(val locator: Locator) : ReaderCommand
    data class GoLink(val link: Link) : ReaderCommand
    data object Next : ReaderCommand
    data object Previous : ReaderCommand
}

/** Modal interactions rendered by Compose. */
sealed interface ReaderDialog {
    data class NewHighlight(val locatorJson: String, val text: String, val chapter: String?) : ReaderDialog
    data class EditHighlight(val highlight: HighlightEntity) : ReaderDialog
    data class Translate(val text: String) : ReaderDialog
    data class ExternalLink(val url: String) : ReaderDialog
    data object AddBookmark : ReaderDialog
    data class EditBookmark(val bookmark: BookmarkEntity) : ReaderDialog
}

/** One-shot events from the view-model to the activity / UI. */
sealed interface ReaderEvent {
    /** A string resource to show briefly (optionally formatted with [arg]). */
    data class Message(val resId: Int, val arg: String? = null) : ReaderEvent

    /** The book must be reopened (e.g. a newly imported font has to be declared in the publication). */
    data object Reopen : ReaderEvent
}

/** Tabs of the reading tools panel. */
enum class ReaderTab { TEXT, LAYOUT, COLORS, SCROLL, TOC, SEARCH, MARKS, INFO }
