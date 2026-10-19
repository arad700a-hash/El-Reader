package com.novelreader.app.ui.reader

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.data.Swatches
import com.novelreader.app.reader.ReaderDialog
import com.novelreader.app.reader.ReaderViewModel
import com.novelreader.app.reader.TranslateLauncher
import com.novelreader.app.reader.TranslateOutcome
import com.novelreader.app.ui.SwatchRow

/** Hosts whichever modal the view-model currently wants shown. */
@Composable
fun ReaderDialogs(vm: ReaderViewModel, dialog: ReaderDialog?) {
    val close = { vm.showDialog(null) }
    when (dialog) {
        null -> Unit
        is ReaderDialog.NewHighlight -> HighlightDialog(
            initialColor = Swatches.highlight.first(), initialNote = "", text = dialog.text, isNew = true,
            onDismiss = { close() },
            onSave = { color, note -> vm.createHighlight(dialog.locatorJson, dialog.text, color, note, dialog.chapter); close() },
            onDelete = null,
        )
        is ReaderDialog.EditHighlight -> HighlightDialog(
            initialColor = dialog.highlight.color, initialNote = dialog.highlight.note.orEmpty(), text = dialog.highlight.text, isNew = false,
            onDismiss = { close() },
            onSave = { color, note -> vm.updateHighlight(dialog.highlight.copy(color = color, note = note.trim().ifEmpty { null })); close() },
            onDelete = { vm.deleteHighlight(dialog.highlight.id); close() },
        )
        is ReaderDialog.Translate -> TranslateDialog(dialog.text, onDismiss = { close() })
        is ReaderDialog.ExternalLink -> ExternalLinkDialog(dialog.url, onDismiss = { close() })
        ReaderDialog.AddBookmark -> BookmarkDialog(
            initialTitle = "", initialNote = "", isNew = true,
            onDismiss = { close() },
            onSave = { title, _ -> vm.addBookmark(title); close() },
            onDelete = null,
        )
        is ReaderDialog.EditBookmark -> BookmarkDialog(
            initialTitle = dialog.bookmark.title.orEmpty(), initialNote = dialog.bookmark.note.orEmpty(), isNew = false,
            onDismiss = { close() },
            onSave = { title, note ->
                vm.updateBookmark(dialog.bookmark.copy(title = title.trim().ifEmpty { null }, note = note.trim().ifEmpty { null }))
                close()
            },
            onDelete = { vm.deleteBookmark(dialog.bookmark.id); close() },
        )
    }
}

@Composable
private fun HighlightDialog(
    initialColor: Int,
    initialNote: String,
    text: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var color by remember { mutableIntStateOf(initialColor) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.new_highlight else R.string.edit_highlight)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                SwatchRow(colors = Swatches.highlight, selected = color, onPick = { color = it })
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(color, note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.remove_highlight)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun BookmarkDialog(
    initialTitle: String,
    initialNote: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.add_bookmark else R.string.edit_bookmark)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.bookmark_name_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isNew) {
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text(stringResource(R.string.note_optional)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, note) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.delete)) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Languages offered for translation; Persian is the suggested default. Codes are Google Translate codes. */
private val TRANSLATE_LANGUAGES = listOf(
    "fa" to "فارسی (Persian)", "en" to "English", "ar" to "العربية (Arabic)", "tr" to "Türkçe",
    "de" to "Deutsch", "fr" to "Français", "es" to "Español", "ru" to "Русский",
    "zh-CN" to "中文 (简体)", "ja" to "日本語",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslateDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var lang by remember { mutableStateOf(TRANSLATE_LANGUAGES.first()) }
    var expanded by remember { mutableStateOf(false) }
    val appInstalled = remember { TranslateLauncher.isTranslateAppInstalled(context) }

    fun run(preferApp: Boolean) {
        val outcome = TranslateLauncher.translate(context, text, lang.first, preferApp)
        val msg = when (outcome) {
            TranslateOutcome.Opened -> null
            is TranslateOutcome.OpenedTruncated -> context.getString(R.string.translate_truncated, outcome.limit)
            TranslateOutcome.NoInternet -> context.getString(R.string.translate_no_internet)
            TranslateOutcome.NoHandler -> context.getString(R.string.translate_no_handler)
        }
        if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        // Keep the dialog open when nothing was launched so the user can retry (e.g. once back online).
        if (outcome is TranslateOutcome.Opened || outcome is TranslateOutcome.OpenedTruncated) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translate)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = lang.second, onValueChange = {}, readOnly = true,
                        label = { Text(stringResource(R.string.target_language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        TRANSLATE_LANGUAGES.forEach { l ->
                            DropdownMenuItem(text = { Text(l.second) }, onClick = { lang = l; expanded = false })
                        }
                    }
                }
                // Privacy notice: the selected text leaves the device only after the user taps a button below.
                Text(stringResource(R.string.translate_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Column {
                if (appInstalled) TextButton(onClick = { run(true) }) { Text(stringResource(R.string.translate_open_app)) }
                TextButton(onClick = { run(false) }) { Text(stringResource(R.string.translate_open_browser)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ExternalLinkDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_link_title)) },
        text = { Text(stringResource(R.string.external_link_message, url)) },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.translate_no_handler, Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) { Text(stringResource(R.string.open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
