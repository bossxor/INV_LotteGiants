package com.bossxor.lottegiants.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** 스냅샷 갱신 후 홈 위젯을 다시 그리게 하는 헬퍼 */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { LotteWidget().updateAll(context) }
    }
}
