package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterAlertTest {

    private fun move(
        name: String,
        date: String,
        register: Boolean = true,
        code: String = "",
    ) = RosterMove(
        playerCode = code,
        playerName = name,
        moveType = if (register) "등록" else "말소",
        moveDate = date,
        isRegister = register,
    )

    @Test
    fun keyIgnoresPlayerCode() {
        assertEquals(
            rosterNotifyKey(move("손호영", "2026-09-03", code = "123")),
            rosterNotifyKey(move("손호영", "2026-09-03", code = "")),
        )
    }

    @Test
    fun fiveAmRestampDoesNotRenotify() {
        val yesterday = listOf(
            move("손호영", "2026-09-03"),
            move("조세진", "2026-09-03"),
            move("윤동희", "2026-09-03", register = false),
            move("한태양", "2026-09-03", register = false),
        )
        val seeded = yesterday.map(::rosterNotifyKey).toSet()
        val restamped = yesterday.map { it.copy(moveDate = "2026-09-04", playerCode = "x") }
        val plan = planRosterNotifications(restamped, seeded, today = "2026-09-04")
        assertTrue(plan.fresh.isEmpty())
    }

    @Test
    fun yesterdayMovesAreNotFreshAfterRollover() {
        val stored = setOf("2026-09-02:등록:다른선수")
        val plan = planRosterNotifications(
            listOf(move("손호영", "2026-09-03")),
            stored,
            today = "2026-09-04",
        )
        assertTrue(plan.fresh.isEmpty())
        assertTrue(plan.stored.contains("2026-09-03:등록:손호영"))
    }

    @Test
    fun firstSeenOnlySeeds() {
        val plan = planRosterNotifications(
            listOf(move("손호영", "2026-09-04")),
            stored = emptySet(),
            today = "2026-09-04",
        )
        assertTrue(plan.fresh.isEmpty())
        assertTrue(plan.stored.contains("2026-09-04:등록:손호영"))
    }

    @Test
    fun sameDayNewMoveIsFresh() {
        val plan = planRosterNotifications(
            listOf(move("손호영", "2026-09-04")),
            stored = setOf("2026-09-01:등록:기준선"),
            today = "2026-09-04",
        )
        assertEquals(1, plan.fresh.size)
        assertEquals("손호영", plan.fresh.single().playerName)
    }
}
