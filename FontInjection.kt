package com.novelreader.app.reader

import com.novelreader.app.core.FontCss
import com.novelreader.app.core.HtmlStyleInjector
import com.novelreader.app.data.CustomFont
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.CompositeContainer
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.file.FileResource
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.TransformingResource
import java.io.File

/**
 * Makes imported fonts usable inside the EPUB renderer without changing the EPUB file and without touching
 * Readium's internals, using only public Readium 3.x APIs:
 *
 *  1. [FontContainer] - an extra `Container<Resource>` that serves the font files from private storage under
 *     `novelreader-fonts/<id>.<ext>`; combined with the book's own container by [CompositeContainer].
 *  2. A [TransformingContainer] entry transformer that injects `@font-face` rules (see [FontCss]) into every
 *     (X)HTML resource as it is read; the original bytes on disk are never modified.
 *  3. The font entries are also listed in `manifest.resources` so the publication "knows" them.
 *
 * The user's choice is then an ordinary `EpubPreferences.fontFamily` (see [ReaderPreferencesMapper]).
 *
 * Applied through `PublicationOpener.open(onCreatePublication = ...)`. It only exists in the reading path;
 * library import opens books without it.
 */
object FontInjection {

    /** Configures [builder] in place. No-op when [fonts] is empty (the book is then opened untouched). */
    fun install(builder: Publication.Builder, fonts: List<CustomFont>, fontsDir: File) {
        val usable = fonts.filter { FontCss.isValidId(it.id) && File(fontsDir, "${it.id}.${it.extension}").isFile }
            .take(FontCss.MAX_FONTS)
        if (usable.isEmpty()) return

        val specs = usable.map { it.toSpec() }
        val servedByPath = usable.associateBy { FontCss.servedPath(it.toSpec()) }

        val fontContainer = FontContainer(servedByPath, fontsDir)

        val injector: (Url, Resource) -> Resource = { url, resource ->
            val path = url.path
            if (HtmlStyleInjector.isHtmlPath(path)) {
                val css = FontCss.build(specs) { spec ->
                    FontCss.relativePath(path.orEmpty(), FontCss.servedPath(spec))
                }
                object : TransformingResource(resource) {
                    override suspend fun transform(data: Try<ByteArray, ReadError>): Try<ByteArray, ReadError> =
                        data.map { bytes -> HtmlStyleInjector.inject(bytes, css) }
                }
            } else {
                resource
            }
        }

        val original = builder.container
        builder.container = CompositeContainer<Resource>(
            listOf<Container<Resource>>(TransformingContainer(original, injector), fontContainer)
        )

        val fontLinks = fontContainer.entries.map { Link(it) }
        builder.manifest = builder.manifest.copy(resources = builder.manifest.resources + fontLinks)
    }
}

/** Serves imported font files. Reads lazily so opening a book never loads font bytes into memory. */
class FontContainer(
    private val fontsByPath: Map<String, CustomFont>,
    private val fontsDir: File,
) : Container<Resource> {

    private val urlToFont: Map<Url, CustomFont> = buildMap {
        for ((path, font) in fontsByPath) {
            Url.fromDecodedPath(path)?.let { put(it, font) }
        }
    }

    override val entries: Set<Url> = urlToFont.keys

    override fun get(url: Url): Resource? {
        val font = urlToFont[url.removeFragment().removeQuery()] ?: return null
        val file = File(fontsDir, "${font.id}.${font.extension}")
        if (!file.isFile) return null
        // FileResource reads the private copy lazily; the file is never loaded until the WebView requests it.
        return FileResource(file)
    }

    override fun close() {}
}
