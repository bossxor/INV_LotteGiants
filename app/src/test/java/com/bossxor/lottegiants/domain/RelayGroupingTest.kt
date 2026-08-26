package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayGroupingTest {

    @Test
    fun noHitIsNotAHit() {
        assertEquals(RelayKind.Other, classifyRelay("오늘 무안타"))
        assertEquals(RelayKind.Hit, classifyRelay("전준우 중전 안타"))
    }

    @Test
    fun groupsByOutThenBatter() {
        val texts = listOf(
            RelayText(1, "1구 스트라이크", 0, 1, out = 0, batterTitle = "1번 전준우"),
            RelayText(2, "유격수 땅볼 아웃", 1, 1, out = 1, batterTitle = "1번 전준우"),
            RelayText(3, "중전 안타", 1, 1, out = 1, batterTitle = "2번 윤동희"),
        )
        val blocks = groupRelayByOut(texts)
        assertEquals(listOf(0, 1), blocks.map { it.outCount })
        assertEquals("1번 전준우", blocks[0].batters.single().title)
        assertEquals("2번 윤동희", blocks[1].batters.single().title)
    }

    @Test
    fun doublePlayAddsTwoOutsWhenInferred() {
        val texts = listOf(
            RelayText(1, "병살타", 1, 1, batterTitle = "3번"),
        )
        val blocks = groupRelayByOut(texts)
        assertEquals(0, blocks.single().outCount)
        assertTrue(isOutMakingPlay("병살타"))
        assertEquals(2, outDelta("병살타"))
    }

    @Test
    fun nextBatOrderWrapsNinth() {
        assertEquals(2, nextBatOrder(1))
        assertEquals(1, nextBatOrder(9))
        assertNull(nextBatOrder(0))
        assertNull(nextBatOrder(10))
    }
}
