package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

import java.time.LocalDate
import java.time.YearMonth

internal class CalendarController(private val scope: CoroutineScope, private val repo: GiantsRepository,
    private val team: () -> String, private val onError: (String) -> Unit) {
    val dayGames = MutableStateFlow<List<MiniGame>>(emptyList())
    val loading = MutableStateFlow(false)
    val monthGames = MutableStateFlow<List<MiniGame>>(emptyList())
    val month = MutableStateFlow(YearMonth.from(kboToday()))
    private val days = LatestRequest(scope)
    private val months = LatestRequest(scope)
    fun loadDay(date: LocalDate) = days.launch {
        loading.value = true
        try {
            result { repo.fetchGamesForDate(date) }
                .onSuccess { dayGames.value = MainViewModel.sortMyTeamFirst(it, team()) }
                .onFailure { if (dayGames.value.isEmpty()) onError(it.message ?: "경기 일정을 불러오지 못했습니다.") }
        } finally { if (isCurrent) loading.value = false }
    }
    fun loadMonth(value: YearMonth) = months.launch {
        month.value = value
        result { repo.fetchGamesForMonth(value) }.onSuccess { monthGames.value = it }
    }
}
