package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class EntryControllerTest {
    private val today = LocalDate.of(2026, 10, 10)
    private fun entry(day: LocalDate, name: String = "박세진") = DayEntryChanges(
        date = day.toString(), registered = listOf(EntryPlayer(name = name)),
    )
    @Test fun recentOfficialDaysMatchDetailAndConfirmedEmptyIsSeparateFromFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val c = EntryController(scope, { d, _ ->
                when (d) {
                    today.minusDays(1) -> entry(d)
                    today.minusDays(2) -> throw IllegalStateException("network")
                    else -> DayEntryChanges(date = d.toString())
                }
            }, { today.minusDays(1) }, { _, _ -> emptySet() }, { today })
            c.open("LT")
            val recent = withTimeout(2000) { c.recent.first { it.size == 7 && it.all { day -> day.changes != null || day.failed } } }
            assertEquals("등록 1 · 말소 0", recent[1].summary)
            assertTrue(recent[2].failed)
            assertEquals("변동 없음", recent[0].summary)
            c.load(today.minusDays(1)).join()
            assertEquals(recent[1].changes, c.changes.value)
            c.load(today.minusDays(2)).join()
            assertTrue(c.failed.value)
            assertNull(c.changes.value)
            assertFalse(c.loading.value)
        } finally { scope.cancel() }
    }
    @Test fun slowRecentLookupDoesNotBlockSelectedDayAndRetryRepairsSummary() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        var offline = true
        try {
            val c = EntryController(scope, { d, _ ->
                if (d == today.minusDays(3)) gate.await()
                if (d == today.minusDays(2) && offline) throw IllegalStateException("offline")
                entry(d)
            }, { today }, { _, _ -> emptySet() }, { today })
            c.open("LT")
            c.load(today).join()
            assertNotNull(c.changes.value)
            assertFalse(c.loading.value)
            withTimeout(2000) { c.recent.first { it.any { day -> day.date == today.minusDays(2) && day.failed } } }
            offline = false
            c.load(today.minusDays(2)).join()
            val repaired = c.recent.value.first { it.date == today.minusDays(2) }
            assertFalse(repaired.failed)
            assertEquals("등록 1 · 말소 0", repaired.summary)
        } finally { scope.cancel(); gate.complete(Unit) }
    }
    @Test fun oldTeamUncancelableResponseCannotPublishIntoNewTeam() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            val c = EntryController(scope, { d, team ->
                if (team == "LT") withContext(NonCancellable) { gate.await() }
                entry(d, team)
            }, { today }, { _, _ -> emptySet() }, { today })
            c.open("LT")
            c.open("SS")
            withTimeout(2000) { c.recent.first { it.size == 7 && it.all { d -> d.changes != null } } }
            gate.complete(Unit)
            yield()
            assertEquals("SS", c.changes.value?.registered?.single()?.name)
            assertTrue(c.recent.value.all { it.changes?.registered?.single()?.name == "SS" })
        } finally { scope.cancel(); gate.complete(Unit) }
    }
    @Test fun playerCodeEnrichmentDoesNotDelayOfficialDetailOrOverwriteNewDate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            val c = EntryController(scope, { d, _ -> entry(d) }, { today }, { _, _ -> emptySet() }, { today },
                enrichDay = { d, _ ->
                    if (d == today) withContext(NonCancellable) { gate.await() }
                    entry(d).copy(registered = listOf(EntryPlayer(name = "박세진", playerCode = "resolved")))
                })
            c.open("LT")
            assertNotNull(c.changes.value)
            assertFalse(c.loading.value)
            c.select(today.minusDays(1))
            c.load(today.minusDays(1)).join()
            gate.complete(Unit)
            yield()
            assertEquals(today.minusDays(1).toString(), c.changes.value?.date)
            assertEquals("resolved", c.changes.value?.registered?.single()?.playerCode)
        } finally { scope.cancel(); gate.complete(Unit) }
    }
    @Test fun unknownDayCannotBePresentedAsNoChanges() {
        assertEquals("확인 중", EntryDayResult(today).summary)
        assertTrue(EntryDayResult(today, failed = true).summary.startsWith("조회 실패"))
    }
}
