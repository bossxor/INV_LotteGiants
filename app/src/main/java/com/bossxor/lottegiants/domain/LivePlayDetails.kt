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
        val substitution = parseSubstitution(text)
        if (substitution?.role == SubstitutionRole.RUNNER) {
            val incoming = resolvedRunnerName(substitution.incoming, roster)
            val base = substitution.base!! - 1
            if (incoming != null) bases[base] = incoming
            continue
        }
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

@kotlinx.serialization.Serializable
data class ScoringPlay(
    val seqno: Int,
    val who: String?,
    val how: String?,
    val runs: Int,
    val rbi: Int?,
    val scorers: List<String>,
    val inning: Int,
    val isTop: Boolean?,
    val startScore: Int = 0,
    val endScore: Int = 0,
    val out: Int? = null,
)

/** 타점은 같은 플레이의 득점 증가분에 한정한다. 실책을 동반한 안타는 추정하지 않는다. */
fun creditedRbi(how: String?, runs: Int, texts: List<RelayText>): Int? {
    val h = how.orEmpty()
    if (listOf("폭투", "패스트볼", "보크", "도루", "실책", "병살").any(h::contains)) return 0
    val raw = texts.joinToString(" ") { it.text }
    Regex("""(\d+)\s*타점""").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    if (raw.contains("실책") || raw.contains("병살")) return null
    return if (listOf("안타", "적시타", "1루타", "2루타", "3루타", "홈런", "희생플라이", "밀어내기", "볼넷", "고의4구", "사구", "몸에 맞는")
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
    data class Group(val cause: RelayText?, val start: Int, var end: Int)
    val grouped = linkedMapOf<Int, Group>()
    var counted = previousScore
    for (anchor in anchors) {
        val atScore = score(anchor) ?: continue
        if (atScore <= counted) continue
        val window = relevant.filter { it.inning == anchor.inning && it.seqno <= anchor.seqno }
        var cause = window.lastOrNull { describePlayHow(it.text) != null &&
            !isRunnerMovement(it.text) && parseSubstitution(it.text) == null }
        // 같은 플레이의 홈인이 다음 조회로 나뉘어도 원인을 유지한다.
        if (cause != null && window.any { it.seqno > cause!!.seqno && looksLikePitch(it.text) &&
                it.batterTitle.isNotBlank() && it.batterTitle != cause!!.batterTitle }) cause = null
        if (cause != null && window.any { it.seqno > cause!!.seqno &&
                isPlateResult(it.text) && describePlayHow(it.text) == null }) cause = null
        val key = cause?.seqno ?: anchor.seqno
        val old = grouped[key]
        if (old != null) old.end = atScore else grouped[key] = Group(cause, counted, atScore)
        counted = atScore
    }
    if (grouped.isEmpty()) {
        val fresh = relevant.filter { it.seqno > lastSeqno }
        val cause = pickScoringRelay(fresh)?.takeIf { describePlayHow(it.text) != null &&
            !isRunnerMovement(it.text) && parseSubstitution(it.text) == null &&
            (it.text.contains("홈런") || it.text.contains("타점") || it.text.contains("적시") ||
                fresh.any { t -> t.inning == it.inning && t.seqno >= it.seqno &&
                    t.batterTitle == it.batterTitle && isRunnerMovement(t.text) &&
                    (t.text.contains("홈인") || t.text.contains("득점")) }) }
        grouped[cause?.seqno ?: (fresh.maxOfOrNull { it.seqno } ?: lastSeqno)] =
            Group(cause, previousScore, currentScore)
    } else if (counted < currentScore) {
        grouped[(relevant.maxOfOrNull { it.seqno } ?: lastSeqno) + 1] = Group(null, counted, currentScore)
    }
    return grouped.map { (key, group) ->
        val cause = group.cause
        val nextCause = relevant.firstOrNull { it.seqno > key && isPlateResult(it.text) }?.seqno ?: Int.MAX_VALUE
        val playTexts = relevant.filter { it.seqno >= key && it.seqno < nextCause &&
            (cause == null || it.inning == cause.inning) && (score(it) == null || score(it)!! <= currentScore) }
        val how = cause?.let { describePlayHow(it.text) }
        val who = cause?.let { pickPlayerName(it.text, it.batterTitle, roster) }
        val scorers = playTexts.filter { isRunnerMovement(it.text) &&
            (it.text.contains("홈인") || it.text.contains("득점")) }
            .flatMap { namesInText(it.text, roster) }.toMutableList()
        if (how?.contains("홈런") == true && who != null) scorers.add(who)
        val runs = group.end - group.start
        ScoringPlay(key, who, how, runs,
            if (anchors.isEmpty()) null else creditedRbi(how, runs, playTexts),
            scorers.distinct(), cause?.inning ?: playTexts.firstOrNull()?.inning ?: 0,
            cause?.isTopInning ?: playTexts.firstOrNull()?.isTopInning,
            group.start, group.end, playTexts.lastOrNull()?.out)
    }
}

fun isPlateResult(text: String): Boolean = !isRunnerMovement(text) && parseSubstitution(text) == null &&
    !isPlateStart(text) && (describePlayHow(text) != null ||
        (text.contains(":") && listOf("아웃", "땅볼", "플라이", "삼진").any(text::contains)))

fun scoringDetailBody(play: ScoringPlay, atBat: String, bases: NamedBases, inningLabel: String): String =
    listOfNotNull(
        play.scorers.takeIf { it.isNotEmpty() }?.joinToString("·")?.let { "홈인 $it" },
        bases.label().takeIf { it.isNotBlank() }?.let { "주자 $it" },
        atBat.takeIf { it.isNotBlank() }?.let { "타석 $it" },
        listOfNotNull(inningLabel, play.out?.let { "${it}아웃" }).joinToString(" "),
    ).joinToString(" · ")
