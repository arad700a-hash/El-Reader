package com.novelreader.app.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.reader.ReaderTab
import com.novelreader.app.reader.ReaderViewModel

/**
 * Non-modal bottom panel (no scrim) so every change is visible live in the upper part of the page.
 * It takes at most about half of the screen height.
 */
@Composable
fun ReaderSheet(vm: ReaderViewModel, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val tab by vm.tab.collectAsState()
    val tabs = ReaderTab.entries

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxPanel = maxHeight * 0.52f
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxPanel),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                Box(Modifier.fillMaxWidth()) {
                    ScrollableTabRow(
                        selectedTabIndex = tabs.indexOf(tab),
                        edgePadding = 8.dp,
                        modifier = Modifier.padding(end = 48.dp),
                        divider = {},
                    ) {
                        tabs.forEach { t ->
                            Tab(
                                selected = t == tab,
                                onClick = { vm.openTab(t) },
                                text = { Text(stringResource(t.labelRes()), maxLines = 1) },
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f, fill = false).padding(horizontal = 16.dp)) {
                    when (tab) {
                        ReaderTab.TEXT -> TextPanel(vm)
                        ReaderTab.LAYOUT -> LayoutPanel(vm)
                        ReaderTab.COLORS -> ColorsPanel(vm)
                        ReaderTab.SCROLL -> ScrollPanel(vm)
                        ReaderTab.TOC -> TocPanel(vm, onDismiss)
                        ReaderTab.SEARCH -> SearchPanel(vm, onDismiss)
                        ReaderTab.MARKS -> MarksPanel(vm, onDismiss)
                        ReaderTab.INFO -> InfoPanel(vm, onDismiss)
                    }
                }
            }
        }
    }
}

private fun ReaderTab.labelRes(): Int = when (this) {
    ReaderTab.TEXT -> R.string.tab_text
    ReaderTab.LAYOUT -> R.string.tab_layout
    ReaderTab.COLORS -> R.string.tab_colors
    ReaderTab.SCROLL -> R.string.tab_scroll
    ReaderTab.TOC -> R.string.tab_toc
    ReaderTab.SEARCH -> R.string.tab_search
    ReaderTab.MARKS -> R.string.tab_marks
    ReaderTab.INFO -> R.string.tab_info
}
