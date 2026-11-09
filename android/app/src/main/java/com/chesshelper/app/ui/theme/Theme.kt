package com.chesshelper.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF2ECC71),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A7A43),
    secondary = Color(0xFFB0B0B0),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    error = Color(0xFFE74C3C),
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2ECC71),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8E6B8),
    background = Color(0xFFFCFCFC),
    surface = Color.White,
)

@Composable
fun ChessHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
