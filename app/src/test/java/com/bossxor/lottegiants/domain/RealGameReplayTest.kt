package com.bossxor.lottegiants.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** 2026-10-09 LG–롯데 공식 중계의 공개 텍스트/상태만 보존한 재현 자료. */
class RealGameReplayTest {
    private val fixture = Json.parseToJsonElement(javaClass.getResourceAsStream("/relay-20261009LGLT.json")!!
        .bufferedReader().use { it.readText() }).jsonObject
    private val texts = Json.decodeFromString<List<RelayText>>(fixture.getValue("texts").toString())
    private val roster = fixture.getValue("roster").jsonArray.map { it.jsonPrimitive.content }

    @Test fun everyPublishedLineReplaysWithoutDuplicateRuns() {
        for (home in listOf(true, false)) {
            var ledger = ScoreLedger()
            var previous = 0
            var seq = 0
            val seen = mutableListOf<RelayText>()
            for (line in texts) {
                seen += line
                val score = (if (home) line.homeScore else line.awayScore) ?: previous
                val result = reconcileScores(ledger, seen, seq, previous, score, home, roster)
                ledger = result.ledger
                previous = score
                seq = line.seqno
                assertEquals("누적 점수: ${line.seqno}", score, ledger.records.filter { it.active }.sumOf { it.play.runs })
                assertEquals("알림 ID 중복: ${line.seqno}", ledger.records.size, ledger.records.distinctBy { it.id }.size)
            }
            assertEquals(if (home) 9 else 1, previous)
            if (home) {
                val han = ledger.records.single { it.play.who == "한동희" }
                assertEquals("좌중간 1루타", han.play.how)
                assertEquals(listOf("나승엽"), han.play.scorers)
                assertEquals(1, han.play.rbi)
                assertTrue(ledger.records.any { it.play.who == "레이예스" && "김동혁" in it.play.scorers })
            }
            assertTrue(reconcileScores(ledger, texts, seq, previous, previous, home, roster).updates.isEmpty())
        }
    }

    @Test fun oneRecoveryFetchPreservesSeparateScoringPlays() {
        val result = reconcileScores(ScoreLedger(), texts, 0, 0, 9, true, roster)
        assertEquals(9, result.ledger.records.sumOf { it.play.runs })
        assertTrue(result.ledger.records.any { it.play.who == "한동희" && it.play.scorers == listOf("나승엽") })
        assertTrue(result.ledger.records.size > 3)
    }

    @Test fun actualPinchRunnerDoesNotBecomeCurrentBatter() {
        val slice = texts.filter { it.seqno in 398..403 }
        val game = LotteGameInfo("game", "2026-10-09", "14:00", "사직", true, "LG", "LG",
            inning = 7, isTopInning = false, isLotteBatting = true, currentBatterName = "박승욱",
            lotteLineup = listOf(LineupSlot(4, "박승욱", ""), LineupSlot(5, "전민재", "")),
            recentTexts = slice)
        assertEquals("전민재", resolvedAtBat(game, NamedBases("박승욱", null, "고승민")))
    }
}
