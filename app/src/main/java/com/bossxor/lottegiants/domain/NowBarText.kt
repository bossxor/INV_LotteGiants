package com.bossxor.lottegiants.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Now Bar(Live Update) 알림 문구. 삼성이 레이아웃을 그리므로 앱은 글자와 이미지 두 장만 낸다.
 * 두 팀은 항상 원정(초 공격)이 왼쪽, 홈(말 공격)이 오른쪽.
 */
data class NowBarContent(
    /** 접힌 제목 */
    val title: String,
    /** 접힌 둘째 줄 */
    val text: String,
    /** 상태바·잠금화면 알약의 짧은 글자 (대략 7자) */
    val chip: String,
    /** 알약의 파란 보조 글자 */
    val chipSub: String,
    /** 펼침: (라벨, 값). 라벨이 빈 줄은 값만. */
    val lines: List<Pair<String, String>>,
    /** 펼침 마지막 이닝 점수표 (고정폭 글꼴로 그린다). 없으면 빈 리스트 */
    val lineScore: List<String>,
)

object NowBarText {

    /** 두 팀 승률로 구한 경기 전 승리 확률(Log5). 자료가 없으면 null. */
    fun pregameFocusProb(focusWra: Double, oppWra: Double): Double? {
        if (focusWra <= 0.0 || oppWra <= 0.0 || focusWra >= 1.0 || oppWra >= 1.0) return null
        val num = focusWra * (1 - oppWra)
        return (num / (num + oppWra * (1 - focusWra))).coerceIn(0.05, 0.95)
    }

    fun dateLabel(gameDate: String): String {
        val d = runCatching { LocalDate.parse(gameDate) }.getOrNull() ?: return gameDate
        val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        return "%d/%02d(%s)".format(d.monthValue, d.dayOfMonth, dow)
    }

    fun kickoff(startTime: String): String =
        Regex("""\d{1,2}:\d{2}""").find(startTime)?.value ?: startTime.trim()

    private fun whenWhere(g: LotteGameInfo): String = buildString {
        append(dateLabel(g.gameDate))
        val t = kickoff(g.startTime)
        if (t.isNotBlank()) append(" $t")
        if (g.stadium.isNotBlank()) append(" · ${g.stadium}")
    }

    private fun dots(n: Int, max: Int): String {
        val k = n.coerceIn(0, max)
        return "●".repeat(k) + "○".repeat(max - k)
    }

    /** 이닝 점수표용 영문 3자 약칭. 한글은 고정폭에서 칸이 어긋나서 쓰지 않는다. */
    fun shortCode(teamName: String): String = when (teamNameToCode(teamName)) {
        "LT" -> "LOT"
        "SS" -> "SAM"
        "HT" -> "KIA"
        "OB" -> "DOO"
        "LG" -> "LG"
        "SK" -> "SSG"
        "HH" -> "HAN"
        "WO" -> "KIW"
        "NC" -> "NC"
        "KT" -> "KT"
        else -> teamName.take(3)
    }.padEnd(3)

    fun lineScore(g: LotteGameInfo, upTo: Int): List<String> {
        val n = maxOf(g.lotteInningScores.size, g.opponentInningScores.size).coerceAtMost(upTo.coerceAtLeast(1))
        if (n == 0) return emptyList()
        fun row(name: String, s: List<String>, total: Int) =
            shortCode(name) + "  " + (0 until n).joinToString(" ") { (s.getOrNull(it) ?: "-").ifBlank { "-" } } + " | $total"
        val me = row(g.focusName(), g.lotteInningScores, g.lotteScore)
        val opp = row(g.opponentName, g.opponentInningScores, g.opponentScore)
        return if (g.isHome) listOf(opp, me) else listOf(me, opp)
    }

    fun basesLabel(g: LotteGameInfo): String {
        val parts = buildList {
            if (g.onBase1) add(if (g.runnerOn1Order > 0) "1루(${g.runnerOn1Order}번)" else "1루")
            if (g.onBase2) add(if (g.runnerOn2Order > 0) "2루(${g.runnerOn2Order}번)" else "2루")
            if (g.onBase3) add(if (g.runnerOn3Order > 0) "3루(${g.runnerOn3Order}번)" else "3루")
        }
        return if (parts.isEmpty()) "주자 없음" else parts.joinToString(" · ")
    }

