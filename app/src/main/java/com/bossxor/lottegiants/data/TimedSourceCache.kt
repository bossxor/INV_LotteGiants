package com.bossxor.lottegiants.data

import java.util.concurrent.ConcurrentHashMap

/** 완료 값 TTL과 진행 중 요청 공유를 분리한다. 실패는 캐시하지 않는다. */
internal class TimedSourceCache<K, V>(private val maxEntries: Int = 64,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val values = ConcurrentHashMap<K, Pair<Long, V>>()
    private val flight = SingleFlight<K, V>()
    suspend fun get(key: K, ttlMs: Long, fetch: suspend () -> V): V {
        fun cached(): Pair<Long, V>? = values[key]?.takeIf { clock() - it.first in 0 until ttlMs }
        cached()?.let { return it.second }
        return flight.run(key) {
            cached()?.second ?: fetch().also {
                values[key] = clock() to it
                while (values.size > maxEntries) {
                    val oldest = values.minByOrNull { entry -> entry.value.first } ?: break
                    values.remove(oldest.key, oldest.value)
                }
            }
        }
    }
}
