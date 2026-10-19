package com.novelreader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.reader.ReaderFailure
import com.novelreader.app.reader.ReaderInfo
import com.novelreader.app.reader.ReaderLoad
import com.novelreader.app.reader.ReaderTab
import com.novelreader.app.reader.ReaderViewModel
import com.novelreader.app.ui.Localized

/**
 * The Compose layer drawn above the Readium navigator. It only consumes touches on its own controls; everything
 * else falls through to the navigator (page turns, text selection, taps on highlights).
 *
 * Always reachable, even with the bars hidden or in fullscreen: the floating "Aa" button (opens the settings
 * and tools sheet). Tapping the middle of the page toggles the top/bottom bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val load by vm.load.collectAsState()
    when (val l = load) {
        ReaderLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ReaderLoad.Failed -> ReaderError(l.reason, onBack)
        is ReaderLoad.Ready -> ReaderContent(vm, l.book.title, onBack)
    }
}

@Composable
private fun ReaderError(reason: ReaderFailure, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.reader_error_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    when (reason) {
                        ReaderFailure.BOOK_NOT_FOUND -> R.string.err_book_not_found
                        ReaderFailure.FILE_MISSING -> R.string.err_file_missing
                        ReaderFailure.NOT_EPUB -> R.string.err_not_epub
                        ReaderFailure.ENCRYPTED -> R.string.err_encrypted
                        ReaderFailure.CORRUPT -> R.string.err_corrupt
                        ReaderFailure.UNSUPPORTED -> R.string.err_unsupported
                    }
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(vm: ReaderViewModel, title: String, onBack: () -> Unit) {
    val chrome by vm.chromeVisible.collectAsState()
    val info by vm.info.collectAsState()
    val inLibrary by vm.inLibrary.collectAsState()
    val settingsOpen by vm.settingsOpen.collectAsState()
    val markers by vm.chapterMarkers.collectAsState()
    val dialog by vm.dialog.collectAsState()
    val search by vm.search.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // ---- Top bar ----
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars).height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    if (!inLibrary) {
                        IconButton(onClick = { vm.addToLibrary() }) {
                            Icon(Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.add_to_library))
                        }
                    }
                    IconButton(onClick = { vm.showDialog(com.novelreader.app.reader.ReaderDialog.AddBookmark) }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.add_bookmark))
                    }
                    IconButton(onClick = { vm.setSettingsOpen(true); vm.openTab(ReaderTab.SEARCH) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tab_search))
                    }
                }
            }
        }

        // ---- Bottom progress bar ----
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), tonalElevation = 3.dp) {
                ProgressBar(
                    info = info,
                    markers = markers,
                    onSeek = { vm.goToFraction(it) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // ---- Search result navigation (visible while a search has results, so it works with the panel closed) ----
        AnimatedVisibility(
            visible = chrome && search.hits.isNotEmpty() && !settingsOpen,
            modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.systemBars).padding(start = 12.dp, bottom = 104.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.previousSearchHit() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.previous_result))
                    }
                    Text(
                        stringResource(
                            R.string.result_x_of_y,
                            Localized.number((search.activeIndex + 1).coerceAtLeast(1)),
                            Localized.number(search.hits.size),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = { vm.nextSearchHit() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.next_result))
                    }
                    IconButton(onClick = { vm.clearSearch() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            }
        }

        // ---- Always-available settings button ("Aa") ----
        // Sits above the progress bar so it never hides under it, and stays visible when the bars are hidden.
        Surface(
            onClick = { vm.setSettingsOpen(true) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (chrome) 1f else 0.72f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(end = 16.dp, bottom = if (chrome) 104.dp else 20.dp)
                .size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.open_reader_settings))
            }
        }

        if (settingsOpen) {
            ReaderSheet(vm = vm, onDismiss = { vm.setSettingsOpen(false) })
        }
        ReaderDialogs(vm = vm, dialog = dialog)
    }
}

/**
 * Seekable progress bar. While the thumb is dragged the bar shows the finger position and the navigator is not
 * moved; it jumps once on release, so a locator update arriving mid-drag cannot make the thumb jump back.
 * Chapter ticks come from the EPUB's own table of contents (see ReaderViewModel.chapterMarkers).
 */
@Composable
private fun ProgressBar(
    info: ReaderInfo,
    markers: List<Double>,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shownFraction = (drag ?: info.fraction.toFloat()).coerceIn(0f, 1f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tickColor = MaterialTheme.colorScheme.outline

    Column(modifier.fillMaxWidth()) {
        info.chapterTitle?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp)) {
                markers.forEach { m ->
                    val f = if (rtl) 1.0 - m else m
                    val x = (size.width * f).toFloat()
                    drawLine(tickColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            Slider(
                value = shownFraction,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { f -> onSeek(f.toDouble()) }; drag = null },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val pct = Localized.percent((shownFraction * 100).toInt())
            Text(pct, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (info.totalPositions > 0) {
                Text(
                    stringResource(
                        R.string.progress_pages,
                        Localized.number(info.position),
                        Localized.number(info.totalPositions),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.progress_remaining, Localized.number(info.remaining)),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
