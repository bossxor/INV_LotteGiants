package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreAlertsTest {

    @Test
    fun tieToLeadIsNotComeback() {
        assertNull(leadChangeTitle(0, 0, 0, 1, "KIA"))
        assertNull(leadChangeTitle(0, 0, 1, 0, "KIA"))
        assertNull(leadChangeTitle(1, 1, 2, 1, "KIA"))
        assertNull(leadChangeTitle(2, 2, 2, 3, "KIA"))
    }

    @Test
    fun trailingToLeadIsComeback() {
        assertEquals("롯데 역전!", leadChangeTitle(3, 4, 5, 4, "KIA"))
        assertEquals("KIA 역전", leadChangeTitle(5, 3, 5, 6, "KIA"))
    }

    @Test
    fun leadToTieIsTie() {
        assertEquals("동점!", leadChangeTitle(4, 3, 4, 4, "KIA"))
        assertEquals("동점!", leadChangeTitle(1, 2, 2, 2, "KIA"))
    }

    @Test
    fun scoreTitleShowsWhoAndRuns() {
        assertEquals("롯데 득점! 전준우 2점 · 5:4", formatLotteScoreTitle("전준우", 2, "5:4"))
        assertEquals("롯데 득점! 1점 · 1:0", formatLotteScoreTitle(null, 1, "1:0"))
        assertEquals("김도영 1점 실점 · 5:4", formatConcedeTitle("김도영", "KIA", 1, "5:4"))
        assertEquals("KIA 1점 실점 · 0:1", formatConcedeTitle(null, "KIA", 1, "0:1"))
    }

    @Test
    fun pickScoringPrefersKeyword() {
        val texts = listOf(
            RelayText(1, "1구 스트라이크", 0, 3),
            RelayText(2, "전준우 중전 안타 득점", 1, 3),
            RelayText(3, "2구 볼", 0, 3),
        )
        assertEquals("전준우 중전 안타 득점", pickScoringRelay(texts)?.text)
    }

    @Test
    fun notificationOpensRelayTab() {
        assertEquals(3, detailTabIndex("relay"))
        assertEquals(3, detailTabIndex("중계"))
        assertEquals(2, detailTabIndex("요약"))
        assertEquals(0, detailTabIndex(null))
    }
}
