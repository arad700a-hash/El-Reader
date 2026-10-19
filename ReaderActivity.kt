package com.novelreader.app.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.novelreader.app.R
import com.novelreader.app.data.ThemeMode
import com.novelreader.app.ui.NovelTheme
import com.novelreader.app.graph
import com.novelreader.app.ui.reader.ReaderBackground
import com.novelreader.app.ui.reader.ReaderScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl

/**
 * Hosts the Readium EPUB navigator (the engine's WebView-based renderer lives inside the fragment) and layers
 * the native Compose reading UI on top of it: progress bar, settings panel, search, highlights, bookmarks...
 *
 * The activity is the only place that talks to the navigator; Compose and the [ReaderViewModel] talk to it
 * through [ReaderCommand]s, decorations and preferences.
 */
class ReaderActivity : AppCompatActivity(), EpubNavigatorFragment.Listener {

    private lateinit var vm: ReaderViewModel
    private lateinit var navContainer: FragmentContainerView
    private var navigator: EpubNavigatorFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The real factory needs the opened Publication, which loads asynchronously. Until then a dummy factory
        // keeps the FragmentManager happy. Fragment state is intentionally not restored (null below): the
        // reading position is restored from the database, which also covers process death.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(null)

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L)
        if (bookId < 0) { finish(); return }
        vm = ViewModelProvider(this, ReaderViewModel.factory(application, bookId))[ReaderViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)

        navContainer = FragmentContainerView(this).apply { id = R.id.navigator_container }
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings = vm.settings.collectAsState().value
                NovelTheme(dark = settings.theme == ThemeMode.DARK) {
                    ReaderScreen(vm = vm, onBack = { finish() })
                }
            }
        }
        // Layer 0: solid colour + optional image, BEHIND the (transparent-capable) navigator.
        val backgroundView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val settings = vm.settings.collectAsState().value
                ReaderBackground(settings, graph.backgrounds)
            }
        }
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(backgroundView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(navContainer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(composeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)

        // Keep book text clear of the status/navigation bars and any display cut-out (also in fullscreen).
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            navContainer.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        observeSystemBars()

        lifecycleScope.launch {
            val ready = vm.load.filterIsInstance<ReaderLoad.Ready>().first()
            setupNavigator(ready)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Navigator setup
    // ---------------------------------------------------------------------------------------------

    private fun setupNavigator(ready: ReaderLoad.Ready) {
        val factory = EpubNavigatorFactory(ready.publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = ready.initialLocator,
            initialPreferences = ReaderPreferencesMapper.toEpubPreferences(vm.settings.value, ready.fontIds),
            listener = this,
            configuration = EpubNavigatorFragment.Configuration(
                selectionActionModeCallback = SelectionCallback(),
                // The activity already pads the navigator container for system bars / cut-outs (see onCreate).
                shouldApplyInsetsPadding = false,
            ),
        )
        supportFragmentManager.commitNow {
            replace(navContainer.id, EpubNavigatorFragment::class.java, Bundle(), NAV_TAG)
        }
        val nav = supportFragmentManager.findFragmentByTag(NAV_TAG) as EpubNavigatorFragment
        navigator = nav
        wire(nav)
    }

    private fun wire(nav: EpubNavigatorFragment) {
        // Edge taps turn pages; a tap in the middle toggles the reading chrome.
        nav.addInputListener(DirectionalNavigationAdapter(nav))
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                vm.toggleChrome()
                return true
            }
        })

        // Tapping an existing highlight opens its edit dialog.
        nav.addDecorationListener(HIGHLIGHT_GROUP, object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val id = event.decoration.id.toString().removePrefix("hl-").toLongOrNull() ?: return false
                vm.openHighlight(id)
                return true
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { nav.currentLocator.collect { vm.onLocatorChanged(it) } }

                launch {
                    combine(vm.settings, vm.availableFontIds) { settings, fontIds ->
                        ReaderPreferencesMapper.toEpubPreferences(settings, fontIds)
                    }
                        .distinctUntilChanged()
                        .collect { nav.submitPreferences(it) }
                }

                launch {
                    vm.events.collect { event ->
                        when (event) {
                            is ReaderEvent.Message -> Toast.makeText(
                                this@ReaderActivity,
                                event.arg?.let { getString(event.resId, it) } ?: getString(event.resId),
                                Toast.LENGTH_LONG,
                            ).show()
                            ReaderEvent.Reopen -> reopen()
                        }
                    }
                }

                launch {
                    vm.highlights.collectLatest { list ->
                        val decorations = list.mapNotNull { h ->
                            parseLocator(h.locatorJson)?.let { loc ->
                                Decoration(
                                    id = "hl-${h.id}",
                                    locator = loc,
                                    style = Decoration.Style.Highlight(tint = h.color),
                                )
                            }
                        }
                        nav.applyDecorations(decorations, HIGHLIGHT_GROUP)
                    }
                }

                launch {
                    vm.search.collectLatest { s ->
                        val decorations = s.hits.take(MAX_SEARCH_DECORATIONS).mapIndexed { i, hit ->
                            Decoration(
                                id = "sr-$i",
                                locator = hit.locator,
                                style = Decoration.Style.Highlight(
                                    tint = SEARCH_TINT,
                                    isActive = i == s.activeIndex,
                                ),
                            )
                        }
                        nav.applyDecorations(decorations, SEARCH_GROUP)
                    }
                }

                launch {
                    vm.commands.collect { cmd ->
                        when (cmd) {
                            is ReaderCommand.GoLocator -> nav.go(cmd.locator, animated = false)
                            is ReaderCommand.GoLink -> nav.go(cmd.link, animated = false)
                            ReaderCommand.Next -> nav.goForward(animated = false)
                            ReaderCommand.Previous -> nav.goBackward(animated = false)
                        }
                    }
                }
            }
        }
    }

    private fun parseLocator(json: String): Locator? =
        try { Locator.fromJSON(JSONObject(json)) } catch (_: Throwable) { null }

    // ---------------------------------------------------------------------------------------------
    // Fullscreen / system bars
    // ---------------------------------------------------------------------------------------------

    private fun observeSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.map { it.fullscreen to it.theme }.distinctUntilChanged().collect { (fullscreen, theme) ->
                    if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
                    else controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.isAppearanceLightStatusBars = theme == ThemeMode.LIGHT
                    controller.isAppearanceLightNavigationBars = theme == ThemeMode.LIGHT
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Navigator listener (hyperlinks)
    // ---------------------------------------------------------------------------------------------

    /** EPUB links to the web are never opened silently: the user is asked first. */
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        vm.showDialog(ReaderDialog.ExternalLink(url.toString()))
    }

    // ---------------------------------------------------------------------------------------------
    // Text selection toolbar
    // ---------------------------------------------------------------------------------------------

    private inner class SelectionCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.reader_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val action = item.itemId
            lifecycleScope.launch {
                val nav = navigator
                val selection = nav?.currentSelection()
                mode.finish()
                if (nav == null || selection == null) return@launch
                val text = selection.locator.text.highlight?.trim().orEmpty()
                if (text.isEmpty()) return@launch
                when (action) {
                    R.id.action_copy -> copyToClipboard(text)
                    R.id.action_highlight -> vm.showDialog(
                        ReaderDialog.NewHighlight(
                            locatorJson = selection.locator.toJSON().toString(),
                            text = text,
                            chapter = vm.chapterTitleFor(selection.locator),
                        )
                    )
                    R.id.action_translate -> vm.showDialog(ReaderDialog.Translate(text))
                }
                nav.clearSelection()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------------------------

    /** Restarts this reader for the same book (position was already saved by the view-model). */
    private fun reopen() {
        val again = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        finish()
        startActivity(again)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onStop() {
        vm.flushPosition()
        super.onStop()
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"
        private const val NAV_TAG = "EpubNavigatorFragment"
        const val HIGHLIGHT_GROUP = "user-highlights"
        const val SEARCH_GROUP = "search-results"
        private const val SEARCH_TINT = 0xFFFF9800.toInt()
        private const val MAX_SEARCH_DECORATIONS = 200

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
