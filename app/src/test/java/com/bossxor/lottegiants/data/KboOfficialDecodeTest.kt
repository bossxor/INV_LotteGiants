package com.bossxor.lottegiants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KboOfficialDecodeTest {

    @Test
    fun gameListAllowsNullScalarsBeforeFirstPitch() {
        val json = """
            {"game":[{
              "G_ID":"20260910KTLT0","SEASON_ID":2026,"G_DT":"20260910",
              "AWAY_ID":"KT","HOME_ID":"LT","AWAY_NM":"KT","HOME_NM":"롯데",
              "GAME_STATE_SC":"1","GAME_TB_SC":null,"GAME_INN_NO":null,
              "STRIKE_CN":null,"BALL_CN":null,"OUT_CN":null,
              "T_SCORE_CN":"0","B_SCORE_CN":"0"
            }]}
        """.trimIndent()
        val parsed = NaverSportsApi.json.decodeFromString(KboGameListResponse.serializer(), json)
        assertEquals(1, parsed.game.size)
        val g = parsed.game[0]
        assertEquals("LT", g.homeId)
        assertEquals("KT", g.awayId)
        assertEquals("", g.topBottom)
        assertEquals(0, g.inning)
        assertTrue(g.involvesTeam("LT"))
    }
}
