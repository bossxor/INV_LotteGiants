package com.bossxor.lottegiants.domain

/** 루 번호는 출발 위치이며, 득점/진루/아웃의 결과 위치와 다르다. */
fun isRunnerMovement(text: String): Boolean =
    Regex("""[123]루\s*주자""").containsMatchIn(text) &&
        listOf("홈인", "득점", "아웃", "진루", "도루", "교체").any(text::contains)

fun resolvedRunnerName(raw: String, roster: List<String>): String? =
    roster.filter { it.isNotBlank() }.sortedByDescending { it.length }
        .firstOrNull { raw == it || raw == "${it}이" || raw == "${it}가" }
        ?: raw.trim().takeIf { roster.isEmpty() && it.isNotBlank() }

/** 이름을 명시한 진루/홈인/아웃만 반영한다. 안타의 진루 거리를 추측하지 않는다. */
fun advanceNamedRunners(before: NamedBases, texts: List<RelayText>, roster: List<String>): NamedBases {
    val bases = arrayOf(before.first, before.second, before.third)
    val runner = Regex("""([123])루\s*주자\s+([가-힣A-Za-z·.]+)""")
    for (line in texts.sortedBy { it.seqno }) {
        val text = line.text
        val match = runner.find(text) ?: continue
        val name = resolvedRunnerName(match.groupValues[2], roster) ?: continue
        val destination = Regex("""([123])루\s*(?:까지|로|진루|도루)""")
            .find(text.substring(match.range.last + 1))?.groupValues?.get(1)?.toIntOrNull()
        if (text.contains("도루 실패") || text.contains("도루실패") || text.contains("아웃") ||
            text.contains("홈인") || text.contains("득점") || destination != null
        ) {
            bases.indices.filter { bases[it] == name }.forEach { bases[it] = null }
            if (destination != null && !text.contains("아웃") && !text.contains("실패")) {
                bases[destination - 1] = name
            }
        }
    }
    return NamedBases(bases[0], bases[1], bases[2])
}

fun NamedBases.onlyOccupied(on1: Boolean, on2: Boolean, on3: Boolean): NamedBases =
    NamedBases(if (on1) first else null, if (on2) second else null, if (on3) third else null)

data class ScoringPlay(
    val seqno: Int,
    val who: String?,
    val how: String?,
    val runs: Int,
    val rbi: Int?,
    val scorers: List<String>,
    val inning: Int,
    val isTop: Boolean?,
)

/** 타점은 같은 플레이의 득점 증가분에 한정한다. 실책을 동반한 안타는 추정하지 않는다. */
fun creditedRbi(how: String?, runs: Int, texts: List<RelayText>): Int? {
    val h = how.orEmpty()
    if (listOf("폭투", "패스트볼", "보크", "도루", "실책", "병살").any(h::contains)) return 0
    val raw = texts.joinToString(" ") { it.text }
    Regex("""(\d+)\s*타점""").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    if (raw.contains("실책") || raw.contains("병살")) return null
    return if (listOf("안타", "적시타", "2루타", "3루타", "홈런", "희생플라이", "밀어내기", "볼넷", "사구")
            .any(h::contains)) runs else null
}

/** 중계에 보존한 플레이별 점수로 득점 원인과 홈인 주자를 묶는다. */
fun scoringPlays(
    texts: List<RelayText>,
    lastSeqno: Int,
    previousScore: Int,
    currentScore: Int,
    scoringTeamIsHome: Boolean,
    roster: List<String>,
): List<ScoringPlay> {
    if (currentScore <= previousScore) return emptyList()
    val relevant = texts.filter { it.isTopInning == null || it.isTopInning == !scoringTeamIsHome }
        .sortedBy { it.seqno }
    fun score(t: RelayText) = if (scoringTeamIsHome) t.homeScore else t.awayScore
    val anchors = relevant.filter { score(it)?.let { s -> s > previousScore && s <= currentScore } == true }
    val grouped = linkedMapOf<Int, Pair<RelayText?, Int>>()
    var counted = previousScore
    for (anchor in anchors) {
        val atScore = score(anchor) ?: continue
        if (atScore <= counted) continue
        val window = relevant.filter { it.inning == anchor.inning && it.seqno <= anchor.seqno &&
            it.seqno >= anchor.seqno - 15 && (score(it) == null || score(it)!! >= previousScore) }
        var cause = pickScoringRelay(window)?.takeIf { describePlayHow(it.text) != null }
        // 새 타석의 투구를 넘어 과거 안타를 이번 득점에 연결하지 않는다.
        if (cause != null && window.any { it.seqno > cause!!.seqno && looksLikePitch(it.text) &&
                it.batterTitle.isNotBlank() && it.batterTitle != cause!!.batterTitle }) cause = null
        if (cause != null && window.any { it.seqno > cause!!.seqno &&
                (it.text.contains("홈인") || it.text.contains("득점")) && score(it) == previousScore }) cause = null
        val key = cause?.seqno ?: anchor.seqno
        grouped[key] = cause to ((grouped[key]?.second ?: 0) + atScore - counted)
        counted = atScore
    }
    if (grouped.isEmpty()) {
        // 점수만 먼저 도착하면 과거 타자와 타점을 재사용하지 않는다.
        val fresh = relevant.filter { it.seqno > lastSeqno }
        val cause = pickScoringRelay(fresh)?.takeIf { describePlayHow(it.text) != null }
        grouped[cause?.seqno ?: (fresh.maxOfOrNull { it.seqno } ?: lastSeqno)] =
            cause to (currentScore - previousScore)
    } else if (counted < currentScore) {
        grouped[(relevant.maxOfOrNull { it.seqno } ?: lastSeqno) + 1] = null to (currentScore - counted)
    }
    return grouped.map { (key, entry) ->
        val (cause, runs) = entry
        val nextCause = relevant.firstOrNull { it.seqno > key && describePlayHow(it.text) != null &&
            !isRunnerMovement(it.text) }?.seqno ?: Int.MAX_VALUE
        val playTexts = relevant.filter { it.seqno >= key && it.seqno < nextCause &&
            (cause == null || it.inning == cause.inning) }
        val how = cause?.let { describePlayHow(it.text) }
        val who = cause?.let { pickPlayerName(it.text, it.batterTitle, roster) }
        val scorers = playTexts.filter { it.text.contains("홈인") || it.text.contains("득점") }
            .flatMap { namesInText(it.text, roster) }.toMutableList()
        if (how?.contains("홈런") == true && who != null) scorers.add(who)
        ScoringPlay(key, who, how, runs,
            if (anchors.isEmpty()) null else creditedRbi(how, runs, playTexts),
            scorers.distinct(), cause?.inning ?: playTexts.firstOrNull()?.inning ?: 0,
            cause?.isTopInning ?: playTexts.firstOrNull()?.isTopInning)
    }
}

fun scoringDetailBody(play: ScoringPlay, atBat: String, bases: NamedBases, inningLabel: String): String =
    listOfNotNull(
        play.scorers.takeIf { it.isNotEmpty() }?.joinToString("·")?.let { "홈인 $it" },
        bases.label().takeIf { it.isNotBlank() }?.let { "주자 $it" },
        atBat.takeIf { it.isNotBlank() }?.let { "타석 $it" },
        inningLabel,
    ).joinToString(" · ")
