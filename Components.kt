package com.novelreader.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novelreader.app.R
import com.novelreader.app.core.ColorMath
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** Section heading inside settings panels. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * A slider with - / + buttons and a live value readout. Dragging applies the value live (debounced so the
 * navigator is not flooded) and the final value is committed on release.
 */
@OptIn(FlowPreview::class)
@Composable
fun StepperSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val latest by rememberUpdatedState(onCommit)
    LaunchedEffect(Unit) {
        snapshotFlow { drag }.filterNotNull().debounce(120).collect { latest(it) }
    }
    val shown = drag ?: value
    fun snap(v: Float): Float {
        val s = ((v - range.start) / step).roundToInt() * step + range.start
        return s.coerceIn(range.start, range.endInclusive)
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText(shown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { latest(snap(shown - step)) }) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease))
            }
            Slider(
                value = shown,
                onValueChange = { drag = snap(it) },
                onValueChangeFinished = { drag?.let { d -> latest(d) }; drag = null },
                valueRange = range,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { latest(snap(shown + step)) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase))
            }
        }
    }
}

/** Row of colour swatches; [selected] gets a check mark. Disabled swatches (unreadable choices) are dimmed. */
@Composable
fun SwatchRow(
    colors: List<Int>,
    selected: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (Int) -> Boolean = { true },
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            val enabled = isEnabled(c)
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(c).copy(alpha = if (enabled) 1f else 0.3f))
                    .border(
                        BorderStroke(if (selected == c) 3.dp else 1.dp, if (selected == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        CircleShape,
                    )
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == c) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(ColorMath.bestTextOn(c)))
                }
            }
        }
    }
}

/** Chip group for a small enum-like choice. */
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * HSV colour picker dialog with a hex field and a live preview. [canConfirm] lets the caller veto colours that
 * would make text unreadable; [vetoMessage] explains why.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    canConfirm: (Int) -> Boolean = { true },
    vetoMessage: String? = null,
) {
    val initialHsv = remember(initial) { ColorMath.argbToHsv(initial) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val color = ColorMath.hsvToArgb(hue, sat, value)
    var hex by remember { mutableStateOf(ColorMath.toHex(initial)) }
    var hexError by remember { mutableStateOf(false) }
    val ok = canConfirm(color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(color))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                Text(stringResource(R.string.picker_hue), style = MaterialTheme.typography.labelMedium)
                Slider(hue, { hue = it; hex = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, value)); hexError = false }, valueRange = 0f..360f)
                Text(stringResource(R.string.picker_saturation), style = MaterialTheme.typography.labelMedium)
                Slider(sat, { sat = it; hex = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, value)); hexError = false }, valueRange = 0f..1f)
                Text(stringResource(R.string.picker_brightness), style = MaterialTheme.typography.labelMedium)
                Slider(value, { value = it; hex = ColorMath.toHex(ColorMath.hsvToArgb(hue, sat, value)); hexError = false }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        val parsed = ColorMath.parseHex(text)
                        hexError = parsed == null
                        if (parsed != null) {
                            val hsv = ColorMath.argbToHsv(parsed)
                            hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                        }
                    },
                    label = { Text("HEX") },
                    singleLine = true,
                    isError = hexError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!ok && vetoMessage != null) {
                    Text(vetoMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }, enabled = ok && !hexError) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
