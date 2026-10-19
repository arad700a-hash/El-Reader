package com.novelreader.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.novelreader.app.core.EpubFileCheck
import com.novelreader.app.reader.EngineResult
import com.novelreader.app.reader.EpubEngine
import com.novelreader.app.reader.EpubFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

sealed interface ImportResult {
    /** [alreadyPresent] = the same file (by content hash) was already stored; nothing was duplicated. */
    data class Success(val bookId: Long, val alreadyPresent: Boolean) : ImportResult
    data class Failure(val error: ImportError, val detail: String? = null) : ImportResult
}

enum class ImportError { NO_ACCESS, NOT_EPUB, CORRUPT, ENCRYPTED, UNSUPPORTED, IO }

class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val engine: EpubEngine,
) {
    private val booksDir get() = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir get() = File(context.filesDir, "covers").apply { mkdirs() }

    fun observeLibrary(): Flow<List<BookEntity>> = db.books().observeLibrary()
    suspend fun get(id: Long): BookEntity? = db.books().getById(id)

    fun bookFile(book: BookEntity): File = File(context.filesDir, book.filePath)

    /**
     * Copies the EPUB behind [uri] into private storage, validates it with the EPUB engine, extracts
     * title/author/cover and stores a library row. All I/O runs on Dispatchers.IO.
     *
     * [addToLibrary] = false stores the book hidden (used by "Open with" until the user adds it).
     * The source file is only ever read.
     */
    suspend fun importFromUri(uri: Uri, addToLibrary: Boolean): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = EpubFileCheck.safeDisplayName(queryDisplayName(uri) ?: uri.lastPathSegment)
        val tmp = File(booksDir, "tmp-${UUID.randomUUID()}")
        try {
            val sha = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val head = ByteArray(4)
            var headRead = 0
            val input = try {
                resolver.openInputStream(uri)
            } catch (e: SecurityException) {
                return@withContext ImportResult.Failure(ImportError.NO_ACCESS, e.message)
            } catch (e: java.io.FileNotFoundException) {
                return@withContext ImportResult.Failure(ImportError.NO_ACCESS, e.message)
            }
            if (input == null) return@withContext ImportResult.Failure(ImportError.NO_ACCESS, "no stream")

            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (headRead < 4) {
                            val take = minOf(4 - headRead, n)
                            System.arraycopy(buf, 0, head, headRead, take)
                            headRead += take
                        }
                        sha.update(buf, 0, n)
                        out.write(buf, 0, n)
                        size += n
                    }
                }
            }
            if (size == 0L || !EpubFileCheck.hasZipMagic(head)) {
                return@withContext ImportResult.Failure(ImportError.NOT_EPUB, "not a zip/epub container")
            }

            val hash = sha.digest().joinToString("") { "%02x".format(it) }
            db.books().getBySha(hash)?.let { existing ->
                if (addToLibrary && !existing.inLibrary) db.books().setInLibrary(existing.id, true)
                return@withContext ImportResult.Success(existing.id, alreadyPresent = true)
            }

            val finalFile = File(booksDir, "${hash.take(32)}.epub")
            if (finalFile.exists()) finalFile.delete()
            if (!tmp.renameTo(finalFile)) {
                tmp.copyTo(finalFile, overwrite = true)
                tmp.delete()
            }

            when (val opened = engine.open(finalFile)) {
                is EngineResult.Failed -> {
                    finalFile.delete()
                    ImportResult.Failure(opened.reason.toImportError(), opened.detail)
                }
                is EngineResult.Ok -> {
                    val pub = opened.value
                    try {
                        val title = pub.metadata.title?.takeIf { it.isNotBlank() }
                            ?: displayName.removeSuffix(".epub").removeSuffix(".EPUB")
                        val author = pub.metadata.authors.map { it.name }.filter { it.isNotBlank() }
                            .joinToString("، ").ifBlank { null }
                        var coverPath: String? = null
                        engine.coverOf(pub)?.let { bmp ->
                            val f = File(coversDir, "${hash.take(32)}.jpg")
                            if (saveCover(bmp, f)) coverPath = "covers/${f.name}"
                        }
                        val id = db.books().insert(
                            BookEntity(
                                title = title,
                                author = author,
                                fileName = displayName,
                                filePath = "books/${finalFile.name}",
                                coverPath = coverPath,
                                sizeBytes = size,
                                sha256 = hash,
                                addedAt = System.currentTimeMillis(),
                                inLibrary = addToLibrary,
                            )
                        )
                        ImportResult.Success(id, alreadyPresent = false)
                    } finally {
                        pub.close()
                    }
                }
            }
        } catch (e: IOException) {
            ImportResult.Failure(ImportError.IO, e.message)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Hides the book from the library; the private copy, highlights, bookmarks and position are kept. */
    suspend fun removeFromLibrary(id: Long) = db.books().setInLibrary(id, false)

    suspend fun addToLibrary(id: Long) = db.books().setInLibrary(id, true)

    suspend fun touch(id: Long) = db.books().touch(id, System.currentTimeMillis())

    /** Deletes the private copy, cover and every annotation of the book (rows cascade). The user's original file is untouched. */
    suspend fun deleteCompletely(id: Long) = withContext(Dispatchers.IO) {
        val book = db.books().getById(id) ?: return@withContext
        File(context.filesDir, book.filePath).delete()
        book.coverPath?.let { File(context.filesDir, it).delete() }
        db.books().deleteById(id)
    }

    fun coverFile(book: BookEntity): File? = book.coverPath?.let { File(context.filesDir, it) }?.takeIf { it.exists() }

    private fun queryDisplayName(uri: Uri): String? = try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } else uri.lastPathSegment
    } catch (_: Exception) {
        null
    }

    private fun saveCover(bmp: Bitmap, file: File): Boolean = try {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    } catch (_: IOException) {
        false
    }

    private fun EpubFailure.toImportError() = when (this) {
        EpubFailure.NOT_EPUB -> ImportError.NOT_EPUB
        EpubFailure.ENCRYPTED -> ImportError.ENCRYPTED
        EpubFailure.CORRUPT -> ImportError.CORRUPT
        EpubFailure.UNSUPPORTED -> ImportError.UNSUPPORTED
    }
}
