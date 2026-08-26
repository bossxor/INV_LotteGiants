package com.bossxor.lottegiants.domain

enum class RelayKind { Score, Hit, Walk, Out, Pitch, Other }

data class RelayBatterBlock(val title: String, val items: List<RelayText>)

data class RelayOutBlock(val outCount: Int, val batters: List<RelayBatterBlock>)

fun classifyRelay(text: String): RelayKind {
    val t = text
    if (t.contains("무안타")) {
        return when {
            listOf("홈런", "득점", "타점", "끝내기", "역전", "동점").any { t.contains(it) } -> RelayKind.Score
            isOutResult(t) -> RelayKind.Out
            else -> RelayKind.Other
        }
    }
    return when {
        listOf("홈런", "득점", "타점", "끝내기", "역전", "동점").any { t.contains(it) } -> RelayKind.Score
        isOutResult(t) -> RelayKind.Out
        listOf("2루타", "3루타", "내야안타", "안타", "희생플라이", "희생번트").any { t.contains(it) } -> RelayKind.Hit
        listOf("볼넷", "사구", "몸에 맞는", "고의4구").any { t.contains(it) } -> RelayKind.Walk
        listOf("스트라이크", "볼", "파울").any { t.contains(it) } -> RelayKind.Pitch
        else -> RelayKind.Other
    }
}

fun isOutMakingPlay(text: String): Boolean = isOutResult(text)

private fun isOutResult(text: String): Boolean {
    if (text.contains("이닝 종료") || text.contains("3아웃")) return true
    if (text.contains("병살") || text.contains("삼진")) return true
    return listOf(
        "땅볼 아웃", "플라이 아웃", "뜬공 아웃", "직선타 아웃", "라인드라이브 아웃",
        "태그 아웃", "태그아웃", "포스 아웃", "포스아웃", "견제 아웃",
        "도루 실패", "아웃",
    ).any { text.contains(it) } && !text.contains("무안타")
}

fun outDelta(text: String): Int = when {
    text.contains("병살") -> 2
    text.contains("이닝 종료") || text.contains("3아웃") -> 3
    else -> 1
}

fun outCountLabel(out: Int): String = when (out) {
    0 -> "0아웃"
    1 -> "1아웃"
    2 -> "2아웃"
    else -> "이닝 종료"
}

/** 이닝 안을 아웃카운트 → 타석 순으로 묶는다. */
fun groupRelayByOut(texts: List<RelayText>): List<RelayOutBlock> {
    if (texts.isEmpty()) return emptyList()
    val chrono = texts.sortedBy { it.seqno }
    var outsBefore = 0
    val tagged = chrono.map { t ->
        val recorded = t.out
        val before = when {
            recorded == null -> outsBefore
            recorded > outsBefore -> outsBefore
            else -> recorded
        }.coerceIn(0, 3)
        outsBefore = when {
            recorded != null -> recorded.coerceIn(0, 3)
            isOutMakingPlay(t.text) -> (outsBefore + outDelta(t.text)).coerceAtMost(3)
            else -> outsBefore
        }
        t to before
    }
    return tagged.groupBy { it.second }.toSortedMap().map { (out, pairs) ->
        val items = pairs.map { it.first }
        val batters = mutableListOf<RelayBatterBlock>()
        val bucket = mutableListOf<RelayText>()
        var currentTitle = ""
        fun flush() {
            if (bucket.isEmpty()) return
            batters.add(RelayBatterBlock(currentTitle, bucket.toList()))
            bucket.clear()
        }
        items.forEach { t ->
            val title = t.batterTitle.ifBlank { currentTitle }
            if (title.isNotBlank() && title != currentTitle && bucket.isNotEmpty()) flush()
            if (title.isNotBlank()) currentTitle = title
            bucket.add(t)
        }
        flush()
        RelayOutBlock(out, batters)
    }
}

fun nextBatOrder(batOrder: Int): Int? =
    if (batOrder in 1..9) (batOrder % 9) + 1 else null
