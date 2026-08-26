package com.bossxor.lottegiants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.ThemeMode

/** 브랜드 네이비 */
val LotteNavy = Color(0xFF041E42)

/** 롯데 포인트 레드 — LIVE·CTA·선택 */
val LotteRed = Color(0xFFE11D48)

/** 샴페인 골드 — 뱃지·히어로 점수 */
val LotteGold = Color(0xFFF0C75A)

/** 루상 주자 점유 */
val BaseOccupied = Color(0xFFCCFF00)

val WinGreen = Color(0xFF2ECB7A)
val LoseRed = Color(0xFFFF5C6A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F7FC),
    onPrimary = Color(0xFF071018),
    secondary = LotteGold,
    onSecondary = Color(0xFF1A1406),
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFF0A1424),
    surface = Color(0xFF1B2A40),
    surfaceVariant = Color(0xFF25364F),
    onBackground = Color(0xFFF5F7FB),
    onSurface = Color(0xFFF5F7FB),
    onSurfaceVariant = Color(0xFFB8C5D8),
    outline = Color(0xFF3D5270),
    outlineVariant = Color(0xFF2C3D56),
    primaryContainer = Color(0xFF24364F),
    onPrimaryContainer = Color(0xFFF4F7FC),
    error = Color(0xFFFF8A8A),
    inversePrimary = LotteRed,
)

private val LightColors = lightColorScheme(
    primary = LotteNavy,
    onPrimary = Color.White,
    secondary = Color(0xFF8A6A12),
    onSecondary = Color.White,
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFFEEF2F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EAF4),
    onBackground = Color(0xFF0E1824),
    onSurface = Color(0xFF0E1824),
    onSurfaceVariant = Color(0xFF4E5D72),
    outline = Color(0xFFC5D0DE),
    outlineVariant = Color(0xFFDCE4EE),
    primaryContainer = Color(0xFFDCE6F4),
    onPrimaryContainer = LotteNavy,
    error = Color(0xFFB3261E),
    inversePrimary = LotteRed,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 라이브 히어로 — 레드가 비치는 네이비 */
@Composable
fun heroGradient(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        Brush.linearGradient(
            listOf(Color(0xFF5A1430), Color(0xFF12264A), Color(0xFF0A1424)),
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFF3A0E22), Color(0xFF0C2A58), Color(0xFF041E42)),
        )
    }
}

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
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
