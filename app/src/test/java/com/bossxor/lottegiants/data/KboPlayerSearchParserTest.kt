package com.bossxor.lottegiants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KboPlayerSearchParserTest {

    private val samplePage = """
        <html><body>
        <input type="hidden" name="__VIEWSTATE" id="__VIEWSTATE" value="abc" />
        <table>
          <tbody>
            <tr>
              <td>110</td>
              <td><a href='/Futures/Player/HitterDetail.aspx?playerId=54506'>김성균</a></td>
              <td>롯데</td>
              <td>내야수</td>
              <td>2003-10-19</td>
              <td>180cm, 83kg</td>
              <td>서울</td>
            </tr>
            <tr>
              <td>2</td>
              <td><a href='/Record/Player/HitterDetail/Basic.aspx?playerId=69517'>고승민</a></td>
              <td>롯데</td>
              <td>내야수</td>
              <td>2000-08-11</td>
              <td>189cm, 92kg</td>
              <td>부산</td>
            </tr>
            <tr>
              <td>21</td>
              <td><a href='/Record/Player/PitcherDetail/Basic.aspx?playerId=65522'>박세웅</a></td>
              <td>롯데</td>
              <td>투수</td>
              <td>1995-02-27</td>
              <td>177cm, 84kg</td>
              <td>부산</td>
            </tr>
            <tr>
              <td>-</td>
              <td><a href='/Futures/Player/PitcherDetail.aspx?playerId=99999'>무번호</a></td>
              <td>롯데</td>
              <td>투수</td>
              <td>2005-01-01</td>
              <td>180cm, 80kg</td>
              <td>서울</td>
            </tr>
          </tbody>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun parsePlayers_includesFuturesAndFirstTeam() {
        val players = KboPlayerSearchParser.parsePlayers(samplePage)
        assertEquals(4, players.size)
        assertTrue(players.any { it.name == "김성균" && it.backNumber == "110" && it.playerCode == "54506" })
        assertTrue(players.any { it.name == "고승민" && it.backNumber == "2" })
        assertTrue(players.any { it.name == "박세웅" && it.isPitcher && it.backNumber == "21" })
        assertTrue(players.any { it.name == "무번호" && it.backNumber.isEmpty() })
    }
}
