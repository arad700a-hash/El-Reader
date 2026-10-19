package com.novelreader.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A book in the app's private store.
 *
 * The EPUB is always copied to app-private storage ([filePath], relative to filesDir) so access can never be
 * revoked by the file manager / sharing app. The user's original file is never modified or deleted.
 *
 * [inLibrary] = false hides the book from the library while keeping its private copy and annotations
 * (used for "remove from library, keep my data" and for books opened once via "Open with").
 */
@Entity(tableName = "books", indices = [Index(value = ["sha256"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val fileName: String,
    val filePath: String,
    val coverPath: String?,
    val sizeBytes: Long,
    val sha256: String,
    val addedAt: Long,
    val lastReadAt: Long? = null,
    /** Whole-book progression 0..1 as last reported by the navigator. */
    val progress: Double = 0.0,
    /** Readium Locator serialised as JSON; null until the book has been opened. */
    val lastLocatorJson: String? = null,
    val inLibrary: Boolean = true,
)

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    /** Readium Locator JSON (includes href, locations and the selected text) - stable across font/size changes. */
    val locatorJson: String,
    val text: String,
    /** ARGB colour. */
    val color: Int,
    val note: String? = null,
    val chapterTitle: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locatorJson: String,
    /** Optional user-given name. */
    val title: String? = null,
    val note: String? = null,
    val chapterTitle: String? = null,
    val progress: Double = 0.0,
    val createdAt: Long,
)

/** Trail of recent reading positions (one entry per chapter visit / exit). */
@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val locatorJson: String,
    val chapterTitle: String? = null,
    val progress: Double = 0.0,
    val createdAt: Long,
)
