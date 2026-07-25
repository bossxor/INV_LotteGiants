package com.bossxor.lottegiants

import android.app.Application
import com.bossxor.lottegiants.live.GameSchedulerWorker
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.live.NotificationHelper
import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.GameStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GiantsApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        GameSchedulerWorker.enqueue(this)
        scope.launch {
            runCatching {
                val snap = GiantsRepository.get(this@GiantsApp).refreshSnapshot()
                if (snap.lotteGame?.status == GameStatus.LIVE) {
                    LiveScoreService.start(this@GiantsApp)
                }
            }
        }
    }
}
