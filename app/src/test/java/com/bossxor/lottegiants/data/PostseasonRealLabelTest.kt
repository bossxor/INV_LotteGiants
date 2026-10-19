package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.isPostseasonGameLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-10 실제 응답 값: KBO는 `준PO2`(SR_ID 3), 네이버는 roundCode `kbo_ps_sp`·specialMatchInfo 없음. */
class PostseasonRealLabelTest {

    @Test
    fun kboAbbreviatedLabels() {
        listOf("WC1", "준PO2", "PO3", "KS4").forEach { assertTrue(it, isPostseasonGameLabel(it)) }
        assertFalse(isPostseasonGameLabel("정규경기"))
    }

    @Test
    fun kboSeriesIdMarksPostseason() {
        assertTrue(KboOfficialGame(gameScNm = "준PO2", srId = 3).isPostseason())
        assertTrue(KboOfficialGame(gameScNm = "", srId = 3).isPostseason())
        assertFalse(KboOfficialGame(gameScNm = "정규경기", srId = 0).isPostseason())
    }

    @Test
    fun naverRoundCodeMarksPostseason() {
        assertTrue(GameDto(gameId = "x", roundCode = "kbo_ps_sp").isPostseason())
        assertFalse(GameDto(gameId = "x", roundCode = "kbo_r_sp").isPostseason())
    }
}
