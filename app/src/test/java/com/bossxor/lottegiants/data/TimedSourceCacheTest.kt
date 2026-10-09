package com.bossxor.lottegiants.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TimedSourceCacheTest {
    @Test fun expiryIsOwnedBySourceAndFreshRequestBypassesCompletedValue() = runBlocking {
        var now = 1L; var calls = 0; val cache = TimedSourceCache<String, Int>(clock = { now })
        assertEquals(1, cache.get("g", 100) { ++calls })
        now = 50; assertEquals(1, cache.get("g", 100) { ++calls })
        now = 101; assertEquals(2, cache.get("g", 100) { ++calls })
        assertEquals(3, cache.get("g", 0) { ++calls })
    }
    @Test fun failedFetchIsNotTreatedAsConfirmedEmptyResult() = runBlocking {
        val cache = TimedSourceCache<String, List<Int>>()
        assertTrue(runCatching { cache.get("today", 1000) { error("official API failed") } }.isFailure)
        assertEquals(listOf(7), cache.get("today", 1000) { listOf(7) })
    }
    @Test fun boundedCacheEvictsOldestGames() = runBlocking {
        var now = 1L; val cache = TimedSourceCache<String, Int>(maxEntries = 2, clock = { now })
        cache.get("first", 100) { 1 }; now++; cache.get("second", 100) { 2 }; now++
        cache.get("third", 100) { 3 }
        assertEquals(9, cache.get("first", 100) { 9 })
        assertEquals(3, cache.get("third", 100) { error("not evicted") })
    }
}
