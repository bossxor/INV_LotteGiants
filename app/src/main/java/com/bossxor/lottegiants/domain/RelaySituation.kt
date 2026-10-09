package com.bossxor.lottegiants.domain

enum class SubstitutionRole { RUNNER, BATTER, DEFENSE }

data class Substitution(val role: SubstitutionRole, val outgoing: String?, val incoming: String,
    val base: Int? = null, val order: Int? = null)

/** 교체 문구와 타석 시작 문구는 타격 결과가 아니다. */
fun parseSubstitution(text: String): Substitution? {
    val name = "([가-힣A-Za-z·.]+)"
    Regex("([123])루\\s*주자\\s+$name\\s*:\\s*대주자\\s+$name").find(text)?.let {
        return Substitution(SubstitutionRole.RUNNER, it.groupValues[2], it.groupValues[3], it.groupValues[1].toInt())
    }
    Regex("([1-9])번\\s*타자\\s+$name\\s*:\\s*대타\\s+$name").find(text)?.let {
        return Substitution(SubstitutionRole.BATTER, it.groupValues[2], it.groupValues[3], order = it.groupValues[1].toInt())
    }
    if (text.contains("수비위치") || (text.contains("교체") && !text.contains("대타") && !text.contains("대주자"))) {
        return null // 수비 교체를 타석·주자 교체로 해석하지 않는다.
    }
    return null
}

fun isPlateStart(text: String): Boolean =
    Regex("^[1-9]번\\s*타자\\s+[^:]+$").matches(text.trim()) ||
        Regex("^대타\\s+[^:]+$").matches(text.trim())

/** 현재 타석의 선수코드를 유지하되 교체/출루로 완료된 타석은 확인된 다음 타석만 사용한다. */
fun resolvedAtBat(game: LotteGameInfo, bases: NamedBases, texts: List<RelayText> = game.recentTexts): String {
    val half = texts.filter { it.inning == game.inning && (it.isTopInning == null || it.isTopInning == game.isTopInning) }
    val pool = if (game.isLotteBatting) game.lotteLineup + game.lotteBenchBatters else game.opponentLineup + game.opponentBenchBatters
    var current = game.currentBatterCode.takeIf(String::isNotBlank)?.let { code -> pool.firstOrNull { it.playerCode == code }?.name }
        ?: game.currentBatterName
    half.sortedBy { it.seqno }.mapNotNull { parseSubstitution(it.text) }
        .lastOrNull { it.role == SubstitutionRole.BATTER && it.outgoing == current }
        ?.incoming?.takeIf { name -> pool.any { it.name == name } }?.let { current = it }
    val runners = listOfNotNull(bases.first, bases.second, bases.third)
    val plate = half.filter { isPlateStart(it.text) }.maxByOrNull { it.seqno }
    val titleName = plate?.let { batterNameFromTitle(it.text) ?: it.text.removePrefix("대타").trim() }
        ?.takeIf { n -> pool.any { it.name == n } && n !in runners }
    val last = half.maxByOrNull { it.seqno }
    if (current in runners) return titleName.orEmpty()
    if (current.isBlank()) return titleName.orEmpty()
    val currentPlate = half.filter { it.batterCode == game.currentBatterCode || it.batterTitle.contains(current) }
    val result = currentPlate.filter { !isRunnerMovement(it.text) && parseSubstitution(it.text) == null &&
        looksLikePlateAppearanceAdvance(it.text) && it.text.contains(current) }.maxByOrNull { it.seqno }
    // 완료된 타석과 동일 선수의 다음 투구를 구분한다.
    if (result != null && last != null && last.seqno >= result.seqno &&
        currentPlate.none { it.seqno > result.seqno && (looksLikePitch(it.text) || isPlateStart(it.text)) }) {
        if (plate != null && plate.seqno > result.seqno && titleName != null) return titleName
        return game.nextBatterName.takeIf { it.isNotBlank() && it !in runners }.orEmpty()
    }
    return current.takeIf { it.isNotBlank() && it !in runners }.orEmpty()
}
