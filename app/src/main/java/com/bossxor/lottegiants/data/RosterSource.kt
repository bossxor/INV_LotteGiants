package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.DayEntryChanges
import com.bossxor.lottegiants.domain.EntryPlayer
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.teamKeuboId
import com.bossxor.lottegiants.domain.matchesTeam
import com.bossxor.lottegiants.domain.KBO_ZONE
import com.bossxor.lottegiants.domain.kboToday
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import android.util.Log
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

internal class RosterSource(
    private val api: NaverSportsApi, private val kboApi: KboMobileApi,
    private val keuboApi: KeuboApi, private val store: SnapshotStore,
    private val fetchLeaders: suspend (Boolean) -> List<LeaderPlayer>,
) {
    private val rosterCache = TimedSourceCache<Pair<LocalDate, String>, Pair<List<ParsedRosterPlayer>, List<ParsedRosterPlayer>>>(400)
    private val movesFlight = SingleFlight<String, List<RosterMove>>()
    private val movesCache = ConcurrentHashMap<String, Pair<Long, List<RosterMove>>>()
    suspend fun fetchDayEntryChanges(
        date: LocalDate,
        resolveCodes: Boolean = true,
        teamCode: String = LOTTE_TEAM_CODE,
    ): DayEntryChanges {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        val season = date.year.toString()
        val gDt = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val (registered, removed) = rosterCache.get(date to code, if (date == LocalDate.now(KBO_ZONE)) 0 else 600_000L) {
            KboRosterParser.parseConfirmed(kboApi.getRoster(KboRosterRequest(season_id = season, g_dt = gDt, t_id = code)))
        }
        val codeByName = if (resolveCodes) {
            runCatching {
                val batters = fetchLeaders(false).filter { it.matchesTeam(code) }
                val pitchers = fetchLeaders(true).filter { it.matchesTeam(code) }
                val moves = fetchAllRosterMoves(code)
                    .filter { it.playerCode.isNotBlank() && it.playerName.isNotBlank() }
                    .associate { it.playerName to it.playerCode }
                Triple(
                    batters.associate { it.name to it.playerCode },
                    pitchers.associate { it.name to it.playerCode },
                    moves,
                )
            }.getOrNull()
        } else {
            null
        }
        fun codeFor(name: String, isPitcher: Boolean): String {
            val maps = codeByName ?: return ""
            val (batterMap, pitcherMap, moveMap) = maps
            val primary = if (isPitcher) pitcherMap[name] else batterMap[name]
            return primary?.takeIf { it.isNotBlank() }
                ?: moveMap[name].orEmpty()
                    .ifBlank { (if (isPitcher) batterMap[name] else pitcherMap[name]).orEmpty() }
        }
        fun toPlayers(players: List<ParsedRosterPlayer>) = players.map {
            val pitcher = it.position.contains("투수")
            EntryPlayer(
                name = it.name,
                playerCode = codeFor(it.name, pitcher),
                backNumber = it.backNumber,
                position = it.position,
                hitType = it.batsThrows,
                isPitcher = pitcher,
            )
        }
        val kboReg = toPlayers(registered)
        val kboRem = toPlayers(removed)
        // 날짜별 인원은 공식 공시가 기준이다. 보조 이력은 선수 코드 연결에만 사용한다.
        return DayEntryChanges(date = gDt, registered = kboReg, removed = kboRem)
    }

    /**
     * 팀별 등번호 일람(구단 소속 전체: 1군·퓨처스). 캐시와 같으면 네트워크 결과만 버리고 캐시를 유지한다.
     * KBO 선수조회 HTML은 OkHttp 동기 호출이라 IO에서 실행해야 한다.
     */
    suspend fun fetchTeamJerseyRoster(
        teamCode: String = LOTTE_TEAM_CODE,
        force: Boolean = false,
    ): List<EntryPlayer> = withContext(Dispatchers.IO) {
        val code = teamCode.ifBlank { LOTTE_TEAM_CODE }
        val season = kboToday().year
        val cachedRaw = store.jerseyRoster(code, season)
        // 등번호가 전부 비면 폴백 캐시로 보고 버린다
        val cached = cachedRaw.takeIf { list ->
            list.isNotEmpty() && list.any { it.backNumber.isNotBlank() }
        }.orEmpty()
        if (cachedRaw.isNotEmpty() && cached.isEmpty()) {
            store.setJerseyRoster(code, season, emptyList())
        }
        val fromSearch = runCatching {
            KboPlayerSearchParser.fetchTeamPlayers(code)
        }.onFailure { e ->
            Log.w("GiantsRepo", "jersey roster search failed: ${e.message}")
        }.getOrDefault(emptyList())
        val fromRegister = runCatching {
            val html = KboRegisterAllParser.fetchHtml()
            KboRegisterAllParser.parseTeamPlayers(html, code)
        }.onFailure { e ->
            Log.w("GiantsRepo", "jersey roster registerAll failed: ${e.message}")
        }.getOrDefault(emptyList())
        // 선수조회가 1군·퓨처스 전체라 기준. 등록현황은 선수코드가 없어 합치면 1군이 두 번 들어가므로
        // 등번호가 빈 선수만 채운다(선수조회가 실패했을 때만 등록현황 그대로).
        val remote = KboPlayerSearchParser.mergeWithRegister(fromSearch, fromRegister)
            .sortedWith(
                compareBy(
                    { it.backNumber.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.backNumber },
                    { it.name },
                ),
            )
        if (remote.isEmpty()) {
            if (cached.isNotEmpty()) return@withContext cached
            return@withContext leadersAsJerseyFallback(code)
        }
        val codeByName = runCatching {
            val batters = fetchLeaders(false).filter { it.matchesTeam(code) }
            val pitchers = fetchLeaders(true).filter { it.matchesTeam(code) }
            (batters + pitchers).associate { it.name to it.playerCode }
        }.getOrDefault(emptyMap())
        val enriched = fillBackNumbersFromNaver(
            remote.map { p ->
                val pc = p.playerCode.ifBlank { codeByName[p.name].orEmpty() }
                if (pc == p.playerCode) p else p.copy(playerCode = pc)
            },
            season,
        )
        if (!force && jerseyFingerprint(enriched) == jerseyFingerprint(cached)) {
            return@withContext cached.ifEmpty { enriched }
        }
        store.setJerseyRoster(code, season, enriched)
        enriched
    }

    /**
     * KBO가 번호를 안 주는 선수(육성선수 등)만 네이버 선수 API로 채운다. 네이버 backNo 0 은 "없음"이라 버린다.
     * 호출은 번호가 빈 선수(구단당 4~11명)뿐이고 결과는 로스터 캐시에 들어간다.
     */
    private suspend fun fillBackNumbersFromNaver(
        players: List<EntryPlayer>,
        season: Int,
    ): List<EntryPlayer> = coroutineScope {
        players.map { p ->
            if (p.backNumber.isNotBlank() || p.playerCode.isBlank()) return@map async { p }
            async {
                // 일시 오류로 번호가 빈 채 캐시되지 않도록 한 번 더 시도한다.
                var no = 0
                for (attempt in 1..2) {
                    val got = runCatching { api.getPlayer(season.toString(), p.playerCode).result?.player?.backNo }
                        .getOrNull()
                    if (got != null) { no = got; break }
                }
                if (no > 0) p.copy(backNumber = no.toString()) else p
            }
        }.awaitAll().sortedWith(
            compareBy({ it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.backNumber }, { it.name }),
        )
    }

    private fun jerseyFingerprint(list: List<EntryPlayer>): String =
        list.sortedWith(
            compareBy({ it.backNumber.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }),
        ).joinToString(";") {
            "${it.backNumber}|${it.name}|${it.position}|${it.playerCode}"
        }

    private suspend fun leadersAsJerseyFallback(teamCode: String): List<EntryPlayer> {
        val batters = runCatching { fetchLeaders(false).filter { it.matchesTeam(teamCode) } }
            .getOrDefault(emptyList())
        val pitchers = runCatching { fetchLeaders(true).filter { it.matchesTeam(teamCode) } }
            .getOrDefault(emptyList())
        return (pitchers.map {
            EntryPlayer(
                name = it.name,
                playerCode = it.playerCode,
                position = "투수",
                isPitcher = true,
            )
        } + batters.map {
            EntryPlayer(
                name = it.name,
                playerCode = it.playerCode,
                position = "타자",
                isPitcher = false,
            )
        }).sortedBy { it.name }
    }

    /** 오늘부터 최대 lookback일 전까지 공시가 있는 가장 최근 날짜 */
    suspend fun findLatestEntryDate(lookback: Int = 21, teamCode: String = LOTTE_TEAM_CODE): LocalDate {
        val today = LocalDate.now(KBO_ZONE)
        for (i in 0..lookback) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val d = today.minusDays(i.toLong())
            val changes = runCatching {
                fetchDayEntryChanges(d, resolveCodes = false, teamCode = teamCode)
            }.getOrNull()
            if (changes != null && changes.hasChanges) return d
        }
        return today
    }

    /** 한 달 중 등말소 공시가 있는 날짜 */
    suspend fun fetchEntryChangeDates(
        month: YearMonth,
        teamCode: String = LOTTE_TEAM_CODE,
    ): Set<LocalDate> {
        val hits = mutableSetOf<LocalDate>()
        for (day in 1..month.lengthOfMonth()) {
            val d = month.atDay(day)
            if (d > LocalDate.now(KBO_ZONE)) break
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            runCatching { fetchDayEntryChanges(d, resolveCodes = false, teamCode = teamCode) }
                .onSuccess { if (it.hasChanges) hits.add(d) }
        }
        return hits
    }

    suspend fun fetchAllRosterMoves(teamCode: String = LOTTE_TEAM_CODE): List<RosterMove> =
        movesFlight.run(teamCode) {
            val cached = movesCache[teamCode]
            if (cached != null && System.nanoTime() - cached.first < 600_000_000_000L) cached.second
            else keuboApi.getRosterMoves(teamKeuboId(teamCode)).moves.map { it.toDomain() }.also {
                movesCache[teamCode] = System.nanoTime() to it
            }
        }

    suspend fun fetchRecentRosterMoves(days: Int = 7, teamCode: String = LOTTE_TEAM_CODE): List<RosterMove> {
        val from = kboToday().minusDays((days - 1).toLong()).toString()
        return fetchAllRosterMoves(teamCode).filter { it.moveDate >= from }.sortedByDescending { it.moveDate }
    }

    /**
     * 엔트리 알림용 경량 조회 — KBO 공식 GetRoster(당일)만 본다. Keubo 전체 이력보다 빠르다.
     */
    suspend fun pollRosterMovesForAlert(teamCode: String = ""): List<RosterMove> {
        val today = kboToday()
        val teamCode = teamCode.ifBlank { store.myTeamCode() }
        val dateStr = today.toString()
        val changes = fetchDayEntryChanges(today, resolveCodes = false, teamCode = teamCode)
        fun EntryPlayer.toMove(register: Boolean) = RosterMove(
            playerCode = playerCode,
            playerName = name,
            moveType = if (register) "등록" else "말소",
            moveDate = dateStr,
            isRegister = register,
        )
        return changes.registered.map { it.toMove(true) } + changes.removed.map { it.toMove(false) }
    }

}
