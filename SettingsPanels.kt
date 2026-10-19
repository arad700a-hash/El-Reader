package com.novelreader.app.ui.reader

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.core.ColorMath
import com.novelreader.app.data.AlignMode
import com.novelreader.app.data.BgImageMode
import com.novelreader.app.data.CustomFont
import com.novelreader.app.data.DirectionMode
import com.novelreader.app.data.FontChoice
import com.novelreader.app.data.ReaderSettings
import com.novelreader.app.data.Swatches
import com.novelreader.app.data.ThemeColors
import com.novelreader.app.data.ThemeMode
import com.novelreader.app.graph
import com.novelreader.app.reader.ReaderViewModel
import com.novelreader.app.ui.ChoiceChips
import com.novelreader.app.ui.ColorPickerDialog
import com.novelreader.app.ui.Localized
import com.novelreader.app.ui.SectionTitle
import com.novelreader.app.ui.StepperSlider
import com.novelreader.app.ui.SwatchRow
import java.io.File

// =============================================================================================
// Text & fonts
// =============================================================================================

@Composable
fun TextPanel(vm: ReaderViewModel) {
    val s by vm.settings.collectAsState()
    val fonts by vm.customFonts.collectAsState()
    val available by vm.availableFontIds.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<CustomFont?>(null) }

    // System picker (Storage Access Framework). No storage permission is needed.
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val fontMimeTypes = arrayOf(
        "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf",
        "application/x-font-opentype", "application/font-sfnt", "application/vnd.ms-opentype",
        "application/octet-stream",
    )

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        StepperSlider(
            label = stringResource(R.string.font_size),
            value = s.fontSizePercent.toFloat(),
            range = 50f..300f,
            step = 5f,
            valueText = { Localized.percent(it.toInt()) },
            onCommit = { v -> vm.updateSettings { it.copy(fontSizePercent = v.toInt()) } },
        )

        SectionTitle(stringResource(R.string.font_family))
        val usingCustom = s.faFontId != null || s.latinFontId != null
        ChoiceChips(
            options = listOf(
                FontChoice.ORIGINAL to stringResource(R.string.font_book_default),
                FontChoice.SERIF to stringResource(R.string.font_serif),
                FontChoice.SANS_SERIF to stringResource(R.string.font_sans),
            ),
            selected = if (usingCustom) null else s.fontChoice,
            onSelect = { c -> if (c != null) vm.updateSettings { it.copy(fontChoice = c, faFontId = null, latinFontId = null) } },
        )
        if (usingCustom && s.keepPublisherStyles) {
            Text(
                stringResource(R.string.font_publisher_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionTitle(stringResource(R.string.imported_fonts))
        if (fonts.isEmpty()) {
            Text(stringResource(R.string.imported_fonts_empty), style = MaterialTheme.typography.bodySmall)
        }
        fonts.forEach { font ->
            FontRow(
                font = font,
                file = context.graph.fonts.file(font),
                usable = font.id in available,
                persianSelected = s.faFontId == font.id,
                latinSelected = s.latinFontId == font.id,
                onPersian = { on -> vm.selectFonts(if (on) font.id else null, s.latinFontId) },
                onLatin = { on -> vm.selectFonts(s.faFontId, if (on) font.id else null) },
                onDelete = { confirmDelete = font },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickFont.launch(fontMimeTypes) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.import_font), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = { vm.resetFonts() }, enabled = usingCustom || s.fontChoice != FontChoice.ORIGINAL) {
                Text(stringResource(R.string.reset_font))
            }
        }
        Text(
            stringResource(R.string.font_import_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    confirmDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_font_title)) },
            text = { Text(stringResource(R.string.delete_font_message, font.displayName)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteFont(font.id); confirmDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FontRow(
    font: CustomFont,
    file: File,
    usable: Boolean,
    persianSelected: Boolean,
    latinSelected: Boolean,
    onPersian: (Boolean) -> Unit,
    onLatin: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    // Live preview rendered with the real font file (Compose's own text engine; not the EPUB WebView).
    val preview: FontFamily? = remember(font.id) {
        try {
            if (file.isFile) FontFamily(Font(file, FontWeight(font.weight), if (font.italic) FontStyle.Italic else FontStyle.Normal)) else null
        } catch (_: Throwable) {
            null
        }
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(font.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_font_title))
                }
            }
            Text(
                stringResource(R.string.font_preview_sample),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = preview),
                modifier = Modifier.padding(vertical = 4.dp),
            )
            val tags = buildList {
                add(font.extension.uppercase())
                if (font.variableMin != null) add(stringResource(R.string.font_variable, font.variableMin, font.variableMax ?: font.variableMin))
                else add(stringResource(R.string.font_weight, font.weight))
                if (font.italic) add(stringResource(R.string.font_italic))
                add(stringResource(if (font.coversPersian) R.string.font_has_persian else R.string.font_no_persian_tag))
                add(stringResource(if (font.coversLatin) R.string.font_has_latin else R.string.font_no_latin_tag))
            }
            Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!usable) {
                Text(stringResource(R.string.font_needs_reopen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = persianSelected, enabled = usable, onClick = { onPersian(!persianSelected) }, label = { Text(stringResource(R.string.use_for_persian)) })
                FilterChip(selected = latinSelected, enabled = usable, onClick = { onLatin(!latinSelected) }, label = { Text(stringResource(R.string.use_for_latin)) })
            }
        }
    }
}

