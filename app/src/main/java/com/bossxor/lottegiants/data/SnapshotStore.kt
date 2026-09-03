package com.bossxor.lottegiants.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bossxor.lottegiants.domain.AlertPreset
import com.bossxor.lottegiants.domain.FavoritePlayer
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LIVE_LEAD_MINUTES_DEFAULT
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.ThemeMode
import com.bossxor.lottegiants.domain.clampLiveLeadMinutes
import com.bossxor.lottegiants.domain.typesForPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "giants_store")

/** 앱/서비스/위젯이 공유하는 최신 경기 스냅샷과 알림 설정 저장소 */
class SnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val snapshotFlow: Flow<LiveSnapshot?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SNAPSHOT]?.let { runCatching { json.decodeFromString<LiveSnapshot>(it) }.getOrNull() }
    }

    suspend fun loadSnapshot(): LiveSnapshot? = snapshotFlow.first()

    suspend fun saveSnapshot(snapshot: LiveSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SNAPSHOT] = json.encodeToString(LiveSnapshot.serializer(), snapshot)
        }
    }

    fun notificationEnabledFlow(type: NotificationType): Flow<Boolean> =
        context.dataStore.data.map { it[booleanPreferencesKey("notif_${type.name}")] ?: true }

    suspend fun isNotificationEnabled(type: NotificationType): Boolean =
        notificationEnabledFlow(type).first()

    suspend fun setNotificationEnabled(type: NotificationType, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("notif_${type.name}")] = enabled }
    }

    suspend fun applyAlertPreset(preset: AlertPreset) {
        val enabled = typesForPreset(preset)
        context.dataStore.edit { prefs ->
            NotificationType.entries.forEach { type ->
                prefs[booleanPreferencesKey("notif_${type.name}")] = type in enabled
            }
        }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME] ?: ThemeMode.SYSTEM.name
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    val liveScoreEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_LIVE_ENABLED] ?: true
    }

    suspend fun isLiveScoreEnabled(): Boolean = liveScoreEnabledFlow.first()

    suspend fun setLiveScoreEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LIVE_ENABLED] = enabled }
    }

    /** 설정 '다시 표시'로 lead 창 밖에서도 알림을 고정할 때 */
    suspend fun isLiveNotificationPinned(): Boolean =
        context.dataStore.data.map { it[KEY_LIVE_PINNED] ?: false }.first()

    suspend fun setLiveNotificationPinned(pinned: Boolean) {
        context.dataStore.edit { it[KEY_LIVE_PINNED] = pinned }
    }

    val liveDisplayModeFlow: Flow<LiveDisplayMode> = context.dataStore.data.map { prefs ->
        runCatching {
            LiveDisplayMode.valueOf(prefs[KEY_LIVE_MODE] ?: LiveDisplayMode.FULL.name)
        }.getOrDefault(LiveDisplayMode.FULL)
    }

    suspend fun liveDisplayMode(): LiveDisplayMode = liveDisplayModeFlow.first()

    suspend fun setLiveDisplayMode(mode: LiveDisplayMode) {
        context.dataStore.edit { it[KEY_LIVE_MODE] = mode.name }
    }

    val liveLeadMinutesFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        clampLiveLeadMinutes(prefs[KEY_LIVE_LEAD_MINUTES] ?: LIVE_LEAD_MINUTES_DEFAULT)
    }

    suspend fun liveLeadMinutes(): Int = liveLeadMinutesFlow.first()

    suspend fun setLiveLeadMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_LIVE_LEAD_MINUTES] = clampLiveLeadMinutes(minutes) }
    }

    /** v1.3.40: ProgressStyle 라이브 바 → 점수판 카드로 한 번만 전환 */
    suspend fun migrateToScorecardModeIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_SCORECARD_MIGRATED] == true) return@edit
            prefs[KEY_LIVE_MODE] = LiveDisplayMode.FULL.name
            prefs[KEY_SCORECARD_MIGRATED] = true
        }
    }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING] ?: false
    }

    suspend fun isOnboardingDone(): Boolean = onboardingDoneFlow.first()

    suspend fun setOnboardingDone(done: Boolean = true) {
        context.dataStore.edit { it[KEY_ONBOARDING] = done }
    }

    suspend fun setHighlight(text: String, durationMs: Long = 45_000L) {
        val prev = loadSnapshot() ?: LiveSnapshot()
        saveSnapshot(
            prev.copy(
                highlightText = text,
                highlightUntilMillis = System.currentTimeMillis() + durationMs,
            ),
        )
    }

    suspend fun setWeather(weather: com.bossxor.lottegiants.domain.StadiumWeather?) {
        val prev = loadSnapshot() ?: return
        saveSnapshot(prev.copy(weather = weather))
    }

    val favoritePlayersFlow: Flow<List<FavoritePlayer>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_FAVORITE_PLAYERS]
            if (!raw.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString(ListSerializer(FavoritePlayer.serializer()), raw)
                }.getOrNull()?.filter { it.code.isNotBlank() }.orEmpty()
            } else {
                prefs[KEY_FAVORITES]?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.map { FavoritePlayer(code = it) }
                    .orEmpty()
            }
        }

    val favoriteCodesFlow: Flow<Set<String>> = favoritePlayersFlow.map { list ->
        list.map { it.code }.toSet()
    }

    suspend fun favoriteCodes(): Set<String> = favoriteCodesFlow.first()

    suspend fun favoritePlayers(): List<FavoritePlayer> =
        favoritePlayersFlow.first()

    suspend fun toggleFavorite(code: String, name: String = "", team: String = ""): Boolean {
        if (code.isBlank()) return false
        var nowFav = false
        context.dataStore.edit { prefs ->
            val cur = favoritePlayersFromPrefs(prefs).toMutableList()
            val idx = cur.indexOfFirst { it.code == code }
            if (idx >= 0) {
                cur.removeAt(idx)
            } else {
                cur.add(FavoritePlayer(code = code, name = name, team = team))
                nowFav = true
            }
            prefs[KEY_FAVORITE_PLAYERS] =
                json.encodeToString(ListSerializer(FavoritePlayer.serializer()), cur)
            prefs[KEY_FAVORITES] = cur.joinToString(",") { it.code }
        }
        return nowFav
    }

    suspend fun removeFavorite(code: String) {
        if (code.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = favoritePlayersFromPrefs(prefs).filterNot { it.code == code }
            prefs[KEY_FAVORITE_PLAYERS] =
                json.encodeToString(ListSerializer(FavoritePlayer.serializer()), cur)
            prefs[KEY_FAVORITES] = cur.joinToString(",") { it.code }
        }
    }

    private fun favoritePlayersFromPrefs(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): List<FavoritePlayer> {
        val raw = prefs[KEY_FAVORITE_PLAYERS]
        if (!raw.isNullOrBlank()) {
            return runCatching {
                json.decodeFromString(ListSerializer(FavoritePlayer.serializer()), raw)
            }.getOrNull()?.filter { it.code.isNotBlank() }.orEmpty()
        }
        return prefs[KEY_FAVORITES]?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { FavoritePlayer(code = it) }
            .orEmpty()
    }

    val bannerDismissedDayFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_BANNER_DAY].orEmpty()
    }

    suspend fun bannerDismissedDay(): String = bannerDismissedDayFlow.first()

    suspend fun setBannerDismissedDay(day: String) {
        context.dataStore.edit { it[KEY_BANNER_DAY] = day }
    }

    /** 이미 알림을 보낸 등말소 키 (중복 알림 방지) */
    suspend fun notifiedRosterKeys(): Set<String> =
        context.dataStore.data.map { it[KEY_NOTIFIED_ROSTER].orEmpty() }.first()

    suspend fun setNotifiedRosterKeys(keys: Set<String>) {
        context.dataStore.edit { it[KEY_NOTIFIED_ROSTER] = keys }
    }

    suspend fun lastLiveNotifyKey(): String =
        context.dataStore.data.map { it[KEY_LIVE_NOTIFY] ?: "" }.first()

    suspend fun setLastLiveNotifyKey(key: String) {
        context.dataStore.edit { it[KEY_LIVE_NOTIFY] = key }
    }

    /**
     * 라인업 알림 진행 단계. `"$gameId:flag"`(발표만 확인) → `"$gameId:full"`(타순까지 확인).
     * 스케줄러 워커는 매 실행마다 새 detector를 만들기 때문에 메모리 대신 여기 남겨야 한다.
     */
    suspend fun notifiedLineupState(): String =
        context.dataStore.data.map { it[KEY_NOTIFIED_LINEUP].orEmpty() }.first()

    suspend fun setNotifiedLineupState(state: String) {
        context.dataStore.edit { it[KEY_NOTIFIED_LINEUP] = state }
    }

    /** 이미 취소 알림을 보낸 경기 ID (중복 알림 방지) */
    suspend fun notifiedCancelGameId(): String =
        context.dataStore.data.map { it[KEY_NOTIFIED_CANCEL].orEmpty() }.first()

    suspend fun setNotifiedCancelGameId(gameId: String) {
        context.dataStore.edit { it[KEY_NOTIFIED_CANCEL] = gameId }
    }

    /** 이미 종료 알림을 보낸 경기 ID (워커 재시작·콜드 스타트 중복 방지) */
    suspend fun notifiedEndGameId(): String =
        context.dataStore.data.map { it[KEY_NOTIFIED_END].orEmpty() }.first()

    suspend fun setNotifiedEndGameId(gameId: String) {
        context.dataStore.edit { it[KEY_NOTIFIED_END] = gameId }
    }

    /** 권한 대기 중인 업데이트 APK (절대 경로). 빈 문자열이면 없음. */
    suspend fun pendingUpdateApkPath(): String =
        context.dataStore.data.map { it[KEY_PENDING_UPDATE_APK].orEmpty() }.first()

    suspend fun pendingUpdateVersionCode(): Int =
        context.dataStore.data.map {
            it[KEY_PENDING_UPDATE_CODE]?.toIntOrNull() ?: 0
        }.first()

    suspend fun setPendingUpdate(apkPath: String, versionCode: Int) {
        context.dataStore.edit {
            it[KEY_PENDING_UPDATE_APK] = apkPath
            it[KEY_PENDING_UPDATE_CODE] = versionCode.toString()
        }
    }

    suspend fun clearPendingUpdate() {
        context.dataStore.edit {
            it.remove(KEY_PENDING_UPDATE_APK)
            it.remove(KEY_PENDING_UPDATE_CODE)
        }
    }

    suspend fun preferredLiveGameId(): String =
        context.dataStore.data.first()[KEY_PREFERRED_LIVE].orEmpty()

    suspend fun setPreferredLiveGameId(gameId: String) {
        context.dataStore.edit { prefs ->
            if (gameId.isBlank()) prefs.remove(KEY_PREFERRED_LIVE)
            else prefs[KEY_PREFERRED_LIVE] = gameId
        }
    }

    val alertsLiveOnlyFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ALERTS_LIVE_ONLY] ?: false
    }

    suspend fun alertsLiveOnly(): Boolean = alertsLiveOnlyFlow.first()

    suspend fun setAlertsLiveOnly(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERTS_LIVE_ONLY] = enabled }
    }

    val quietHoursEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_QUIET_ENABLED] ?: false
    }

    suspend fun quietHoursEnabled(): Boolean = quietHoursEnabledFlow.first()

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_QUIET_ENABLED] = enabled }
    }

    val quietStartHourFlow: Flow<Int> = context.dataStore.data.map {
        it[KEY_QUIET_START] ?: 23
    }

    val quietEndHourFlow: Flow<Int> = context.dataStore.data.map {
        it[KEY_QUIET_END] ?: 8
    }

    suspend fun quietStartHour(): Int = quietStartHourFlow.first()

    suspend fun quietEndHour(): Int = quietEndHourFlow.first()

    suspend fun setQuietHours(startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[KEY_QUIET_START] = startHour.coerceIn(0, 23)
            it[KEY_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    val widgetOpacityFlow: Flow<Int> = context.dataStore.data.map {
        (it[KEY_WIDGET_OPACITY] ?: 100).coerceIn(20, 100)
    }

    suspend fun widgetOpacity(): Int = widgetOpacityFlow.first()

    suspend fun setWidgetOpacity(pct: Int) {
        context.dataStore.edit { it[KEY_WIDGET_OPACITY] = pct.coerceIn(20, 100) }
    }

    val widgetShowOppLogoFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_WIDGET_OPP_LOGO] ?: true
    }

    suspend fun widgetShowOppLogo(): Boolean = widgetShowOppLogoFlow.first()

    suspend fun setWidgetShowOppLogo(show: Boolean) {
        context.dataStore.edit { it[KEY_WIDGET_OPP_LOGO] = show }
    }

    val wearLastSyncFlow: Flow<Long> = context.dataStore.data.map {
        it[KEY_WEAR_LAST_SYNC] ?: 0L
    }

    suspend fun setWearLastSync(millis: Long) {
        context.dataStore.edit { it[KEY_WEAR_LAST_SYNC] = millis }
    }

    suspend fun lastRaceFingerprint(): String =
        context.dataStore.data.map { it[KEY_LAST_RACE].orEmpty() }.first()

    suspend fun setLastRaceFingerprint(value: String) {
        context.dataStore.edit { it[KEY_LAST_RACE] = value }
    }

    companion object {
        private val KEY_SNAPSHOT = stringPreferencesKey("live_snapshot")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_LIVE_ENABLED = booleanPreferencesKey("live_score_display_enabled")
        private val KEY_LIVE_PINNED = booleanPreferencesKey("live_notification_pinned")
        private val KEY_LIVE_MODE = stringPreferencesKey("live_display_mode")
        private val KEY_LIVE_LEAD_MINUTES = intPreferencesKey("live_lead_minutes")
        private val KEY_SCORECARD_MIGRATED = booleanPreferencesKey("scorecard_notif_migrated_140")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        private val KEY_FAVORITES = stringPreferencesKey("favorite_player_codes")
        private val KEY_FAVORITE_PLAYERS = stringPreferencesKey("favorite_players")
        private val KEY_BANNER_DAY = stringPreferencesKey("perm_banner_dismissed_day")
        private val KEY_NOTIFIED_CANCEL = stringPreferencesKey("notified_cancel_game_id")
        private val KEY_NOTIFIED_END = stringPreferencesKey("notified_end_game_id")
        private val KEY_NOTIFIED_ROSTER = stringSetPreferencesKey("notified_roster_keys")
        private val KEY_LIVE_NOTIFY = stringPreferencesKey("last_live_notify_key")
        private val KEY_NOTIFIED_LINEUP = stringPreferencesKey("notified_lineup_state")
        private val KEY_PENDING_UPDATE_APK = stringPreferencesKey("pending_update_apk")
        private val KEY_PENDING_UPDATE_CODE = stringPreferencesKey("pending_update_code")
        private val KEY_PREFERRED_LIVE = stringPreferencesKey("preferred_live_game_id")
        private val KEY_ALERTS_LIVE_ONLY = booleanPreferencesKey("alerts_live_only")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val KEY_QUIET_START = intPreferencesKey("quiet_start_hour")
        private val KEY_QUIET_END = intPreferencesKey("quiet_end_hour")
        private val KEY_WIDGET_OPACITY = intPreferencesKey("widget_opacity_pct")
        private val KEY_WIDGET_OPP_LOGO = booleanPreferencesKey("widget_show_opp_logo")
        private val KEY_WEAR_LAST_SYNC = longPreferencesKey("wear_last_sync_millis")
        private val KEY_LAST_RACE = stringPreferencesKey("last_race_fingerprint")
    }
}

