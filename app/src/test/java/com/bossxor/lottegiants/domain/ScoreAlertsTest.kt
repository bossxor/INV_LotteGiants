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
    fun scoreTitleShowsWhoHowAndRbi() {
        assertEquals("롯데 득점! 전준우 좌전 적시타 · 2타점 · 5:4", formatLotteScoreTitle("전준우", 2, "5:4", "좌전 적시타"))
        assertEquals("롯데 득점! 전준우 · 2타점 · 5:4", formatLotteScoreTitle("전준우", 2, "5:4"))
        assertEquals("롯데 득점! · 1타점 · 1:0", formatLotteScoreTitle(null, 1, "1:0"))
        assertEquals("실점! 김도영 적시타 · 1점 · 5:4", formatConcedeTitle("김도영", "KIA", 1, "5:4", "적시타"))
        assertEquals("실점! KIA · 1점 · 0:1", formatConcedeTitle(null, "KIA", 1, "0:1"))
    }

    @Test
    fun pickPlayerPrefersBatterTitleOverEarlierLineupName() {
        val text = "황성빈이 홈인하고 전준우의 좌전 적시타"
        assertEquals(
            "전준우",
            pickPlayerName(text, "8번타자 전준우", listOf("황성빈", "전준우", "윤동희")),
        )
    }

    @Test
    fun pickPlayerDoesNotMatchShorterNameInsideLonger() {
        assertEquals(
            "김민석",
            pickPlayerName("김민석 중전 안타", "", listOf("김민", "김민석")),
        )
    }

    @Test
    fun stealUsesRunnerInTextNotBatter() {
        assertEquals(
            "황성빈",
            pickPlayerName("2루주자 황성빈 도루 성공", "8번타자 전준우", listOf("황성빈", "전준우")),
        )
    }

    @Test
    fun scoringChanceShowsWhoHowAndNamedRunners() {
        val alert = formatScoringChanceAlert(
            loaded = false,
            who = "전준우",
            how = "좌전 2루타",
            runners = "2루 전준우",
            batterNow = "윤동희",
            inningLabel = "5회말",
            outs = 1,
        )
        assertEquals("전준우 좌전 2루타, 득점권 · 타석 윤동희", alert.title)
        assertEquals("2루 전준우 · 타석 윤동희 · 5회말 1아웃", alert.text)
    }

    @Test
    fun basesLoadedChanceTitle() {
        val alert = formatScoringChanceAlert(
            loaded = true,
            who = "황성빈",
            how = "볼넷",
            runners = "3루 전준우 · 2루 윤동희 · 1루 황성빈",
            batterNow = "고승민",
            inningLabel = "6회말",
            outs = 0,
        )
        assertEquals("황성빈 볼넷, 만루 · 타석 고승민", alert.title)
        assertEquals("3루 전준우 · 2루 윤동희 · 1루 황성빈 · 타석 고승민 · 6회말 0아웃", alert.text)
    }

    @Test
    fun atBatPrefersNextWhenMakerStillListed() {
        assertEquals("윤동희", atBatForChance("전준우", "윤동희", "전준우"))
        assertEquals("윤동희", atBatForChance("윤동희", "고승민", "전준우"))
        assertEquals("전준우", atBatForChance("전준우", "", "전준우"))
    }

    @Test
    fun describePlayHowKeepsDirection() {
        assertEquals("좌전 2루타", describePlayHow("전준우 : 좌전 2루타"))
        assertEquals("볼넷", describePlayHow("황성빈 볼넷으로 출루"))
        assertEquals("도루", describePlayHow("2루주자 황성빈 도루 성공"))
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
