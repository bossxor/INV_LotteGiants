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
val LotteNavy = Color(0xFF071428)

/** 롯데 포인트 레드 — LIVE·CTA */
val LotteRed = Color(0xFFE11D48)

/** 샴페인 골드 — 뱃지·히어로 점수 */
val LotteGold = Color(0xFFE0C36A)

/** 루상 주자 점유 */
val BaseOccupied = Color(0xFFCCFF00)

val WinGreen = Color(0xFF34C27A)
val LoseRed = Color(0xFFFF5C5C)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8EEF7),
    onPrimary = Color(0xFF081018),
    secondary = LotteGold,
    onSecondary = Color(0xFF1A1406),
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFF05070C),
    surface = Color(0xFF10151F),
    surfaceVariant = Color(0xFF1A212E),
    onBackground = Color(0xFFF3F5F8),
    onSurface = Color(0xFFF3F5F8),
    onSurfaceVariant = Color(0xFF9AA6B8),
    outline = Color(0xFF2A3344),
    outlineVariant = Color(0xFF1E2533),
    primaryContainer = Color(0xFF1C2433),
    onPrimaryContainer = Color(0xFFE8EEF7),
    error = Color(0xFFFF8A8A),
    inversePrimary = LotteRed,
)

private val LightColors = lightColorScheme(
    primary = LotteNavy,
    onPrimary = Color.White,
    secondary = Color(0xFF9A7B1A),
    onSecondary = Color.White,
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFFF6F2EA),
    surface = Color(0xFFFFFCF7),
    surfaceVariant = Color(0xFFEDE6D8),
    onBackground = Color(0xFF101820),
    onSurface = Color(0xFF101820),
    onSurfaceVariant = Color(0xFF5A6573),
    outline = Color(0xFFD5CBB8),
    outlineVariant = Color(0xFFE8E0D2),
    primaryContainer = Color(0xFFE8E4DB),
    onPrimaryContainer = LotteNavy,
    error = Color(0xFFB3261E),
    inversePrimary = LotteRed,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 라이브 히어로 — 잉크 네이비에 레드 한 줄기 */
@Composable
fun heroGradient(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        Brush.linearGradient(
            listOf(Color(0xFF2A0C16), Color(0xFF0C1628), Color(0xFF05070C)),
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFF1A0A12), Color(0xFF0A1C38), Color(0xFF071428)),
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
