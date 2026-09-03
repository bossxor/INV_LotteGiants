package com.bossxor.lottegiants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.bossxor.lottegiants.domain.ThemeMode

val LotteNavy = Color(0xFF041E42)
val LotteRed = Color(0xFFE11D48)
val LotteGold = Color(0xFFF0C75A)
val BaseOccupied = Color(0xFFCCFF00)
val WinGreen = Color(0xFF2ECB7A)
val LoseRed = Color(0xFFFF5C6A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6F6F4),
    onPrimary = Color(0xFF0A0A0A),
    secondary = LotteGold,
    onSecondary = Color(0xFF1A1406),
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFF080808),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF6F6F4),
    onSurface = Color(0xFFF6F6F4),
    onSurfaceVariant = Color(0xFF9A9A96),
    outline = Color(0xFF2C2C2C),
    outlineVariant = Color(0xFF222222),
    primaryContainer = Color(0xFF1C1C1C),
    onPrimaryContainer = Color(0xFFF6F6F4),
    error = Color(0xFFFF8A8A),
    inversePrimary = LotteRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF8A6A12),
    onSecondary = Color.White,
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFFF4F3EF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEAE4),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF6B6B66),
    outline = Color(0xFFDDDAD2),
    outlineVariant = Color(0xFFE8E6E0),
    primaryContainer = Color(0xFFF0EEE8),
    onPrimaryContainer = Color(0xFF111111),
    error = Color(0xFFB3261E),
    inversePrimary = LotteRed,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun heroGradient(): Brush {
    val dark = isAppDark()
    return if (dark) {
        Brush.verticalGradient(
            listOf(Color(0xFF2A0814), Color(0xFF0C0C0C)),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFF6E4E8), Color(0xFFF4F3EF)),
        )
    }
}

@Composable
fun heroOnColor(): Color =
    if (isAppDark()) Color.White else MaterialTheme.colorScheme.onBackground

@Composable
fun heroLeadScoreColor(): Color =
    if (isAppDark()) LotteGold else Color(0xFF9A6B10)

@Composable
fun LotteGiantsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !dark
            insets.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
