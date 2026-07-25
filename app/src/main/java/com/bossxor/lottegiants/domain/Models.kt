package com.bossxor.lottegiants.domain

import kotlinx.serialization.Serializable

const val LOTTE_TEAM_CODE = "LT"

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
    val stadium: String = "",
    val startTime: String = "",
)

@Serializable
data class LineupSlot(
    val batOrder: Int,
    val name: String,
    val position: String,
    val seasonAvg: Double? = null,
    val todayHits: Int = 0,
    val todayAtBats: Int = 0,
    val isSubstitute: Boolean = false,
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
    val lotteScore: Int = 0,
    val opponentScore: Int = 0,
    val status: GameStatus = GameStatus.BEFORE,
    val statusText: String = "",
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
)

@Serializable
data class RelayText(
    val seqno: Int,
    val text: String,
    val type: Int,
    val inning: Int,
)

/** 위젯/앱이 공유하는 전체 스냅샷 */
@Serializable
data class LiveSnapshot(
    val updatedAtMillis: Long = 0L,
    val lotteGame: LotteGameInfo? = null,
    val nextLotteGame: LotteGameInfo? = null,
    val otherGames: List<MiniGame> = emptyList(),
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

val LotteGameInfo.inningLabel: String
    get() = when {
        status == GameStatus.BEFORE -> startTime
        status == GameStatus.CANCELED -> "취소"
        status == GameStatus.ENDED -> "종료"
        inning <= 0 -> statusText
        else -> "${inning}회${if (isTopInning) "초" else "말"}"
    }