enum class NotificationType(val label: String, val description: String) {
    SCORE("득점", "롯데가 득점할 때 알림"),
    CONCEDING("실점", "상대가 득점할 때 알림"),
    PITCHER_CHANGE("투수 교체", "양 팀 투수 교체 시 알림"),
    HOMERUN("홈런", "홈런이 나오면 알림 (롯데는 강조)"),
    SCORING_CHANCE("롯데 득점권 찬스", "누가 어떤 타구·볼넷·도루로 득점권이 됐는지 알림"),
    LEAD_CHANGE("역전/동점", "리드가 바뀌거나 동점이 되는 순간 알림"),
    INNING_CHANGE("이닝 교대", "매 이닝 종료 시 중간 스코어 알림"),
    EIGHTH_INNING("8회말", "롯데 경기 8회말 시작 알림"),
    EXTRA_INNINGS("연장 시작", "롯데 경기 연장전 진입 알림"),
    GAME_START("경기 시작", "롯데 경기 시작 알림"),
    GAME_END("경기 종료", "최종 결과 알림"),
    PREGAME_REMINDER("경기 30분 전", "경기 시작 30분 전 리마인더"),
    LINEUP("선발 라인업", "라인업 발표 시 선발투수와 타순 알림"),
    CANCELED("경기 취소", "우천 취소·순연 알림"),
    ROSTER("엔트리 등말소", "롯데 선수 등록·말소 공시 알림"),
    FAVORITE_AT_BAT("즐겨찾기 타석", "즐겨찾기 선수가 타석에 설 때"),
    FAVORITE_PITCHING("즐겨찾기 등판", "즐겨찾기 투수가 마운드에 오를 때"),
    FAVORITE_ROSTER("즐겨찾기 등말소", "즐겨찾기 선수 등록·말소 시"),
    RACE_NUMBER("매직·트래직", "매직넘버·트래직넘버가 줄거나 확정·탈락될 때"),
}

/** 알림을 눌렀을 때 열 화면. `openTab`은 하단 탭/오버레이, `detailTab`은 라이브 상세. */
fun NotificationType.destination(): Pair<String, String?> = when (this) {
    NotificationType.ROSTER, NotificationType.FAVORITE_ROSTER -> "entry" to null
    NotificationType.RACE_NUMBER -> "standings" to null
    NotificationType.LINEUP -> "live" to "lineup"
    NotificationType.SCORE,
    NotificationType.HOMERUN,
    NotificationType.CONCEDING,
    NotificationType.LEAD_CHANGE,
    NotificationType.PITCHER_CHANGE,
    NotificationType.FAVORITE_PITCHING,
    NotificationType.FAVORITE_AT_BAT,
    NotificationType.SCORING_CHANCE,
    -> "live" to "relay"
    else -> "live" to null
}
