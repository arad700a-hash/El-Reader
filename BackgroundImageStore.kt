package com.novelreader.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface BgImageResult {
    data class Ok(val path: String) : BgImageResult
    data object Invalid : BgImageResult
    data object TooLarge : BgImageResult
    data object IoError : BgImageResult
}

/**
 * Validates and stores a user-picked background image in app-private storage so access never depends on the
 * original location. Only PNG, JPEG and WebP that actually decode are accepted.
 */
class BackgroundImageStore(private val context: Context) {

    private val dir get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    suspend fun import(uri: Uri): BgImageResult = withContext(Dispatchers.IO) {
        val tmp = File(dir, "tmp-${UUID.randomUUID()}")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext BgImageResult.IoError
            var size = 0L
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > MAX_BYTES) return@withContext BgImageResult.TooLarge
                        out.write(buf, 0, n)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            val mime = bounds.outMimeType
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || mime !in ALLOWED_MIME) {
                return@withContext BgImageResult.Invalid
            }
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val dest = File(dir, "bg-${UUID.randomUUID()}.$ext")
            if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true) }
            BgImageResult.Ok(dest.absolutePath)
        } catch (e: SecurityException) {
            BgImageResult.IoError
        } catch (e: IOException) {
            BgImageResult.IoError
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Deletes a previously stored image (only files inside our own directory are ever touched). */
    fun delete(path: String?) {
        if (path == null) return
        val f = File(path)
        if (f.parentFile?.canonicalPath == dir.canonicalPath) f.delete()
    }

    /** Decodes a down-sampled bitmap (longest side <= [maxSide]) for drawing; null if the file vanished/corrupt. */
    suspend fun load(path: String, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        val ALLOWED_MIME = setOf("image/png", "image/jpeg", "image/webp")
    }
}
