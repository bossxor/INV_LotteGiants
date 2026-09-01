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
    val specialMatchInfo: String? = null,
    val gameOnAir: Boolean = false,
    val homeStarterName: String? = null,
    val awayStarterName: String? = null,
    val winPitcherName: String? = null,
    val losePitcherName: String? = null,
    val homeCurrentPitcherName: String? = null,
    val awayCurrentPitcherName: String? = null,
    val broadChannel: String? = null,
    val homeTeamEmblemUrl: String? = null,
    val awayTeamEmblemUrl: String? = null,
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
    val lastValidMetricOption: MetricOptionDto? = null,
)

@Serializable
data class MetricOptionDto(
    val homeTeamWinRate: Double? = null,
    val awayTeamWinRate: Double? = null,
    val wpaByPlate: Double? = null,
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
    val vsHra: Double? = null,
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
    val inn: String? = null,
    val hit: Int? = null,
    val run: Int? = null,
    val er: Int? = null,
    val kk: Int? = null,
    val so: Int? = null,
    val bb: Int? = null,
    val bf: Int? = null,
    val hr: Int? = null,
    val pitchCount: Int? = null,
    val pitchcnt: Int? = null,
    val ballCount: Int? = null,
    val seasonEra: String? = null,
    val todayEra: Double? = null,
    val vsEra: String? = null,
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
    val base1: String? = null,
    val base2: String? = null,
    val base3: String? = null,
)

@Serializable
data class TextRelayDto(
    val no: Int = 0,
    val inn: Int = 0,
    val homeOrAway: String = "",
    val title: String? = null,
    val textOptions: List<TextOptionDto> = emptyList(),
    val ptsOptions: List<PtsOptionDto> = emptyList(),
    val metricOption: MetricOptionDto? = null,
)

@Serializable
data class TextOptionDto(
    val seqno: Int = 0,
    val text: String = "",
    val type: Int = 0,
    val stuff: String? = null,
    val currentGameState: GameStateDto? = null,
)

@Serializable
data class PtsOptionDto(
    val pitchId: String? = null,
    val inn: Int = 0,
    val ballcount: Int = 0,
    val crossPlateX: Double? = null,
    val crossPlateY: Double? = null,
    val topSz: Double? = null,
    val bottomSz: Double? = null,
    val vy0: Double? = null,
    val vz0: Double? = null,
    val vx0: Double? = null,
    val z0: Double? = null,
    val y0: Double? = null,
    val x0: Double? = null,
    val ax: Double? = null,
    val ay: Double? = null,
    val az: Double? = null,
    val stance: String? = null,
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

@Serializable
data class PreviewResponse(
    val code: Int = 0,
    val success: Boolean = false,
    val result: PreviewResult? = null,
)

@Serializable
data class PreviewResult(
    val previewData: PreviewData? = null,
)

@Serializable
data class PreviewData(
    val homeStarter: PreviewPlayerBlock? = null,
    val awayStarter: PreviewPlayerBlock? = null,
    val homeTopPlayer: PreviewPlayerBlock? = null,
    val awayTopPlayer: PreviewPlayerBlock? = null,
    val gameInfo: PreviewGameInfo? = null,
    val homeTeamLineUp: PreviewTeamLineUp? = null,
    val awayTeamLineUp: PreviewTeamLineUp? = null,
    val homeTeamPreviousGames: List<PreviewPreviousGame> = emptyList(),
    val awayTeamPreviousGames: List<PreviewPreviousGame> = emptyList(),
)

@Serializable
data class PreviewGameInfo(
    val stadium: String? = null,
    val gtime: String? = null,
)

@Serializable
data class PreviewPlayerBlock(
    val playerCode: String? = null,
    val playerInfo: PreviewPlayerInfo? = null,
    val currentSeasonStats: PreviewSeasonStats? = null,
    val recentFiveGamesStats: PreviewSeasonStats? = null,
    val currentSeasonStatsOnOpponents: PreviewSeasonStats? = null,
    val hotColdZone: List<HotColdZoneDto> = emptyList(),
)

@Serializable
data class HotColdZoneDto(
    val zone: Int = 0,
    val hra: String? = null,
    val hraStep: String? = null,
    val kk: Double = 0.0,
)

@Serializable
data class PreviewTeamLineUp(
    val fullLineUp: List<PreviewLineupPlayer> = emptyList(),
)

@Serializable
data class PreviewLineupPlayer(
    val playerName: String? = null,
    val playerCode: String? = null,
    val position: String? = null,
    val positionName: String? = null,
    val backnum: String? = null,
    val hitType: String? = null,
    val batsThrows: String? = null,
)

@Serializable
data class PreviewPreviousGame(
    val gameId: String? = null,
    val result: String? = null,
    val hScore: Int = 0,
    val aScore: Int = 0,
    val aName: String? = null,
    val hName: String? = null,
    val aCode: String? = null,
    val hCode: String? = null,
    val gdate: Int = 0,
)

@Serializable
data class PreviewPlayerInfo(
    val backnum: String? = null,
    val hitType: String? = null,
    val pCode: String? = null,
    val name: String? = null,
    val birth: String? = null,
    val weight: String? = null,
    val height: String? = null,
)

@Serializable
data class PreviewSeasonStats(
    val ab: Int? = null,
    val gameCount: Int? = null,
    val hit: Int? = null,
    val hra: String? = null,
    val rbi: Int? = null,
    val hr: Int? = null,
    val obp: Double? = null,
    val era: String? = null,
    val w: Int? = null,
    val l: Int? = null,
    val kk: Int? = null,
    val inn: String? = null,
    val whip: String? = null,
    val playerName: String? = null,
    val playerCode: String? = null,
)

@Serializable
data class OpenMeteoResponse(
    val current: OpenMeteoCurrent? = null,
)

@Serializable
data class OpenMeteoCurrent(
    val time: String? = null,
    val temperature_2m: Double? = null,
    val weather_code: Int? = null,
    val precipitation_probability: Int? = null,
)
