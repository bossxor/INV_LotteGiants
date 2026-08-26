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
    private const val PREFS = "sajik_wear"
    private val _snapshot = MutableStateFlow(SajikSnapshot.EMPTY)
    val snapshot: StateFlow<SajikSnapshot> = _snapshot.asStateFlow()

    val current: SajikSnapshot get() = _snapshot.value

    fun hydrate(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("updatedAt")) return
        _snapshot.value = SajikSnapshot(
            updatedAt = p.getLong("updatedAt", 0L),
            status = p.getString("status", "").orEmpty(),
            lotteScore = p.getInt("lotteScore", 0),
            oppScore = p.getInt("oppScore", 0),
            opponent = p.getString("opponent", "").orEmpty(),
            inning = p.getString("inning", "").orEmpty(),
            ball = p.getInt("ball", 0),
            strike = p.getInt("strike", 0),
            out = p.getInt("out", 0),
            on1 = p.getBoolean("on1", false),
            on2 = p.getBoolean("on2", false),
            on3 = p.getBoolean("on3", false),
            pitcher = p.getString("pitcher", "").orEmpty(),
            batter = p.getString("batter", "").orEmpty(),
            nextBatter = p.getString("nextBatter", "").orEmpty(),
            lotteBatting = p.getBoolean("lotteBatting", false),
            highlight = p.getString("highlight", "").orEmpty(),
            startTime = p.getString("startTime", "").orEmpty(),
            starterLotte = p.getString("starterLotte", "").orEmpty(),
            starterOpp = p.getString("starterOpp", "").orEmpty(),
        )
    }

    fun updateFromDataMap(context: Context, map: DataMap) {
        val snap = SajikSnapshot.fromDataMap(map)
        _snapshot.value = snap
        persist(context, snap)
        WearLiveNotifier.update(context, snap)
        notifyWearUi(context)
    }

    private fun persist(context: Context, snap: SajikSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("updatedAt", snap.updatedAt)
            .putString("status", snap.status)
            .putInt("lotteScore", snap.lotteScore)
            .putInt("oppScore", snap.oppScore)
            .putString("opponent", snap.opponent)
            .putString("inning", snap.inning)
            .putInt("ball", snap.ball)
            .putInt("strike", snap.strike)
            .putInt("out", snap.out)
            .putBoolean("on1", snap.on1)
            .putBoolean("on2", snap.on2)
            .putBoolean("on3", snap.on3)
            .putString("pitcher", snap.pitcher)
            .putString("batter", snap.batter)
            .putString("nextBatter", snap.nextBatter)
            .putBoolean("lotteBatting", snap.lotteBatting)
            .putString("highlight", snap.highlight)
            .putString("startTime", snap.startTime)
            .putString("starterLotte", snap.starterLotte)
            .putString("starterOpp", snap.starterOpp)
            .apply()
    }

    fun notifyWearUi(context: Context) {
        runCatching { getUpdater(context).requestUpdate(SajikTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, SajikComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
