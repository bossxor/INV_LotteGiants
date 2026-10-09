package com.bossxor.lottegiants.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

sealed interface AlertQueryResult<out T> {
    data class Success<T>(val value: T) : AlertQueryResult<T>
    data class Failed(val reason: String) : AlertQueryResult<Nothing>
    data object Skipped : AlertQueryResult<Nothing>
}

/** 간격 제한과 진행 중 요청 제한을 함께 적용한다. 서로 다른 공시는 독립적으로 조회한다. */
object AlertPollCoordinator {
    private val lineup = Mutex()
    private val roster = Mutex()

    suspend fun <T> query(lineupQuery: Boolean, gapMs: Long, fetch: suspend () -> T): AlertQueryResult<T> {
        val lock = if (lineupQuery) lineup else roster
        if (!lock.tryLock()) return AlertQueryResult.Skipped
        try {
            val begin = if (lineupQuery) AlertPollGate.tryBeginLineup(gapMs) else AlertPollGate.tryBeginRoster(gapMs)
            if (!begin) return AlertQueryResult.Skipped
            return try {
                AlertQueryResult.Success(withTimeout(12_000L) { fetch() })
            } catch (_: TimeoutCancellationException) {
                AlertQueryResult.Failed("조회 시간 초과")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AlertQueryResult.Failed(e.javaClass.simpleName)
            }
        } finally { lock.unlock() }
    }
}
