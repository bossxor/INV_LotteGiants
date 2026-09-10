package com.bossxor.lottegiants.data

import android.content.Context
import com.bossxor.lottegiants.LauncherIcon
import com.bossxor.lottegiants.domain.normalizeTeamCode
import com.bossxor.lottegiants.live.AlertBootstrap
import com.bossxor.lottegiants.live.AlertWatchService
import com.bossxor.lottegiants.live.LiveScoreService
import com.bossxor.lottegiants.widget.WidgetUpdater

/** 내 팀을 바꾸고 스냅샷·알림·위젯·런처를 한 번에 맞춘다. */
object TeamSwitcher {

    suspend fun switchTeam(context: Context, teamCode: String) {
        val app = context.applicationContext
        val repo = GiantsRepository.get(app)
        val code = normalizeTeamCode(teamCode)
        val prev = repo.store.myTeamCode()
        repo.store.setMyTeamCode(code)
        LauncherIcon.request(code)
        if (code != prev) {
            repo.store.clearTeamTransientKeys()
            repo.clearTeamCaches()
        }
        runCatching { repo.refreshSnapshot(force = true) }
        WidgetUpdater.updateAll(app)
        AlertBootstrap.run(app)
        if (repo.store.isLiveScoreEnabled()) {
            LiveScoreService.start(app)
        } else {
            LiveScoreService.stop(app)
        }
        AlertWatchService.startIfNeeded(app)
    }

    suspend fun afterImport(context: Context) {
        val app = context.applicationContext
        val repo = GiantsRepository.get(app)
        LauncherIcon.request(repo.store.myTeamCode())
        repo.clearTeamCaches()
        runCatching { repo.refreshSnapshot(force = true) }
        WidgetUpdater.updateAll(app)
        AlertBootstrap.run(app)
        AlertWatchService.startIfNeeded(app)
    }
}
