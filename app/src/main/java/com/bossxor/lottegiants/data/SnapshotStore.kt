package com.bossxor.lottegiants.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bossxor.lottegiants.domain.FavoritePlayer
import com.bossxor.lottegiants.domain.LiveDisplayMode
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.ThemeMode
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

    val themeModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME] ?: ThemeMode.SYSTEM.name
    }

    suspend fun themeMode(): String = themeModeFlow.first()

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

    val liveDisplayModeFlow: Flow<LiveDisplayMode> = context.dataStore.data.map { prefs ->
        runCatching {
            LiveDisplayMode.valueOf(prefs[KEY_LIVE_MODE] ?: LiveDisplayMode.LOCK_NOW.name)
        }.getOrDefault(LiveDisplayMode.LOCK_NOW)
    }

    suspend fun liveDisplayMode(): LiveDisplayMode = liveDisplayModeFlow.first()

    suspend fun setLiveDisplayMode(mode: LiveDisplayMode) {
        context.dataStore.edit { it[KEY_LIVE_MODE] = mode.name }
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

    suspend fun isFavorite(code: String): Boolean = code in favoriteCodes()

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

    companion object {
        private val KEY_SNAPSHOT = stringPreferencesKey("live_snapshot")
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_LIVE_ENABLED = booleanPreferencesKey("live_score_display_enabled")
        private val KEY_LIVE_MODE = stringPreferencesKey("live_display_mode")
        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_done")
        private val KEY_FAVORITES = stringPreferencesKey("favorite_player_codes")
        private val KEY_FAVORITE_PLAYERS = stringPreferencesKey("favorite_players")
        private val KEY_BANNER_DAY = stringPreferencesKey("perm_banner_dismissed_day")
        private val KEY_NOTIFIED_CANCEL = stringPreferencesKey("notified_cancel_game_id")
        private val KEY_NOTIFIED_END = stringPreferencesKey("notified_end_game_id")
        private val KEY_NOTIFIED_ROSTER = stringSetPreferencesKey("notified_roster_keys")
        private val KEY_PENDING_UPDATE_APK = stringPreferencesKey("pending_update_apk")
        private val KEY_PENDING_UPDATE_CODE = stringPreferencesKey("pending_update_code")
    }
}

enum class NotificationType(val label: String, val description: String) {
    SCORE("득점", "롯데가 득점할 때 알림"),
    CONCEDING("실점", "상대가 득점할 때 알림"),
    PITCHER_CHANGE("투수 교체", "양 팀 투수 교체 시 알림"),
    HOMERUN("홈런", "홈런이 나오면 알림 (롯데는 강조)"),
    SCORING_CHANCE("롯데 득점권 찬스", "주자 2·3루 진루, 만루 시 알림"),
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
    FAVORITE_ROSTER("즐겨찾기 등말소", "즐겨찾기 선수 등록·말소 시"),
}
