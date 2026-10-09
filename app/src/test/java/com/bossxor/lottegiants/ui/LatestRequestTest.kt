package com.bossxor.lottegiants.ui

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LatestRequestTest {
    @Test fun oldUncancelablePlayerResultCannotOverwriteNewSelection() = runBlocking {
        val requests = LatestRequest(this); val gate = CompletableDeferred<Unit>(); var visible = ""
        requests.launch { result { withContext(NonCancellable) { gate.await() }; "old" }.onSuccess { visible = it } }
        yield()
        requests.launch { result { "new" }.onSuccess { visible = it } }.join()
        gate.complete(Unit); yield(); assertEquals("new", visible)
    }
    @Test fun closingScreenInvalidatesPendingResult() = runBlocking {
        val requests = LatestRequest(this); val gate = CompletableDeferred<Unit>(); var visible: String? = null
        val old = requests.launch { result { withContext(NonCancellable) { gate.await() }; "old" }.onSuccess { visible = it } }
        yield(); requests.cancel(); gate.complete(Unit); old.join(); assertNull(visible)
    }
    @Test fun cancellationNeverPublishesFallbackOrClearsNewLoadingState() = runBlocking {
        val requests = LatestRequest(this); val gate = CompletableDeferred<Unit>(); var loading = false; var fallback = false
        val old = requests.launch {
            loading = true
            try { result { gate.await() }.onFailure { fallback = true } }
            finally { if (isCurrent) loading = false }
        }
        yield()
        val next = requests.launch { loading = true; delay(100) }
        yield(); old.join(); assertTrue(loading); assertFalse(fallback); next.cancelAndJoin()
    }
}