    /** @param focusProb 경기 전 롯데 승리 확률(0~1). null이면 승률 줄을 뺀다. */
    fun build(game: LotteGameInfo?, next: LotteGameInfo?, focusProb: Double?): NowBarContent {
        if (game == null) {
            return NowBarContent("집관 라이브", "대기 중", "대기", "", emptyList(), emptyList())
        }
        val me = game.focusName()
        val opp = game.opponentName
        val awayName = if (game.isHome) opp else me
        val homeName = if (game.isHome) me else opp
        val awayScore = if (game.isHome) game.opponentScore else game.lotteScore
        val homeScore = if (game.isHome) game.lotteScore else game.opponentScore
        val vs = "$awayName vs $homeName"
        val scoreTitle = "$awayName $awayScore : $homeScore $homeName"
        val chipScore = "$awayScore:$homeScore"
        val dh = dhSuffix(game.doubleHeaderNo)

        if (game.isSuspended) {
            return NowBarContent(
                scoreTitle, game.suspendLabel, chipScore, "중단",
                listOf("" to game.suspendLabel), lineScore(game, 99),
            )
        }
        return when (game.status) {
            GameStatus.BEFORE -> {
                val t = kickoff(game.startTime).ifBlank { "예정" }
                val lines = buildList {
                    val myStarter = game.lotteStartingPitcher.ifBlank { "-" }
                    val oppStarter = game.opponentStartingPitcher.ifBlank { "-" }
                    add("선발" to if (game.isHome) "$awayName $oppStarter · $homeName $myStarter" else "$awayName $myStarter · $homeName $oppStarter")
                    // 순위·일시는 카드 UI(로고 위 순위, 가운데 구장/시각)로 보여서 텍스트 줄에서 뺀다.
                    if (focusProb != null) {
                        val awayP = ((if (game.isHome) 1 - focusProb else focusProb) * 100).toInt().coerceIn(0, 100)
                        val filled = ((awayP + 5) / 10).coerceIn(0, 10)
                        add("승리확률" to "$awayName $awayP% ${"▰".repeat(filled)}${"▱".repeat(10 - filled)} $homeName ${100 - awayP}%")
                    }
                }
                NowBarContent(vs, whenWhere(game) + dh, t, "경기 전", lines, emptyList())
            }
            GameStatus.LIVE -> {
                val half = game.inningLabel + dh
                val atk = if (game.isLotteBatting) me else opp
                val pit = buildString {
                    append("투 ").append(game.currentPitcherName.ifBlank { "-" })
                    if (game.currentPitcherPitchCount > 0) append("(${game.currentPitcherPitchCount}구)")
                }
                val bat = buildString {
                    append("타 ")
                    if (game.currentBatterOrder > 0) append("${game.currentBatterOrder}번 ")
                    append(game.currentBatterName.ifBlank { "-" })
                }
                val lines = listOf(
                    "" to "$half · $atk 공격",
                    "아웃" to "${dots(game.out, 2)}   B ${dots(game.ball, 3)}   S ${dots(game.strike, 2)}",
                    "주자" to basesLabel(game),
                    "투수" to game.currentPitcherName.ifBlank { "-" } +
                        if (game.currentPitcherPitchCount > 0) " ${game.currentPitcherPitchCount}구" else "",
                    "타자" to (if (game.currentBatterOrder > 0) "${game.currentBatterOrder}번 " else "") +
                        game.currentBatterName.ifBlank { "-" },
                )
                NowBarContent(
                    scoreTitle, "$half · $atk 공격", chipScore,
                    game.inningLabel, lines, lineScore(game, game.inning.coerceAtLeast(1)),
                )
            }
            GameStatus.ENDED -> {
                val result = when {
                    game.lotteScore > game.opponentScore -> "$me 승리"
                    game.lotteScore < game.opponentScore -> "$me 패배"
                    else -> "무승부"
                }
                // 다음 경기 텍스트는 카드에 넣지 않는다(승패세·스코어보드만).
                val lines = buildList {
                    add("" to result)
                    val pitchers = buildList {
                        if (game.winPitcherName.isNotBlank()) add("승 ${game.winPitcherName}")
                        if (game.losePitcherName.isNotBlank()) add("패 ${game.losePitcherName}")
                        if (game.savePitcherName.isNotBlank()) add("세 ${game.savePitcherName}")
                    }
                    if (pitchers.isNotEmpty()) add("투수" to pitchers.joinToString(" · "))
                }
                NowBarContent(
                    scoreTitle,
                    "경기 종료",
                    chipScore, "종료", lines, lineScore(game, 99),
                )
            }
            GameStatus.CANCELED -> NowBarContent(
                vs, game.cancelLabel.ifBlank { "경기 취소" }, "취소", "", listOf("" to whenWhere(game)), emptyList(),
            )
        }
    }
}
