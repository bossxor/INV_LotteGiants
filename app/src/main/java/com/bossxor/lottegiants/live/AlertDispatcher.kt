package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.data.*
import com.bossxor.lottegiants.domain.*
import java.time.LocalTime

/** 정책은 배치 시작 시 한 번 읽고, 경기별 상태는 호출자가 명시한다. */
internal class AlertDispatcher(private val store: SnapshotStore) {
    suspend fun batch(context: Context, gameIsLive: Boolean) = AlertBatch(context, store, store.alertPolicy(), gameIsLive)
}
internal class AlertBatch(val context: Context, private val store: SnapshotStore,
    val policy: AlertPolicySnapshot, private val gameIsLive: Boolean) {
    suspend fun dispatch(alerts: List<PlannedAlert>, canceled: List<Pair<Int, String>> = emptyList()) {
        for ((id, key) in canceled) {
            NotificationHelper.cancelEvent(context, id)
            store.removeAlertHistory(key)
        }
        for (alert in alerts) {
            if (!shouldEmitAlert(alert.type in policy.enabled, policy.liveOnly, gameIsLive,
                    policy.quietEnabled, policy.quietStart, policy.quietEnd, LocalTime.now(KBO_ZONE), alert.type)) continue
            NotificationHelper.notifyEvent(context, alert.type, alert.title, alert.text, alert.id,
                alert.gameId, alert.detailTab, alert.silentUpdate, policy.vibrate)
            store.appendAlertHistory(AlertHistoryItem(System.currentTimeMillis(), alert.type.name,
                alert.title, alert.text, alert.eventKey))
        }
    }
}
