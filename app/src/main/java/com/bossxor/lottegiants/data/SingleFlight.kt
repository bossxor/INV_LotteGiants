package com.bossxor.lottegiants.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 같은 키의 진행 중 조회를 공유한다. 완료 결과의 캐시 수명은 소유자가 정한다. */
internal class SingleFlight<K, V> {
    private val lock = Mutex()
    private val pending = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(key: K, fetch: suspend () -> V): V {
        var owner = false
        val result = lock.withLock {
            pending[key] ?: CompletableDeferred<V>().also { pending[key] = it; owner = true }
        }
        if (!owner) {
            try { return result.await() }
            catch (e: CancellationException) {
                // 대기자 자신의 취소는 전파한다. 다른 요청자의 취소로 이 대기자의 감시 루프를 끝내지 않는다.
                currentCoroutineContext().ensureActive()
                throw SharedFetchCanceledException(e)
            }
        }
        try {
            return fetch().also { result.complete(it) }
        } catch (e: Throwable) {
            result.completeExceptionally(e)
            throw e
        } finally {
            withContext(NonCancellable) {
                lock.withLock { if (pending[key] === result) pending.remove(key) }
            }
        }
    }
}

internal class SharedFetchCanceledException(cause: CancellationException) :
    Exception("공유 조회 중단; 다음 요청에서 재시도", cause)
