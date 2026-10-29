package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.kboToday
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** KBO 장애 때 같은 날짜를 폴링마다 다시 조회하지 않는다. */
class ScheduleSourceTest {
    private class FakeApi(var fail: Boolean) : KboOfficialApi {
        var calls = 0
        var games = emptyList<KboOfficialGame>()
        override suspend fun getGameList(leId: Int, srId: String, date: String): KboGameListResponse {
            calls++
            if (fail) throw java.io.IOException("down")
            return KboGameListResponse(code = "100", game = games)
        }
        override suspend fun getTeamRank(leId: Int, srId: Int) = throw NotImplementedError()
        override suspend fun getScoreBoardScroll(leId: Int, srId: Int, seasonId: Int, gameId: String) = throw NotImplementedError()
        override suspend fun getBoxScoreScroll(leId: Int, srId: Int, seasonId: Int, gameId: String) = throw NotImplementedError()
    }

    @Test fun failureIsNotRetriedWithinBackoff() = runBlocking {
        var now = 0L
        val api = FakeApi(fail = true)
        val src = ScheduleSource(api) { now }
        val d = kboToday().plusDays(20)
        assertEquals(emptyList<KboOfficialGame>(), src.day(d))
        src.day(d); src.day(d)
        assertEquals(1, api.calls)
        now = 61_000L
        api.fail = false
        src.day(d)
        assertEquals(2, api.calls)
    }

    @Test fun failureKeepsLastGoodDataEvenWhenExpired() = runBlocking {
        var now = 0L
        val api = FakeApi(fail = false)
        val d = kboToday().plusDays(20)
        api.games = listOf(KboOfficialGame(gameId = "G1", gameDate = d.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)))
        val src = ScheduleSource(api) { now }
        assertEquals(1, src.day(d).size)
        now = 700_000L // TTL(10분) 지남
        api.fail = true
        assertEquals(1, src.day(d).size)
        assertEquals(1, src.day(d).size) // 백오프 중에도 직전 정상 데이터
    }

    @Test fun freshIgnoresBackoff() = runBlocking {
        val api = FakeApi(fail = true)
        val src = ScheduleSource(api) { 0L }
        val d = kboToday().plusDays(20)
        src.day(d)
        api.fail = false
        src.day(d, fresh = true)
        assertEquals(2, api.calls)
    }
}
