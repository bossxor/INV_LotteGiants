package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LineupSlot
import com.bossxor.lottegiants.domain.PlayerDetail
import com.bossxor.lottegiants.domain.isPitcherPosition
import com.bossxor.lottegiants.domain.playerPhotoUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class PlayerSource(
    private val api: NaverSportsApi, private val keuboApi: KeuboApi,
    private val relays: RelaySource, private val previews: PreviewSource, private val store: SnapshotStore,
    private val fetchLeaders: suspend (Boolean) -> List<LeaderPlayer>,
) {
    private val playerFlight = SingleFlight<Triple<String, LineupSlot?, String?>, PlayerDetail>()
    suspend fun fetchPlayerDetail(
        playerCode: String,
        fallback: LineupSlot? = null,
        gameIdHint: String? = null,
    ): PlayerDetail = playerFlight.run(Triple(playerCode, fallback, gameIdHint)) {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val resolvedCode = playerCode.ifBlank {
            val name = fallback?.name.orEmpty()
            if (name.isBlank()) ""
            else runCatching {
                val pitcherFirst = fallback?.isPitcher == true ||
                    isPitcherPosition(fallback?.position.orEmpty())
                val ordered = if (pitcherFirst) {
                    fetchLeaders(true) + fetchLeaders(false)
                } else {
                    fetchLeaders(false) + fetchLeaders(true)
                }
                pickLeaderByName(name, ordered)?.playerCode.orEmpty()
            }.getOrDefault("")
        }
        val hintId = gameIdHint?.takeIf { it.isNotBlank() }
            ?: runCatching { api.getGames(
                fromDate = today.minusDays(14).format(fmt),
                toDate = today.plusDays(3).format(fmt),
            ).result?.games.orEmpty() }.getOrDefault(emptyList())
                .filter { it.categoryId == "kbo" && it.involvesTeam(store.myTeamCode()) }
                .maxByOrNull { it.gameDateTime }
                ?.gameId

        var detail = basePlayerFromLineup(fallback, resolvedCode)

        if (!hintId.isNullOrBlank()) {
            val preview = runCatching { previews.get(hintId) }.getOrNull()
            val blocks = listOfNotNull(
                preview?.homeStarter,
                preview?.awayStarter,
                preview?.homeTopPlayer,
                preview?.awayTopPlayer,
            )
            val match = blocks.firstOrNull {
                resolvedCode.isNotBlank() && (
                    it.playerCode == resolvedCode ||
                        it.playerInfo?.pCode == resolvedCode ||
                        it.currentSeasonStats?.playerCode == resolvedCode
                    )
            } ?: blocks.firstOrNull {
                resolvedCode.isBlank() &&
                    fallback?.name?.isNotBlank() == true &&
                    it.playerInfo?.name == fallback.name
            }
            if (match != null) {
                detail = mergePreviewPlayer(detail, match)
            }

            val relay = runCatching { relays.current(hintId) }.getOrNull()
            val entryPlayer = listOfNotNull(relay?.homeEntry, relay?.awayEntry)
                .flatMap { it.batter + it.pitcher }
                .firstOrNull { resolvedCode.isNotBlank() && it.pcode == resolvedCode }
            if (entryPlayer != null) {
                detail = detail.copy(
                    name = detail.name.ifBlank { entryPlayer.name },
                    playerCode = detail.playerCode.ifBlank { entryPlayer.pcode },
                    hitType = detail.hitType.ifBlank {
                        entryPlayer.hittype ?: entryPlayer.pitchingStyle.orEmpty()
                    },
                    position = detail.position.ifBlank { entryPlayer.pos.orEmpty() },
                    isPitcher = detail.isPitcher || entryPlayer.pos == "1" ||
                        (entryPlayer.pitchingStyle?.isNotBlank() == true && entryPlayer.hittype.isNullOrBlank()),
                )
            }
        }

        detail.copy(
            photoUrl = if (detail.playerCode.isNotBlank()) playerPhotoUrl(detail.playerCode) else "",
        ).let { withKeuboSeasonStats(it) }.let { PlayerBiographySource.enrich(it, api) }
    }

    /** 프리뷰에 없는 선수라도 루타(Keubo) 시즌 스탯으로 보강. 투수/타자 힌트를 존중한다. */
    private suspend fun withKeuboSeasonStats(detail: PlayerDetail): PlayerDetail {
        val season = LocalDate.now().let { if (it.monthValue < 3) it.year - 1 else it.year }
        val code = detail.playerCode
        val name = detail.name
        val pitcherHint = detail.isPitcher || isPitcherPosition(detail.position)

        fun matchByCode(s: KeuboStatDto): Boolean =
            code.isNotBlank() && (s.kboId == code || s.playerId == code)

        fun matchByNamePreferLotte(stats: List<KeuboStatDto>): KeuboStatDto? {
            val hits = stats.filter { name.isNotBlank() && it.name == name }
            return hits.firstOrNull { it.team.contains("롯데") || it.team.equals("LT", true) }
                ?: hits.firstOrNull()
        }

        suspend fun findPitcher(): KeuboStatDto? = runCatching {
            val stats = keuboApi.getStats("pitcher", season).stats
            stats.firstOrNull(::matchByCode) ?: matchByNamePreferLotte(stats)
        }.getOrNull()

        suspend fun findBatter(): KeuboStatDto? = runCatching {
            val stats = keuboApi.getStats("batter", season).stats
            stats.firstOrNull(::matchByCode) ?: matchByNamePreferLotte(stats)
        }.getOrNull()

        if (pitcherHint) {
            val pitcher = findPitcher() ?: return detail
            val seeded = pitcher.toLeader(true)
            return detail.copy(
                name = detail.name.ifBlank { seeded.name },
                seasonGames = if (detail.seasonGames > 0) detail.seasonGames else seeded.games,
                pitcherEra = detail.pitcherEra.ifBlank { seeded.era },
                pitcherWins = if (detail.pitcherWins > 0) detail.pitcherWins else seeded.wins,
                pitcherLosses = if (detail.pitcherLosses > 0) detail.pitcherLosses else seeded.losses,
                pitcherSo = if (detail.pitcherSo > 0) detail.pitcherSo else seeded.so,
                pitcherInn = detail.pitcherInn.ifBlank { seeded.ip },
                pitcherSaves = if (detail.pitcherSaves > 0) detail.pitcherSaves else seeded.saves,
                pitcherHolds = if (detail.pitcherHolds > 0) detail.pitcherHolds else seeded.holds,
                pitcherWhip = detail.pitcherWhip.ifBlank { seeded.whip },
                isPitcher = true,
            )
        }

        val batter = findBatter()
        if (batter != null) {
            val seeded = batter.toLeader(false)
            return detail.copy(
                name = detail.name.ifBlank { seeded.name },
                seasonAvg = detail.seasonAvg.ifBlank { seeded.avg },
                seasonGames = if (detail.seasonGames > 0) detail.seasonGames else seeded.games,
                seasonHits = if (detail.seasonHits > 0) detail.seasonHits else seeded.hits,
                seasonHr = if (detail.seasonHr > 0) detail.seasonHr else seeded.hr,
                seasonRbi = if (detail.seasonRbi > 0) detail.seasonRbi else seeded.rbi,
                seasonObp = detail.seasonObp.ifBlank { seeded.obp },
                seasonOps = detail.seasonOps.ifBlank { seeded.ops },
                seasonSlg = detail.seasonSlg.ifBlank { seeded.slg },
                seasonSb = if (detail.seasonSb > 0) detail.seasonSb else seeded.sb,
                isPitcher = false,
            )
        }

        val pitcher = findPitcher() ?: return detail
        val seeded = pitcher.toLeader(true)
        return detail.copy(
            name = detail.name.ifBlank { seeded.name },
            seasonGames = if (detail.seasonGames > 0) detail.seasonGames else seeded.games,
            pitcherEra = detail.pitcherEra.ifBlank { seeded.era },
            pitcherWins = if (detail.pitcherWins > 0) detail.pitcherWins else seeded.wins,
            pitcherLosses = if (detail.pitcherLosses > 0) detail.pitcherLosses else seeded.losses,
            pitcherSo = if (detail.pitcherSo > 0) detail.pitcherSo else seeded.so,
            pitcherInn = detail.pitcherInn.ifBlank { seeded.ip },
            pitcherSaves = if (detail.pitcherSaves > 0) detail.pitcherSaves else seeded.saves,
            pitcherHolds = if (detail.pitcherHolds > 0) detail.pitcherHolds else seeded.holds,
            pitcherWhip = detail.pitcherWhip.ifBlank { seeded.whip },
            isPitcher = true,
        )
    }

    private fun pickLeaderByName(name: String, leaders: List<LeaderPlayer>): LeaderPlayer? {
        val hits = leaders.filter { it.name == name }
        return hits.firstOrNull { it.isLotte }
    }

    private fun basePlayerFromLineup(slot: LineupSlot?, code: String): PlayerDetail {
        val c = code.ifBlank { slot?.playerCode.orEmpty() }
        val pitcher = slot?.isPitcher == true || isPitcherPosition(slot?.position.orEmpty())
        return PlayerDetail(
            playerCode = c,
            name = slot?.name.orEmpty(),
            backNumber = slot?.backNumber.orEmpty(),
            hitType = slot?.hitType.orEmpty(),
            position = slot?.position.orEmpty(),
            seasonAvg = slot?.seasonAvg?.let { String.format("%.3f", it) }.orEmpty(),
            todayLine = if (slot != null) "${slot.todayHits}/${slot.todayAtBats}" else "",
            photoUrl = if (c.isNotBlank()) playerPhotoUrl(c) else "",
            isPitcher = pitcher,
        )
    }

    private fun mergePreviewPlayer(base: PlayerDetail, block: PreviewPlayerBlock): PlayerDetail {
        val info = block.playerInfo
        val stats = block.currentSeasonStats
        val isPitcher = base.isPitcher ||
            isPitcherPosition(base.position) ||
            stats?.era != null || stats?.inn != null
        return base.copy(
            playerCode = info?.pCode ?: block.playerCode ?: base.playerCode,
            name = info?.name?.takeIf { it.isNotBlank() } ?: base.name,
            backNumber = info?.backnum?.takeIf { it.isNotBlank() } ?: base.backNumber,
            hitType = info?.hitType?.takeIf { it.isNotBlank() } ?: base.hitType,
            birth = info?.birth.orEmpty(),
            heightCm = info?.height.orEmpty(),
            weightKg = info?.weight.orEmpty(),
            seasonAvg = stats?.hra?.takeIf { it.isNotBlank() } ?: base.seasonAvg,
            seasonGames = stats?.gameCount ?: base.seasonGames,
            seasonHits = stats?.hit ?: base.seasonHits,
            seasonAb = stats?.ab ?: base.seasonAb,
            seasonHr = stats?.hr ?: base.seasonHr,
            seasonRbi = stats?.rbi ?: base.seasonRbi,
            seasonObp = stats?.obp?.let { String.format("%.3f", it) }.orEmpty(),
            pitcherEra = stats?.era.orEmpty(),
            pitcherWins = stats?.w ?: 0,
            pitcherLosses = stats?.l ?: 0,
            pitcherSo = stats?.kk ?: 0,
            pitcherInn = stats?.inn.orEmpty(),
            isPitcher = isPitcher || base.isPitcher,
            hotCold = block.hotColdZone.map { it.toDomain() }.ifEmpty { base.hotCold },
        )
    }

}
