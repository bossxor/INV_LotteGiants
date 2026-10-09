package com.bossxor.lottegiants.domain

import org.junit.Assert.*
import org.junit.Test

class RelaySituationTest {
    @Test fun confirmedPinchHitterReplacesOldApiBatter() {
        val game = LotteGameInfo("game", "2026-10-09", "14:00", "사직", true, "LG", "LG",
            inning = 7, isTopInning = true, isLotteBatting = false,
            currentBatterName = "이영빈", currentBatterCode = "old",
            opponentLineup = listOf(LineupSlot(9, "이영빈", "", playerCode = "old")),
            opponentBenchBatters = listOf(LineupSlot(9, "홍창기", "", playerCode = "new")),
            recentTexts = listOf(RelayText(100, "9번타자 이영빈 : 대타 홍창기 (으)로 교체", 1, 7, true)))
        assertEquals("홍창기", resolvedAtBat(game, NamedBases(null, null, null)))
    }
    @Test fun actualPinchRunnerTextReplacesOnlyItsBase() {
        val text = "1루주자 한동희 : 대주자 박승욱 (으)로 교체"
        val sub = parseSubstitution(text)!!
        assertEquals(SubstitutionRole.RUNNER, sub.role)
        assertEquals(1, sub.base)
        val bases = advanceNamedRunners(NamedBases("한동희", null, "고승민"),
            listOf(RelayText(402, text, 1, 7, false)), listOf("한동희", "박승욱", "고승민"))
        assertEquals(NamedBases("박승욱", null, "고승민"), bases)
        assertFalse(isPlateStart(text))
    }
    @Test fun pinchHitterHasDifferentRoleAndDefenseDoesNotChangeAtBat() {
        assertEquals(SubstitutionRole.BATTER,
            parseSubstitution("9번타자 이영빈 : 대타 홍창기 (으)로 교체")!!.role)
        assertNull(parseSubstitution("대주자 박승욱 : 3루수(으)로 수비위치 변경"))
        assertTrue(isPlateStart("대타 홍창기"))
    }
    @Test fun runnerIsNeverUsedAsUnconfirmedBatter() {
        val game = LotteGameInfo("game", "2026-10-09", "14:00", "사직", true, "LG", "LG",
            inning = 7, isTopInning = false, isLotteBatting = true,
            currentBatterName = "박승욱", nextBatterName = "전민재")
        assertEquals("", resolvedAtBat(game, NamedBases("박승욱", null, "고승민")))
        assertEquals("", atBatForChance("박승욱", "전민재", runnerNames = listOf("박승욱")))
    }
    @Test fun actualSingleHitAndDirectionAreRecognized() {
        assertEquals("좌중간 1루타", describePlayHow("한동희 : 좌중간 1루타"))
        assertEquals("몸에 맞는 볼", describePlayHow("고승민 : 몸에 맞는 볼"))
    }
}
