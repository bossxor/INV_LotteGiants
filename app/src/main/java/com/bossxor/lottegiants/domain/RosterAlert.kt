package com.bossxor.lottegiants.domain

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
 * 이미 본 공시(같은 날짜·이름·등록/말소, 또는 날짜만 바뀐 동일 인물)는 다시 알리지 않는다.
 * 오전 5시에 kboToday()가 바뀌거나 코드가 생겼다 사라지는 경우의 재알림을 막는다.
 */
fun planRosterNotifications(
    moves: List<RosterMove>,
    stored: Set<String>,
    today: String,
): RosterNotifyPlan {
    if (moves.isEmpty()) return RosterNotifyPlan(emptyList(), stored, false)
    if (stored.isEmpty()) {
        return RosterNotifyPlan(emptyList(), moves.map(::rosterNotifyKey).toSet(), true)
    }
    val next = stored.toMutableSet()
    val fresh = mutableListOf<RosterMove>()
    var changed = false
    for (move in moves) {
        val key = rosterNotifyKey(move)
        val identity = rosterIdentity(move)
        val known = next.any { it == key || it.endsWith(":$identity") }
        if (next.add(key)) changed = true
        if (!known && move.moveDate == today) fresh.add(move)
    }
    return RosterNotifyPlan(fresh, next, changed)
}
