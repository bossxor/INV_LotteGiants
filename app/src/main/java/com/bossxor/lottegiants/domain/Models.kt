package com.bossxor.lottegiants.domain

import kotlinx.serialization.Serializable

const val LOTTE_TEAM_CODE = "LT"

private val TEAM_CODE_IN_LOGO_URL =
    Regex("(?:emblem_|initial_|/default/)([A-Za-z]{2})(?:[_./]|$)")

/** 네이버 스포츠 KBO 팀 엠블럼 (투명 PNG, 184px) */
fun teamLogoUrl(teamCode: String): String {
    val code = teamCode.trim().uppercase()
    if (code.isBlank()) return ""
    return "https://sports-phinf.pstatic.net/team/kbo/default/$code.png"
}

/** KBO CDN 시즌 엠블럼. 스코어보드용이라 64px 수준이라 화면에는 쓰지 않는다. */
fun kboTeamEmblemUrl(teamCode: String, season: Int = java.time.LocalDate.now().year): String {
    val code = teamCode.trim().uppercase()
    if (code.isBlank()) return ""
    return "https://6ptotvmi5753.edge.naverncp.com/KBO_IMAGE/emblem/regular/$season/emblem_$code.png"
}

/** KBO API `//` 프로토콜 상대 URL → https */
fun normalizeKboImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.isNotBlank() -> url
    else -> ""
}

/** 스코어보드 `H/A_INITIAL_LK` 는 26px 이니셜이라 팀 로고가 아니다. */
fun isKboInitialLogoUrl(url: String): Boolean =
    url.contains("/initial_", ignoreCase = true)

fun teamCodeFromLogoUrl(url: String): String =
    TEAM_CODE_IN_LOGO_URL.find(url)?.groupValues?.get(1)?.uppercase().orEmpty()

/**
 * 화면에 쓸 팀 로고.
 * KBO 스코어보드는 이니셜(26px)·워드마크(64px)를 내려주므로, 팀 코드가 있으면
 * 네이버 KBO 미디어 엠블럼을 쓰고, 없을 때만 원본 URL을 남긴다.
 */
fun resolveTeamLogoUrl(
    teamCode: String,
    kboUrl: String = "",
    season: Int = java.time.LocalDate.now().year,
): String {
    val code = teamCode.trim().uppercase()
        .ifBlank { teamCodeFromLogoUrl(kboUrl) }
    if (code.isNotBlank()) return teamLogoUrl(code)
    val fromKbo = normalizeKboImageUrl(kboUrl)
    if (fromKbo.isNotBlank() && !isKboInitialLogoUrl(fromKbo)) return fromKbo
    return kboTeamEmblemUrl(teamCode, season)
}

val LOTTE_LOGO_URL = teamLogoUrl(LOTTE_TEAM_CODE)

val KBO_ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")

/** 자정~이 시각 전까지는 전날을 경기일(오늘)로 본다. */
val KBO_DAY_ROLLOVER: java.time.LocalTime = java.time.LocalTime.of(5, 0)

fun kboNow(): java.time.ZonedDateTime = java.time.ZonedDateTime.now(KBO_ZONE)

/** 서울 기준 오전 5시가 지나야 날짜가 바뀐다. 위젯·라이브·결과 탭의 '오늘'에 쓴다. */
fun kboToday(now: java.time.ZonedDateTime = kboNow()): java.time.LocalDate {
    val date = now.toLocalDate()
    return if (now.toLocalTime() < KBO_DAY_ROLLOVER) date.minusDays(1) else date
}

/** KBO `G_TM`은 `"6:30"`처럼 한 자리 시가 온다. */
fun parseKboStartMillis(date: String, time: String): Long? = runCatching {
    val datePart = date.trim().let { d ->
        if (d.length == 8 && d.all { it.isDigit() }) {
            "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6, 8)}"
        } else {
            d
        }
    }
    val nums = time.trim().split(':', '\uFF1A').mapNotNull { it.trim().toIntOrNull() }
    if (nums.size < 2) return@runCatching null
    java.time.LocalDate.parse(datePart)
        .atTime(nums[0], nums[1], nums.getOrNull(2) ?: 0)
        .atZone(java.time.ZoneId.of("Asia/Seoul"))
        .toInstant()
        .toEpochMilli()
}.getOrNull()

