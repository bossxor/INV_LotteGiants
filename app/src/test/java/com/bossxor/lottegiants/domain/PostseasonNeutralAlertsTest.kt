package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 포스트시즌은 응원팀과 무관하게 양 팀을 똑같이 알린다. */
class PostseasonNeutralAlertsTest {
    private fun game(label: String) = LotteGameInfo("g", "2026-10-18", "18:30", "광주", true, "LG", "LG",
        gameScLabel = label, status = GameStatus.LIVE, inning = 3, isTopInning = true, isLotteBatting = false,
        opponentScore = 1,
        recentTexts = listOf(
            RelayText(10, "김현수 : 우중간 적시타", 1, 3, true, homeScore = 0, awayScore = 0),
            RelayText(11, "3루주자 문성주 : 홈인", 1, 3, true, homeScore = 0, awayScore = 1),
        ))
    private fun previous() = LiveEventCursor(gameId = "g", seqno = 0, status = GameStatus.LIVE,
        focusScore = 0, opponentScore = 0, inning = 3, isTop = true)

    @Test fun postseasonAwayScoringIsPlainScoreAlertWithTeamName() {
        val alert = GameEventReducer.reduce(previous(), game("준PO2"), emptyList(), true).alerts
            .single { it.type == NotificationType.SCORE }
        assertTrue(alert.title, alert.title.startsWith("LG 득점!"))
        assertTrue(alert.title, alert.title.endsWith("1:0"))
    }

    @Test fun regularSeasonKeepsConcedeWording() {
        val alerts = GameEventReducer.reduce(previous(), game("정규경기"), emptyList(), true).alerts
        assertTrue(alerts.any { it.type == NotificationType.CONCEDING && it.title.startsWith("실점!") })
        assertFalse(alerts.any { it.type == NotificationType.SCORE })
    }

    @Test fun neutralScoreIsAwayThenHome() {
        val g = game("준PO2")
        assertEquals("1:0", g.neutralScore)
        assertEquals("LG", g.awayTeamLabel)
        assertTrue(g.isPostseason)
        assertFalse(game("정규경기").isPostseason)
    }

    @Test fun leadChangeNeutralAddsExclamationForBothTeams() {
        assertEquals("LG 역전!", leadChangeTitle(2, 1, 2, 3, "LG", "롯데", neutral = true))
        assertEquals("LG 역전", leadChangeTitle(2, 1, 2, 3, "LG", "롯데"))
        assertEquals("롯데 역전!", leadChangeTitle(1, 2, 3, 2, "LG", "롯데", neutral = true))
    }
}
