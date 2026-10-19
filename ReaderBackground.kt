package com.novelreader.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.novelreader.app.data.BgImageMode
import com.novelreader.app.data.BackgroundImageStore
import com.novelreader.app.data.ReaderSettings
import com.novelreader.app.data.ThemeColors
import com.novelreader.app.reader.ReaderPreferencesMapper
import kotlin.math.max
import kotlin.math.min

/**
 * Layer drawn BEHIND the Readium navigator: the solid background colour, and on top of it the user's image at the
 * chosen opacity. For the image to be visible the navigator page itself must be transparent, which
 * [ReaderPreferencesMapper] requests (alpha-0 background colour) whenever an image is set.
 *
 * Status: depends on Readium honouring a transparent `backgroundColor` preference - to be verified on a device
 * (see docs/TEST_REPORT.md). If it does not, the solid page colour simply covers this layer.
 */
@Composable
fun ReaderBackground(settings: ReaderSettings, store: BackgroundImageStore, modifier: Modifier = Modifier) {
    val base = Color(settings.backgroundColor ?: ThemeColors.background(settings.theme))
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val path = settings.backgroundImagePath

    LaunchedEffect(path) {
        bitmap = if (path == null) null else store.load(path)?.asImageBitmap()
    }

    Box(modifier.fillMaxSize().background(base)) {
        val img = bitmap
        if (img != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawImageFitted(img, settings.backgroundImageMode, settings.backgroundImageOpacity)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageFitted(
    img: ImageBitmap,
    mode: BgImageMode,
    alpha: Float,
) {
    val w = size.width
    val h = size.height
    val iw = img.width.toFloat()
    val ih = img.height.toFloat()
    if (iw <= 0f || ih <= 0f || w <= 0f || h <= 0f) return
    when (mode) {
        // Fill the screen, keep aspect ratio, crop the overflow (never stretched).
        BgImageMode.COVER -> {
            val scale = max(w / iw, h / ih)
            val dw = iw * scale
            val dh = ih * scale
            drawImage(
                img,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt()),
                dstSize = IntSize(dw.toInt(), dh.toInt()),
                alpha = alpha,
            )
        }
        // Whole image visible, keep aspect ratio, letterboxed.
        BgImageMode.FIT -> {
            val scale = min(w / iw, h / ih)
            val dw = iw * scale
            val dh = ih * scale
            drawImage(
                img,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(((w - dw) / 2f).toInt(), ((h - dh) / 2f).toInt()),
                dstSize = IntSize(dw.toInt(), dh.toInt()),
                alpha = alpha,
            )
        }
        // Repeat at natural size.
        BgImageMode.TILE -> {
            val tw = img.width
            val th = img.height
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    drawImage(img, dstOffset = IntOffset(x, y), alpha = alpha)
                    x += tw
                }
                y += th
            }
        }
    }
}
