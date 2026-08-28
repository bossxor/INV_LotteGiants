package com.bossxor.lottegiants.domain

import java.util.Locale
import kotlin.math.abs

/** KBO 정규시즌 경기 수. 실제 소화 경기가 더 많으면 그 값을 쓴다. */
const val KBO_REGULAR_GAMES = 144

/** 정규시즌 포스트시즌 진출 컷 (5위, 와일드카드). */
const val KBO_POSTSEASON_SPOTS = 5

data class RaceSummary(
    val headline: String,
    val lines: List<String>,
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

fun widgetRaceLine(rank: Int, remaining: Int, starter: String): String {
    val parts = buildList {
        if (rank > 0) add("${rank}위")
        if (remaining > 0) add("잔여 ${remaining}")
        else if (rank > 0) add("잔여 0")
        val s = starter.trim()
        if (s.isNotBlank() && s != "-") add("선발 $s")
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
    lotteCode: String = LOTTE_TEAM_CODE,
): RaceSummary? {
    if (standings.size < 2) return null
    val sorted = standings.sortedBy { it.ranking }
    val lotte = sorted.firstOrNull { it.teamId.equals(lotteCode, ignoreCase = true) } ?: return null
    val seasonG = seasonLength(sorted)
    val rem = remainingGames(lotte, seasonG)
    val slot = kboPostseasonSlot(lotte.ranking)
    fun at(rank: Int) = sorted.firstOrNull { it.ranking == rank } ?: sorted.getOrNull(rank - 1)

    val headline = buildString {
        append("롯데 ${lotte.ranking}위")
        if (slot.isNotBlank()) append(" · $slot")
        append(" · 잔여 ${rem}경기")
    }
    val lines = mutableListOf<String>()

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

    formatRemainingOpponents(remainingOpponents).takeIf { it.isNotBlank() }?.let { lines += it }

    return RaceSummary(headline, lines)
}
