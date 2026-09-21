package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RunnerOrderFromRelayTest {

    private val batting = mapOf(
        1 to ("1001" to "황성빈"),
        2 to ("1002" to "전준우"),
        3 to ("1003" to "윤동희"),
        4 to ("1004" to "고승민"),
    )
    private val names = batting.values.associate { it.first to it.second }

    @Test
    fun emptyRelayKeepsKboOrder() {
        assertEquals(2, runnerOrderFromRelay(null, batting, names, 2))
        assertEquals(0, runnerOrderFromRelay("", batting, names, 0))
        assertEquals(3, runnerOrderFromRelay("   ", batting, names, 3))
    }

    @Test
    fun unoccupiedClearsOrder() {
        assertEquals(0, runnerOrderFromRelay("0", batting, names, 2))
        assertEquals(0, runnerOrderFromRelay("n", batting, names, 1))
    }

    @Test
    fun occupancyFlagKeepsKboOrder() {
        assertEquals(2, runnerOrderFromRelay("1", batting, names, 2))
        assertEquals(3, runnerOrderFromRelay("Y", batting, names, 3))
        assertEquals(0, runnerOrderFromRelay("1", batting, names, 0))
    }

    @Test
    fun playerCodeMapsToBatOrder() {
        assertEquals(2, runnerOrderFromRelay("1002", batting, names, 0))
        assertEquals(4, runnerOrderFromRelay("1004", batting, names, 1))
    }

    @Test
    fun codeMappedViaNamesTable() {
        // names에만 있고 batting pcode와 다른 키로 올 때 이름으로 타순 매칭
        val namesOnly = mapOf("X900" to "윤동희")
        assertEquals(3, runnerOrderFromRelay("X900", batting, namesOnly, 0))
    }

    @Test
    fun unknownCodeFallsBackToKbo() {
        assertEquals(1, runnerOrderFromRelay("9999", batting, names, 1))
        assertEquals(0, runnerOrderFromRelay("9999", batting, names, 0))
    }
}
