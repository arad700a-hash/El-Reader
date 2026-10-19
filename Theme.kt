package com.novelreader.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE8B04A),
    onPrimary = Color(0xFF2A1D00),
    primaryContainer = Color(0xFF4A3608),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFB9C7DE),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1B1B1D),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2B30),
    onSurfaceVariant = Color(0xFFC6C6CE),
    surfaceContainer = Color(0xFF202124),
    surfaceContainerHigh = Color(0xFF2A2B2F),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF2A1D00),
    secondary = Color(0xFF4F5B70),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF1A1A1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFE4E5EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainer = Color(0xFFF1F1F6),
    surfaceContainerHigh = Color(0xFFE9E9EF),
    outline = Color(0xFF74777F),
    error = Color(0xFFBA1A1A),
)

/** App-wide Material 3 theme. Dark is the default; the reader and the library share the same choice. */
@Composable
fun NovelTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
