package com.bossxor.lottegiants.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** KBO 정규시즌 경기 수. 실제 소화 경기가 더 많으면 그 값을 쓴다. */
const val KBO_REGULAR_GAMES = 144

/** 정규시즌 포스트시즌 진출 컷 (5위, 와일드카드). */
const val KBO_POSTSEASON_SPOTS = 5

data class RaceSummary(
    val headline: String,
    val lines: List<String>,
    val upcoming: List<String> = emptyList(),
)

data class RemainingSeries(
    val opponent: String,
    val games: Int,
    val home: Boolean,
    val startDate: String,
    val endDate: String,
)

data class RemainingOpponent(
    val code: String,
    val name: String,
    val games: Int,
    val home: Int,
    val away: Int,
)

/** 알림 비교용. magic/tragic이 null이면 해당 줄 없음. */
data class RacePulse(
    val rank: Int,
    val slot: String,
    val magic: Int?,
    val tragic: Int?,
    val magicLabel: String,
) {
    fun fingerprint(): String = "$rank|$slot|${magic ?: -1}|${tragic ?: -1}"
}

fun seasonLength(standings: List<TeamStanding>): Int =
    maxOf(KBO_REGULAR_GAMES, standings.maxOfOrNull { it.gameCount } ?: 0)

fun remainingGames(team: TeamStanding, seasonG: Int): Int =
    (seasonG - team.gameCount).coerceAtLeast(0)

/**
 * A가 B를 상대로 순위를 확정하는 매직넘버.
 * `G + 1 - A승 - B패`. 0 이하면 이미 확정.
 * 추격 팀 기준으로 같은 식을 쓰면 트래직넘버가 된다.
 */
fun magicNumber(leader: TeamStanding, trailing: TeamStanding, seasonG: Int): Int =
    seasonG + 1 - leader.win - trailing.lose

/** [team] 기준 [base]와의 게임차. 음수면 뒤짐, 양수면 앞섬. */
fun gamesAhead(team: TeamStanding, base: TeamStanding): Double =
    ((team.win - team.lose) - (base.win - base.lose)) / 2.0

fun formatGamesAbs(gb: Double): String =
    if (abs(gb) % 1.0 < 0.05) abs(gb).toInt().toString()
    else String.format(Locale.US, "%.1f", abs(gb))

fun kboPostseasonSlot(rank: Int): String = when {
    rank <= 0 -> ""
    rank == 1 -> "한국시리즈 직행"
    rank == 2 -> "플레이오프 직행"
    rank == 3 -> "준플레이오프"
    rank <= KBO_POSTSEASON_SPOTS -> "와일드카드"
    else -> "포스트시즌 밖"
}

fun widgetRaceLine(rank: Int, remaining: Int, starter: String, countdown: String = ""): String {
    val parts = buildList {
        if (rank > 0) add("${rank}위")
        if (remaining > 0) add("잔여 ${remaining}")
        else if (rank > 0) add("잔여 0")
        val s = starter.trim()
        if (s.isNotBlank() && s != "-") add("선발 $s")
        val c = countdown.trim()
        if (c.isNotBlank()) add(c)
    }
    return parts.joinToString(" · ")
}

fun formatRemainingOpponents(list: List<RemainingOpponent>, limit: Int = 6): String {
    if (list.isEmpty()) return ""
    val shown = list.sortedByDescending { it.games }.take(limit)
    val rest = list.size - shown.size
    val body = shown.joinToString(" · ") { "${it.name} ${it.games}" }
    return if (rest > 0) "잔여 상대 $body 외 ${rest}팀" else "잔여 상대 $body"
}

fun remainingOpponentsFrom(
    games: List<MiniGame>,
    todayIso: String,
    lotteCode: String = LOTTE_TEAM_CODE,
): List<RemainingOpponent> {
    val upcoming = games.filter { g ->
        g.involvesTeam(lotteCode) &&
            g.status != GameStatus.CANCELED &&
            g.status != GameStatus.ENDED &&
            (g.gameDate > todayIso || (g.gameDate == todayIso && g.status == GameStatus.BEFORE))
    }
    return upcoming.groupBy { g ->
        val home = g.isTeamHome(lotteCode) == true
        val code = if (home) g.awayTeamCode else g.homeTeamCode
        code.ifBlank { if (home) g.awayName else g.homeName }
    }.map { (code, list) ->
        val sample = list.first()
        val homeSample = sample.isTeamHome(lotteCode) == true
        val rawName = if (homeSample) sample.awayName else sample.homeName
        RemainingOpponent(
            code = code,
            name = teamCodeToName(code).ifBlank { rawName },
            games = list.size,
            home = list.count { it.isTeamHome(lotteCode) == true },
            away = list.count { it.isTeamHome(lotteCode) != true },
        )
    }.sortedByDescending { it.games }
}

