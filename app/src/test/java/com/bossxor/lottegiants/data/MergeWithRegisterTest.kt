package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.EntryPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeWithRegisterTest {
    private fun p(name: String, back: String, code: String = "") = EntryPlayer(name = name, backNumber = back, playerCode = code)

    @Test fun registerDoesNotDuplicateFirstTeamPlayers() {
        val search = listOf(p("전준우", "8", "1"), p("황성빈", "0", "2"), p("최항", "", "3"))
        val register = listOf(p("전준우", "8"), p("황성빈", "0"))
        val merged = KboPlayerSearchParser.mergeWithRegister(search, register)
        assertEquals(3, merged.size)
        assertEquals(listOf("1", "2", "3"), merged.map { it.playerCode })
    }

    @Test fun fillsBlankBackNumberFromRegister() {
        val merged = KboPlayerSearchParser.mergeWithRegister(listOf(p("최항", "", "3")), listOf(p("최항", "14")))
        assertEquals("14", merged.single().backNumber)
    }

    @Test fun sameNameIsNotGuessed() {
        val search = listOf(p("김민", "", "1"), p("김민", "", "2"))
        val merged = KboPlayerSearchParser.mergeWithRegister(search, listOf(p("김민", "40")))
        assertEquals(listOf("", ""), merged.map { it.backNumber })
    }

    @Test fun registerOnlyWhenSearchFails() {
        val register = listOf(p("전준우", "8"))
        assertEquals(register, KboPlayerSearchParser.mergeWithRegister(emptyList(), register))
    }
}
