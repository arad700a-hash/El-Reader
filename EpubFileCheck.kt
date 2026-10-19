package com.novelreader.app.core

/** Cheap, pure pre-checks before handing a file to the EPUB engine. */
object EpubFileCheck {

    /** ZIP local-file-header magic: "PK\u0003\u0004" (EPUB is a ZIP container). */
    fun hasZipMagic(firstBytes: ByteArray): Boolean =
        firstBytes.size >= 4 &&
            firstBytes[0] == 0x50.toByte() && firstBytes[1] == 0x4B.toByte() &&
            firstBytes[2] == 0x03.toByte() && firstBytes[3] == 0x04.toByte()

    /** True if [name] ends with ".epub" (case-insensitive). Display name only; never trusted alone. */
    fun hasEpubExtension(name: String?): Boolean = name?.trim()?.lowercase()?.endsWith(".epub") == true

    /** MIME types commonly reported for EPUB files by file managers/apps. */
    val epubMimeTypes: List<String> = listOf(
        "application/epub+zip",
        "application/epub",
        "application/x-epub+zip",
        "application/octet-stream",
    )

    /** Strips path separators and control chars so a display name is safe to show/store. */
    fun safeDisplayName(raw: String?): String {
        val base = (raw ?: "book.epub").substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.filter { !it.isISOControl() }.trim()
        return cleaned.ifEmpty { "book.epub" }
    }
}