fun formatHomeAwayRemaining(list: List<RemainingOpponent>): String {
    val home = list.sumOf { it.home }
    val away = list.sumOf { it.away }
    if (home + away == 0) return ""
    return "잔여 홈 $home · 원정 $away"
}

fun upcomingLotteGames(
    games: List<MiniGame>,
    todayIso: String,
    lotteCode: String = LOTTE_TEAM_CODE,
): List<MiniGame> =
    games.filter { g ->
        g.involvesTeam(lotteCode) &&
            g.status != GameStatus.CANCELED &&
            g.status != GameStatus.ENDED &&
            (g.gameDate > todayIso || (g.gameDate == todayIso && g.status == GameStatus.BEFORE))
    }.sortedWith(compareBy({ it.gameDate }, { it.startTime }, { it.doubleHeaderNo }))

fun formatMdDate(iso: String): String {
    val p = iso.take(10).split('-')
    if (p.size < 3) return iso
    val m = p[1].toIntOrNull() ?: return iso
    val d = p[2].toIntOrNull() ?: return iso
    return "$m/$d"
}

fun formatDateRange(startIso: String, endIso: String): String {
    if (startIso.take(10) == endIso.take(10)) return formatMdDate(startIso)
    val a = startIso.take(10).split('-')
    val b = endIso.take(10).split('-')
    if (a.size >= 3 && b.size >= 3 && a[1] == b[1]) {
        val m = a[1].toIntOrNull() ?: return "${formatMdDate(startIso)}~${formatMdDate(endIso)}"
        val d1 = a[2].toIntOrNull() ?: return "${formatMdDate(startIso)}~${formatMdDate(endIso)}"
        val d2 = b[2].toIntOrNull() ?: return "${formatMdDate(startIso)}~${formatMdDate(endIso)}"
        return "$m/$d1~$d2"
    }
    return "${formatMdDate(startIso)}~${formatMdDate(endIso)}"
}

fun formatUpcomingLine(g: MiniGame, lotteCode: String = LOTTE_TEAM_CODE): String {
    val home = g.isTeamHome(lotteCode) == true
    val oppCode = if (home) g.awayTeamCode else g.homeTeamCode
    val oppName = teamCodeToName(oppCode).ifBlank { if (home) g.awayName else g.homeName }
    val dh = if (g.doubleHeaderNo > 0) " DH${g.doubleHeaderNo}" else ""
    val time = g.startTime.trim()
    return buildString {
        append(formatMdDate(g.gameDate))
        append(if (home) " 홈 " else " 원정 ")
        append(oppName)
        if (time.isNotBlank()) {
            append(' ')
            append(time)
        }
        append(dh)
    }
}

fun remainingSeriesFrom(
    games: List<MiniGame>,
    todayIso: String,
    lotteCode: String = LOTTE_TEAM_CODE,
): List<RemainingSeries> {
    val upcoming = upcomingLotteGames(games, todayIso, lotteCode)
    if (upcoming.isEmpty()) return emptyList()
    val series = mutableListOf<RemainingSeries>()
    var i = 0
    while (i < upcoming.size) {
        val first = upcoming[i]
        val home = first.isTeamHome(lotteCode) == true
        val oppCode = if (home) first.awayTeamCode else first.homeTeamCode
        val oppName = teamCodeToName(oppCode).ifBlank { if (home) first.awayName else first.homeName }
        var j = i + 1
        while (j < upcoming.size) {
            val g = upcoming[j]
            val gHome = g.isTeamHome(lotteCode) == true
            val gOpp = if (gHome) g.awayTeamCode else g.homeTeamCode
            if (gHome != home || !gOpp.equals(oppCode, ignoreCase = true)) break
            j++
        }
        val chunk = upcoming.subList(i, j)
        series += RemainingSeries(
            opponent = oppName,
            games = chunk.size,
            home = home,
            startDate = chunk.first().gameDate,
            endDate = chunk.last().gameDate,
        )
        i = j
    }
    return series
}

