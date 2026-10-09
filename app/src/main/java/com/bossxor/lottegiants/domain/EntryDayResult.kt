package com.bossxor.lottegiants.domain

import java.time.LocalDate

/** 성공한 빈 공시와 미조회/실패를 구분한다. */
data class EntryDayResult(
    val date: LocalDate,
    val changes: DayEntryChanges? = null,
    val failed: Boolean = false,
) {
    val summary: String get() = when {
        failed -> "조회 실패 · 눌러서 다시 확인"
        changes == null -> "확인 중"
        !changes.hasChanges -> "변동 없음"
        else -> "등록 ${changes.registered.size} · 말소 ${changes.removed.size}"
    }
}
