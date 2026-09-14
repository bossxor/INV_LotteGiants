package com.bossxor.lottegiants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KboRegisterAllParserTest {

    private val sampleRowHtml = """
        <tr>
          <th scope="row" class="fir">롯데<br/><br/>45명</th>
          <td><ul><li>김태형(88)</li></ul></td>
          <td><ul><li>조재영(70)</li><li>이재율(71)</li></ul></td>
          <td><ul><li>박세웅(21)</li><li>김원중(34)</li></ul></td>
          <td><ul><li>유강남(27)</li></ul></td>
          <td><ul><li>나승엽(51)</li><li>한동희(25)</li></ul></td>
          <td class="last"><ul><li>전준우(8)</li><li>황성빈(0)</li></ul></td>
        </tr>
    """.trimIndent()

    private val samplePage = """
        <html><body>
        <table><tr><td>이서준</td><td>내</td><td>롯데</td></tr></table>
        <table>
          <caption>전체등록현황</caption>
          $sampleRowHtml
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parsePlayersFromRow_extractsPitchersAndFielders() {
        val row =
            "롯데 45명|김태형(88)|조재영(70)이재율(71)|" +
                "박세웅(21)김원중(34)|유강남(27)|나승엽(51)한동희(25)|전준우(8)황성빈(0)"
        val players = KboRegisterAllParser.parsePlayersFromRow(row)
        assertTrue(players.any { it.name == "박세웅" && it.backNumber == "21" && it.isPitcher })
        assertTrue(players.any { it.name == "나승엽" && it.backNumber == "51" && it.position == "내야수" })
        assertTrue(players.any { it.name == "황성빈" && it.backNumber == "0" })
        assertTrue(players.none { it.name == "김태형" })
        assertTrue(players.none { it.name == "조재영" })
        val numbers = players.map { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }
        assertEquals(numbers.sorted(), numbers)
    }

    @Test
    fun parseTeamPlayers_fromRealHtmlShape() {
        val players = KboRegisterAllParser.parseTeamPlayers(samplePage, "LT")
        assertEquals(7, players.size)
        assertTrue(players.any { it.name == "박세웅" && it.backNumber == "21" })
        assertTrue(players.first().backNumber.toIntOrNull() != null)
        assertEquals("0", players.first { it.name == "황성빈" }.backNumber)
        assertEquals(listOf(0, 8, 21, 25, 27, 34, 51), players.map { it.backNumber.toInt() })
    }

    @Test
    fun parseDownloadedRegisterAll_ifPresent() {
        val stream = javaClass.classLoader?.getResourceAsStream("register_all_sample.html")
            ?: return // 로컬 샘플 없으면 스킵
        val html = stream.bufferedReader(Charsets.UTF_8).readText()
        val players = KboRegisterAllParser.parseTeamPlayers(html, "LT")
        assertTrue("롯데 선수 ${players.size}명", players.size >= 20)
        assertTrue(players.any { it.backNumber == "21" })
        assertTrue(players.none { it.backNumber.isBlank() })
    }
}
