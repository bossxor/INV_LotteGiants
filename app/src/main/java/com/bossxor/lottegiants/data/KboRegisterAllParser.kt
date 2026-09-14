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
 *
 * 실제 HTML은 팀마다 별도 테이블이며, 선수명은 `<li>이름(등번호)</li>` 형태다.
 */
object KboRegisterAllParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val playerRe = Regex("""([가-힣A-Za-z·\.]+)\((\d+|00)\)""")
    private val rowRe = Regex("""<tr[^>]*>[\s\S]*?</tr>""", RegexOption.IGNORE_CASE)
    private val cellRe = Regex("""<t[dh][^>]*>([\s\S]*?)</t[dh]>""", RegexOption.IGNORE_CASE)

    /** 팀 표시명(표 첫 칸) → KBO 팀 코드. 긴 이름 우선. */
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
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("RegisterAll HTTP ${res.code}")
            val body = res.body ?: return ""
            // charset 헤더가 없어도 페이지는 UTF-8. 잘못 디코딩하면 팀명을 못 찾는다.
            val bytes = body.bytes()
            return String(bytes, Charsets.UTF_8)
        }
    }

    fun parseTeamPlayers(html: String, teamCode: String): List<EntryPlayer> {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }.uppercase()
        val label = teamAliases.firstOrNull { it.second == code }?.first
            ?: teamCodeToName(code).ifBlank { return emptyList() }
        val rowHtml = findTeamRowHtml(html, label) ?: return emptyList()
        return parsePlayersFromRowHtml(rowHtml)
    }

    /** 테스트용: `|` 구분 plain 텍스트 행 */
    fun parsePlayersFromRow(rowPlain: String): List<EntryPlayer> {
        val cells = rowPlain.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (cells.size < 5) return extractByPositionHints(rowPlain)
        return playersFromCells(cells.drop(1)) // 0=구단명
    }

    fun parsePlayersFromRowHtml(rowHtml: String): List<EntryPlayer> {
        val cells = cellRe.findAll(rowHtml).map { stripTags(it.groupValues[1]) }.toList()
        if (cells.size < 4) {
            // li만 있어도 번호는 살린다
            return extractByPositionHints(stripTags(rowHtml))
        }
        // 0=구단, 1=감독, 2=코치, 3=투수, 4=포수, 5=내야수, 6=외야수
        return playersFromCells(cells.drop(1))
    }

    private fun playersFromCells(roleCells: List<String>): List<EntryPlayer> {
        val out = mutableListOf<EntryPlayer>()
        fun addAll(cell: String?, position: String, pitcher: Boolean) {
            if (cell.isNullOrBlank()) return
            for ((name, back) in extractPlayers(cell)) {
                out += EntryPlayer(
                    name = name,
                    backNumber = back,
                    position = position,
                    isPitcher = pitcher || isPitcherPosition(position),
                )
            }
        }
        // roleCells: 감독, 코치, 투수, 포수, 내야, 외야 (감독·코치 스킵)
        addAll(roleCells.getOrNull(2), "투수", true)
        addAll(roleCells.getOrNull(3), "포수", false)
        addAll(roleCells.getOrNull(4), "내야수", false)
        addAll(roleCells.getOrNull(5), "외야수", false)
        if (out.isEmpty()) {
            // 열이 밀렸을 때: 감독·코치 제외하고 번호만 있는 전체에서 70번대 미만
            return extractByPositionHints(roleCells.drop(2).joinToString(" "))
        }
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

    private fun extractByPositionHints(plain: String): List<EntryPlayer> =
        extractPlayers(plain)
            .map { (name, back) ->
                val n = back.toIntOrNull() ?: -1
                EntryPlayer(
                    name = name,
                    backNumber = back,
                    position = if (n >= 70) "코치" else "",
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

    private fun extractPlayers(text: String): List<Pair<String, String>> =
        playerRe.findAll(text).map { m ->
            m.groupValues[1] to m.groupValues[2]
        }.toList()

    /**
     * `롯데<br/>45명` 또는 `롯데 45명` 형태가 들어 있는 선수 등록 행을 찾는다.
     * 1군 등록/말소 표의 단순 `롯데` 셀은 제외.
     */
    fun findTeamRowHtml(html: String, teamLabel: String): String? {
        val labelEsc = Regex.escape(teamLabel)
        // th.fir 안: 롯데 + (br/공백) + 숫자 + 명
        val headerHint = Regex(
            """class=["']fir["'][^>]*>\s*$labelEsc(?:\s|<br\s*/?>|&nbsp;)*\d+\s*명""",
            setOf(RegexOption.IGNORE_CASE),
        )
        val hint = headerHint.find(html) ?: run {
            // strip 후 매칭용 폴백
            return findTeamRowByPlain(html, teamLabel)
        }
        // hint 위치에서 앞으로 가장 가까운 <tr 시작
        val before = html.lastIndexOf("<tr", hint.range.first, ignoreCase = true)
        if (before < 0) return findTeamRowByPlain(html, teamLabel)
        val after = html.indexOf("</tr>", hint.range.last, ignoreCase = true)
        if (after < 0) return findTeamRowByPlain(html, teamLabel)
        return html.substring(before, after + 5)
    }

    private fun findTeamRowByPlain(html: String, teamLabel: String): String? {
        val headRe = Regex("""${Regex.escape(teamLabel)}\s*\d+\s*명""")
        for (row in rowRe.findAll(html)) {
            val plain = stripTags(row.value)
            if (headRe.containsMatchIn(plain) && playerRe.containsMatchIn(plain)) {
                return row.value
            }
        }
        return null
    }

    private fun stripTags(html: String): String =
        html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
