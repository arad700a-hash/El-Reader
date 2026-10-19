package com.novelreader.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE inLibrary = 1")
    fun observeLibrary(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE sha256 = :sha")
    suspend fun getBySha(sha: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("UPDATE books SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: Long, inLibrary: Boolean)

    @Query(
        "UPDATE books SET progress = :progress, lastLocatorJson = :locatorJson, lastReadAt = :readAt WHERE id = :id"
    )
    suspend fun updatePosition(id: Long, progress: Double, locatorJson: String, readAt: Long)

    @Query("UPDATE books SET lastReadAt = :readAt WHERE id = :id")
    suspend fun touch(id: Long, readAt: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observe(bookId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: Long): HighlightEntity?

    @Insert
    suspend fun insert(h: HighlightEntity): Long

    @Update
    suspend fun update(h: HighlightEntity)

    @Delete
    suspend fun delete(h: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress ASC, createdAt ASC")
    fun observe(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(b: BookmarkEntity): Long

    @Update
    suspend fun update(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(bookId: Long, limit: Int): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(h: HistoryEntity): Long

    @Query("SELECT * FROM history WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(bookId: Long): HistoryEntity?

    /** Keeps only the newest [keep] rows for a book. */
    @Query(
        "DELETE FROM history WHERE bookId = :bookId AND id NOT IN " +
            "(SELECT id FROM history WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT :keep)"
    )
    suspend fun trim(bookId: Long, keep: Int)
}
