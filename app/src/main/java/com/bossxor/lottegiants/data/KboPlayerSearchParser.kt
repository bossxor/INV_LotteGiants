package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.isPitcherPosition
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * KBO 「선수 조회」에서 구단 전체 선수(1군·퓨처스 포함) 등번호 일람을 가져온다.
 * https://www.koreabaseball.com/Player/Search.aspx
 *
 * ASP.NET WebForms POST + 페이지네이션(ucPager).
 */
object KboPlayerSearchParser {

    private const val URL = "https://www.koreabaseball.com/Player/Search.aspx"
    private const val TEAM_FIELD =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$ddlTeam"
    private const val POS_FIELD =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$ddlPosition"
    private const val NAME_FIELD =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$txtSearchPlayerName"
    private const val PAGE_FIELD =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$hfPage"
    private const val TEAM_EVENT =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$ddlTeam"
    private const val PAGER_PREFIX =
        "ctl00\$ctl00\$ctl00\$cphContents\$cphContents\$cphContents\$ucPager\$"

    private val cookieStore = mutableMapOf<String, List<okhttp3.Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
                cookieStore[url.host].orEmpty()
        })
        .build()

    private val hiddenRe = Regex("""id="([^"]+)"\s+value="([^"]*)"""")
    private val rowRe = Regex(
        """(?is)<tr>\s*<td[^>]*>\s*([^<]*?)\s*</td>\s*<td[^>]*>\s*<a[^>]+playerId=(\d+)[^>]*>([^<]+)</a>\s*</td>\s*<td[^>]*>\s*([^<]*?)\s*</td>\s*<td[^>]*>\s*([^<]*?)\s*</td>""",
    )
    /** KBO 페이저는 btnNext가 없고 btnNo1, btnNo2… 숫자 버튼이다. ($는 정규식에서 이스케이프) */
    private val pageBtnRe = Regex(
        """ucPager\${'$'}btnNo(\d+)""",
    )
    private val pageBtnOnRe = Regex(
        """id="[^"]*ucPager_btnNo(\d+)"[^>]*class="[^"]*\bon\b""",
    )

    fun fetchTeamPlayers(teamCode: String, maxPages: Int = 40): List<EntryPlayer> {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }.uppercase()
        var html = get(URL)
        val out = linkedMapOf<String, EntryPlayer>() // playerCode → player
        val visitedPages = linkedSetOf<Int>()
        var eventTarget = TEAM_EVENT
        var triedLast = false
        for (pageIdx in 0 until maxPages) {
            val fields = hiddenFields(html)
            html = post(
                eventTarget = eventTarget,
                teamCode = code,
                viewState = fields["__VIEWSTATE"].orEmpty(),
                viewStateGen = fields["__VIEWSTATEGENERATOR"].orEmpty(),
                eventValidation = fields["__EVENTVALIDATION"].orEmpty(),
                hfPage = fields["cphContents_cphContents_cphContents_hfPage"]
                    ?: fields["__hfPage"].orEmpty(),
            )
            val pagePlayers = parsePlayers(html)
            if (pagePlayers.isEmpty()) break
            var added = 0
            for (p in pagePlayers) {
                val key = p.playerCode.ifBlank { "${p.name}|${p.backNumber}" }
                if (out.putIfAbsent(key, p) == null) added++
            }
            val current = currentPage(html)
            if (current != null) visitedPages.add(current)
            if (pageIdx > 0 && added == 0) break
            val nextPage = nextPageNumber(html, visitedPages)
            if (nextPage != null) {
                eventTarget = "${PAGER_PREFIX}btnNo$nextPage"
                continue
            }
            // 1~5만 보이는 페이저: 6페이지 이상은 btnLast로 이동 (hfPage 기준)
            if (!triedLast && html.contains("ucPager\$btnLast")) {
                triedLast = true
                eventTarget = "${PAGER_PREFIX}btnLast"
                continue
            }
            break
        }
        return out.values
            .sortedWith(
                compareBy(
                    { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.backNumber },
                    { it.name },
                ),
            )
    }

    /** 화면에 보이는 btnNo 중 아직 안 본 가장 작은 페이지. */
    internal fun nextPageNumber(html: String, visited: Set<Int>): Int? {
        val available = pageBtnRe.findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.toSortedSet()
        if (available.isEmpty()) return null
        return available.firstOrNull { it !in visited }
    }

    /** hfPage가 진실. btnNo class=on 은 6페이지 이상에서 어긋날 수 있다. */
    internal fun currentPage(html: String): Int? {
        hiddenFields(html)["cphContents_cphContents_cphContents_hfPage"]?.toIntOrNull()?.let { return it }
        return pageBtnOnRe.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun parsePlayers(html: String): List<EntryPlayer> =
        rowRe.findAll(html).mapNotNull { m ->
            val rawBack = m.groupValues[1].trim()
            val back = when {
                rawBack.isBlank() || rawBack == "-" || rawBack == "." -> ""
                rawBack.all { it.isDigit() } -> rawBack.trimStart('0').ifBlank { "0" }
                else -> rawBack
            }
            val code = m.groupValues[2].trim()
            val name = m.groupValues[3].trim()
            val position = m.groupValues[5].trim()
            if (name.isBlank()) return@mapNotNull null
            EntryPlayer(
                name = name,
                playerCode = code,
                backNumber = back,
                position = position,
                isPitcher = isPitcherPosition(position) || position.contains("투수"),
            )
        }.toList()

    private fun hiddenFields(html: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (m in hiddenRe.findAll(html)) {
            val id = m.groupValues[1]
            val value = m.groupValues[2]
            when (id) {
                "__VIEWSTATE", "__VIEWSTATEGENERATOR", "__EVENTVALIDATION",
                "cphContents_cphContents_cphContents_hfPage",
                -> map[id] = value
            }
        }
        // also name= form for viewstate
        Regex("""name="(__VIEWSTATE|__VIEWSTATEGENERATOR|__EVENTVALIDATION)"\s+id="[^"]*"\s+value="([^"]*)"""")
            .findAll(html).forEach { map[it.groupValues[1]] = it.groupValues[2] }
        Regex("""name="(__VIEWSTATE|__VIEWSTATEGENERATOR|__EVENTVALIDATION)"\s+value="([^"]*)"""")
            .findAll(html).forEach { map.putIfAbsent(it.groupValues[1], it.groupValues[2]) }
        return map
    }

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header(UA_HEADER, UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("PlayerSearch GET HTTP ${res.code}")
            return res.body?.bytes()?.toString(Charsets.UTF_8).orEmpty()
        }
    }

    private fun post(
        eventTarget: String,
        teamCode: String,
        viewState: String,
        viewStateGen: String,
        eventValidation: String,
        hfPage: String,
    ): String {
        val body = FormBody.Builder()
            .add("__EVENTTARGET", eventTarget)
            .add("__EVENTARGUMENT", "")
            .add("__LASTFOCUS", "")
            .add("__VIEWSTATE", viewState)
            .add("__VIEWSTATEGENERATOR", viewStateGen)
            .add("__EVENTVALIDATION", eventValidation)
            .add(TEAM_FIELD, teamCode)
            .add(POS_FIELD, "")
            .add(NAME_FIELD, "")
            .add(PAGE_FIELD, hfPage)
            .build()
        val req = Request.Builder()
            .url(URL)
            .header(UA_HEADER, UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Origin", "https://www.koreabaseball.com")
            .header("Referer", URL)
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("PlayerSearch POST HTTP ${res.code}")
            return res.body?.bytes()?.toString(Charsets.UTF_8).orEmpty()
        }
    }

    private const val UA_HEADER = "User-Agent"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
