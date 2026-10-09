package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.YearMonth

internal class EntryController(
    private val scope: CoroutineScope,
    private val fetchDay: suspend (LocalDate, String) -> DayEntryChanges,
    private val findLatest: suspend (String) -> LocalDate,
    private val fetchDates: suspend (YearMonth, String) -> Set<LocalDate>,
    private val today: () -> LocalDate = { LocalDate.now(KBO_ZONE) },
    private val enrichDay: suspend (LocalDate, String) -> DayEntryChanges? = { _, _ -> null },
) {
    constructor(scope: CoroutineScope, repo: GiantsRepository) : this(
        scope,
        { day, code -> repo.fetchDayEntryChanges(day, resolveCodes = false, teamCode = code) },
        { code -> repo.findLatestEntryDate(21, code) },
        { month, code -> repo.fetchEntryChangeDates(month, code) },
        enrichDay = { day, code -> repo.fetchDayEntryChanges(day, resolveCodes = true, teamCode = code) },
    )
    val date = MutableStateFlow(today())
    val changes = MutableStateFlow<DayEntryChanges?>(null)
    val failed = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val dates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val recent = MutableStateFlow<List<EntryDayResult>>(emptyList())
    private var team = LOTTE_TEAM_CODE
    private val bootstrap = LatestRequest(scope)
    private val days = LatestRequest(scope)
    private val months = LatestRequest(scope)
    private val recentDays = LatestRequest(scope)

    fun open(code: String) {
        bootstrap.cancel(); days.cancel(); months.cancel(); recentDays.cancel()
        if (team != code) dates.value = emptySet()
        team = code
        val now = today()
        date.value = now
        load(now)
        recent.value = (0..6).map { EntryDayResult(now.minusDays(it.toLong())) }
        recentDays.launch {
            val limit = Semaphore(3)
            coroutineScope {
                recent.value.map { pending ->
                    async {
                        limit.withPermit {
                            val outcome = result { fetchDay(pending.date, code) }
                            publishRecent(EntryDayResult(pending.date, outcome.getOrNull(), outcome.isFailure))
                        }
                    }
                }.awaitAll()
            }
        }
        bootstrap.launch {
            val latest = result { findLatest(code) }.getOrDefault(now)
            if (latest != date.value) { date.value = latest; load(latest) }
            prefetch(YearMonth.from(latest))
        }
    }

    fun select(value: LocalDate) {
        bootstrap.cancel()
        date.value = value
        load(value)
        prefetch(YearMonth.from(value))
    }

    fun load(value: LocalDate) = days.launch {
        val code = team
        loading.value = true
        failed.value = false
        changes.value = null
        try {
            val outcome = result { fetchDay(value, code) }
            changes.value = outcome.getOrNull()
            failed.value = outcome.isFailure
            publishRecent(EntryDayResult(value, outcome.getOrNull(), outcome.isFailure))
            loading.value = false
            // 공시 인원은 먼저 보여 주고 상세 연결용 선수 코드는 별도로 보완한다.
            if (outcome.isSuccess) result { enrichDay(value, code) }.onSuccess { enriched ->
                if (enriched != null) {
                    changes.value = enriched
                    publishRecent(EntryDayResult(value, enriched))
                }
            }
        } finally { if (isCurrent) loading.value = false }
    }

    private fun publishRecent(day: EntryDayResult) {
        // 중복 조회의 늦은 실패가 이미 확인한 공식 공시를 지우지 않는다.
        recent.value = recent.value.map {
            if (it.date != day.date || (day.failed && it.changes != null)) it else day
        }
        if (day.changes?.hasChanges == true) dates.value = dates.value + day.date
    }

    private fun prefetch(month: YearMonth) = months.launch {
        val code = team
        result { fetchDates(month, code) }.onSuccess { dates.value = dates.value + it }
    }
}
