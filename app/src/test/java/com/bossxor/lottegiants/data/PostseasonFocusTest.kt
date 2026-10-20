package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.isPostseasonGameLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostseasonFocusTest {

    @Test
    fun postseasonLabels() {
        assertTrue(isPostseasonGameLabel("한국시리즈"))
        assertTrue(isPostseasonGameLabel("와일드카드 결정전"))
        assertTrue(isPostseasonGameLabel("준플레이오프"))
        assertTrue(isPostseasonGameLabel("플레이오프"))
        assertFalse(isPostseasonGameLabel("정규시즌"))
        assertFalse(isPostseasonGameLabel(""))
        assertFalse(isPostseasonGameLabel("폭염취소"))
    }

    @Test
    fun regularSeasonKeepsStoredTeam() {
        val games = listOf(
            kbo("LG", "LT", "정규시즌", 2),
            kbo("OB", "SS", "정규시즌", 1),
        )
        val (focus, picked) = pickPostseasonOrTeamGame(games, null, "LT")
        assertEquals("LT", focus)
        assertEquals("LT", picked?.homeId)
    }

    @Test
    fun postseasonUsesHomeTeamEvenIfStoredIsAway() {
        val games = listOf(kbo("LT", "SS", "한국시리즈", 2))
        val (focus, picked) = pickPostseasonOrTeamGame(games, null, "LT")
        assertEquals("SS", focus)
        assertEquals("SS", picked?.homeId)
        assertEquals("LT", picked?.awayId)
        assertTrue(picked!!.toLotteBase(focus).isHome)
    }

    @Test
    fun postseasonPicksLiveGameHomeWhenStoredTeamIsNotPlaying() {
        val games = listOf(
            kbo("OB", "LG", "플레이오프", 2),
        )
        val (focus, picked) = pickPostseasonOrTeamGame(games, null, "LT")
        assertEquals("LG", focus)
        assertEquals("LG", picked?.homeId)
    }

    @Test
    fun postseasonPrefersLiveOverScheduled() {
        val games = listOf(
            kbo("HH", "KT", "준플레이오프", 1, start = "18:30"),
            kbo("NC", "SK", "준플레이오프", 2, start = "14:00"),
        )
        val (focus, picked) = pickPostseasonOrTeamGame(games, null, "LT")
        assertEquals("SK", focus)
        assertEquals(2, picked?.gameState)
    }

    @Test
    fun postseasonSplitListsAllPsForChips() {
        val games = listOf(
            kbo("OB", "LG", "플레이오프", 2),
            kbo("HH", "KT", "준플레이오프", 1),
            kbo("LT", "SS", "정규시즌", 1),
        )
        val (live, other) = splitTodayLiveGames(games, "LT")
        assertEquals(2, live.size)
        assertTrue(live.all { it.isPostseason() })
        assertEquals(1, other.size)
        assertEquals("LT", other.single().awayId)
    }

    @Test
    fun regularSplitKeepsMyTeamOnly() {
        val games = listOf(
            kbo("LG", "LT", "정규시즌", 2),
            kbo("OB", "SS", "정규시즌", 1),
        )
        val (live, other) = splitTodayLiveGames(games, "LT")
        assertEquals(1, live.size)
        assertEquals("LT", live.single().homeId)
        assertEquals(1, other.size)
    }

    private fun kbo(
        away: String,
        home: String,
        sc: String,
        state: Int,
        start: String = "18:30",
    ) = KboOfficialGame(
        gameDate = "20261018",
        gameId = "${away}${home}20261018",
        seasonId = 2026,
        startTime = start,
        awayId = away,
        homeId = home,
        awayName = away,
        homeName = home,
        gameState = state,
        gameScNm = sc,
    )
}
