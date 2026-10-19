package com.novelreader.app.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelreader.app.R
import com.novelreader.app.data.BookEntity
import com.novelreader.app.data.LibrarySort
import com.novelreader.app.ui.Localized

private const val EPUB_PICKER_MIME = "*/*"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenBook: (Long) -> Unit,
    onPickBooks: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val books by vm.books.collectAsState()
    val total by vm.total.collectAsState()
    val query by vm.queryText.collectAsState()
    val sort by vm.sort.collectAsState()
    val grid by vm.grid.collectAsState()
    val importing by vm.importing.collectAsState()
    val loaded by vm.loaded.collectAsState()

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<BookEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<BookEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_books)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.library))
                    }
                },
                actions = {
                    IconButton(onClick = { if (searching) vm.setQuery(""); searching = !searching }) {
                        Icon(
                            if (searching) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_books),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                LibrarySort.LAST_READ to R.string.sort_last_read,
                                LibrarySort.TITLE to R.string.sort_title,
                                LibrarySort.ADDED to R.string.sort_added,
                            ).forEach { (s, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label), color = if (s == sort) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                                    onClick = { vm.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.setGrid(!grid) }) {
                        Icon(
                            if (grid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = stringResource(if (grid) R.string.view_list else R.string.view_grid),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickBooks,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_book)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                total == 0 -> EmptyLibrary(onPickBooks)
                books.isEmpty() -> Text(
                    stringResource(R.string.no_search_results),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                )
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(books, key = { it.id }) { b ->
                        GridBook(b, vm, onClick = { onOpenBook(b.id) }, onLongClick = { actionBook = b })
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(books, key = { it.id }) { b ->
                        ListBook(b, vm, onClick = { onOpenBook(b.id) }, onLongClick = { actionBook = b })
                    }
                }
            }
            if (importing) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.importing), modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    actionBook?.let { book ->
        AlertDialog(
            onDismissRequest = { actionBook = null },
            title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(R.string.book_actions_hint)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { vm.removeFromLibrary(book.id); actionBook = null }) {
                        Text(stringResource(R.string.remove_from_library))
                    }
                    TextButton(onClick = { confirmDelete = book; actionBook = null }) {
                        Text(stringResource(R.string.delete_with_data), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = { actionBook = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    confirmDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_with_data)) },
            text = { Text(stringResource(R.string.delete_with_data_message, book.title)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteCompletely(book.id); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EmptyLibrary(onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            stringResource(R.string.empty_hint),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        TextButton(onClick = onPick) { Text(stringResource(R.string.add_first_book)) }
    }
}

@Composable
private fun Cover(book: BookEntity, vm: LibraryViewModel, modifier: Modifier) {
    val file = vm.coverFile(book)
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(placeholderColor(book.title)), contentAlignment = Alignment.Center) {
        if (file != null) {
            AsyncImage(model = file, contentDescription = book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // No cover in the EPUB: the UI still works, showing the title's first letter.
            Text(
                book.title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

private fun placeholderColor(seed: String): Color {
    val h = (seed.hashCode().toLong() and 0x7fffffff) % 360
    return Color.hsv(h.toFloat(), 0.35f, 0.42f)
}

@Composable
private fun lastReadText(book: BookEntity): String =
    book.lastReadAt?.let {
        stringResource(
            R.string.last_read,
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
        )
    } ?: stringResource(R.string.not_read_yet)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBook(book: BookEntity, vm: LibraryViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Cover(book, vm, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        book.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LinearProgressIndicator(progress = { book.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text(
            "${Localized.percent((book.progress * 100).toInt())} · ${lastReadText(book)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListBook(book: BookEntity, vm: LibraryViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(book, vm, Modifier.width(56.dp).aspectRatio(2f / 3f))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                book.author?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                LinearProgressIndicator(progress = { book.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Text(
                    "${Localized.percent((book.progress * 100).toInt())} · ${lastReadText(book)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Mime filter for the system picker; validation (ZIP magic + EPUB parse) happens after picking. */
fun epubPickerMimeTypes(): Array<String> = arrayOf(EPUB_PICKER_MIME)
