package com.novelreader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.novelreader.app.core.EpubFileCheck
import com.novelreader.app.core.FontCss
import com.novelreader.app.core.FontInfoParser
import com.novelreader.app.core.FontParseResult
import com.novelreader.app.core.FontReject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface FontImportResult {
    data class Success(val font: CustomFont, val alreadyPresent: Boolean) : FontImportResult
    data class Failure(val error: FontImportError) : FontImportResult
}

enum class FontImportError { NO_ACCESS, EMPTY, TOO_LARGE, NOT_A_FONT, COLLECTION, WEB_FONT, CORRUPT, LIMIT_REACHED, IO }

/**
 * Imports TTF/OTF files picked with the system document picker (ACTION_OPEN_DOCUMENT / SAF), validates them with
 * [FontInfoParser], stores a private copy in filesDir/fonts and records it in the DataStore registry.
 * The original file is only read. Files are served to the reader by [com.novelreader.app.reader.FontInjection].
 */
class FontRepository(private val context: Context, private val settings: SettingsRepository) {

    private val dir get() = File(context.filesDir, "fonts").apply { mkdirs() }

    /** Directory holding the private font copies (served to the reader by FontInjection). */
    val directory: File get() = dir

    val fonts: Flow<List<CustomFont>> get() = settings.customFonts

    fun file(font: CustomFont): File = File(context.filesDir, font.relativePath)

    suspend fun import(uri: Uri): FontImportResult = withContext(Dispatchers.IO) {
        val existing = settings.currentCustomFonts()
        try {
            val bytes = readLimited(uri) ?: return@withContext FontImportResult.Failure(FontImportError.TOO_LARGE)
            val info = when (val parsed = FontInfoParser.parse(bytes)) {
                is FontParseResult.Rejected -> return@withContext FontImportResult.Failure(parsed.reason.toError())
                is FontParseResult.Ok -> parsed.info
            }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            existing.firstOrNull { it.sha256 == sha }?.let {
                return@withContext FontImportResult.Success(it, alreadyPresent = true)
            }
            if (existing.size >= FontCss.MAX_FONTS) return@withContext FontImportResult.Failure(FontImportError.LIMIT_REACHED)

            val id = newId(existing.map { it.id }.toSet())
            val dest = File(dir, "$id.${info.extension}")
            dest.outputStream().use { it.write(bytes) }

            val shownName = listOfNotNull(info.family, info.subfamily?.takeIf { !it.equals("Regular", true) && !it.equals("Book", true) })
                .joinToString(" ")
            val font = CustomFont(
                id = id,
                displayName = shownName,
                family = info.family,
                extension = info.extension,
                weight = info.weight,
                italic = info.italic,
                variableMin = info.variableWeightRange?.first,
                variableMax = info.variableWeightRange?.last,
                coversPersian = info.coversPersian,
                coversLatin = info.coversLatin,
                sizeBytes = bytes.size.toLong(),
                sha256 = sha,
                originalName = EpubFileCheck.safeDisplayName(queryName(uri)),
            )
            settings.updateCustomFonts { it + font }
            FontImportResult.Success(font, alreadyPresent = false)
        } catch (e: SecurityException) {
            FontImportResult.Failure(FontImportError.NO_ACCESS)
        } catch (e: java.io.FileNotFoundException) {
            FontImportResult.Failure(FontImportError.NO_ACCESS)
        } catch (e: IOException) {
            FontImportResult.Failure(FontImportError.IO)
        }
    }

    /** Removes the font file and registry entry; any selection pointing at it is cleared atomically. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val font = settings.currentCustomFonts().firstOrNull { it.id == id } ?: return@withContext
        settings.updateCustomFonts { list -> list.filterNot { it.id == id } }
        file(font).delete()
    }

    /** Deletes font files that are not in the registry (e.g. left behind by a crash mid-import). */
    suspend fun sweepOrphans() = withContext(Dispatchers.IO) {
        val keep = settings.currentCustomFonts().map { File(context.filesDir, it.relativePath).name }.toSet()
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    /** Reads at most [FontInfoParser.MAX_FONT_BYTES]+1 bytes; null when the file is larger. */
    private fun readLimited(uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no stream")
        return input.use { ins ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > FontInfoParser.MAX_FONT_BYTES) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    private fun queryName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun newId(taken: Set<String>): String {
        val rnd = SecureRandom()
        while (true) {
            val id = ByteArray(4).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            if (id !in taken) return id
        }
    }

    private fun FontReject.toError() = when (this) {
        FontReject.EMPTY -> FontImportError.EMPTY
        FontReject.TOO_LARGE -> FontImportError.TOO_LARGE
        FontReject.NOT_A_FONT -> FontImportError.NOT_A_FONT
        FontReject.COLLECTION -> FontImportError.COLLECTION
        FontReject.WEB_FONT -> FontImportError.WEB_FONT
        FontReject.CORRUPT -> FontImportError.CORRUPT
    }
}
