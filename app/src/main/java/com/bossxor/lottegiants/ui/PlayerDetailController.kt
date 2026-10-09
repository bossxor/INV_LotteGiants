package com.bossxor.lottegiants.ui

import com.bossxor.lottegiants.data.GiantsRepository
import com.bossxor.lottegiants.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class PlayerDetailController(private val scope: CoroutineScope, private val repo: GiantsRepository,
    private val gameId: () -> String?) {
    val _playerDetail = MutableStateFlow<PlayerDetail?>(null)
    val _playerLoading = MutableStateFlow(false)
    private val requests = LatestRequest(scope)
    private fun launch(action: suspend LatestRequest.Token.() -> Unit) = requests.launch {
        _playerLoading.value = true
        try { action() } finally { if (isCurrent) _playerLoading.value = false }
    }
    fun loadPlayerByCode(playerCode: String, name: String = "") {
        if (playerCode.isBlank()) return
        launch {
            _playerDetail.value = null
            val slot = LineupSlot(
                batOrder = 0,
                name = name,
                position = "",
                playerCode = playerCode,
            )
            result { repo.fetchPlayerDetail(playerCode, slot, null) }
                .onSuccess { _playerDetail.value = it }
                .onFailure {
                    _playerDetail.value = PlayerDetail(
                        playerCode = playerCode,
                        name = name,
                        photoUrl = playerPhotoUrl(playerCode),
                    )
                }
        }
    }

    fun loadPlayerFromLeader(player: LeaderPlayer) {
        if (player.playerCode.isBlank()) {
            requests.cancel()
            _playerLoading.value = false
            _playerDetail.value = player.toDetailSeed()
            return
        }
        launch {
            val seeded = player.toDetailSeed()
            _playerDetail.value = seeded
            val slot = LineupSlot(
                batOrder = 0,
                name = player.name,
                position = "",
                playerCode = player.playerCode,
            )
            result { repo.fetchPlayerDetail(player.playerCode, slot, null) }
                .onSuccess { fetched ->
                    _playerDetail.value = seeded.copy(
                        backNumber = fetched.backNumber.ifBlank { seeded.backNumber },
                        hitType = fetched.hitType.ifBlank { seeded.hitType },
                        position = fetched.position.ifBlank { seeded.position },
                        birth = fetched.birth.ifBlank { seeded.birth },
                        education = fetched.education.ifEmpty { seeded.education },
                        careers = fetched.careers.ifEmpty { seeded.careers },
                        profileUrl = fetched.profileUrl.ifBlank { seeded.profileUrl },
                        heightCm = fetched.heightCm.ifBlank { seeded.heightCm },
                        weightKg = fetched.weightKg.ifBlank { seeded.weightKg },
                        photoUrl = fetched.photoUrl.ifBlank { seeded.photoUrl },
                        seasonAvg = seeded.seasonAvg.ifBlank { fetched.seasonAvg },
                        seasonGames = if (seeded.seasonGames > 0) seeded.seasonGames else fetched.seasonGames,
                        seasonHits = if (seeded.seasonHits > 0) seeded.seasonHits else fetched.seasonHits,
                        seasonHr = if (seeded.seasonHr > 0) seeded.seasonHr else fetched.seasonHr,
                        seasonRbi = if (seeded.seasonRbi > 0) seeded.seasonRbi else fetched.seasonRbi,
                        seasonObp = seeded.seasonObp.ifBlank { fetched.seasonObp },
                        seasonOps = seeded.seasonOps.ifBlank { fetched.seasonOps },
                        seasonSlg = seeded.seasonSlg.ifBlank { fetched.seasonSlg },
                        seasonSb = if (seeded.seasonSb > 0) seeded.seasonSb else fetched.seasonSb,
                        pitcherEra = seeded.pitcherEra.ifBlank { fetched.pitcherEra },
                        pitcherWins = if (seeded.pitcherWins > 0) seeded.pitcherWins else fetched.pitcherWins,
                        pitcherLosses = if (seeded.pitcherLosses > 0) seeded.pitcherLosses else fetched.pitcherLosses,
                        pitcherSo = if (seeded.pitcherSo > 0) seeded.pitcherSo else fetched.pitcherSo,
                        pitcherInn = seeded.pitcherInn.ifBlank { fetched.pitcherInn },
                        pitcherSaves = if (seeded.pitcherSaves > 0) seeded.pitcherSaves else fetched.pitcherSaves,
                        pitcherHolds = if (seeded.pitcherHolds > 0) seeded.pitcherHolds else fetched.pitcherHolds,
                        pitcherWhip = seeded.pitcherWhip.ifBlank { fetched.pitcherWhip },
                        isPitcher = seeded.isPitcher || fetched.isPitcher,
                    )
                }
        }
    }

    fun loadPitcherDetail(p: PitcherLine) {
        if (p.playerCode.isBlank()) return
        launch {
            _playerDetail.value = PlayerDetail(
                playerCode = p.playerCode,
                name = p.name,
                backNumber = p.backNumber,
                isPitcher = true,
                todayLine = listOfNotNull(
                    p.innings.takeIf { it.isNotBlank() }?.let { "${it}이닝" },
                    "${p.strikeouts}K",
                    "${p.hits}H",
                ).joinToString(" · "),
                photoUrl = playerPhotoUrl(p.playerCode),
            )
            val slot = LineupSlot(
                batOrder = 0,
                name = p.name,
                position = "투수",
                playerCode = p.playerCode,
                backNumber = p.backNumber,
                isPitcher = true,
            )
            result { repo.fetchPlayerDetail(p.playerCode, slot, gameId()) }
                .onSuccess { fetched ->
                    _playerDetail.value = fetched.copy(
                        isPitcher = true,
                        todayLine = _playerDetail.value?.todayLine.orEmpty().ifBlank { fetched.todayLine },
                    )
                }
        }
    }

    fun loadPlayerDetail(slot: LineupSlot, gameId: String?) {
        launch {
            _playerDetail.value = null
            result { repo.fetchPlayerDetail(slot.playerCode, slot, gameId) }
                .onSuccess { _playerDetail.value = it }
                .onFailure {
                    _playerDetail.value = PlayerDetail(
                        playerCode = slot.playerCode,
                        name = slot.name,
                        backNumber = slot.backNumber,
                        hitType = slot.hitType,
                        position = slot.position,
                        isPitcher = slot.isPitcher ||
                            com.bossxor.lottegiants.domain.isPitcherPosition(slot.position),
                        seasonAvg = slot.seasonAvg?.let { a -> String.format("%.3f", a) }.orEmpty(),
                        todayLine = "${slot.todayHits}/${slot.todayAtBats}",
                        photoUrl = if (slot.playerCode.isNotBlank()) playerPhotoUrl(slot.playerCode) else "",
                    )
                }
        }
    }

    fun clearPlayerDetail() {
        requests.cancel()
        _playerLoading.value = false
        _playerDetail.value = null
    }
}

private fun LeaderPlayer.toDetailSeed(): PlayerDetail =
    if (isPitcher) {
        PlayerDetail(
            playerCode = playerCode,
            name = name,
            seasonGames = games,
            pitcherEra = era,
            pitcherWins = wins,
            pitcherLosses = losses,
            pitcherSo = so,
            pitcherInn = ip,
            pitcherSaves = saves,
            pitcherHolds = holds,
            pitcherWhip = whip,
            isPitcher = true,
            photoUrl = if (playerCode.isNotBlank()) playerPhotoUrl(playerCode) else "",
        )
    } else {
        PlayerDetail(
            playerCode = playerCode,
            name = name,
            seasonAvg = avg,
            seasonGames = games,
            seasonHits = hits,
            seasonHr = hr,
            seasonRbi = rbi,
            seasonObp = obp,
            seasonOps = ops,
            seasonSlg = slg,
            seasonSb = sb,
            isPitcher = false,
            photoUrl = if (playerCode.isNotBlank()) playerPhotoUrl(playerCode) else "",
        )
    }
