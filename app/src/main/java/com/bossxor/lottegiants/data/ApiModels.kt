package com.bossxor.lottegiants.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val result: ScheduleResult? = null,
)

@Serializable
data class ScheduleResult(
    val games: List<GameDto> = emptyList(),
)

@Serializable
data class GameDto(
    val gameId: String = "",
    val categoryId: String = "",
    val gameDate: String = "",
    val gameDateTime: String = "",
    val stadium: String? = null,
    val homeTeamCode: String = "",
    val homeTeamName: String = "",
    val homeTeamScore: Int = 0,
    val awayTeamCode: String = "",
    val awayTeamName: String = "",
    val awayTeamScore: Int = 0,
    val statusCode: String = "",
    val statusNum: Int = 0,
    val statusInfo: String? = null,
    val cancel: Boolean = false,
    val suspended: Boolean = false,
    val gameOnAir: Boolean = false,
    val homeStarterName: String? = null,
    val awayStarterName: String? = null,
    val winPitcherName: String? = null,
    val losePitcherName: String? = null,
    val homeCurrentPitcherName: String? = null,
    val awayCurrentPitcherName: String? = null,
    val broadChannel: String? = null,
)

@Serializable
data class RelayResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val result: RelayResult? = null,
)

@Serializable
data class RelayResult(
    val textRelayData: TextRelayData? = null,
)

@Serializable
data class TextRelayData(
    val gameId: String = "",
    val no: Int = 0,
    val inn: Int = 0,
    val homeOrAway: String = "",
    val inningScore: InningScoreDto? = null,
    val homeEntry: EntryDto? = null,
    val awayEntry: EntryDto? = null,
    val homeLineup: LineupDto? = null,
    val awayLineup: LineupDto? = null,
    val currentGameState: GameStateDto? = null,
    val textRelays: List<TextRelayDto> = emptyList(),
)

@Serializable
data class InningScoreDto(
    val home: Map<String, String> = emptyMap(),
    val away: Map<String, String> = emptyMap(),
)

@Serializable
data class EntryDto(
    val batter: List<EntryPlayerDto> = emptyList(),
    val pitcher: List<EntryPlayerDto> = emptyList(),
)

@Serializable
data class EntryPlayerDto(
    val name: String = "",
    val pcode: String = "",
    val pos: String? = null,
    val hittype: String? = null,
    val pitchingStyle: String? = null,
)

@Serializable
data class LineupDto(
    val batter: List<LineupBatterDto> = emptyList(),
    val pitcher: List<LineupPitcherDto> = emptyList(),
)

@Serializable
data class LineupBatterDto(
    val name: String = "",
    val pcode: String = "",
    val batOrder: Int = 0,
    val seqno: Int = 0,
    val posName: String? = null,
    val hitType: String? = null,
    val seasonHra: Double? = null,
    val todayHra: Double? = null,
    val hit: Int = 0,
    val ab: Int = 0,
    val pa: Int = 0,
    val rbi: Int = 0,
    val run: Int = 0,
    val backnum: String? = null,
)

@Serializable
data class LineupPitcherDto(
    val name: String = "",
    val pcode: String = "",
    val seqno: Int = 0,
    val backnum: String? = null,
)

@Serializable
data class GameStateDto(
    val homeScore: String = "0",
    val awayScore: String = "0",
    val homeHit: String = "0",
    val awayHit: String = "0",
    val homeError: String = "0",
    val awayError: String = "0",
    val homeBallFour: String = "0",
    val awayBallFour: String = "0",
    val pitcher: String? = null,
    val batter: String? = null,
    val strike: String = "0",
    val ball: String = "0",
    val out: String = "0",
    val base1: String = "0",
    val base2: String = "0",
    val base3: String = "0",
)

@Serializable
data class TextRelayDto(
    val no: Int = 0,
    val inn: Int = 0,
    val homeOrAway: String = "",
    val title: String? = null,
    val textOptions: List<TextOptionDto> = emptyList(),
)

@Serializable
data class TextOptionDto(
    val seqno: Int = 0,
    val text: String = "",
    val type: Int = 0,
    val currentGameState: GameStateDto? = null,
)

@Serializable
data class StandingsResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val result: StandingsResult? = null,
)

@Serializable
data class StandingsResult(
    val seasonTeamStats: List<TeamStatDto> = emptyList(),
)

@Serializable
data class TeamStatDto(
    val teamId: String = "",
    val teamName: String = "",
    val teamShortName: String? = null,
    val ranking: Int = 0,
    val wra: Double = 0.0,
    val gameCount: Int = 0,
    val winGameCount: Int = 0,
    val drawnGameCount: Int = 0,
    val loseGameCount: Int = 0,
    val gameBehind: Double = 0.0,
    val continuousGameResult: String? = null,
    val lastFiveGames: String? = null,
)
