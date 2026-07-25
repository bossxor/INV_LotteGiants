package com.bossxor.lottegiants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LotteNavy = Color(0xFF041E42)
val LotteRed = Color(0xFFD00F31)
val LotteGold = Color(0xFFC9A227)

private val DarkColors = darkColorScheme(
    primary = LotteRed,
    onPrimary = Color.White,
    secondary = LotteGold,
    background = Color(0xFF0A1128),
    surface = Color(0xFF12203F),
    surfaceVariant = Color(0xFF1A2C52),
    onBackground = Color(0xFFE8EAF0),
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFFAAB4CB),
)

private val LightColors = lightColorScheme(
    primary = LotteRed,
    onPrimary = Color.White,
    secondary = LotteNavy,
    background = Color(0xFFF5F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF5),
    onBackground = LotteNavy,
    onSurface = LotteNavy,
    onSurfaceVariant = Color(0xFF4A5878),
)

@Composable
fun LotteGiantsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
