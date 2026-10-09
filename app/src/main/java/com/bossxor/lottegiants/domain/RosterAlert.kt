package com.bossxor.lottegiants.domain

/** 「등말소 변화 없음」은 감시가 켜지는 14시 이후에만. 오전 5시 날짜 넘김에는 보내지 않는다. */
const val ROSTER_NONE_START_HOUR = 14
const val ROSTER_NONE_END_HOUR = 23

fun shouldSendRosterNoneAlert(
    nowHour: Int,
    today: String,
    notifiedNoneDay: String,
    hasTodayRosterNotifyKey: Boolean,
    waitForLineup: Boolean,
): Boolean {
    if (notifiedNoneDay == today) return false
    if (hasTodayRosterNotifyKey) return false
    if (nowHour !in ROSTER_NONE_START_HOUR until ROSTER_NONE_END_HOUR) return false
    if (waitForLineup) return false
    return true
}

/** 등말소 알림 키. 선수코드는 조회마다 비어 있을 수 있어 넣지 않는다. */
fun rosterNotifyKey(move: RosterMove): String =
    "${move.moveDate}:${move.moveType}:${move.playerName}"

fun rosterIdentity(move: RosterMove): String =
    "${move.moveType}:${move.playerName}"

data class RosterNotifyPlan(
    val fresh: List<RosterMove>,
    val stored: Set<String>,
    val changed: Boolean,
)

/**
 * 같은 날짜·이름·등록/말소만 중복이다. 다른 날짜의 재등록은 새 공시다.
 * 이전 날짜 자료는 호출부에서 조회 날짜와 함께 검증한다. 선수코드 유무는 키에 영향을 주지 않는다.
 */
fun planRosterNotifications(
    moves: List<RosterMove>,
    stored: Set<String>,
    today: String,
): RosterNotifyPlan {
    if (moves.isEmpty()) return RosterNotifyPlan(emptyList(), stored, false)
    val next = stored.toMutableSet()
    val fresh = mutableListOf<RosterMove>()
    var changed = false
    for (move in moves) {
        val key = rosterNotifyKey(move)
        val known = key in next
        if (next.add(key)) changed = true
        if (!known && move.moveDate == today) fresh.add(move)
    }
    return RosterNotifyPlan(fresh, next, changed)
}
