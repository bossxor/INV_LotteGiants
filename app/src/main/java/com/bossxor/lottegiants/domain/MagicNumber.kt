package com.bossxor.lottegiants.domain

import java.util.Locale
import kotlin.math.abs

/** KBO 정규시즌 경기 수. 실제 소화 경기가 더 많으면 그 값을 쓴다. */
const val KBO_REGULAR_GAMES = 144

/** 정규시즌 포스트시즌 진출 컷 (5위). */
const val KBO_POSTSEASON_SPOTS = 5

data class RaceSummary(
    val headline: String,
    val lines: List<String>,
)

fun seasonLength(standings: List<TeamStanding>): Int =
    maxOf(KBO_REGULAR_GAMES, standings.maxOfOrNull { it.gameCount } ?: 0)

fun remainingGames(team: TeamStanding, seasonG: Int): Int =
    (seasonG - team.gameCount).coerceAtLeast(0)

/**
 * A가 B를 상대로 순위를 확정하는 매직넘버.
 * `G + 1 - A승 - B패`. 0 이하면 이미 확정.
 * 추격 팀 기준으로 같은 식을 쓰면 트래직넘버(탈락 확정까지)가 된다.
 */
fun magicNumber(leader: TeamStanding, trailing: TeamStanding, seasonG: Int): Int =
    seasonG + 1 - leader.win - trailing.lose

/** [team] 기준 [base]와의 게임차. 음수면 뒤짐, 양수면 앞섬. */
fun gamesAhead(team: TeamStanding, base: TeamStanding): Double =
    ((team.win - team.lose) - (base.win - base.lose)) / 2.0

fun formatGamesAbs(gb: Double): String =
    if (abs(gb) % 1.0 < 0.05) abs(gb).toInt().toString()
    else String.format(Locale.US, "%.1f", abs(gb))

fun lotteRaceSummary(
    standings: List<TeamStanding>,
    lotteCode: String = LOTTE_TEAM_CODE,
): RaceSummary? {
    if (standings.size < 2) return null
    val sorted = standings.sortedBy { it.ranking }
    val lotte = sorted.firstOrNull { it.teamId.equals(lotteCode, ignoreCase = true) } ?: return null
    val seasonG = seasonLength(sorted)
    val rem = remainingGames(lotte, seasonG)
    val first = sorted.firstOrNull { it.ranking == 1 } ?: sorted.first()
    val second = sorted.firstOrNull { it.ranking == 2 } ?: sorted.getOrNull(1)
    val fifth = sorted.firstOrNull { it.ranking == KBO_POSTSEASON_SPOTS }
        ?: sorted.getOrNull(KBO_POSTSEASON_SPOTS - 1)
    val sixth = sorted.firstOrNull { it.ranking == KBO_POSTSEASON_SPOTS + 1 }
        ?: sorted.getOrNull(KBO_POSTSEASON_SPOTS)

    val headline = "롯데 ${lotte.ranking}위 · 잔여 ${rem}경기"
    val lines = mutableListOf<String>()

    if (lotte.ranking <= KBO_POSTSEASON_SPOTS) {
        if (sixth != null) {
            val m = magicNumber(lotte, sixth, seasonG)
            lines += if (m <= 0) "5위 이상 확정" else "5위 확정 매직 $m"
        }
    } else if (fifth != null) {
        val gap = gamesAhead(lotte, fifth)
        lines += "5위까지 ${formatGamesAbs(gap)}게임차"
        val elim = magicNumber(fifth, lotte, seasonG)
        lines += if (elim <= 0) {
            "포스트시즌 탈락"
        } else {
            "5위 트래직넘버 $elim (5위 승+롯데 패)"
        }
    }

    if (lotte.ranking == 1) {
        if (second != null) {
            val m = magicNumber(lotte, second, seasonG)
            lines += if (m <= 0) "1위 확정" else "1위 확정 매직 $m"
        }
    } else {
        val gap = gamesAhead(lotte, first)
        lines += "1위와 ${formatGamesAbs(gap)}게임차"
    }

    return RaceSummary(headline, lines)
}
