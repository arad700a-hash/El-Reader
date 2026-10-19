package com.novelreader.app.reader

import android.content.Context
import android.graphics.Bitmap
import com.novelreader.app.data.CustomFont
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** Why an EPUB could not be opened; mapped to a user-facing message in the UI. */
enum class EpubFailure { NOT_EPUB, ENCRYPTED, CORRUPT, UNSUPPORTED }

sealed interface EngineResult<out T> {
    data class Ok<T>(val value: T) : EngineResult<T>
    data class Failed(val reason: EpubFailure, val detail: String?) : EngineResult<Nothing>
}

/**
 * Thin wrapper around the Readium Kotlin Toolkit (3.x) streamer: the single place that knows how to turn a
 * file into a [Publication]. Everything else in the app depends on this class, not on Readium's opener API.
 *
 * Readium is the EPUB engine (EPUB 2 + 3 parsing, reading order, TOC, positions, search, rendering via its
 * navigator). Content is rendered by Readium's own WebView-based navigator fragment - the only WebView in the
 * app; library, settings, chrome and all state are native Compose/Room/DataStore.
 */
class EpubEngine(context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val parser = DefaultPublicationParser(context.applicationContext, httpClient, assetRetriever, null)
    private val opener = PublicationOpener(parser)

    /**
     * Opens [file] (an app-private copy). Never throws for bad books; returns [EngineResult.Failed] instead.
     *
     * [fonts]/[fontsDir]: imported fonts to make available to the renderer (reading path only; see [FontInjection]).
     */
    suspend fun open(file: File, fonts: List<CustomFont> = emptyList(), fontsDir: File? = null): EngineResult<Publication> {
        return try {
            val asset = assetRetriever.retrieve(file)
                .getOrElse { e -> return classify(e.message) }
            val publication = opener.open(
                asset,
                allowUserInteraction = false,
                onCreatePublication = {
                    if (fonts.isNotEmpty() && fontsDir != null) FontInjection.install(this, fonts, fontsDir)
                },
            ).getOrElse { e -> return classify(e.message) }
            if (!publication.conformsTo(Publication.Profile.EPUB)) {
                publication.close()
                return EngineResult.Failed(EpubFailure.NOT_EPUB, "not an EPUB publication")
            }
            EngineResult.Ok(publication)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            EngineResult.Failed(EpubFailure.CORRUPT, t.message)
        }
    }

    suspend fun coverOf(publication: Publication): Bitmap? =
        try { publication.cover() } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            null
        }

    private fun classify(message: String?): EngineResult.Failed {
        val m = message.orEmpty().lowercase()
        val reason = when {
            "lcp" in m || "drm" in m || "encrypt" in m || "protect" in m || "licen" in m -> EpubFailure.ENCRYPTED
            "format" in m || "unsupported" in m || "recogni" in m -> EpubFailure.UNSUPPORTED
            else -> EpubFailure.CORRUPT
        }
        return EngineResult.Failed(reason, message)
    }
}
