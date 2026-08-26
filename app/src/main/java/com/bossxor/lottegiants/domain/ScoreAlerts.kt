package com.bossxor.lottegiants.domain

/** 1=롯데 리드, -1=상대 리드, 0=동점 */
fun leadOf(lotte: Int, opp: Int): Int = when {
    lotte > opp -> 1
    lotte < opp -> -1
    else -> 0
}

/**
 * 역전은 **지고 있던 팀**이 앞서는 경우만.
 * 무승부에서 선취/리드 (`0:0 → 0:1`)는 역전이 아니다.
 */
fun leadChangeTitle(
    prevLotte: Int,
    prevOpp: Int,
    nowLotte: Int,
    nowOpp: Int,
    opponentName: String,
): String? {
    val prev = leadOf(prevLotte, prevOpp)
    val now = leadOf(nowLotte, nowOpp)
    if (prev == now) return null
    if (now == 0) return "동점!"
    if (now == 1 && prev == -1) return "롯데 역전!"
    if (now == -1 && prev == 1) return "${opponentName.ifBlank { "상대" }} 역전"
    return null
}

fun pickScoringRelay(texts: List<RelayText>): RelayText? {
    val keys = listOf("홈런", "득점", "타점", "적시", "희생플라이", "밀어내기")
    return texts
        .filter { t -> keys.any { k -> t.text.contains(k) } }
        .maxByOrNull { it.seqno }
        ?: texts.maxByOrNull { it.seqno }
}

fun formatLotteScoreTitle(who: String?, runs: Int, score: String): String {
    val n = runs.coerceAtLeast(1)
    return buildString {
        append("롯데 득점! ")
        if (!who.isNullOrBlank()) append("$who ")
        append("${n}점 · $score")
    }
}

fun formatHomerunTitle(who: String?, runs: Int, score: String): String {
    val n = runs.coerceAtLeast(1)
    return buildString {
        append("롯데 홈런! ${n}점")
        if (!who.isNullOrBlank()) append(" · $who")
        append(" · $score")
    }
}

fun formatConcedeTitle(who: String?, opponentName: String, runs: Int, score: String): String {
    val n = runs.coerceAtLeast(1)
    val name = who?.takeIf { it.isNotBlank() } ?: opponentName.ifBlank { "상대" }
    return "${name} ${n}점 실점 · $score"
}

fun detailTabIndex(tab: String?): Int = when (tab?.lowercase()) {
    "preview", "프리뷰", "0" -> 0
    "lineup", "라인업", "1" -> 1
    "summary", "요약", "2" -> 2
    "relay", "중계", "3" -> 3
    "record", "기록", "4" -> 4
    else -> 0
}
