package com.bossxor.lottegiants.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.bossxor.lottegiants.BuildConfig
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.UpdateChecker
import com.bossxor.lottegiants.data.UpdateInfo
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.ThemeMode
import com.bossxor.lottegiants.domain.playerPhotoUrl
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.live.NotificationHelper
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    favoritePlayers: List<com.bossxor.lottegiants.domain.FavoritePlayer> = emptyList(),
    onRemoveFavorite: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val store = GiantsRepository.get(context).store
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        Spacer(Modifier.height(20.dp))
        Text("화면 테마", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Column {
                Text(
                    "라이트 · 다크 · 시스템 중 선택",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeOption(
                        selected = themeMode == ThemeMode.LIGHT,
                        icon = Icons.Default.LightMode,
                        label = "라이트",
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    )
                    ThemeOption(
                        selected = themeMode == ThemeMode.DARK,
                        icon = Icons.Default.DarkMode,
                        label = "다크",
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeModeChange(ThemeMode.DARK) },
                    )
                    ThemeOption(
                        selected = themeMode == ThemeMode.SYSTEM,
                        icon = Icons.Default.BrightnessAuto,
                        label = "시스템",
                        modifier = Modifier.weight(1f),
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("즐겨찾기 선수", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "타이틀·라인업·상세에서 ☆로 추가할 수 있습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        SectionCard {
            if (favoritePlayers.isEmpty()) {
                Text("등록된 즐겨찾기 선수가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                favoritePlayers.forEachIndexed { i, fav ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (fav.code.isNotBlank()) {
                                AsyncImage(
                                    model = playerPhotoUrl(fav.code),
                                    contentDescription = fav.name.ifBlank { fav.code },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                fav.name.ifBlank { fav.code },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            if (fav.team.isNotBlank()) {
                                Text(fav.team, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            "삭제",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRemoveFavorite(fav.code) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            color = LoseRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                    if (i < favoritePlayers.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        SectionCard {
            Column {
                NotificationType.entries.forEachIndexed { i, type ->
                    val enabled by store.notificationEnabledFlow(type).collectAsState(initial = true)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(type.label, fontWeight = FontWeight.SemiBold)
                            Text(type.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                scope.launch { store.setNotificationEnabled(type, on) }
                            },
                        )
                    }
                    if (i < NotificationType.entries.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("실시간 스코어 표시", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "알림 표시 형태입니다. 잠금화면·상태바 공개는 기기/OS에 따라 다를 수 있습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        val liveEnabled by store.liveScoreEnabledFlow.collectAsState(initial = true)
        val liveMode by store.liveDisplayModeFlow.collectAsState(initial = LiveDisplayMode.LOCK_NOW)
        val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        SectionCard {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("실시간 스코어", fontWeight = FontWeight.SemiBold)
                        Text("끄면 알림·Now Bar를 숨깁니다", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = liveEnabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                store.setLiveScoreEnabled(on)
                                if (on) LiveScoreService.restart(context) else LiveScoreService.stop(context)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("표시 모드", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip("상세 알림", liveMode == LiveDisplayMode.FULL) {
                        scope.launch {
                            store.setLiveDisplayMode(LiveDisplayMode.FULL)
                            if (store.isLiveScoreEnabled()) LiveScoreService.restart(context)
                        }
                    }
                    ModeChip("점수만", liveMode == LiveDisplayMode.STATUS_SCORE) {
                        scope.launch {
                            store.setLiveDisplayMode(LiveDisplayMode.STATUS_SCORE)
                            if (store.isLiveScoreEnabled()) LiveScoreService.restart(context)
                        }
                    }
                    ModeChip("잠금·공개", liveMode == LiveDisplayMode.LOCK_NOW) {
                        scope.launch {
                            store.setLiveDisplayMode(LiveDisplayMode.LOCK_NOW)
                            if (store.isLiveScoreEnabled()) LiveScoreService.restart(context)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            store.setLiveScoreEnabled(true)
                            NotificationHelper.createChannels(context)
                            LiveScoreService.restart(context)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("실시간 스코어 알림 다시 표시", fontWeight = FontWeight.Bold)
                }
                if (!notifEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text("시스템 알림이 꺼져 있습니다.", color = LoseRed, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val intent = Intent().apply {
                                if (Build.VERSION.SDK_INT >= 26) {
                                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                } else {
                                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("알림 권한 설정 열기")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("위젯 / 배터리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Column {
                Text(
                    "경기 중 빠른 갱신을 위해 배터리 최적화 예외가 필요합니다.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val pm = context.getSystemService(PowerManager::class.java)
                        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                },
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("배터리 최적화 예외 설정", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Column {
                Text(
                    "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    "GitHub Releases에서 최신 APK를 확인합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            checkingUpdate = true
                            val info = withContext(Dispatchers.IO) {
                                UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                            }
                            checkingUpdate = false
                            if (info == null) {
                                Toast.makeText(context, "최신 버전입니다.", Toast.LENGTH_SHORT).show()
                            } else {
                                updateInfo = info
                            }
                        }
                    },
                    enabled = !checkingUpdate && !downloadProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (checkingUpdate) "확인 중…" else "업데이트 확인",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloadProgress) updateInfo = null },
            title = { Text("업데이트 가능") },
            text = {
                Text(
                    "새 버전 ${info.tagName.ifBlank { info.versionCode.toString() }} " +
                        "(versionCode ${info.versionCode})을(를) 설치할까요?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadProgress = true
                            val ok = withContext(Dispatchers.IO) {
                                UpdateChecker.downloadAndInstall(context, info.apkUrl)
                            }
                            downloadProgress = false
                            updateInfo = null
                            if (!ok) {
                                Toast.makeText(context, "다운로드에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !downloadProgress,
                ) {
                    Text(if (downloadProgress) "받는 중…" else "설치")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!downloadProgress) updateInfo = null }) {
                    Text("나중에")
                }
            },
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun ThemeOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