/** 네이버 gameId `YYYYMMDDAAHH0` / `…0{season}` 끝자리가 DH 차전. 0이면 단경기. */
fun doubleHeaderNoFromGameId(gameId: String): Int {
    val id = gameId.trim()
    if (id.length < 13) return 0
    val kboId = if (id.length >= 17 && id.takeLast(4).all { it.isDigit() }) id.dropLast(4) else id
    val n = kboId.lastOrNull()?.digitToIntOrNull() ?: return 0
    return if (n in 1..2) n else 0
}

@Serializable
enum class GameStatus { BEFORE, LIVE, ENDED, CANCELED }

/** 다른 팀 경기 요약 (몇 회, 몇 대 몇) */
@Serializable
data class MiniGame(
    val gameId: String,
    val homeName: String,
    val awayName: String,
    val homeScore: Int,
    val awayScore: Int,
    val status: GameStatus,
    val statusText: String,
    /** 취소·순연 사유 (폭염, 우천 등) */
    val cancelReason: String = "",
    val stadium: String = "",
    val startTime: String = "",
    val homeLogoUrl: String = "",
    val awayLogoUrl: String = "",
    val homeStarter: String = "",
    val awayStarter: String = "",
    val broadChannel: String = "",
    val winPitcherName: String = "",
    val losePitcherName: String = "",
    val gameDate: String = "",
    val homeTeamCode: String = "",
    val awayTeamCode: String = "",
    val homeRank: Int = 0,
    val awayRank: Int = 0,
    /** 0=없음, 1·2=더블헤더 차전 */
    val doubleHeaderNo: Int = 0,
    val seasonSeriesNo: Int = 0,
    val lineupAnnounced: Boolean = false,
)

@Serializable
data class LineupSlot(
    val batOrder: Int,
    val name: String,
    val position: String,
    val seasonAvg: Double? = null,
    val todayHits: Int = 0,
    val todayAtBats: Int = 0,
    val todayPa: Int = 0,
    val todayRbi: Int = 0,
    val todayRun: Int = 0,
    val todayAvg: Double? = null,
    val isSubstitute: Boolean = false,
    val playerCode: String = "",
    val backNumber: String = "",
    val hitType: String = "",
    val isPitcher: Boolean = false,
)

@Serializable
data class PitcherLine(
    val name: String,
    val playerCode: String = "",
    val backNumber: String = "",
    val innings: String = "",
    val hits: Int = 0,
    val runs: Int = 0,
    val earnedRuns: Int = 0,
    val strikeouts: Int = 0,
    val walks: Int = 0,
    val pitchCount: Int = 0,
    val battersFaced: Int = 0,
    val homeRunsAllowed: Int = 0,
    val seasonEra: String = "",
    val seqno: Int = 0,
)

@Serializable
data class StadiumWeather(
    val stadium: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val precipProbability: Int? = null,
    val summary: String,
    val updatedAt: String = "",
)

@Serializable
data class PlayerDetail(
    val playerCode: String,
    val name: String,
    val backNumber: String = "",
    val hitType: String = "",
    val position: String = "",
    val birth: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val seasonAvg: String = "",
    val seasonGames: Int = 0,
    val seasonHits: Int = 0,
    val seasonAb: Int = 0,
    val seasonHr: Int = 0,
    val seasonRbi: Int = 0,
    val seasonObp: String = "",
    val seasonOps: String = "",
    val seasonSlg: String = "",
    val seasonSb: Int = 0,
    val pitcherEra: String = "",
    val pitcherWins: Int = 0,
    val pitcherLosses: Int = 0,
    val pitcherSo: Int = 0,
    val pitcherInn: String = "",
    val pitcherSaves: Int = 0,
    val pitcherHolds: Int = 0,
    val pitcherWhip: String = "",
    val isPitcher: Boolean = false,
    val todayLine: String = "",
    val photoUrl: String = "",
    /** 네이버 프리뷰 키 타자의 존별 타율 (없으면 빈 목록) */
    val hotCold: List<HotColdZone> = emptyList(),
)

