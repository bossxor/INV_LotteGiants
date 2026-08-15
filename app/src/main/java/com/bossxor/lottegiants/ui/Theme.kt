package com.bossxor.lottegiants.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bossxor.lottegiants.domain.ThemeMode

/** 브랜드 네이비 — primary / 선택 상태 */
val LotteNavy = Color(0xFF0B2A4A)

/** 롯데 포인트 레드 — LIVE·중요 CTA만 */
val LotteRed = Color(0xFFC8102E)

/** 골드 액센트 — 뱃지·차트 전용 (제목에는 쓰지 않음) */
val LotteGold = Color(0xFFC9A227)

/** 루상 주자 점유 — 알림·위젯·다이아몬드 공통 */
val BaseOccupied = Color(0xFFCCFF00)

val WinGreen = Color(0xFF2EA35C)
val LoseRed = Color(0xFFE04545)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC2EC),
    onPrimary = Color(0xFF081120),
    secondary = Color(0xFFD4B84A),
    onSecondary = Color(0xFF1A1205),
    tertiary = Color(0xFFFF6B81),
    onTertiary = Color.White,
    background = Color(0xFF0C111C),
    surface = Color(0xFF151C2C),
    surfaceVariant = Color(0xFF1F2940),
    onBackground = Color(0xFFEDF1F8),
    onSurface = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFFAAB8CE),
    outline = Color(0xFF33405A),
    outlineVariant = Color(0xFF27324A),
    primaryContainer = Color(0xFF1D3557),
    onPrimaryContainer = Color(0xFFD3E2F6),
    error = Color(0xFFFF8080),
)

private val LightColors = lightColorScheme(
    primary = LotteNavy,
    onPrimary = Color.White,
    secondary = Color(0xFF9A7B1A),
    onSecondary = Color.White,
    tertiary = LotteRed,
    onTertiary = Color.White,
    background = Color(0xFFF4F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EDF4),
    onBackground = Color(0xFF121D2E),
    onSurface = Color(0xFF121D2E),
    onSurfaceVariant = Color(0xFF4B5B71),
    outline = Color(0xFFC9D3E0),
    outlineVariant = Color(0xFFDDE4EE),
    primaryContainer = Color(0xFFDCE6F2),
    onPrimaryContainer = LotteNavy,
    error = Color(0xFFB3261E),
)

/** 본문 가독성 위주 타이포 스케일 */
private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

/** 라이브 히어로 — 네이비→슬레이트만 */
@Composable
fun heroGradient(): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        Brush.linearGradient(listOf(Color(0xFF152238), Color(0xFF0B2A4A), Color(0xFF0A101C)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0B2A4A), Color(0xFF143A5C), Color(0xFF1A4A6E)))
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
        content = content,
    )
}
