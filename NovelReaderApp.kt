package com.novelreader.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.novelreader.app.data.AnnotationRepository
import com.novelreader.app.data.AppDatabase
import com.novelreader.app.data.BackgroundImageStore
import com.novelreader.app.data.FontRepository
import com.novelreader.app.data.LibraryRepository
import com.novelreader.app.data.SettingsRepository
import com.novelreader.app.reader.EpubEngine

/**
 * Manual dependency container (no DI framework): one database, one EPUB engine, three repositories.
 * Access from anywhere via `(context.applicationContext as NovelReaderApp).graph`.
 */
class NovelReaderApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Persian is the default UI language; the user's explicit per-app choice (if ever set) wins.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE_TAG))
        }
        val db = AppDatabase.get(this)
        val engine = EpubEngine(this)
        val settings = SettingsRepository(this)
        graph = AppGraph(
            library = LibraryRepository(this, db, engine),
            annotations = AnnotationRepository(db),
            settings = settings,
            fonts = FontRepository(this, settings),
            backgrounds = BackgroundImageStore(this),
            engine = engine,
        )
    }

    companion object {
        /** Change this (or add a language picker) to ship another default language. */
        const val DEFAULT_LANGUAGE_TAG = "fa"
    }
}

class AppGraph(
    val library: LibraryRepository,
    val annotations: AnnotationRepository,
    val settings: SettingsRepository,
    val fonts: FontRepository,
    val backgrounds: BackgroundImageStore,
    val engine: EpubEngine,
)

val android.content.Context.graph: AppGraph
    get() = (applicationContext as NovelReaderApp).graph