@Serializable
data class FavoritePlayer(
    val code: String,
    val name: String = "",
    val team: String = "",
)

fun playerPhotoUrl(playerCode: String): String =
    playerPhotoCandidates(playerCode).firstOrNull().orEmpty()

/** KBO 공식 이미지 우선, 없으면 네이버 CDN */
fun playerPhotoCandidates(playerCode: String): List<String> {
    val code = playerCode.trim()
    if (code.isBlank()) return emptyList()
    return listOf(
        "https://img.koreabaseball.com/file/person/middle/$code.jpg",
        "https://img.koreabaseball.com/file/person/middle/new/$code.jpg",
        "https://www.koreabaseball.com/file/person/middle/new/$code.jpg",
        "https://www.koreabaseball.com/file/person/middle/$code.jpg",
        "https://sports-phinf.pstatic.net/player/kbo/default/$code.png",
        "https://sports-phinf.pstatic.net/player/kbo/default/$code.jpg",
    )
}

/**
 * 네이버 중계는 주자를 `"1"`뿐 아니라 선수코드·`Y`로도 준다.
 * `"0"` / 빈 값만 비점유로 본다.
 */
fun runnerOccupied(raw: String?): Boolean {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return false
    return when (v.lowercase()) {
        "0", "00", "false", "n", "no", "off", "-", ".", "null" -> false
        else -> true
    }
}

fun isPitcherPosition(position: String, hinted: Boolean = false): Boolean {
    if (hinted) return true
    val p = position.trim()
    return p.contains("투수") || p.equals("P", true) || p == "1" ||
        p.contains("pitcher", true)
}

@Serializable
data class EntryPlayer(
    val name: String,
    val playerCode: String = "",
    val position: String = "",
    val hitType: String = "",
    val backNumber: String = "",
    val isPitcher: Boolean = false,
)

/** 특정 날짜의 공식 등록/말소 (KBO 공시) */
@Serializable
data class DayEntryChanges(
    val date: String = "",
    val registered: List<EntryPlayer> = emptyList(),
    val removed: List<EntryPlayer> = emptyList(),
) {
    val hasChanges: Boolean get() = registered.isNotEmpty() || removed.isNotEmpty()
}

