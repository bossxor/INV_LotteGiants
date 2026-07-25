package com.bossxor.lottegiants.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bossxor.lottegiants.domain.LiveSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    companion object {
        private val KEY_SNAPSHOT = stringPreferencesKey("live_snapshot")
    }
}

enum class NotificationType(val label: String, val description: String) {
    SCORE("득점", "양 팀 득점 시 득점 과정과 함께 알림"),
    PITCHER_CHANGE("투수 교체", "양 팀 투수 교체 시 알림"),
    HOMERUN("홈런", "홈런이 나오면 알림 (롯데는 강조)"),
    SCORING_CHANCE("롯데 득점권 찬스", "주자 2·3루 진루, 만루 시 알림"),
    LEAD_CHANGE("역전/동점", "리드가 바뀌거나 동점이 되는 순간 알림"),
    INNING_CHANGE("이닝 교대", "매 이닝 종료 시 중간 스코어 알림"),
    GAME_START_END("경기 시작/종료", "경기 시작과 최종 결과 알림"),
    PREGAME_REMINDER("경기 30분 전", "경기 시작 30분 전 리마인더"),
    LINEUP("선발 라인업", "라인업 발표 시 선발투수와 타순 알림"),
    CANCELED("경기 취소", "우천 취소·순연 알림"),
}
