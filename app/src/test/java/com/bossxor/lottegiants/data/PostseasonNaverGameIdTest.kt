package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.naverGameIdFor
import com.bossxor.lottegiants.domain.naverGameIdYmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 네이버 포스트시즌 gameId: WC 4444 · 준PO 3333 · PO 5555 · KS 7777 (2025·2026 일정으로 확인). */
class PostseasonNaverGameIdTest {
    @Test fun postseasonIdsUseSeriesPrefix() {
        assertEquals("44441014OBLG02026", naverGameIdFor("20261014OBLG0", 2026, 4))
        assertEquals("33331020HTLG02026", naverGameIdFor("20261020HTLG0", 2026, 3))
        assertEquals("55551025HTSS02026", naverGameIdFor("20261025HTSS0", 2026, 5))
        assertEquals("77771026HHLG02025", naverGameIdFor("20251026HHLG0", 2025, 7))
        assertEquals("20260930LTKT02026", naverGameIdFor("20260930LTKT0", 2026, 0))
    }

    @Test fun kboGameBuildsNaverIdAndKoreanSeriesIsPostseason() {
        val ks = KboOfficialGame(gameId = "20251026HHLG0", seasonId = 2025, srId = 7, gameScNm = "KS1")
        assertEquals("77771026HHLG02025", ks.naverGameId())
        assertTrue(ks.isPostseason())
    }

    @Test fun dateFromPostseasonIdUsesSeasonYear() {
        assertEquals("20261020", naverGameIdYmd("33331020HTLG02026"))
        assertEquals("20251031", naverGameIdYmd("77771031LGHH02025"))
        assertEquals("20260930", naverGameIdYmd("20260930LTKT02026"))
        assertEquals(java.time.LocalDate.of(2026, 10, 20), parseNaverGameIdDate("33331020HTLG02026"))
    }
}
