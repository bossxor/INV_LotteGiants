package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.GameStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 라이브/상세/라인업이 같은 원본 요청과 이닝 캐시를 사용한다. */
internal class RelaySource(private val api: NaverSportsApi) {
    private data class Key(val game: String, val inning: Int?, val generation: Long)
    private val flight = SingleFlight<Key, TextRelayData?>()
    private val generation = AtomicLong()
    private val innings = ConcurrentHashMap<String, ConcurrentHashMap<Int, List<TextRelayDto>>>()
    private val lastUsed = ConcurrentHashMap<String, Long>()
    private val cacheLock = Any()

    fun clear() = synchronized(cacheLock) { generation.incrementAndGet(); innings.clear(); lastUsed.clear() }

    suspend fun current(gameId: String): TextRelayData? = query(gameId, null, generation.get())

    private suspend fun query(game: String, inning: Int?, epoch: Long): TextRelayData? =
        flight.run(Key(game, inning, epoch)) { api.getRelay(game, inning).result?.textRelayData }

    fun hasLineup(relay: TextRelayData): Boolean = listOfNotNull(relay.homeLineup, relay.awayLineup)
        .any { dto -> dto.batter.any { it.name.isNotBlank() } }

    suspend fun live(game: String, recoverFrom: Int? = null): TextRelayData? = collect(game, false, recoverFrom)
    suspend fun full(game: String): TextRelayData? = collect(game, true, null)
    suspend fun poll(game: String, status: GameStatus, lineupAnnounced: Boolean): TextRelayData? {
        if (status == GameStatus.BEFORE && lineupAnnounced) {
            current(game)?.takeIf { hasLineup(it) }?.let { return it }
        }
        return live(game)
    }

    private suspend fun collect(game: String, full: Boolean, recoverFrom: Int?): TextRelayData? {
        val epoch = generation.get()
        val base = query(game, null, epoch) ?: return null
        val cache = synchronized(cacheLock) {
            if (epoch != generation.get()) throw CancellationException("중계 대상 변경")
            lastUsed[game] = System.nanoTime()
            while (lastUsed.size > 12) {
                val oldest = lastUsed.minByOrNull { it.value }?.key ?: break
                lastUsed.remove(oldest); innings.remove(oldest)
            }
            innings.getOrPut(game) { ConcurrentHashMap() }
        }
        val current = base.textRelays.filter { it.inn == base.inn || it.inn == 0 }.ifEmpty { base.textRelays }
        if (current.isNotEmpty()) cache[base.inn] = current
        val from = if (full) 1 else recoverFrom?.coerceIn(1, base.inn.coerceAtLeast(1)) ?: (base.inn - 1).coerceAtLeast(1)
        coroutineScope {
            val limit = Semaphore(3)
            (from until base.inn).filter { cache[it].isNullOrEmpty() }.map { inning -> async {
                limit.withPermit {
                    try {
                        query(game, inning, epoch)?.textRelays?.takeIf { it.isNotEmpty() }?.let { cache[inning] = it }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (!full) throw e }
                }
            } }.awaitAll()
        }
        if (epoch != generation.get()) throw CancellationException("중계 대상 변경")
        val merged = cache.entries.sortedBy { it.key }.flatMap { it.value }.ifEmpty { base.textRelays }
        return base.copy(textRelays = merged)
    }
}