fun formatRemainingSeries(list: List<RemainingSeries>, limit: Int = 4): List<String> =
    list.take(limit).map { s ->
        val n = if (s.games >= 2) "${s.games}연전" else "1경기"
        "${formatDateRange(s.startDate, s.endDate)} ${if (s.home) "홈" else "원정"} ${s.opponent} $n"
    }

/** 매직/트래직 상대 순위. 1위→2위, 4·5위→6위, 6위 이하→5위. */
fun raceOpponentRank(lotteRank: Int): Int? = when {
    lotteRank <= 0 -> null
    lotteRank == 1 -> 2
    lotteRank == 2 -> 3
    lotteRank == 3 -> 4
    lotteRank in 4..KBO_POSTSEASON_SPOTS -> 6
    else -> KBO_POSTSEASON_SPOTS
}

fun formatVsRaceOpponent(
    remaining: List<RemainingOpponent>,
    standings: List<TeamStanding>,
    lotteCode: String = LOTTE_TEAM_CODE,
): String {
    if (remaining.isEmpty()) return ""
    val lotte = standings.firstOrNull { it.teamId.equals(lotteCode, ignoreCase = true) } ?: return ""
    val vsRank = raceOpponentRank(lotte.ranking) ?: return ""
    val vs = standings.firstOrNull { it.ranking == vsRank } ?: return ""
    val rec = remaining.firstOrNull { it.code.equals(vs.teamId, ignoreCase = true) }
    val n = rec?.games ?: 0
    val ha = when {
        rec == null || n <= 0 -> ""
        rec.home > 0 && rec.away > 0 -> " · 홈 ${rec.home}·원정 ${rec.away}"
        rec.home > 0 -> " · 홈"
        else -> " · 원정"
    }
    return if (n <= 0) "${vs.teamName}와 잔여 맞대결 없음"
    else "${vs.teamName}와 잔여 ${n}경기$ha"
}

fun formatPaceLine(lotte: TeamStanding, seasonG: Int): String {
    val rem = remainingGames(lotte, seasonG)
    val decided = lotte.win + lotte.lose
    if (decided <= 0 || rem <= 0) return ""
    val extraWins = (lotte.wra * rem).roundToInt().coerceIn(0, rem)
    val projW = lotte.win + extraWins
    val projL = lotte.lose + (rem - extraWins)
    return "현재 페이스면 최종 ${projW}승 ${projL}패"
}

fun formatSelfClinchLine(lotte: TeamStanding, vs: TeamStanding, seasonG: Int): String {
    if (lotte.ranking <= 0 || lotte.ranking > KBO_POSTSEASON_SPOTS) return ""
    val rem = remainingGames(lotte, seasonG)
    val m = magicNumber(lotte, vs, seasonG)
    if (m <= 0 || rem <= 0) return ""
    return if (m <= rem) "자력: 잔여 ${rem}경기 중 ${m}승이면 상대 결과 무관"
    else "자력 불가 · 상대 패가 ${m - rem}번 필요"
}

fun lastFiveMarks(
    games: List<MiniGame>,
    todayIso: String,
    lotteCode: String = LOTTE_TEAM_CODE,
): String {
    val ended = games.filter { g ->
        g.involvesTeam(lotteCode) &&
            g.status == GameStatus.ENDED &&
            g.gameDate.isNotBlank() &&
            g.gameDate <= todayIso
    }.sortedWith(compareByDescending<MiniGame> { it.gameDate }.thenByDescending { it.doubleHeaderNo })
        .take(5)
        .reversed()
    if (ended.isEmpty()) return ""
    val marks = ended.joinToString("") { g ->
        when (g.teamWon(lotteCode)) {
            true -> "승"
            false -> "패"
            null -> "무"
        }
    }
    return "최근 ${ended.size}경기 $marks"
}

fun gameCountdownLabel(
    date: String,
    time: String,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val start = parseKboStartMillis(date, time)
    if (start == null) {
        val d = runCatching { java.time.LocalDate.parse(date.take(10)) }.getOrNull() ?: return ""
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(KBO_ZONE).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt()
        return when {
            days > 1 -> "D-$days"
            days == 1 -> "내일"
            days == 0 -> "오늘"
            else -> ""
        }
    }
    val delta = start - nowMillis
    if (delta <= 0L) return "임박"
    val minutes = delta / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    val timePart = time.trim()
    return when {
        days >= 2L -> "D-$days"
        days == 1L -> if (timePart.isNotBlank()) "내일 $timePart" else "내일"
        hours >= 1L -> "${hours}시간 ${minutes % 60}분 후"
        minutes >= 1L -> "${minutes}분 후"
        else -> "임박"
    }
}

