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

private val SCORING_KEYS = listOf("홈런", "득점", "타점", "적시", "희생플라이", "밀어내기", "홈인")
private val ADVANCE_KEYS = listOf(
    "홈런", "3루타", "2루타", "내야안타", "적시", "안타",
    "볼넷", "사구", "몸에 맞는", "고의4구",
    "도루", "폭투", "패스트볼", "실책", "야수선택",
    "희생", "진루", "밀어내기", "홈인",
)
private val PITCH_KEYS = listOf("스트라이크", "볼", "파울")
private val PLAY_HOW = listOf(
    "만루홈런", "그랜드슬램", "3점홈런", "2점홈런", "솔로홈런", "홈런",
    "3루타", "2루타", "내야안타", "적시타", "안타",
    "희생플라이", "희생번트", "밀어내기",
    "고의4구", "볼넷", "사구", "몸에 맞는 공",
    "도루", "폭투", "패스트볼", "실책", "야수선택",
)
private val PLAY_DIR = listOf("좌월", "우월", "중월", "좌전", "우전", "중전", "좌익", "우익", "중견")

fun pickScoringRelay(texts: List<RelayText>): RelayText? {
    val scored = texts.filter { t -> SCORING_KEYS.any { k -> t.text.contains(k) } }
    if (scored.isNotEmpty()) return scored.maxByOrNull { it.seqno }
    return texts.filterNot { looksLikePitch(it.text) }.maxByOrNull { it.seqno }
        ?: texts.maxByOrNull { it.seqno }
}

fun pickAdvanceRelay(texts: List<RelayText>): RelayText? {
    val advanced = texts.filter { t ->
        val text = t.text
        ADVANCE_KEYS.any { text.contains(it) } && !text.contains("도루 실패")
    }
    if (advanced.isNotEmpty()) return advanced.maxByOrNull { it.seqno }
    return texts.filterNot { looksLikePitch(it.text) }.maxByOrNull { it.seqno }
}

fun looksLikePitch(text: String): Boolean {
    if (SCORING_KEYS.any { text.contains(it) } || ADVANCE_KEYS.any { text.contains(it) }) return false
    return PITCH_KEYS.any { text.contains(it) }
}

fun batterNameFromTitle(batterTitle: String): String? {
    val t = batterTitle.trim()
    if (t.isBlank()) return null
    val after = t.substringAfter("타자", missingDelimiterValue = "").trim()
    if (after.isNotBlank()) return after
    return Regex("""\d+\s*번\s+(.+)""").find(t)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}

/** 긴 이름부터 매칭해서 `김민`이 `김민석`에 걸리지 않게 한다. */
fun namesInText(text: String, names: List<String>): List<String> {
    val sorted = names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    val found = mutableListOf<String>()
    val buf = StringBuilder(text)
    for (n in sorted) {
        val at = buf.indexOf(n)
        if (at >= 0) {
            found.add(n)
            repeat(n.length) { i -> buf.setCharAt(at + i, ' ') }
        }
    }
    return found
}

fun pickPlayerName(text: String, batterTitle: String, roster: List<String>): String? {
    val how = describePlayHow(text)
    val inText = namesInText(text, roster)
    if (how in setOf("도루", "폭투", "패스트볼")) {
        inText.firstOrNull()?.let { return it }
    }
    val fromTitle = batterNameFromTitle(batterTitle)?.takeIf { it in roster }
    if (fromTitle != null) return fromTitle
    return inText.firstOrNull()
}

fun describePlayHow(text: String): String? {
    if (text.isBlank()) return null
    val kind = PLAY_HOW.firstOrNull { text.contains(it) } ?: return null
    val dir = PLAY_DIR.firstOrNull { text.contains(it) }
    val hit = kind == "안타" || kind == "적시타" || kind == "2루타" || kind == "3루타" || kind == "홈런" ||
        kind.endsWith("홈런")
    return if (dir != null && hit) "$dir $kind" else kind
}

fun formatLotteScoreTitle(who: String?, runs: Int, score: String, how: String? = null): String {
    val n = runs.coerceAtLeast(1)
    val whoPart = who?.trim().orEmpty()
    val howPart = how?.trim().orEmpty()
    val play = listOf(whoPart, howPart).filter { it.isNotBlank() }.joinToString(" ")
    return buildString {
        append("롯데 득점!")
        if (play.isNotBlank()) append(" $play")
        append(" · ${n}타점 · $score")
    }
}

