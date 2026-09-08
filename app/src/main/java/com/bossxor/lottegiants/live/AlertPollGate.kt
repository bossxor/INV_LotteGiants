package com.bossxor.lottegiants.live

import java.util.concurrent.atomic.AtomicLong

/**
 * 라인업·등말소 폴링이 알람·감시 서비스·워커에서 겹치지 않게 최소 간격을 강제한다.
 */
object AlertPollGate {
    const val LINEUP_MIN_GAP_MS = 15_000L
    const val ROSTER_MIN_GAP_MS = 25_000L

    private val lastLineupAt = AtomicLong(0L)
    private val lastRosterAt = AtomicLong(0L)

    fun tryBeginLineup(minGapMs: Long = LINEUP_MIN_GAP_MS): Boolean =
        tryBegin(lastLineupAt, minGapMs)

    fun tryBeginRoster(minGapMs: Long = ROSTER_MIN_GAP_MS): Boolean =
        tryBegin(lastRosterAt, minGapMs)

    internal fun resetForTest() {
        lastLineupAt.set(0L)
        lastRosterAt.set(0L)
    }

    private fun tryBegin(stamp: AtomicLong, minGapMs: Long): Boolean {
        val now = System.currentTimeMillis()
        while (true) {
            val prev = stamp.get()
            if (now - prev < minGapMs) return false
            if (stamp.compareAndSet(prev, now)) return true
        }
    }
}
