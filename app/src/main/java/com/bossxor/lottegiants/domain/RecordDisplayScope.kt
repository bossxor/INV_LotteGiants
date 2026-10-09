package com.bossxor.lottegiants.domain

import java.time.LocalDate
import java.time.YearMonth

/** 제목은 요청한 달보다 실제 표시 자료를 따른다. 부분 시즌 응답도 전체 시즌으로 단정하지 않는다. */
data class RecordDisplayScope(val label: String, val range: String)

fun recordDisplayScope(dates: List<String>, usesSeasonData: Boolean, requestedMonth: YearMonth): RecordDisplayScope {
    val known = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.distinct().sorted()
    val months = known.map { YearMonth.from(it) }.distinct()
    val month = months.singleOrNull() ?: requestedMonth
    val label = if (!usesSeasonData && months.size <= 1) "${month.year}년 ${month.monthValue}월 전적" else "조회된 경기 전적"
    val range = if (known.isEmpty()) "조회된 경기 없음" else "${known.first()} ~ ${known.last()} 조회 결과"
    return RecordDisplayScope(label, range)
}
