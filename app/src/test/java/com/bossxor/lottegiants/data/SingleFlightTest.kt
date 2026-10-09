package com.bossxor.lottegiants.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleFlightTest {
    @Test fun twentyCallersShareOneRequest() = runBlocking {
        val f = SingleFlight<String, Int>(); var calls = 0; val gate = CompletableDeferred<Unit>()
        val jobs = (1..20).map { async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { calls++; gate.await(); 7 } } }
        gate.complete(Unit); assertTrue(jobs.awaitAll().all { it == 7 }); assertEquals(1, calls)
    }
    @Test fun cancelingWaiterDoesNotCancelOwner() = runBlocking {
        val f = SingleFlight<String, Int>(); val gate = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { gate.await(); 7 } }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { error("duplicate") } }
        waiter.cancelAndJoin(); gate.complete(Unit); assertEquals(7, owner.await())
    }
    @Test fun ownerCancellationReleasesWaitersAndNextRequestCanRetry() = runBlocking {
        val f = SingleFlight<String, Int>(); val gate = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { gate.await(); 7 } }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { error("duplicate") } }
        owner.cancelAndJoin()
        try { withTimeout(1000) { waiter.await() }; fail() } catch (_: CancellationException) { }
        assertEquals(9, f.run("same") { 9 })
    }
    @Test fun failureIsSharedAndRetryIsNotPoisoned() = runBlocking {
        supervisorScope {
            val f = SingleFlight<String, Int>(); val gate = CompletableDeferred<Unit>(); var calls = 0
            val jobs = (1..2).map { async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { f.run("same") { calls++; gate.await(); error("network") } }
            } }
            gate.complete(Unit); assertTrue(jobs.awaitAll().all { it.isFailure }); assertEquals(1, calls)
            assertEquals(8, f.run("same") { 8 })
        }
    }
}