data class TeamHistorySection(
    val title: String,
    val items: List<String>,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 실시간 스코어 알림/Now Bar 표시 모드 */
enum class LiveDisplayMode {
    /** 커스텀 뷰 상세 카드 (선수 사진·루상). Live Update 승격 불가 */
    FULL,
    /** 상태바·알림줄에 점수만 */
    STATUS_SCORE,
    /** 이닝 진행 바 기반 Live Update — 잠금화면·Now Bar에 크게 표시 */
    LOCK_NOW,
}

@Serializable
data class LeaderPlayer(
    val rank: Int = 0,
    val name: String,
    val team: String,
    val isLotte: Boolean = false,
    val isPitcher: Boolean = false,
    val avg: String = "",
    val era: String = "",
    val games: Int = 0,
    val hits: Int = 0,
    val hr: Int = 0,
    val rbi: Int = 0,
    val ops: String = "",
    val obp: String = "",
    val slg: String = "",
    val sb: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val saves: Int = 0,
    val holds: Int = 0,
    val so: Int = 0,
    val whip: String = "",
    val ip: String = "",
    val playerCode: String = "",
    /** 규정타석/이닝 충족 (타이틀 레이스용) */
    val qualified: Boolean = false,
)

/** 크보팬식 타이틀 순위 한 줄 */
data class TitleRankEntry(
    val rank: Int,
    val player: LeaderPlayer,
    val valueLabel: String,
)

fun teamNameToCode(team: String): String = when {
    team.contains("롯데") || team.equals("LT", true) -> "LT"
    team.contains("삼성") || team.equals("SS", true) -> "SS"
    team.contains("KIA") || team.contains("기아") || team.equals("HT", true) -> "HT"
    team.contains("두산") || team.equals("OB", true) -> "OB"
    team.contains("LG") || team.equals("LG", true) -> "LG"
    team.contains("SSG") || team.equals("SK", true) -> "SK"
    team.contains("한화") || team.equals("HH", true) -> "HH"
    team.contains("키움") || team.equals("WO", true) || team.equals("넥센", true) -> "WO"
    team.contains("NC") || team.equals("NC", true) -> "NC"
    team.contains("KT") || team.equals("KT", true) -> "KT"
    else -> ""
}

fun teamCodeToName(code: String): String = when (code.trim().uppercase()) {
    "LT" -> "롯데"
    "SS" -> "삼성"
    "HT" -> "KIA"
    "OB" -> "두산"
    "LG" -> "LG"
    "SK" -> "SSG"
    "HH" -> "한화"
    "WO" -> "키움"
    "NC" -> "NC"
    "KT" -> "KT"
    else -> ""
}

@Serializable
data class RankPoint(val date: String, val rank: Int)

@Serializable
data class WeeklyPoint(val week: String, val value: Double)

@Serializable
data class LotteTeamCard(
    val currentRank: Int = 0,
    val gamesBehind: Double = 0.0,
    val streak: String = "",
    val recentForm: List<String> = emptyList(),
    val rankHistory: List<RankPoint> = emptyList(),
    val weeklyBatting: List<WeeklyPoint> = emptyList(),
    val weeklyPitching: List<WeeklyPoint> = emptyList(),
    val weeklyBattingRank: Int? = null,
    val weeklyPitchingRank: Int? = null,
)

@Serializable
data class RosterMove(
    val playerCode: String = "",
    val playerName: String = "",
    val moveType: String = "",
    val moveDate: String = "",
    val isRegister: Boolean = true,
)

/** 롯데 경기 전체 상태 (앱 화면 + 위젯 + 알림 공용) */
@Serializable
data class LotteGameInfo(
    val gameId: String,
    val gameDate: String,
    val startTime: String,
    val stadium: String,
    val isHome: Boolean,
    val opponentCode: String,
    val opponentName: String,
    val opponentLogoUrl: String = "",
    val lotteLogoUrl: String = "",
    val lotteRank: Int = 0,
    val opponentRank: Int = 0,
    val doubleHeaderNo: Int = 0,
    val seasonSeriesNo: Int = 0,
    val lineupAnnounced: Boolean = false,
    val runnerOn1Order: Int = 0,
    val runnerOn2Order: Int = 0,
    val runnerOn3Order: Int = 0,
    val crowdCount: String = "",
    val gameDuration: String = "",
    /** 포스트시즌·특수경기 라벨 (GAME_SC_NM) */
    val gameScLabel: String = "",
    val lotteScore: Int = 0,
    val opponentScore: Int = 0,
    val status: GameStatus = GameStatus.BEFORE,
    val statusText: String = "",
    /** 취소·순연 사유 (API statusInfo, 예: 폭염) */
    val cancelReason: String = "",
    val broadChannel: String = "",
    // 실시간 (relay)
    val inning: Int = 0,
    val isTopInning: Boolean = true,
    val strike: Int = 0,
    val ball: Int = 0,
    val out: Int = 0,
    val onBase1: Boolean = false,
    val onBase2: Boolean = false,
    val onBase3: Boolean = false,
    val currentPitcherName: String = "",
    val currentPitcherCode: String = "",
    val currentBatterName: String = "",
    val currentBatterOrder: Int = 0,
    val nextBatterName: String = "",
    val isLotteBatting: Boolean = false,
    // 라인업
    val lotteStartingPitcher: String = "",
    val opponentStartingPitcher: String = "",
    val lotteLineup: List<LineupSlot> = emptyList(),
    val opponentLineup: List<LineupSlot> = emptyList(),
    val lotteBenchBatters: List<LineupSlot> = emptyList(),
    val opponentBenchBatters: List<LineupSlot> = emptyList(),
    val lottePitchers: List<PitcherLine> = emptyList(),
    val opponentPitchers: List<PitcherLine> = emptyList(),
    // 스코어보드
    val lotteInningScores: List<String> = emptyList(),
    val opponentInningScores: List<String> = emptyList(),
    val lotteHits: Int = 0,
    val opponentHits: Int = 0,
    val lotteErrors: Int = 0,
    val opponentErrors: Int = 0,
    val lotteBb: Int = 0,
    val opponentBb: Int = 0,
    // 최근 문자중계 (이벤트 감지/표시용)
    val recentTexts: List<RelayText> = emptyList(),
    val winPitcherName: String = "",
    val losePitcherName: String = "",
    val savePitcherName: String = "",
    val holdPitcherName: String = "",
    // 경기요약 확장 (프리뷰·요약)
    val preview: GamePreview? = null,
    val currentPitcherPitchCount: Int = 0,
    val keyPlays: List<KeyPlay> = emptyList(),
    val provisionalMvpName: String = "",
    val provisionalMvpLine: String = "",
    /** 당일 투구 위치 (네이버 ptsOptions) */
    val pitchLocations: List<PitchLocation> = emptyList(),
    /** 라인업·중계·기록 보강 실패 시 사용자에게 보여줄 메시지 */
    val detailError: String = "",
    /**
     * 이 화면에서 기준이 되는 팀. 위젯·알림의 오늘 경기는 항상 롯데.
     * 다른 팀 경기를 볼 때만 홈팀 코드가 들어간다.
     */
    val focusTeamCode: String = LOTTE_TEAM_CODE,
    val focusTeamName: String = "롯데",
)

fun LotteGameInfo.focusName(): String =
    focusTeamName.ifBlank { teamCodeToName(focusTeamCode) }.ifBlank { "롯데" }

fun LotteGameInfo.isFocusLotte(): Boolean =
    focusTeamCode.isBlank() || focusTeamCode.equals(LOTTE_TEAM_CODE, ignoreCase = true)

/** 루타식 프리뷰 묶음 */
@Serializable
data class GamePreview(
    val gameDate: String = "",
    val startTime: String = "",
    val stadium: String = "",
    val broadChannel: String = "",
    val lotteStarter: PreviewPitcher = PreviewPitcher(),
    val opponentStarter: PreviewPitcher = PreviewPitcher(),
    val lotteKeyBatter: PreviewBatter = PreviewBatter(),
    val opponentKeyBatter: PreviewBatter = PreviewBatter(),
    val lotteStanding: PreviewTeamLine = PreviewTeamLine(),
    val opponentStanding: PreviewTeamLine = PreviewTeamLine(),
    val seasonMatchup: MatchupRecord = MatchupRecord(),
    val recentMatchups: List<MiniGame> = emptyList(),
    val weather: StadiumWeather? = null,
    val lotteRecentForm: List<RecentFormGame> = emptyList(),
    val opponentRecentForm: List<RecentFormGame> = emptyList(),
    /** 네이버 프리뷰 핫/콜드 존이 있으면 true */
    val hotColdAvailable: Boolean = false,
    val pitchAnalysisAvailable: Boolean = false,
    val winProbAvailable: Boolean = false,
)

@Serializable
data class PreviewPitcher(
    val name: String = "",
    val playerCode: String = "",
    val era: String = "",
    val wins: Int = 0,
    val losses: Int = 0,
    val strikeouts: Int = 0,
    val innings: String = "",
    val whip: String = "",
    val games: Int = 0,
)

@Serializable
data class PreviewBatter(
    val name: String = "",
    val playerCode: String = "",
    val avg: String = "",
    val hits: Int = 0,
    val hr: Int = 0,
    val rbi: Int = 0,
    val games: Int = 0,
    val ops: String = "",
    /** 최근 5경기 (네이버가 키 플레이어를 뽑는 기준) */
    val recentAvg: String = "",
    val recentHits: Int = 0,
    val recentRbi: Int = 0,
    /** 이번 시즌 해당 상대팀 상대 성적 */
    val vsOpponentAvg: String = "",
    val vsOpponentHits: Int = 0,
    val vsOpponentHr: Int = 0,
    val hotCold: List<HotColdZone> = emptyList(),
)

@Serializable
data class PreviewTeamLine(
    val teamCode: String = "",
    val teamName: String = "",
    val rank: Int = 0,
    val win: Int = 0,
    val draw: Int = 0,
    val lose: Int = 0,
    val wra: Double = 0.0,
)

@Serializable
data class RecentFormGame(
    val gameId: String = "",
    val date: String = "",
    val opponentName: String = "",
    val isHome: Boolean = true,
    val teamScore: Int = 0,
    val oppScore: Int = 0,
    /** 승 / 패 / 무 */
    val result: String = "",
)

@Serializable
data class HotColdZone(
    val zone: Int = 0,
    val avg: String = "",
    val heat: Int = 3,
    /** 해당 존 삼진 비율(%) */
    val kRate: Double = 0.0,
)

@Serializable
data class MatchupRecord(
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val label: String = "",
)

@Serializable
data class KeyPlay(
    val inning: Int = 0,
    val isTop: Boolean? = null,
    val text: String = "",
    val isScoring: Boolean = false,
)

@Serializable
data class WinProbPoint(
    val seq: Int = 0,
    val label: String = "",
    val homeProb: Double = 0.5,
)

@Serializable
data class HotColdCell(
    val row: Int = 0,
    val col: Int = 0,
    /** -1 cold ~ +1 hot */
    val value: Float = 0f,
)

@Serializable
data class PitchLocation(
    val x: Float = 0f,
    val y: Float = 0f,
    val speed: Int = 0,
    val pitchType: String = "",
    val result: String = "",
    val inning: Int = 0,
    val topSz: Float = 3.5f,
    val bottomSz: Float = 1.5f,
)

@Serializable
data class RelayText(
    val seqno: Int,
    val text: String,
    val type: Int,
    val inning: Int,
    /** true=초, false=말, null=미상 */
    val isTopInning: Boolean? = null,
)

/** 위젯/앱이 공유하는 전체 스냅샷 */
@Serializable
data class LiveSnapshot(
    val updatedAtMillis: Long = 0L,
    val lotteGame: LotteGameInfo? = null,
    val nextLotteGame: LotteGameInfo? = null,
    val lastLotteGame: LotteGameInfo? = null,
    val recentLotteGames: List<LotteGameInfo> = emptyList(),
    val otherGames: List<MiniGame> = emptyList(),
    val yesterdayGames: List<MiniGame> = emptyList(),
    /** 오늘 롯데 경기 (DH 1·2차전 전환용) */
    val todayLotteGames: List<MiniGame> = emptyList(),
    val highlightText: String = "",
    val highlightUntilMillis: Long = 0L,
    val weather: StadiumWeather? = null,
    /** 루타 API 연결 여부 (고급 차트용) */
    val rutaConnected: Boolean = false,
    val winProbSeries: List<WinProbPoint> = emptyList(),
    val hotColdZone: List<HotColdCell> = emptyList(),
    val pitchLocations: List<PitchLocation> = emptyList(),
)

@Serializable
data class TeamStanding(
    val teamId: String,
    val teamName: String,
    val ranking: Int,
    val wra: Double,
    val gameCount: Int,
    val win: Int,
    val draw: Int,
    val lose: Int,
    val gameBehind: Double,
    val streak: String = "",
    val lastFive: String = "",
)

/** KBO CANCEL_SC_ID → 짧은 사유 */
fun kboCancelReasonById(id: Int): String? = when (id) {
    1 -> "우천"
    2 -> "한파"
    3 -> "폭염"
    4 -> "안개"
    5 -> "강설"
    6 -> "그라운드사정"
    7 -> "태풍"
    8 -> "미세먼지"
    9 -> "폭염"
    10 -> "황사"
    99 -> "기타"
    else -> null
}

/** KBO/네이버 취소 문자열 → 짧은 사유 (없으면 null) */
fun resolveCancelReason(raw: String?, cancelId: Int = 0): String? {
    val name = raw?.trim().orEmpty()
    if (name.isNotBlank() && name != "정상경기") {
        normalizeCancelReason(name).takeIf { it.isNotBlank() }?.let { return it }
        val stripped = name
            .replace("경기", "")
            .replace("취소", "")
            .replace("순연", "")
            .trim()
        if (stripped.isNotBlank()) return stripped.take(12)
        if (!name.equals("경기취소", ignoreCase = true) && !name.equals("취소", ignoreCase = true)) {
            return name.take(12)
        }
    }
    return kboCancelReasonById(cancelId)
}

/** API statusInfo → 짧은 취소 사유 (폭염, 우천…). 일반 '경기취소'만 있으면 빈 문자열 */
fun normalizeCancelReason(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isBlank()) return ""
    // 알려진 구체 사유 우선
    val known = listOf(
        "폭염" to "폭염",
        "우천" to "우천",
        "강우" to "우천",
        "비" to "우천",
        "안개" to "안개",
        "강설" to "강설",
        "적설" to "강설",
        "눈" to "강설",
        "태풍" to "태풍",
        "미세먼지" to "미세먼지",
        "황사" to "황사",
        "순연" to "순연",
        "기타" to "기타",
        "구장" to "구장 사정",
        "그라운드" to "그라운드사정",
        "조명" to "조명",
        "정전" to "정전",
        "한파" to "한파",
        "폭풍" to "폭풍",
        "천둥" to "천둥번개",
        "낙뢰" to "천둥번개",
        "바람" to "강풍",
        "강풍" to "강풍",
        "지진" to "지진",
        "안전" to "안전 사유",
        "코로나" to "코로나",
    )
    for ((key, label) in known) {
        if (s.contains(key)) return label
    }
    // "경기취소" / "취소" / "CANCEL" 등 일반 표기만 있는 경우
    val generic = s
        .replace(Regex("""(?i)cancel(ed|lation)?"""), "")
        .replace("경기", "")
        .replace("취소", "")
        .replace("순연", "")
        .replace(Regex("""[\s·\-_/():（）\[\]]+"""), "")
        .trim()
    if (generic.isBlank()) return "" // UI에서 '경기 취소'만
    // 남은 문자열을 짧고 읽기 좋게
    return generic.take(12)
}