fun shareRaceText(summary: RaceSummary): String = buildString {
    append(summary.headline)
    summary.lines.forEach { line ->
        append('\n')
        append(line)
    }
    if (summary.upcoming.isNotEmpty()) {
        append("\n다음 경기")
        summary.upcoming.forEach { line ->
            append('\n')
            append(line)
        }
    }
    append("\n#사직스코어")
}

fun racePulse(
    standings: List<TeamStanding>,
    lotteCode: String = LOTTE_TEAM_CODE,
): RacePulse? {
    if (standings.size < 2) return null
    val sorted = standings.sortedBy { it.ranking }
    val lotte = sorted.firstOrNull { it.teamId.equals(lotteCode, ignoreCase = true) } ?: return null
    val seasonG = seasonLength(sorted)
    val slot = kboPostseasonSlot(lotte.ranking)
    fun at(rank: Int) = sorted.firstOrNull { it.ranking == rank } ?: sorted.getOrNull(rank - 1)

    return when {
        lotte.ranking <= 0 -> null
        lotte.ranking > KBO_POSTSEASON_SPOTS -> {
            val fifth = at(KBO_POSTSEASON_SPOTS) ?: return RacePulse(lotte.ranking, slot, null, null, "")
            RacePulse(
                rank = lotte.ranking,
                slot = slot,
                magic = null,
                tragic = magicNumber(fifth, lotte, seasonG),
                magicLabel = "",
            )
        }
        else -> {
            val (label, vsRank) = when (lotte.ranking) {
                1 -> "한국시리즈 직행 매직넘버" to 2
                2 -> "플레이오프 직행 매직넘버" to 3
                3 -> "준플레이오프 매직넘버" to 4
                else -> "와일드카드 매직넘버" to 6
            }
            val vs = at(vsRank)
            RacePulse(
                rank = lotte.ranking,
                slot = slot,
                magic = vs?.let { magicNumber(lotte, it, seasonG) },
                tragic = null,
                magicLabel = label,
            )
        }
    }
}

/**
 * 이전 펄스와 비교해 알림 제목·본문. 첫 관측(prev=null)이거나 변화 없으면 null.
 */
fun raceChangeAlert(prev: RacePulse?, now: RacePulse): Pair<String, String>? {
    if (prev == null) return null
    if (prev.fingerprint() == now.fingerprint()) return null
    if (now.tragic != null && now.tragic <= 0 && (prev.tragic == null || prev.tragic > 0)) {
        return "포스트시즌 탈락" to "트래직넘버 소멸 · ${now.rank}위"
    }
    if (now.magic != null && now.magic <= 0 && (prev.magic == null || prev.magic > 0) && now.rank <= KBO_POSTSEASON_SPOTS) {
        return "${now.slot} 확정" to "매직넘버 소멸 · 롯데 ${now.rank}위"
    }
    if (now.magic != null && prev.magic != null && now.magic < prev.magic) {
        val label = now.magicLabel.ifBlank { "매직넘버" }
        return "$label ${now.magic}" to "${prev.magic} → ${now.magic} (롯데 승+상대 패)"
    }
    if (now.tragic != null && prev.tragic != null && now.tragic < prev.tragic) {
        return "트래직넘버 ${now.tragic}" to "${prev.tragic} → ${now.tragic} (5위 승+롯데 패)"
    }
    if (now.rank != prev.rank) {
        return "롯데 ${now.rank}위" to "${prev.slot} → ${now.slot}"
    }
    return null
}

fun parseRacePulse(raw: String): RacePulse? {
    if (raw.isBlank()) return null
    val p = raw.split('|')
    if (p.size < 4) return null
    val rank = p[0].toIntOrNull() ?: return null
    val slot = p[1]
    val magic = p[2].toIntOrNull()?.takeIf { it >= 0 }
    val tragic = p[3].toIntOrNull()?.takeIf { it >= 0 }
    val label = when (rank) {
        1 -> "한국시리즈 직행 매직넘버"
        2 -> "플레이오프 직행 매직넘버"
        3 -> "준플레이오프 매직넘버"
        in 4..5 -> "와일드카드 매직넘버"
        else -> ""
    }
    return RacePulse(rank, slot, magic, tragic, label)
}

