package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.PlayerCareer
import com.bossxor.lottegiants.domain.PlayerDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.Period
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class PlayerBiography(
    val birth: String = "",
    val education: List<String> = emptyList(),
    val careers: List<PlayerCareer> = emptyList(),
    val sourceUrl: String = "",
)

/** 공개 프로필만 읽고, 선수코드와 인물 식별자가 확인된 정보만 붙인다. */
internal object PlayerBiographySource {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, PlayerBiography>>()

    suspend fun enrich(detail: PlayerDetail, api: NaverSportsApi): PlayerDetail = withContext(Dispatchers.IO) {
        if (!detail.playerCode.matches(Regex("[0-9]+"))) return@withContext detail
        val key = detail.playerCode
        val cached = cache[key]?.takeIf {
            System.currentTimeMillis() - it.first < if (it.second.careers.isEmpty()) 300_000L else 86_400_000L
        }?.second
        val bio = cached ?: run {
            val player = runCatching { api.getPlayer(LocalDate.now(KBO_ZONE).year.toString(), key).result?.player }
                .getOrNull()?.takeIf { it.playerId == key }
            val name = player?.playerName?.ifBlank { detail.name } ?: detail.name
            val birth = player?.dateOfBirth?.ifBlank { detail.birth } ?: detail.birth
            val kind = if (detail.isPitcher) "PitcherDetail" else "HitterDetail"
            val kboUrl = "https://www.koreabaseball.com/Record/Player/$kind/Basic.aspx?playerId=$key"
            val official = runCatching { parseKboBiography(get(kboUrl), name) }.getOrNull()
                ?: PlayerBiography()
            val naverUrl = "https://m.search.naver.com/search.naver?query=" +
                java.net.URLEncoder.encode("$name 프로필", "UTF-8")
            val native = if (!player?.osId.isNullOrBlank()) runCatching {
                parseNaverBiography(get(naverUrl), name, official.birth.ifBlank { birth }, player!!.osId)
            }.getOrNull() else null
            PlayerBiography(
                official.birth.ifBlank { birth },
                official.education.ifEmpty { native?.education.orEmpty().ifEmpty { schoolNames(player?.career.orEmpty()) } },
                native?.careers.orEmpty(),
                if (native != null) naverUrl else kboUrl,
            ).also {
                // 빈 실패 결과는 하루 동안 캐시하지 않는다.
                if (it.birth.isNotBlank() || it.education.isNotEmpty() || it.careers.isNotEmpty()) {
                    cache[key] = System.currentTimeMillis() to it
                }
            }
        }
        detail.copy(birth = bio.birth.ifBlank { detail.birth }, education = bio.education,
            careers = bio.careers, profileUrl = bio.sourceUrl)
    }

    private fun get(url: String): String = client.newCall(Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 17) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
        .build()).execute().use { response ->
            check(response.isSuccessful)
            response.body?.string().orEmpty()
        }
}

internal fun biographyText(html: String): String = html
    .replace(Regex("(?s)<script\\b.*?</script>"), " ")
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("\\s+"), " ").trim()

internal fun schoolNames(career: String): List<String> = career.split('-', '→')
    .map { it.trim().removeSurrounding("(", ")") }
    .filter { Regex("""(?:초|중|고|대)(?:등학교|학교)?(?:\(.*\))?$""").containsMatchIn(it) }
    .distinct()

internal fun parseKboBiography(html: String, expectedName: String): PlayerBiography? {
    fun field(suffix: String) = Regex("""(?s)<span[^>]*id="[^"]*playerProfile_${suffix}"[^>]*>(.*?)</span>""")
        .find(html)?.groupValues?.get(1)?.let(::biographyText).orEmpty()
    val name = field("lblName")
    if (expectedName.isBlank() || name != expectedName) return null
    val rawBirth = field("lblBirthday").ifBlank { field("lblBirth") }
    val birth = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""").find(rawBirth)?.let {
        "%s-%02d-%02d".format(java.util.Locale.US, it.groupValues[1],
            it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }.orEmpty()
    return PlayerBiography(birth, schoolNames(field("lblCareer")))
}

internal fun parseNaverBiography(html: String, expectedName: String, birth: String, osId: String): PlayerBiography? {
    val module = Regex("""(?s)<section[^>]*_au_people_content_wrap[^>]*>(.*?)</section>""")
        .find(html)?.value ?: return null
    if (!module.contains("os=$osId&") && !module.contains("os=$osId\"")) return null
    if (!module.contains("alt=\"$expectedName\"") && !module.contains("data-title=\"$expectedName\"")) return null
    val born = Regex("""(?s)<dt[^>]*>.*?출생</dt>\s*<dd[^>]*>(.*?)</dd>""")
        .find(module)?.groupValues?.get(1)?.let(::biographyText).orEmpty()
    val digits = birth.filter(Char::isDigit)
    if (digits.length != 8 || !born.filter(Char::isDigit).startsWith(digits)) return null
    fun section(marker: String): String {
        val start = module.indexOf("cm_content_area _cm_content_area_$marker")
        if (start < 0) return ""
        val end = module.indexOf("</dl>", start)
        return if (end >= start) module.substring(start, end + 5) else ""
    }
    val education = Regex("""(?s)<span class="text">(.*?)</span>""").findAll(section("school"))
        .map { biographyText(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
    val careers = Regex("""(?s)<div class="info_group[^"]*">\s*<dt[^>]*>(.*?)</dt>\s*<dd[^>]*>(.*?)</dd>""")
        .findAll(section("career")).mapNotNull { match ->
            val team = Regex("""(?s)<span class="text">(.*?)</span>""").find(match.groupValues[2])
                ?.groupValues?.get(1)?.let(::biographyText).orEmpty()
            team.takeIf { it.isNotBlank() }?.let { PlayerCareer(biographyText(match.groupValues[1]), it) }
        }.toList()
    return PlayerBiography(birth, education, careers)
}

fun playerBirthDate(raw: String): LocalDate? {
    val match = Regex("""(\d{4})\D*(\d{2})\D*(\d{2})""").find(raw.trim()) ?: return null
    return runCatching { LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(),
        match.groupValues[3].toInt()) }.getOrNull()
}

fun playerAge(raw: String, today: LocalDate = LocalDate.now(KBO_ZONE)): Int? =
    playerBirthDate(raw)?.takeUnless { it.isAfter(today) }?.let { Period.between(it, today).years }
