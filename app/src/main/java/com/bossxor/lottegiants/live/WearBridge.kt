package com.bossxor.lottegiants.live

import android.content.Context
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.inningLabel
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WearBridge {
    const val PATH_SNAPSHOT = "/sajik/snapshot"
    const val PATH_EVENT = "/sajik/event"

    suspend fun syncSnapshot(context: Context, snap: LiveSnapshot?) {
        runCatching {
            val game = snap?.lotteGame ?: snap?.nextLotteGame
            val req = PutDataMapRequest.create(PATH_SNAPSHOT).apply {
                dataMap.putLong("updatedAt", snap?.updatedAtMillis ?: 0L)
                dataMap.putString("status", game?.status?.name.orEmpty())
                dataMap.putInt("lotteScore", game?.lotteScore ?: 0)
                dataMap.putInt("oppScore", game?.opponentScore ?: 0)
                dataMap.putString("opponent", game?.opponentName.orEmpty())
                dataMap.putString("inning", game?.inningLabel.orEmpty())
                dataMap.putInt("ball", game?.ball ?: 0)
                dataMap.putInt("strike", game?.strike ?: 0)
                dataMap.putInt("out", game?.out ?: 0)
                dataMap.putBoolean("on1", game?.onBase1 == true)
                dataMap.putBoolean("on2", game?.onBase2 == true)
                dataMap.putBoolean("on3", game?.onBase3 == true)
                dataMap.putString("pitcher", game?.currentPitcherName.orEmpty())
                dataMap.putString("batter", game?.currentBatterName.orEmpty())
                dataMap.putString("nextBatter", game?.nextBatterName.orEmpty())
                dataMap.putBoolean("lotteBatting", game?.isLotteBatting == true)
                dataMap.putString("highlight", snap?.highlightText.orEmpty())
                dataMap.putString("startTime", game?.startTime.orEmpty())
                dataMap.putString("starterLotte", game?.lotteStartingPitcher.orEmpty())
                dataMap.putString("starterOpp", game?.opponentStartingPitcher.orEmpty())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req).await()
        }
    }

    fun sendScoreEvent(context: Context, message: String) {
        runCatching {
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes ->
                    nodes.forEach { node ->
                        Wearable.getMessageClient(context)
                            .sendMessage(node.id, PATH_EVENT, message.toByteArray(Charsets.UTF_8))
                    }
                }
        }
    }
}
