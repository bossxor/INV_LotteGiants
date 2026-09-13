package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.isPitcherPosition
import com.bossxor.lottegiants.domain.teamCodeToName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * KBO 공식 「전체 등록 현황」 HTML에서 팀별 등번호 일람을 파싱한다.
 * https://www.koreabaseball.com/Player/RegisterAll.aspx
 */
object KboRegisterAllParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val playerRe = Regex("""([가-힣A-Za-z·\.]+)\((\d+|00)\)""")

    /** 팀 표시명(표 첫 칸) → KBO 팀 코드 */
    private val teamAliases: List<Pair<String, String>> = listOf(
        "롯데" to "LT",
        "삼성" to "SS",
        "KIA" to "HT",
        "기아" to "HT",
        "LG" to "LG",
        "KT" to "KT",
        "두산" to "OB",
        "한화" to "HH",
        "NC" to "NC",
        "SSG" to "SK",
        "키움" to "WO",
    )

    fun fetchHtml(): String {
        val req = Request.Builder()
            .url("https://www.koreabaseball.com/Player/RegisterAll.aspx")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("RegisterAll HTTP ${res.code}")
            return res.body?.string().orEmpty()
        }
    }

    fun parseTeamPlayers(html: String, teamCode: String): List<EntryPlayer> {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }.uppercase()
        val label = teamAliases.firstOrNull { it.second == code }?.first
            ?: teamCodeToName(code).ifBlank { return emptyList() }
        val rowText = findTeamRowPlain(html, label) ?: return emptyList()
        return parsePlayersFromRow(rowText)
    }

    /** 테스트·오프라인용: 이미 plain 텍스트인 한 줄(구단|감독|코치|투수|…) */
    fun parsePlayersFromRow(rowPlain: String): List<EntryPlayer> {
        val cells = rowPlain.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (cells.size < 5) {
            // HTML에서 태그 제거만 된 경우: 포지션 헤더 없이 연속 텍스트일 수 있음
            return extractByPositionHints(rowPlain)
        }
        // 0=구단, 1=감독, 2=코치, 3=투수, 4=포수, 5=내야수, 6=외야수
        val out = mutableListOf<EntryPlayer>()
        fun addAll(cell: String, position: String, pitcher: Boolean) {
            for ((name, back) in extractPlayers(cell)) {
                out += EntryPlayer(
                    name = name,
                    backNumber = back,
                    position = position,
                    isPitcher = pitcher || isPitcherPosition(position),
                )
            }
        }
        if (cells.size >= 4) addAll(cells[3], "투수", true)
        if (cells.size >= 5) addAll(cells[4], "포수", false)
        if (cells.size >= 6) addAll(cells[5], "내야수", false)
        if (cells.size >= 7) addAll(cells[6], "외야수", false)
        if (out.isEmpty()) return extractByPositionHints(rowPlain)
        return out
            .distinctBy { "${it.name}|${it.backNumber}" }
            .sortedWith(
                compareBy<EntryPlayer>(
                    { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.backNumber },
                    { it.name },
                ),
            )
    }

    private fun extractByPositionHints(plain: String): List<EntryPlayer> {
        // 감독·코치 구간을 건너뛰고 투수~외야만 잡기 어렵다면 전체에서 뽑되 고번호(70+)는 코치로 배제
        return extractPlayers(plain)
            .map { (name, back) ->
                val n = back.toIntOrNull() ?: -1
                val coachLike = n >= 70
                EntryPlayer(
                    name = name,
                    backNumber = back,
                    position = if (coachLike) "코치" else "",
                    isPitcher = false,
                )
            }
            .filter { it.position != "코치" }
            .distinctBy { "${it.name}|${it.backNumber}" }
            .sortedWith(
                compareBy<EntryPlayer>(
                    { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.backNumber },
                    { it.name },
                ),
            )
    }

    private fun extractPlayers(text: String): List<Pair<String, String>> =
        playerRe.findAll(text).map { m ->
            m.groupValues[1] to m.groupValues[2]
        }.toList()

    private fun findTeamRowPlain(html: String, teamLabel: String): String? {
        val rows = Regex(
            """<tr[^>]*>[\s\S]*?</tr>""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
        for (row in rows) {
            val plain = stripTags(row.value)
            if (plain.contains(teamLabel) && Regex("""${Regex.escape(teamLabel)}\s*\d+명""").containsMatchIn(plain)) {
                // 셀 경계를 | 로 재구성
                val tds = Regex(
                    """<t[dh][^>]*>([\s\S]*?)</t[dh]>""",
                    RegexOption.IGNORE_CASE,
                ).findAll(row.value).map { stripTags(it.groupValues[1]) }.toList()
                if (tds.isNotEmpty()) return tds.joinToString("|")
                return plain
            }
        }
        return null
    }

    private fun stripTags(html: String): String =
        html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
