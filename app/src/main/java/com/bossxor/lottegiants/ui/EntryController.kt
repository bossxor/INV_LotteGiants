package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

import java.time.LocalDate
import java.time.YearMonth

internal class EntryController(private val scope: CoroutineScope, private val repo: GiantsRepository) {
    val date = MutableStateFlow(kboToday())
    val changes = MutableStateFlow<DayEntryChanges?>(null)
    val loading = MutableStateFlow(false)
    val dates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val recent = MutableStateFlow<List<RosterMove>>(emptyList())
    private var team = LOTTE_TEAM_CODE
    private val bootstrap = LatestRequest(scope)
    private val days = LatestRequest(scope)
    private val months = LatestRequest(scope)
    fun open(code: String) {
        bootstrap.cancel(); days.cancel(); months.cancel()
        if (team != code) { dates.value = emptySet(); changes.value = null; recent.value = emptyList() }
        team = code
        bootstrap.launch {
            loading.value = true
            try {
                result { repo.fetchRecentRosterMoves(7, code) }.onSuccess { recent.value = it }
                val latest = result { repo.findLatestEntryDate(21, code) }.getOrDefault(kboToday())
                date.value = latest
                load(latest)
                prefetch(YearMonth.from(latest))
            } finally { if (isCurrent && !days.active) loading.value = false }
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
        try {
            result { repo.fetchDayEntryChanges(value, teamCode = code) }
                .onSuccess { changes.value = it }
                .onFailure { changes.value = DayEntryChanges(date = value.toString()) }
        } finally { if (isCurrent) loading.value = false }
    }
    private fun prefetch(month: YearMonth) = months.launch {
        val code = team
        result { repo.fetchEntryChangeDates(month, code) }.onSuccess { dates.value = dates.value + it }
    }
}