fun lotteRaceSummary(
    standings: List<TeamStanding>,
    remainingOpponents: List<RemainingOpponent> = emptyList(),
    seasonGames: List<MiniGame> = emptyList(),
    todayIso: String = "",
    lotteCode: String = LOTTE_TEAM_CODE,
): RaceSummary? {
    if (standings.size < 2) return null
    val sorted = standings.sortedBy { it.ranking }
    val lotte = sorted.firstOrNull { it.teamId.equals(lotteCode, ignoreCase = true) } ?: return null
    val seasonG = seasonLength(sorted)
    val rem = remainingGames(lotte, seasonG)
    val slot = kboPostseasonSlot(lotte.ranking)
    val today = todayIso.ifBlank { kboToday().toString() }
    val remaining = remainingOpponents.ifEmpty {
        if (seasonGames.isEmpty()) emptyList()
        else remainingOpponentsFrom(seasonGames, today, lotteCode)
    }
    fun at(rank: Int) = sorted.firstOrNull { it.ranking == rank } ?: sorted.getOrNull(rank - 1)

    val headline = buildString {
        append("롯데 ${lotte.ranking}위")
        if (slot.isNotBlank()) append(" · $slot")
        append(" · 잔여 ${rem}경기")
    }
    val lines = mutableListOf<String>()

    lotte.streak.trim().takeIf { it.isNotBlank() }?.let { lines += it }
    when {
        lotte.lastFive.isNotBlank() -> lines += "최근 5경기 ${lotte.lastFive.trim()}"
        else -> lastFiveMarks(seasonGames, today, lotteCode).takeIf { it.isNotBlank() }?.let { lines += it }
    }

    when {
        lotte.ranking <= 0 -> {}
        lotte.ranking > KBO_POSTSEASON_SPOTS -> {
            val fifth = at(KBO_POSTSEASON_SPOTS)
            if (fifth != null) {
                val gap = gamesAhead(lotte, fifth)
                lines += "와일드카드까지 ${formatGamesAbs(gap)}게임차"
                val elim = magicNumber(fifth, lotte, seasonG)
                lines += if (elim <= 0) {
                    "포스트시즌 탈락"
                } else {
                    "와일드카드 트래직넘버 $elim (5위 승+롯데 패)"
                }
            }
        }
        else -> {
            val (label, vsRank) = when (lotte.ranking) {
                1 -> "한국시리즈 직행 매직넘버" to 2
                2 -> "플레이오프 직행 매직넘버" to 3
                3 -> "준플레이오프 매직넘버" to 4
                else -> "와일드카드 매직넘버" to 6
            }
            val vs = at(vsRank)
            if (vs != null) {
                val m = magicNumber(lotte, vs, seasonG)
                lines += if (m <= 0) {
                    "${kboPostseasonSlot(lotte.ranking)} 확정"
                } else {
                    "$label $m (롯데 승+${vsRank}위 패)"
                }
                formatSelfClinchLine(lotte, vs, seasonG).takeIf { it.isNotBlank() }?.let { lines += it }
            }
            val next = when (lotte.ranking) {
                2 -> 1 to "한국시리즈 직행"
                3 -> 2 to "플레이오프 직행"
                4, 5 -> 3 to "준플레이오프"
                else -> null
            }
            if (next != null) {
                val target = at(next.first)
                if (target != null) {
                    val gap = gamesAhead(lotte, target)
                    lines += "${next.second}까지 ${formatGamesAbs(gap)}게임차"
                }
            }
        }
    }

    formatPaceLine(lotte, seasonG).takeIf { it.isNotBlank() }?.let { lines += it }
    formatHomeAwayRemaining(remaining).takeIf { it.isNotBlank() }?.let { lines += it }
    formatVsRaceOpponent(remaining, sorted, lotteCode).takeIf { it.isNotBlank() }?.let { lines += it }
    formatRemainingOpponents(remaining).takeIf { it.isNotBlank() }?.let { lines += it }
    formatRemainingSeries(remainingSeriesFrom(seasonGames, today, lotteCode)).forEach { lines += it }
    val upcoming = upcomingLotteGames(seasonGames, today, lotteCode).take(5).map { formatUpcomingLine(it, lotteCode) }

    return RaceSummary(headline, lines, upcoming)
}
