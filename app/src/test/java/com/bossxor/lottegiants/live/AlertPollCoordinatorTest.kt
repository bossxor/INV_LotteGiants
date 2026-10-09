package com.bossxor.lottegiants.live

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AlertPollCoordinatorTest {
    @Before fun reset() = AlertPollGate.resetForTest()

    @Test fun slowLineupDoesNotBlockRosterOrAllowDuplicateFetch() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val first = async {
            AlertPollCoordinator.query(true, 0) { started.complete(Unit); finish.await(); "lineup" }
        }
        started.await()
        assertEquals(AlertQueryResult.Skipped, AlertPollCoordinator.query(true, 0) { error("중복 조회") })
        assertEquals(AlertQueryResult.Success("roster"), AlertPollCoordinator.query(false, 0) { "roster" })
        finish.complete(Unit)
        assertEquals(AlertQueryResult.Success("lineup"), first.await())
    }

    @Test fun failedRequestIsDifferentFromSuccessfulEmptyAndReleasesLock() = runBlocking {
        assertTrue(AlertPollCoordinator.query(false, 0) { throw IllegalStateException("통신 실패") } is AlertQueryResult.Failed)
        assertEquals(AlertQueryResult.Success(emptyList<String>()), AlertPollCoordinator.query(false, 0) { emptyList<String>() })
    }

    @Test fun cancellationReleasesInFlightLock() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch { AlertPollCoordinator.query(true, 0) { started.complete(Unit); awaitCancellation() } }
        started.await()
        job.cancelAndJoin()
        assertEquals(AlertQueryResult.Success(1), AlertPollCoordinator.query(true, 0) { 1 })
    }
}
