package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** 공식 일정의 날짜 캐시·진행 중 조회 공유·범위 조회 부하를 한 곳에서 관리한다. */
internal class ScheduleSource(private val api: KboOfficialApi,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val cache = ConcurrentHashMap<LocalDate, Pair<Long, List<KboOfficialGame>>>()
    private val flight = SingleFlight<LocalDate, List<KboOfficialGame>>()
    private val rangeLimit = Semaphore(6)
    private val rangeFlight = SingleFlight<LocalDate, List<KboOfficialGame>>()
    @Volatile private var season = emptyList<KboOfficialGame>()
    @Volatile private var seasonAt = 0L
    @Volatile private var seasonDay: LocalDate? = null

    fun hasSeasonWindow(today: LocalDate): Boolean = seasonDay == today && season.isNotEmpty() && clock() - seasonAt in 0 until 600_000L

    suspend fun day(date: LocalDate, fresh: Boolean = false): List<KboOfficialGame> {
        val ttl = if (date == kboToday() || date == LocalDate.now(KBO_ZONE)) 30_000L else 600_000L
        if (!fresh) cache[date]?.takeIf { clock() - it.first in 0 until ttl }?.let { return it.second }
        return try {
            flight.run(date) {
                val response = api.getGameList(date = KboOfficialApi.dateParam(date))
                check(response.code == "100") { "KBO 일정 조회 실패: ${response.code}" }
                response.game.filter { it.gameId.isNotBlank() }.forKboDate(date).also {
                    // 정상 빈 일정도 캐시한다. 실패 응답은 저장하지 않는다.
                    cache[date] = clock() to it
                    val earliest = kboToday().minusDays(400)
                    cache.keys.removeAll { d -> d < earliest || d > kboToday().plusDays(400) }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (fresh) throw e else emptyList() }
    }

    suspend fun range(from: LocalDate, to: LocalDate): List<KboOfficialGame> = coroutineScope {
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { date -> async { rangeLimit.withPermit { day(date) } } }.toList().awaitAll().flatten()
    }

    suspend fun seasonWindow(today: LocalDate): List<KboOfficialGame> {
        if (hasSeasonWindow(today)) return season
        return rangeFlight.run(today) {
            if (hasSeasonWindow(today)) return@run season
            range(today.minusDays(21), today.plusDays(14)).also {
                if (it.isNotEmpty()) { season = it; seasonAt = clock(); seasonDay = today }
            }.ifEmpty { if (seasonDay == today) season else emptyList() }
        }
    }
}
