package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.data.NotificationType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class GameEventReducerTest {
    private fun game() = LotteGameInfo("g", "2026-10-09", "14:00", "사직", true, "LG", "LG",
        status = GameStatus.LIVE, inning = 7, isTopInning = false, isLotteBatting = true)
    private fun previous() = LiveEventCursor(gameId = "g", seqno = 0, status = GameStatus.LIVE,
        focusScore = 0, opponentScore = 0, inning = 7, isTop = false)
    @Test fun all519RealRelayLinesPassReducerAndSerializedRestartWithoutDuplicateScores() {
        val fixture = Json.parseToJsonElement(javaClass.getResourceAsStream("/relay-20261009LGLT.json")!!.bufferedReader().use { it.readText() }).jsonObject
        val texts = Json.decodeFromString<List<RelayText>>(fixture.getValue("texts").toString())
        val roster = fixture.getValue("roster").jsonArray.mapIndexed { i, name -> LineupSlot(i + 1, name.jsonPrimitive.content, "") }
        var cursor = previous().copy(inning = 1, isTop = true)
        var home = 0; var away = 0
        val seen = mutableListOf<RelayText>()
        assertEquals(519, texts.size)
        for (line in texts) {
            seen += line; home = line.homeScore ?: home; away = line.awayScore ?: away
            val g = game().copy(lotteScore = home, opponentScore = away, inning = line.inning,
                isTopInning = line.isTopInning ?: true, isLotteBatting = line.isTopInning == false,
                lotteLineup = roster, opponentLineup = roster, recentTexts = seen.toList())
            val plan = GameEventReducer.reduce(cursor, g, emptyList(), true)
            cursor = LiveEventCursor.decode(plan.cursor.encode())
            assertEquals("home seq ${line.seqno}", home, cursor.focusScores.records.filter { it.active }.sumOf { it.play.runs })
            assertEquals("away seq ${line.seqno}", away, cursor.opponentScores.records.filter { it.active }.sumOf { it.play.runs })
            assertFalse(GameEventReducer.reduce(cursor, g, emptyList(), true).alerts.any {
                it.type in setOf(NotificationType.SCORE, NotificationType.HOMERUN, NotificationType.CONCEDING) })
        }
        assertEquals(9, home); assertEquals(1, away)
    }

    @Test fun actualPinchRunnerIsNotShownAsBatterInChanceAlert() {
        val fixture = Json.parseToJsonElement(javaClass.getResourceAsStream("/relay-20261009LGLT.json")!!.bufferedReader().use { it.readText() }).jsonObject
        val texts = Json.decodeFromString<List<RelayText>>(fixture.getValue("texts").toString()).filter { it.seqno in 398..403 }
        val g = game().copy(onBase1 = true, onBase3 = true, runnerOn1Name = "박승욱", runnerOn3Name = "고승민",
            currentBatterName = "박승욱", lotteLineup = listOf(LineupSlot(4, "박승욱", ""), LineupSlot(5, "전민재", "")), recentTexts = texts)
        val plan = GameEventReducer.reduce(previous(), g, emptyList(), true)
        val alert = plan.alerts.single { it.type == NotificationType.SCORING_CHANCE }
        assertTrue(alert.text.contains("전민재")); assertTrue(alert.text.contains("박승욱"))
        assertEquals("전민재", plan.cursor.chanceBatter)
        assertTrue(GameEventReducer.reduce(plan.cursor, g, emptyList(), true).alerts.isEmpty())
    }
    @Test fun restoredCursorDoesNotRepeatSameScore() {
        val g = game().copy(lotteScore = 1, recentTexts = listOf(RelayText(10, "전준우 : 좌중간 2루타", 1, 7, false, homeScore = 0, awayScore = 0),
            RelayText(11, "3루주자 윤동희 : 홈인", 1, 7, false, homeScore = 1, awayScore = 0)))
        val first = GameEventReducer.reduce(previous(), g, emptyList(), true)
        assertEquals(1, first.alerts.count { it.type == NotificationType.SCORE })
        val restored = LiveEventCursor.decode(first.cursor.encode())
        assertTrue(GameEventReducer.reduce(restored, g, emptyList(), true).alerts.isEmpty())
    }
    @Test fun chanceSettingIsAnExplicitReducerInput() {
        val g = game().copy(onBase2 = true, runnerOn2Name = "윤동희", currentBatterName = "전준우")
        val old = previous().copy(basesKey = basesKey(false, true, false), chanceBatter = "고승민", chanceBases = NamedBases(null, "윤동희", null))
        assertFalse(GameEventReducer.reduce(old, g, emptyList(), false).alerts.any { it.type == NotificationType.SCORING_CHANCE })
        assertTrue(GameEventReducer.reduce(old, g, emptyList(), true).alerts.any { it.type == NotificationType.SCORING_CHANCE })
    }
}
