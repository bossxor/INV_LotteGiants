package com.bossxor.lottegiants.data

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
        if (!owner) return result.await()
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