fun formatHomerunTitle(who: String?, runs: Int, score: String, how: String? = null): String {
    val n = runs.coerceAtLeast(1)
    val howPart = how?.trim()?.takeIf { it.isNotBlank() } ?: "${n}점홈런"
    val whoPart = who?.trim().orEmpty()
    return buildString {
        append("롯데 홈런!")
        if (whoPart.isNotBlank()) append(" $whoPart")
        append(" $howPart · $score")
    }
}

fun formatConcedeTitle(
    who: String?,
    opponentName: String,
    runs: Int,
    score: String,
    how: String? = null,
): String {
    val n = runs.coerceAtLeast(1)
    val whoPart = who?.trim()?.takeIf { it.isNotBlank() } ?: opponentName.ifBlank { "상대" }
    val howPart = how?.trim().orEmpty()
    val play = listOf(whoPart, howPart).filter { it.isNotBlank() }.joinToString(" ")
    return "실점! $play · ${n}점 · $score"
}

data class ChanceAlert(val title: String, val text: String)

fun runnersLabel(first: String?, second: String?, third: String?): String =
    listOfNotNull(
        third?.takeIf { it.isNotBlank() }?.let { "3루 $it" },
        second?.takeIf { it.isNotBlank() }?.let { "2루 $it" },
        first?.takeIf { it.isNotBlank() }?.let { "1루 $it" },
    ).joinToString(" · ")

fun formatScoringChanceAlert(
    loaded: Boolean,
    who: String?,
    how: String?,
    runners: String,
    batterNow: String,
    inningLabel: String,
    outs: Int,
): ChanceAlert {
    val chance = if (loaded) "만루" else "득점권"
    val whoPart = who?.trim().orEmpty()
    val howPart = how?.trim().orEmpty()
    val atBat = batterNow.trim()
    val play = when {
        whoPart.isNotBlank() && howPart.isNotBlank() -> "$whoPart $howPart, $chance"
        howPart.isNotBlank() -> "$howPart, $chance"
        whoPart.isNotBlank() -> "$whoPart, $chance"
        loaded -> "만루 찬스"
        else -> "득점권 찬스"
    }
    val title = if (atBat.isNotBlank()) "$play · 타석 $atBat" else play
    val text = listOfNotNull(
        runners.takeIf { it.isNotBlank() },
        atBat.takeIf { it.isNotBlank() }?.let { "타석 $it" },
        "$inningLabel ${outCountLabel(outs)}",
    ).joinToString(" · ")
    return ChanceAlert(title, text)
}

/** 방금 출루한 선수가 아직 currentBatter로 남아 있으면 다음 타자를 쓴다. */
fun atBatForChance(currentBatter: String, nextBatter: String, playMaker: String?): String {
    val cur = currentBatter.trim()
    val nxt = nextBatter.trim()
    val maker = playMaker?.trim().orEmpty()
    if (cur.isNotBlank() && cur != maker) return cur
    if (nxt.isNotBlank()) return nxt
    return cur
}

fun scoringBody(playText: String, who: String?, how: String?, inningLabel: String): String {
    val raw = playText.trim()
    if (raw.isNotBlank() && !looksLikePitch(raw)) return raw
    return listOfNotNull(who?.takeIf { it.isNotBlank() }, how?.takeIf { it.isNotBlank() }, inningLabel)
        .joinToString(" · ")
        .ifBlank { inningLabel }
}

fun basesKey(on1: Boolean, on2: Boolean, on3: Boolean): String = "$on1,$on2,$on3"

fun parseBasesKey(key: String): Triple<Boolean, Boolean, Boolean> {
    val p = key.split(',')
    if (p.size != 3) return Triple(false, false, false)
    return Triple(p[0].toBooleanStrictOrNull() == true, p[1].toBooleanStrictOrNull() == true, p[2].toBooleanStrictOrNull() == true)
}

fun detailTabIndex(tab: String?): Int = when (tab?.lowercase()) {
    "preview", "프리뷰", "0" -> 0
    "lineup", "라인업", "1" -> 1
    "summary", "요약", "2" -> 2
    "relay", "중계", "3" -> 3
    "record", "기록", "4" -> 4
    else -> 0
}
