package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.LotteGameInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** 포스트시즌 간이 MVP는 응원팀과 무관하게 양 팀 전체에서 고른다. */
class PostseasonMvpTest {
    private fun game(label: String) = LotteGameInfo("g", "2026-10-20", "18:30", "잠실", true, "HT", "KIA",
        gameScLabel = label, status = GameStatus.ENDED,
        lotteLineup = listOf(LineupSlot(3, "오스틴", "", todayHits = 0, todayAtBats = 4, todayRbi = 1)),
        opponentLineup = listOf(LineupSlot(3, "김도영", "", todayHits = 3, todayAtBats = 4, todayRbi = 4, todayRun = 2)))

    @Test fun postseasonPicksBestAcrossBothTeams() {
        val (name, line) = provisionalMvp(game("준PO3"))
        assertEquals("김도영", name)
        assertEquals("KIA · 3안타 4타점 2득점 (3/4)", line)
    }

    @Test fun regularSeasonKeepsMyTeamOnly() {
        val (name, line) = provisionalMvp(game("정규경기"))
        assertEquals("오스틴", name)
        assertEquals("0안타 1타점 (0/4)", line)
    }
}
