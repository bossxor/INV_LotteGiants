package com.bossxor.lottegiants.domain

import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class RecordDisplayScopeTest {
    @Test fun fallbackMonthNeverClaimsSeasonRecord() {
        val scope = recordDisplayScope(listOf("2026-10-03", "2026-10-09"), false, YearMonth.of(2026, 10))
        assertEquals("2026년 10월 전적", scope.label)
        assertEquals("2026-10-03 ~ 2026-10-09 조회 결과", scope.range)
    }
    @Test fun pendingMonthSelectionUsesActualLoadedMonth() {
        assertEquals("2026년 9월 전적", recordDisplayScope(listOf("2026-09-30"), false, YearMonth.of(2026, 10)).label)
    }
    @Test fun partialSeasonDoesNotClaimCompleteSeason() {
        val scope = recordDisplayScope(listOf("2026-03-28", "2026-10-09"), true, YearMonth.of(2026, 10))
        assertEquals("조회된 경기 전적", scope.label)
        assertEquals("2026-03-28 ~ 2026-10-09 조회 결과", scope.range)
    }
    @Test fun emptyAndMalformedDatesRemainExplicit() {
        assertEquals("조회된 경기 없음", recordDisplayScope(listOf("", "bad"), false, YearMonth.of(2026, 10)).range)
    }
}
