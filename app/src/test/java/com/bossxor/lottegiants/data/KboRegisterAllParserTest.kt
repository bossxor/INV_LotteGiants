package com.bossxor.lottegiants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KboRegisterAllParserTest {

    @Test
    fun parsePlayersFromRow_extractsPitchersAndFielders() {
        val row =
            "롯데 45명|김태형(88)|조재영(70)이재율(71)|" +
                "박세웅(21)김원중(34)|유강남(27)|나승엽(51)한동희(25)|전준우(8)황성빈(0)"
        val players = KboRegisterAllParser.parsePlayersFromRow(row)
        assertTrue(players.any { it.name == "박세웅" && it.backNumber == "21" && it.isPitcher })
        assertTrue(players.any { it.name == "나승엽" && it.backNumber == "51" && it.position == "내야수" })
        assertTrue(players.any { it.name == "황성빈" && it.backNumber == "0" })
        assertTrue(players.none { it.name == "김태형" }) // 감독 제외
        assertTrue(players.none { it.name == "조재영" }) // 코치 제외
        val numbers = players.map { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }
        assertEquals(numbers.sorted(), numbers)
    }
}