// =============================================================================================
// Layout
// =============================================================================================

@Composable
fun LayoutPanel(vm: ReaderViewModel) {
    val s by vm.settings.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.keep_publisher_styles), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.keep_publisher_styles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.keepPublisherStyles, onCheckedChange = { on -> vm.updateSettings { it.copy(keepPublisherStyles = on) } })
        }

        StepperSlider(
            label = stringResource(R.string.line_height),
            value = s.lineHeight.toFloat(), range = 1f..2.5f, step = 0.05f,
            valueText = { Localized.decimal(it.toDouble(), 2) },
            onCommit = { v -> vm.updateSettings { it.copy(lineHeight = v.toDouble(), keepPublisherStyles = false) } },
        )
        StepperSlider(
            label = stringResource(R.string.paragraph_spacing),
            value = s.paragraphSpacing.toFloat(), range = 0f..3f, step = 0.1f,
            valueText = { Localized.decimal(it.toDouble(), 1) },
            onCommit = { v -> vm.updateSettings { it.copy(paragraphSpacing = v.toDouble(), keepPublisherStyles = false) } },
        )
        StepperSlider(
            label = stringResource(R.string.paragraph_indent),
            value = s.paragraphIndent.toFloat(), range = 0f..3f, step = 0.1f,
            valueText = { Localized.decimal(it.toDouble(), 1) },
            onCommit = { v -> vm.updateSettings { it.copy(paragraphIndent = v.toDouble(), keepPublisherStyles = false) } },
        )
        StepperSlider(
            label = stringResource(R.string.page_margins),
            value = s.pageMargins.toFloat(), range = 0.5f..3f, step = 0.1f,
            valueText = { Localized.decimal(it.toDouble(), 1) },
            onCommit = { v -> vm.updateSettings { it.copy(pageMargins = v.toDouble()) } },
        )

        SectionTitle(stringResource(R.string.text_alignment))
        ChoiceChips(
            options = listOf(
                AlignMode.ORIGINAL to stringResource(R.string.align_original),
                AlignMode.RIGHT to stringResource(R.string.align_right),
                AlignMode.CENTER to stringResource(R.string.align_center),
                AlignMode.LEFT to stringResource(R.string.align_left),
            ),
            selected = s.align,
            onSelect = { a -> vm.updateSettings { it.copy(align = a, keepPublisherStyles = if (a == AlignMode.ORIGINAL) it.keepPublisherStyles else false) } },
        )

        OutlinedButton(
            onClick = {
                vm.updateSettings {
                    it.copy(
                        keepPublisherStyles = true, lineHeight = 1.5, paragraphSpacing = 0.5, paragraphIndent = 0.0,
                        align = AlignMode.ORIGINAL, pageMargins = 1.0,
                    )
                }
            },
            modifier = Modifier.padding(top = 12.dp),
        ) { Text(stringResource(R.string.restore_book_layout)) }
    }
}

// =============================================================================================
// Colours, theme, background image
// =============================================================================================

