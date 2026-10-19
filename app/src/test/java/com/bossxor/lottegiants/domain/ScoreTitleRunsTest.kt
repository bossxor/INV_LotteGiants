package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 누적 점수 차이를 "이번 득점"으로 쓰지 않는다. */
class ScoreTitleRunsTest {
    @Test
    fun catchUpDeltaDoesNotClaimSixRuns() {
        assertEquals("KIA 득점! · 6:2", formatLotteScoreTitle(null, 6, "6:2", null, "KIA", null))
    }

    @Test
    fun singleRunStillShown() {
        assertEquals("KIA 득점! · 1득점 · 3:2", formatLotteScoreTitle(null, 1, "3:2", null, "KIA", null))
    }

    @Test
    fun knownPlayKeepsCountAndRbi() {
        assertEquals("KIA 득점! 김도영 우중간 2루타 · 2타점 · 4:2",
            formatLotteScoreTitle("김도영", 2, "4:2", "우중간 2루타", "KIA", 2))
        assertEquals("KIA 득점! 김도영 적시타 · 2득점 · 4:2",
            formatLotteScoreTitle("김도영", 2, "4:2", "적시타", "KIA", null))
    }

    @Test
    fun unknownMultiRunConcedeOmitsCount() {
        assertEquals("실점! LG · 5:2", formatConcedeTitle(null, "LG", 3, "5:2", null))
    }

    @Test
    fun homerunNeverSaysMoreThanFour() {
        assertEquals("KIA 홈런! 홈런 · 6:2", formatHomerunTitle(null, 6, "6:2", null, "KIA"))
    }
}
