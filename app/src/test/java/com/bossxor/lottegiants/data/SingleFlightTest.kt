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
    @Test fun ownerCancellationDoesNotCancelActiveWatcherAndNextRequestCanRetry() = runBlocking {
        val f = SingleFlight<String, Int>(); val gate = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) { f.run("same") { gate.await(); 7 } }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { runCatching { f.run("same") { error("duplicate") } } }
        owner.cancelAndJoin()
        val failed = withTimeout(1000) { waiter.await() }
        assertTrue(failed.exceptionOrNull() is SharedFetchCanceledException)
        assertFalse(waiter.isCancelled)
        assertEquals(9, f.run("same") { 9 })
    }
    @Test fun sharedCancellationCannotTerminateContinuousWatcher() = runBlocking {
        val f = SingleFlight<String, Int>(); val gate = CompletableDeferred<Unit>()
        val ui = async(start = CoroutineStart.UNDISPATCHED) { f.run("today") { gate.await(); 1 } }
        var observed = 0
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            repeat(2) {
                try { observed = f.run("today") { 9 } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { yield() }
            }
        }
        ui.cancelAndJoin(); watcher.join(); assertEquals(9, observed); assertFalse(watcher.isCancelled)
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