@Composable
fun ColorsPanel(vm: ReaderViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    var pickText by remember { mutableStateOf(false) }
    var pickBg by remember { mutableStateOf(false) }

    val effectiveBg = s.backgroundColor ?: ThemeColors.background(s.theme)
    val effectiveText = s.textColor ?: ThemeColors.text(s.theme)
    val lowContrast = stringResource(R.string.contrast_too_low)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackground(uri)
    }

    fun setText(c: Int?) {
        if (c != null && !ColorMath.isReadable(c, effectiveBg)) {
            Toast.makeText(context, lowContrast, Toast.LENGTH_SHORT).show(); return
        }
        vm.updateSettings { it.copy(textColor = c) }
    }

    fun setBackground(c: Int?) {
        val bg = c ?: ThemeColors.background(s.theme)
        if (!ColorMath.isReadable(effectiveText, bg)) {
            Toast.makeText(context, lowContrast, Toast.LENGTH_SHORT).show(); return
        }
        vm.updateSettings { it.copy(backgroundColor = c) }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionTitle(stringResource(R.string.theme))
        ChoiceChips(
            options = listOf(
                ThemeMode.DARK to stringResource(R.string.theme_dark),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
            ),
            selected = s.theme,
            // Switching theme drops custom colours so the new theme is actually visible and readable.
            onSelect = { t -> vm.updateSettings { it.copy(theme = t, textColor = null, backgroundColor = null) } },
        )

        SectionTitle(stringResource(R.string.text_color))
        SwatchRow(
            colors = Swatches.text,
            selected = s.textColor,
            onPick = { setText(it) },
            isEnabled = { ColorMath.isReadable(it, effectiveBg) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { pickText = true }) { Text(stringResource(R.string.custom_color)) }
            OutlinedButton(onClick = { setText(null) }, enabled = s.textColor != null) { Text(stringResource(R.string.restore_original)) }
        }

        SectionTitle(stringResource(R.string.background_color))
        SwatchRow(
            colors = Swatches.background,
            selected = s.backgroundColor,
            onPick = { setBackground(it) },
            isEnabled = { ColorMath.isReadable(effectiveText, it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { pickBg = true }) { Text(stringResource(R.string.custom_color)) }
            OutlinedButton(onClick = { setBackground(null) }, enabled = s.backgroundColor != null) { Text(stringResource(R.string.restore_original)) }
        }

        SectionTitle(stringResource(R.string.background_image))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pickImage.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }) {
                Text(stringResource(R.string.choose_image))
            }
            OutlinedButton(onClick = { vm.removeBackgroundImage() }, enabled = s.backgroundImagePath != null) {
                Text(stringResource(R.string.remove_image))
            }
        }
        if (s.backgroundImagePath != null) {
            ChoiceChips(
                options = listOf(
                    BgImageMode.COVER to stringResource(R.string.bg_cover),
                    BgImageMode.FIT to stringResource(R.string.bg_fit),
                    BgImageMode.TILE to stringResource(R.string.bg_tile),
                ),
                selected = s.backgroundImageMode,
                onSelect = { m -> vm.updateSettings { it.copy(backgroundImageMode = m) } },
                modifier = Modifier.padding(top = 8.dp),
            )
            StepperSlider(
                label = stringResource(R.string.bg_opacity),
                value = s.backgroundImageOpacity, range = 0f..1f, step = 0.05f,
                valueText = { Localized.percent((it * 100).toInt()) },
                onCommit = { v -> vm.updateSettings { it.copy(backgroundImageOpacity = v) } },
            )
        }
    }

    if (pickText) {
        ColorPickerDialog(
            title = stringResource(R.string.text_color),
            initial = effectiveText,
            onDismiss = { pickText = false },
            onConfirm = { setText(it); pickText = false },
            canConfirm = { ColorMath.isReadable(it, effectiveBg) },
            vetoMessage = lowContrast,
        )
    }
    if (pickBg) {
        ColorPickerDialog(
            title = stringResource(R.string.background_color),
            initial = effectiveBg,
            onDismiss = { pickBg = false },
            onConfirm = { setBackground(it); pickBg = false },
            canConfirm = { ColorMath.isReadable(effectiveText, it) },
            vetoMessage = lowContrast,
        )
    }
}

// =============================================================================================
// Scroll, direction, fullscreen
// =============================================================================================

@Composable
fun ScrollPanel(vm: ReaderViewModel) {
    val s: ReaderSettings by vm.settings.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        SectionTitle(stringResource(R.string.reading_mode))
        ChoiceChips(
            options = listOf(
                false to stringResource(R.string.mode_paged),
                true to stringResource(R.string.mode_scroll),
            ),
            selected = s.scroll,
            onSelect = { v -> vm.updateSettings { it.copy(scroll = v) } },
        )
        Text(
            stringResource(R.string.reading_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle(stringResource(R.string.page_direction))
        ChoiceChips(
            options = listOf(
                DirectionMode.AUTO to stringResource(R.string.direction_auto),
                DirectionMode.LTR to stringResource(R.string.direction_ltr),
                DirectionMode.RTL to stringResource(R.string.direction_rtl),
            ),
            selected = s.direction,
            onSelect = { d -> vm.updateSettings { it.copy(direction = d) } },
        )

        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fullscreen), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.fullscreen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = s.fullscreen, onCheckedChange = { on -> vm.updateSettings { it.copy(fullscreen = on) } })
        }

        SectionTitle(stringResource(R.string.zoom))
        Text(
            stringResource(R.string.zoom_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
