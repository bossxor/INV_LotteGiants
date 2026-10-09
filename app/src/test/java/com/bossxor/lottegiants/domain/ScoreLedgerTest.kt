package com.bossxor.lottegiants.domain

import org.junit.Assert.*
import org.junit.Test

class ScoreLedgerTest {
    private val roster = listOf("전준우", "윤동희", "레이예스", "고승민")
    private fun line(seq: Int, text: String, score: Int) = RelayText(seq, text, 1, 5, true,
        batterTitle = "4번타자 전준우", homeScore = 0, awayScore = score)
    private val hit = line(10, "전준우 : 좌중간 2루타", 0)
    private val first = line(11, "3루주자 윤동희 : 홈인", 1)
    private val second = line(12, "2루주자 레이예스 : 홈인", 2)

    @Test fun splitHomeInsEnrichSameNotification() {
        val one = reconcileScores(ScoreLedger(), listOf(hit, first), 9, 0, 1, false, roster)
        val two = reconcileScores(one.ledger, listOf(hit, first, second), 11, 1, 2, false, roster)
        assertEquals(one.updates.single().record.id, two.updates.single().record.id)
        assertTrue(two.updates.single().silent)
        assertEquals(2, two.updates.single().record.play.rbi)
        assertEquals(listOf("윤동희", "레이예스"), two.updates.single().record.play.scorers)
        assertEquals("전준우", two.updates.single().record.play.who)
    }
    @Test fun scoreBeforeTextIsEnrichedWithoutNewScore() {
        val early = reconcileScores(ScoreLedger(), emptyList(), 9, 0, 1, false, roster)
        val late = reconcileScores(early.ledger, listOf(hit, first), 9, 1, 1, false, roster)
        assertEquals(early.updates.single().record.id, late.updates.single().record.id)
        assertEquals("전준우", late.updates.single().record.play.who)
        assertTrue(late.updates.single().silent)
    }
    @Test fun sameDataAfterRestoreDoesNotNotifyAgain() {
        val one = reconcileScores(ScoreLedger(), listOf(hit, first), 9, 0, 1, false, roster)
        val cursor = LiveEventCursor(listOf("game"), one.ledger)
        val restored = LiveEventCursor.decode(cursor.encode())
        assertTrue(reconcileScores(restored.focusScores, listOf(hit, first), 11, 1, 1, false, roster).updates.isEmpty())
    }
    @Test fun correctedScoreDoesNotSoundAgainWhenRestored() {
        val one = reconcileScores(ScoreLedger(), listOf(hit, first), 9, 0, 1, false, roster)
        val corrected = reconcileScores(one.ledger, emptyList(), 11, 1, 0, false, roster)
        assertTrue(corrected.updates.single().correction)
        val restored = reconcileScores(corrected.ledger, listOf(hit, first), 11, 0, 1, false, roster)
        assertTrue(restored.updates.single().silent)
    }
    @Test fun separatePlateAppearancesProduceSeparateEvents() {
        val texts = listOf(hit, first, line(20, "고승민 : 우전 1루타", 1), line(21, "2루주자 전준우 : 홈인", 2))
        val result = reconcileScores(ScoreLedger(), texts, 9, 0, 2, false, roster)
        assertEquals(2, result.updates.size)
        assertNotEquals(result.updates[0].record.id, result.updates[1].record.id)
        assertEquals("고승민", result.updates[1].record.play.who)
    }
    @Test fun legacyCursorIsReadable() {
        val cursor = LiveEventCursor.decode("game|11|1|0|true,false,false|P|B|LIVE|next||runner;;|5|true")
        assertEquals("game", cursor.gameId)
        assertEquals(5, cursor.inning)
    }
    @Test fun delayedTwoSeparatePlaysSplitOnePendingEventSilently() {
        val early = reconcileScores(ScoreLedger(), emptyList(), 9, 0, 2, false, roster)
        val late = reconcileScores(early.ledger, listOf(hit, first,
            line(20, "고승민 : 우전 1루타", 1), line(21, "2루주자 전준우 : 홈인", 2)), 9, 2, 2, false, roster)
        assertEquals(2, late.ledger.records.size)
        assertEquals(2, late.ledger.records.sumOf { it.play.runs })
        assertTrue(late.updates.all { it.silent })
        assertEquals(early.updates.single().record.id, late.updates.first().record.id)
    }
    @Test fun pendingRemainderOfSamePlayIsMergedAndRemoved() {
        val firstPoll = reconcileScores(ScoreLedger(), listOf(hit, first), 9, 0, 1, false, roster)
        val scoreFirst = reconcileScores(firstPoll.ledger, listOf(hit, first), 11, 1, 2, false, roster)
        assertEquals(2, scoreFirst.ledger.records.size)
        val enriched = reconcileScores(scoreFirst.ledger, listOf(hit, first, second), 11, 2, 2, false, roster)
        assertEquals(1, enriched.ledger.records.size)
        assertEquals(2, enriched.ledger.records.single().play.runs)
        assertEquals(listOf(2), enriched.supersededIds)
        assertTrue(enriched.updates.all { it.silent })
    }
    @Test fun partialScoreCorrectionUpdatesRunsAndScorers() {
        val full = reconcileScores(ScoreLedger(), listOf(hit, first, second), 9, 0, 2, false, roster)
        val corrected = reconcileScores(full.ledger, listOf(hit, first, second), 12, 2, 1, false, roster)
        assertEquals(1, corrected.ledger.records.single().play.runs)
        assertEquals(listOf("윤동희"), corrected.ledger.records.single().play.scorers)
        assertTrue(corrected.updates.all { it.silent })
    }
    @Test fun unscoredHitCannotExplainAnEarlierPendingScore() {
        val early = reconcileScores(ScoreLedger(), emptyList(), 9, 0, 1, false, roster)
        val ambiguousHit = hit.copy(homeScore = null, awayScore = null)
        val late = reconcileScores(early.ledger, listOf(ambiguousHit), 9, 1, 1, false, roster)
        assertNull(late.ledger.records.single().play.who)
        assertTrue(late.updates.isEmpty())
    }
}
