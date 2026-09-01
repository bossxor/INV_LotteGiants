package com.bossxor.lottegiants.ui.screens

import android.app.AlarmManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.bossxor.lottegiants.BuildConfig
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.data.InstallResult
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.UpdateCheckResult
import com.bossxor.lottegiants.data.UpdateChecker
import com.bossxor.lottegiants.domain.AlertPreset
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_MAX
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_MIN
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_STEP
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.ThemeMode
import com.bossxor.lottegiants.domain.clampLiveLeadMinutes
import com.bossxor.lottegiants.domain.liveLeadLabel
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.live.NotificationHelper
import com.bossxor.lottegiants.widget.WidgetUpdater
import java.time.Instant
import java.time.format.DateTimeFormatter
import com.bossxor.lottegiants.ui.LoseRed
import com.bossxor.lottegiants.ui.LotteRed
import com.bossxor.lottegiants.ui.components.PlayerAvatar
import com.bossxor.lottegiants.ui.components.ScreenTitle
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
    onOpenPlayerSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = GiantsRepository.get(context).store
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ScreenTitle("설정", "테마 · 알림 · 업데이트")

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
        Button(
            onClick = onOpenPlayerSearch,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("선수 검색해서 즐겨찾기", fontWeight = FontWeight.Bold)
        }
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
                        PlayerAvatar(
                            playerCode = fav.code,
                            name = fav.name.ifBlank { fav.code },
                            size = 40.dp,
                        )
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
        Text(
            "프리셋으로 한 번에 고르거나, 아래에서 종류별로 켤 수 있습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        val liveOnly by store.alertsLiveOnlyFlow.collectAsState(initial = false)
        val quietOn by store.quietHoursEnabledFlow.collectAsState(initial = false)
        val quietStart by store.quietStartHourFlow.collectAsState(initial = 23)
        val quietEnd by store.quietEndHourFlow.collectAsState(initial = 8)
        SectionCard {
            Column {
                Text("프리셋", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertPreset.entries.forEach { preset ->
                        ModeChip(preset.label, false) {
                            scope.launch { store.applyAlertPreset(preset) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("경기 중에만", fontWeight = FontWeight.SemiBold)
                        Text(
                            "라인업·등말소·취소·경기 30분 전은 예외",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = liveOnly,
                        onCheckedChange = { on -> scope.launch { store.setAlertsLiveOnly(on) } },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("무음 시간", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${quietStart}시–${quietEnd}시에는 알림 없음",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = quietOn,
                        onCheckedChange = { on -> scope.launch { store.setQuietHoursEnabled(on) } },
                    )
                }
                if (quietOn) {
                    Spacer(Modifier.height(8.dp))
                    HourStepper("시작", quietStart) { h ->
                        scope.launch { store.setQuietHours(h, quietEnd) }
                    }
                    HourStepper("끝", quietEnd) { h ->
                        scope.launch { store.setQuietHours(quietStart, h) }
                    }
                }
            }
        }
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
            "알림 표시 형태입니다. One UI 9 Now Bar(라이브 알림)를 켜 두면 잠금화면·상태바 칩에 점수가 뜹니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        val liveEnabled by store.liveScoreEnabledFlow.collectAsState(initial = true)
        val liveMode by store.liveDisplayModeFlow.collectAsState(initial = LiveDisplayMode.FULL)
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
                    ModeChip("라이브 바", liveMode == LiveDisplayMode.LOCK_NOW) {
                        scope.launch {
                            store.setLiveDisplayMode(LiveDisplayMode.LOCK_NOW)
                            if (store.isLiveScoreEnabled()) LiveScoreService.restart(context)
                        }
                    }
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
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (liveMode) {
                        LiveDisplayMode.LOCK_NOW ->
                            "이닝 진행 바 알림. Now Bar·잠금화면 칩에 점수가 뜹니다."
                        LiveDisplayMode.FULL ->
                            "팀 로고·점수 카드. 하단에 양 팀 승리 예측 게이지. 경기 전에는 시각·선발·구장, 중에는 루상·투수·타자, 끝나면 승·패. 카드는 Now Bar 칩으로 승격되지 않습니다."
                        LiveDisplayMode.STATUS_SCORE ->
                            "점수·이닝만 한 줄. 칩에는 점수가 뜹니다."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                val liveLead by store.liveLeadMinutesFlow.collectAsState(initial = 120)
                Text("Now Bar 표시 시작", fontWeight = FontWeight.SemiBold)
                Text(
                    "경기 시작 ${liveLeadLabel(liveLead)}부터 알림이 뜹니다. 경기 중에는 항상 표시되고, 끝나면 밀어 지울 수 있습니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    liveLeadLabel(liveLead),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Slider(
                    value = liveLead.toFloat(),
                    onValueChange = { raw ->
                        scope.launch {
                            store.setLiveLeadMinutes(clampLiveLeadMinutes(raw.toInt()))
                        }
                    },
                    onValueChangeFinished = {
                        scope.launch {
                            if (store.isLiveScoreEnabled()) LiveScoreService.restart(context)
                        }
                    },
                    valueRange = LIVE_LEAD_MINUTES_MIN.toFloat()..LIVE_LEAD_MINUTES_MAX.toFloat(),
                    steps = (LIVE_LEAD_MINUTES_MAX - LIVE_LEAD_MINUTES_MIN) / LIVE_LEAD_MINUTES_STEP - 1,
                )
                Text(
                    "30분 단위 · 최소 30분 · 최대 6시간",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "워치4 등 Wear OS는 폰과 페어링되면 워치 앱·타일·컴플리케이션에 같은 점수와 순위·선발이 갑니다. 워치에 앱이 없으면 갤럭시 웨어러블에서 사직스코어를 설치하세요.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val wearSync by store.wearLastSyncFlow.collectAsState(initial = 0L)
                if (wearSync > 0L) {
                    val clock = Instant.ofEpochMilli(wearSync).atZone(KBO_ZONE).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text(
                        "워치 마지막 동기화 $clock",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val nowBar = NotificationHelper.nowBarStatus(context)
                Text(
                    NotificationHelper.nowBarStatusLabel(nowBar),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT >= 36) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { NotificationHelper.openNowBarSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Now Bar 허용 열기", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            LiveScoreService.reshow(context)
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
                if (Build.VERSION.SDK_INT >= 31) {
                    val am = context.getSystemService(AlarmManager::class.java)
                    if (am != null && !am.canScheduleExactAlarms()) {
                        Spacer(Modifier.height(10.dp))
                        Text("정확한 경기 시작 알람이 꺼져 있습니다.", color = LoseRed, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("정확한 알람 허용 열기", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("홈 화면 위젯", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val widgetOpacity by store.widgetOpacityFlow.collectAsState(initial = 100)
        val showOppLogo by store.widgetShowOppLogoFlow.collectAsState(initial = true)
        var opacitySlide by remember { mutableStateOf(widgetOpacity.toFloat()) }
        LaunchedEffect(widgetOpacity) { opacitySlide = widgetOpacity.toFloat() }
        SectionCard {
            Column {
                Text("배경 투명도  ${opacitySlide.toInt()}%", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = opacitySlide,
                    onValueChange = { opacitySlide = it },
                    onValueChangeFinished = {
                        scope.launch {
                            store.setWidgetOpacity(opacitySlide.toInt())
                            WidgetUpdater.updateAll(context)
                        }
                    },
                    valueRange = 20f..100f,
                    steps = 7,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("상대 팀 로고", fontWeight = FontWeight.SemiBold)
                        Text("끄면 위젯에서 상대 엠블럼을 숨깁니다", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = showOppLogo,
                        onCheckedChange = { on ->
                            scope.launch {
                                store.setWidgetShowOppLogo(on)
                                WidgetUpdater.updateAll(context)
                            }
                        },
                    )
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
                    "앱을 열면 최신 빌드를 자동으로 받아 설치 화면까지 진행합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            checkingUpdate = true
                            updateStatus = "업데이트 확인 중…"
                            val result = withContext(Dispatchers.IO) {
                                UpdateChecker.check(BuildConfig.VERSION_CODE)
                            }
                            when (result) {
                                is UpdateCheckResult.UpToDate -> {
                                    checkingUpdate = false
                                    updateStatus = null
                                    Toast.makeText(context, "최신 버전입니다.", Toast.LENGTH_SHORT).show()
                                }
                                is UpdateCheckResult.Failed -> {
                                    checkingUpdate = false
                                    updateStatus = null
                                    Toast.makeText(
                                        context,
                                        "확인 실패: ${result.message}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                is UpdateCheckResult.Available -> {
                                    val info = result.info
                                    updateStatus =
                                        "새 버전 ${info.tagName.ifBlank { info.versionCode.toString() }} 다운로드 중…"
                                    val storeRef = GiantsRepository.get(context).store
                                    val install = withContext(Dispatchers.IO) {
                                        UpdateChecker.downloadAndInstall(context, info, storeRef) { downloaded, total ->
                                            if (total > 0L) {
                                                val pct = ((downloaded * 100) / total).toInt()
                                                updateStatus = "업데이트 다운로드 중… $pct%"
                                            }
                                        }
                                    }
                                    checkingUpdate = false
                                    when (install) {
                                        is InstallResult.Launched ->
                                            updateStatus = "설치 화면으로 이동합니다…"
                                        is InstallResult.NeedsPermission -> {
                                            updateStatus = null
                                            Toast.makeText(
                                                context,
                                                "설치 권한을 허용하면 자동으로 설치가 진행됩니다.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        is InstallResult.DownloadFailed -> {
                                            updateStatus = null
                                            Toast.makeText(
                                                context,
                                                install.message,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !checkingUpdate,
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
                        if (checkingUpdate) "업데이트 중…" else "지금 업데이트 확인",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    updateStatus?.let { status ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("업데이트") },
            text = { Text(status) },
            confirmButton = {
                Text("잠시만요…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(48.dp), fontSize = 13.sp)
        Text(
            "−",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onChange((hour + 23) % 24) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
        )
        Text("${hour}시", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            "+",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onChange((hour + 1) % 24) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        color = if (selected) LotteRed else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 13.sp,
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
