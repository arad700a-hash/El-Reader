package com.novelreader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.reader.ReaderDialog
import com.novelreader.app.reader.ReaderViewModel
import com.novelreader.app.reader.TocItem
import com.novelreader.app.ui.ChoiceChips
import com.novelreader.app.ui.Localized

// =============================================================================================
// Table of contents (real EPUB structure, collapsible hierarchy)
// =============================================================================================

private data class TocRow(val item: TocItem, val flatIndex: Int)

@Composable
fun TocPanel(vm: ReaderViewModel, onClose: () -> Unit) {
    val toc by vm.toc.collectAsState()
    val info by vm.info.collectAsState()
    // Expanded nodes are keyed by their flat index; nodes on the path to the current chapter start expanded.
    var collapsed by remember { mutableStateOf(setOf<Int>()) }

    val rows = remember(toc, collapsed) {
        val out = ArrayList<TocRow>()
        var counter = 0
        fun walk(items: List<TocItem>) {
            for (node in items) {
                val idx = counter++
                out += TocRow(node, idx)
                if (idx !in collapsed) walk(node.children) else counter += countDescendants(node)
            }
        }
        walk(toc)
        out
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(info.chapterTitle ?: stringResource(R.string.no_current_chapter), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.book_progress, Localized.percent(info.percent)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = { vm.continueFromSaved(); onClose() }) { Text(stringResource(R.string.back_to_saved)) }
        }
        LinearProgressIndicator(progress = { info.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())

        if (toc.isEmpty()) {
            Text(stringResource(R.string.toc_empty), modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn {
                items(rows.size) { i ->
                    val row = rows[i]
                    val full = row.flatIndex
                    val current = full == info.currentTocIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.go(row.item.link); onClose() }
                            .padding(start = (row.item.depth * 16).dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.item.children.isNotEmpty()) {
                            val isCollapsed = row.flatIndex in collapsed
                            IconButton(onClick = {
                                collapsed = if (isCollapsed) collapsed - row.flatIndex else collapsed + row.flatIndex
                            }) {
                                Icon(
                                    if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = stringResource(if (isCollapsed) R.string.expand else R.string.collapse),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun countDescendants(item: TocItem): Int = item.children.sumOf { 1 + countDescendants(it) }

// =============================================================================================
// In-book search
// =============================================================================================

@Composable
fun SearchPanel(vm: ReaderViewModel, onClose: () -> Unit) {
    val state by vm.search.collectAsState()
    var text by rememberSaveable { mutableStateOf(state.query) }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text(stringResource(R.string.search_in_book)) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = { text = ""; vm.clearSearch() }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.runSearch(text) }),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.runSearch(text) }, enabled = text.isNotBlank() && !state.running) {
                Text(stringResource(R.string.search))
            }
            Text(
                when {
                    state.running -> stringResource(R.string.searching)
                    state.unsupported -> stringResource(R.string.search_unsupported)
                    state.failed -> stringResource(R.string.search_failed)
                    state.done && state.hits.isEmpty() -> stringResource(R.string.no_results)
                    state.hits.isNotEmpty() -> stringResource(
                        if (state.truncated) R.string.results_count_truncated else R.string.results_count,
                        Localized.number(state.hits.size),
                    )
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            IconButton(onClick = { vm.previousSearchHit() }, enabled = state.hits.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.previous_result))
            }
            IconButton(onClick = { vm.nextSearchHit() }, enabled = state.hits.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.next_result))
            }
        }
        val accent = MaterialTheme.colorScheme.primary
        LazyColumn {
            itemsIndexed(state.hits) { index, hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.goToSearchHit(index); onClose() }
                        .padding(vertical = 8.dp),
                ) {
                    hit.chapter?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        snippet(hit.before, hit.match, hit.after, accent),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

private fun snippet(before: String, match: String, after: String, accent: Color): AnnotatedString = buildAnnotatedString {
    append(before.takeLast(60).trimStart())
    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(match) }
    append(after.take(80).trimEnd())
}

// =============================================================================================
// Highlights, bookmarks, history
// =============================================================================================

private enum class MarksSection { HIGHLIGHTS, BOOKMARKS, HISTORY }

@Composable
fun MarksPanel(vm: ReaderViewModel, onClose: () -> Unit) {
    var section by rememberSaveable { mutableStateOf(MarksSection.HIGHLIGHTS) }
    val highlights by vm.highlights.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val history by vm.history.collectAsState()

    Column {
        ChoiceChips(
            options = listOf(
                MarksSection.HIGHLIGHTS to stringResource(R.string.highlights),
                MarksSection.BOOKMARKS to stringResource(R.string.bookmarks),
                MarksSection.HISTORY to stringResource(R.string.history),
            ),
            selected = section,
            onSelect = { section = it },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn {
            when (section) {
                MarksSection.HIGHLIGHTS -> {
                    if (highlights.isEmpty()) item { EmptyHint(stringResource(R.string.no_highlights)) }
                    items(highlights, key = { it.id }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.goToLocatorJson(h.locatorJson); onClose() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).background(Color(h.color), CircleShape))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(h.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                h.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                                h.chapterTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            IconButton(onClick = { vm.showDialog(ReaderDialog.EditHighlight(h)) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { vm.deleteHighlight(h.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        HorizontalDivider()
                    }
                }
                MarksSection.BOOKMARKS -> {
                    if (bookmarks.isEmpty()) item { EmptyHint(stringResource(R.string.no_bookmarks)) }
                    items(bookmarks, key = { it.id }) { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.goToLocatorJson(b.locatorJson); onClose() }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title ?: b.chapterTitle ?: stringResource(R.string.bookmark), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    Localized.percent((b.progress * 100).toInt()) + (b.chapterTitle?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                b.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                            }
                            IconButton(onClick = { vm.showDialog(ReaderDialog.EditBookmark(b)) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                            }
                            IconButton(onClick = { vm.deleteBookmark(b.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        HorizontalDivider()
                    }
                }
                MarksSection.HISTORY -> {
                    if (history.isEmpty()) item { EmptyHint(stringResource(R.string.no_history)) }
                    items(history, key = { it.id }) { h ->
                        Column(Modifier.fillMaxWidth().clickable { vm.goToLocatorJson(h.locatorJson); onClose() }.padding(vertical = 10.dp)) {
                            Text(h.chapterTitle ?: stringResource(R.string.position), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Localized.percent((h.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
}

// =============================================================================================
// Reading progress info
// =============================================================================================

@Composable
fun InfoPanel(vm: ReaderViewModel, onClose: () -> Unit) {
    val info by vm.info.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoLine(stringResource(R.string.info_progress), Localized.percent(info.percent))
        LinearProgressIndicator(progress = { info.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
        InfoLine(stringResource(R.string.info_chapter), info.chapterTitle ?: "—")
        if (info.totalPositions > 0) {
            InfoLine(stringResource(R.string.info_page), stringResource(R.string.progress_pages, Localized.number(info.position), Localized.number(info.totalPositions)))
            InfoLine(stringResource(R.string.info_remaining), Localized.number(info.remaining))
        }
        Text(stringResource(R.string.pages_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { vm.continueFromSaved(); onClose() }) { Text(stringResource(R.string.back_to_saved)) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
