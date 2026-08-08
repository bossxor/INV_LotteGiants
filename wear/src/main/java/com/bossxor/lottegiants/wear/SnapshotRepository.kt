package com.bossxor.lottegiants.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService.getUpdater
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SnapshotRepository {
    private val _snapshot = MutableStateFlow(SajikSnapshot.EMPTY)
    val snapshot: StateFlow<SajikSnapshot> = _snapshot.asStateFlow()

    val current: SajikSnapshot get() = _snapshot.value

    fun updateFromDataMap(map: DataMap) {
        _snapshot.value = SajikSnapshot.fromDataMap(map)
    }

    fun notifyWearUi(context: Context) {
        getUpdater(context).requestUpdate(SajikTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, SajikComplicationService::class.java),
        ).requestUpdateAll()
    }
}
