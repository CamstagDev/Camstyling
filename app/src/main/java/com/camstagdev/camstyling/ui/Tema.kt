package com.camstagdev.camstyling.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mismos tonos que TaskCam: fondo casi negro y acento verde agua.
private val colores = darkColorScheme(
    primary = Color(0xFF2EC4A6),
    onPrimary = Color(0xFF0D0E13),
    background = Color(0xFF0D0E13),
    onBackground = Color(0xFFE6E7EC),
    surface = Color(0xFF0D0E13),
    onSurface = Color(0xFFE6E7EC),
    surfaceContainer = Color(0xFF181A22),
    surfaceContainerHigh = Color(0xFF1F222C),
    onSurfaceVariant = Color(0xFF9EA2B0),
    error = Color(0xFFFF6B6B),
)

@Composable
fun TemaCamstyling(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colores, content = contenido)
}