/** 표시용: "취소" 또는 "취소(폭염)" */
fun cancelDisplayLabel(reason: String?): String {
    val r = reason?.trim().orEmpty()
    return if (r.isBlank()) "취소" else "취소($r)"
}

val LotteGameInfo.cancelLabel: String
    get() = cancelDisplayLabel(cancelReason.ifBlank { null })

/** 취소 사유만 (폭염, 우천… 알 수 없으면 빈 문자열) */
val MiniGame.cancelReasonText: String
    get() {
        if (cancelReason.isNotBlank()) return cancelReason
        Regex("""(?:경기\s*)?취소\s*\(([^)]+)\)""").find(statusText)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return resolveCancelReason(statusText).orEmpty()
    }

/** 결과·일정 목록용 취소 표시 ("취소(폭염)") */
val MiniGame.cancelLabel: String
    get() {
        if (!isCanceledGame()) return statusText
        return cancelDisplayLabel(cancelReasonText.ifBlank { null })
    }

/** 캘린더 셀처럼 폭이 좁은 곳용: 사유만, 사유를 모르면 "취소" */
val MiniGame.cancelShortLabel: String
    get() = cancelReasonText.ifBlank { "취소" }

/** KBO·네이버 취소 경기 판별 (status 누락·폴백 데이터 보강) */
fun MiniGame.isCanceledGame(): Boolean {
    if (status == GameStatus.CANCELED) return true
    if (cancelReason.isNotBlank()) return true
    val text = statusText.trim()
    if (text.isBlank()) return false
    return text.contains("취소") ||
        text.contains("순연") ||
        text.contains("폭염") ||
        text.contains("우천") ||
        text.contains("취소됨", ignoreCase = true)
}

val LotteGameInfo.inningLabel: String
    get() = when {
        status == GameStatus.BEFORE -> startTime
        status == GameStatus.CANCELED -> cancelLabel
        status == GameStatus.ENDED -> "종료"
        inning <= 0 -> statusText
        else -> "${inning}회${if (isTopInning) "초" else "말"}"
    }
