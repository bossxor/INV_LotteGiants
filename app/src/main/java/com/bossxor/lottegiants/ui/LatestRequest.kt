package com.bossxor.lottegiants.ui

import kotlinx.coroutines.*

/** 화면 선택이 바뀌면 이전 작업을 취소하고, 취소를 삼킨 조회도 게시 전에 세대를 확인한다. */
internal class LatestRequest(private val scope: CoroutineScope) {
    private var generation = 0L
    private var job: Job? = null
    val active get() = job?.isActive == true
    fun cancel() { generation++; job?.cancel(); job = null }
    fun launch(action: suspend Token.() -> Unit): Job {
        cancel()
        val token = Token(generation)
        return scope.launch { token.action() }.also { job = it }
    }
    inner class Token(private val version: Long) {
        val isCurrent get() = version == generation
        suspend fun <T> result(fetch: suspend () -> T): Result<T> {
            val result = try { Result.success(fetch()) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure(e) }
            currentCoroutineContext().ensureActive()
            if (!isCurrent) throw CancellationException("화면 선택 변경")
            return result
        }
    }
}
