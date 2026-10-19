package com.novelreader.app.data

import kotlinx.coroutines.flow.Flow

/** Highlights, bookmarks, notes and reading-position history. Every row is scoped to one book id. */
class AnnotationRepository(private val db: AppDatabase) {

    // ---- Highlights ----
    fun highlights(bookId: Long): Flow<List<HighlightEntity>> = db.highlights().observe(bookId)

    suspend fun addHighlight(
        bookId: Long, locatorJson: String, text: String, color: Int, chapterTitle: String?,
    ): Long = db.highlights().insert(
        HighlightEntity(
            bookId = bookId, locatorJson = locatorJson, text = text, color = color,
            chapterTitle = chapterTitle, createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun highlight(id: Long): HighlightEntity? = db.highlights().getById(id)

    suspend fun updateHighlight(h: HighlightEntity) = db.highlights().update(h)
    suspend fun deleteHighlight(id: Long) = db.highlights().deleteById(id)

    // ---- Bookmarks ----
    fun bookmarks(bookId: Long): Flow<List<BookmarkEntity>> = db.bookmarks().observe(bookId)

    suspend fun addBookmark(
        bookId: Long, locatorJson: String, title: String?, chapterTitle: String?, progress: Double,
    ): Long = db.bookmarks().insert(
        BookmarkEntity(
            bookId = bookId, locatorJson = locatorJson, title = title?.ifBlank { null },
            chapterTitle = chapterTitle, progress = progress, createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun updateBookmark(b: BookmarkEntity) = db.bookmarks().update(b)
    suspend fun deleteBookmark(id: Long) = db.bookmarks().deleteById(id)

    // ---- Position + history ----
    suspend fun savePosition(bookId: Long, progress: Double, locatorJson: String) =
        db.books().updatePosition(bookId, progress, locatorJson, System.currentTimeMillis())

    fun history(bookId: Long, limit: Int = 30): Flow<List<HistoryEntity>> = db.history().observeRecent(bookId, limit)

    /** Appends a history entry unless the newest one already points at the same chapter. */
    suspend fun addHistory(bookId: Long, locatorJson: String, chapterTitle: String?, progress: Double) {
        val last = db.history().latest(bookId)
        if (last != null && last.chapterTitle == chapterTitle && chapterTitle != null) return
        db.history().insert(
            HistoryEntity(
                bookId = bookId, locatorJson = locatorJson, chapterTitle = chapterTitle,
                progress = progress, createdAt = System.currentTimeMillis(),
            )
        )
        db.history().trim(bookId, 50)
    }
}
