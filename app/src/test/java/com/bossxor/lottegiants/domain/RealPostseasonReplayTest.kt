package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10-20 준PO3 KIA–LG(13:1) 네이버 중계 621줄을 몇 줄씩 나눠 받으며 득점 알림 문구를 확인한다. */
class RealPostseasonReplayTest {
    private fun replay(): List<PlannedAlert> {
        val fx = Json.parseToJsonElement(javaClass.getResourceAsStream("/relay-20261020HTLG.json")!!.bufferedReader().use { it.readText() }).jsonObject
        val texts = Json.decodeFromString<List<RelayText>>(fx.getValue("texts").toString())
        val roster = fx.getValue("roster").jsonArray.mapIndexed { i, n -> LineupSlot(i + 1, n.jsonPrimitive.content, "") }
        var cursor = LiveEventCursor(gameId = "g", seqno = 0, status = GameStatus.LIVE, focusScore = 0, opponentScore = 0, inning = 1, isTop = true)
        var home = 0; var away = 0
        val seen = mutableListOf<RelayText>()
        val alerts = mutableListOf<PlannedAlert>()
        for ((i, line) in texts.withIndex()) {
            seen += line; home = line.homeScore ?: home; away = line.awayScore ?: away
            if (i % 3 != 2 && i != texts.lastIndex) continue
            val g = LotteGameInfo("g", "2026-10-20", "18:30", "잠실", true, "HT", "KIA", gameScLabel = "준PO3",
                focusTeamCode = "LG", focusTeamName = "LG",
                status = GameStatus.LIVE, lotteScore = home, opponentScore = away, inning = line.inning,
                isTopInning = line.isTopInning ?: true, isLotteBatting = line.isTopInning == false,
                currentBatterName = seen.last().batterTitle.substringAfter(" ").trim(),
                lotteLineup = roster, opponentLineup = roster, recentTexts = seen.toList())
            val plan = GameEventReducer.reduce(cursor, g, emptyList(), true)
            cursor = plan.cursor
            alerts += plan.alerts.filter { it.type in setOf(NotificationType.SCORE, NotificationType.HOMERUN, NotificationType.CONCEDING) }
        }
        return alerts
    }

    @Test fun scoreAlertsNameTheHitterAndRunsNotTheNextBatter() {
        val alerts = replay()
        // 득점 뒤 타석에 선 다음 타자(박재현·카스트로 등)를 본문에 넣지 않는다.
        assertFalse(alerts.any { it.text.contains("타석") })
        val slam = alerts.first { it.title.contains("김도영") }
        assertEquals("KIA 홈런! 김도영 좌중간 만루 홈런 · 5:0", slam.title)
        assertTrue(alerts.any { it.title == "KIA 홈런! 나성범 중견 솔로 홈런 · 1:0" })
        assertTrue(alerts.any { it.title == "KIA 득점! 박정우 좌익 1루타 · 1타점 · 2:0" })
        assertTrue(alerts.any { it.title == "KIA 득점! 박민 좌익 2루타 · 2타점 · 13:1" })
        assertTrue(alerts.any { it.title == "LG 득점! · 1득점 · 9:1" })
        assertFalse(alerts.any { it.type == NotificationType.CONCEDING })
    }
}
