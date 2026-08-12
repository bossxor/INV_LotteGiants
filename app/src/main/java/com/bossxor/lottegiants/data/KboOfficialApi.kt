package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LOTTE_TEAM_CODE
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.MiniGame
import com.bossxor.lottegiants.domain.cancelDisplayLabel
import com.bossxor.lottegiants.domain.normalizeCancelReason
import com.bossxor.lottegiants.domain.teamLogoUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * KBO 공식 일정 API. 하루치 경기 목록 하나로 점수·이닝·BSO·주자·투수/타자·선발·구장·
 * 중계채널·취소 사유까지 모두 내려주므로 앱의 1차 데이터 소스로 쓴다.
 * 문자중계·투구 위치·라인업 상세처럼 KBO가 제공하지 않는 항목만 네이버로 보완한다.
 */
interface KboOfficialApi {

    @GET("ws/Main.asmx/GetKboGameList")
    suspend fun getGameList(
        @Query("leId") leId: Int = 1,
        @Query("srId") srId: String = "0,1,3,4,5,6,7,8,9",
        @Query("date") date: String,
    ): KboGameListResponse

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun dateParam(date: LocalDate): String = date.format(DATE_FMT)

        fun create(): KboOfficialApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                            )
                            .header("Referer", "https://www.koreabaseball.com/")
                            .build(),
                    )
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://www.koreabaseball.com/")
                .client(client)
                .addConverterFactory(
                    NaverSportsApi.json.asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(KboOfficialApi::class.java)
        }
    }
}

@Serializable
data class KboGameListResponse(
    val game: List<KboOfficialGame> = emptyList(),
)

/**
 * GetKboGameList의 한 경기.
 *
 * 접두사 규칙: `T_`는 초(원정) 쪽, `B_`는 말(홈) 쪽이다. 따라서 초 공격 중에는
 * `T_P_NM`이 타자·`B_P_NM`이 투수이고, 말 공격 중에는 반대가 된다.
 */
@Serializable
data class KboOfficialGame(
    @SerialName("G_DT") val gameDate: String = "",
    @SerialName("G_ID") val gameId: String = "",
    @SerialName("SEASON_ID") val seasonId: Int = 0,
    @SerialName("G_TM") val startTime: String = "",
    @SerialName("S_NM") val stadium: String = "",
    @SerialName("AWAY_ID") val awayId: String = "",
    @SerialName("HOME_ID") val homeId: String = "",
    @SerialName("AWAY_NM") val awayName: String = "",
    @SerialName("HOME_NM") val homeName: String = "",
    @SerialName("T_PIT_P_NM") val awayStarter: String = "",
    @SerialName("B_PIT_P_NM") val homeStarter: String = "",
    @SerialName("W_PIT_P_NM") val winPitcher: String = "",
    @SerialName("L_PIT_P_NM") val losePitcher: String = "",
    @SerialName("SV_PIT_P_NM") val savePitcher: String = "",
    @SerialName("GAME_STATE_SC") val gameState: Int = 0,
    @SerialName("CANCEL_SC_ID") val cancelScId: Int = 0,
    @SerialName("CANCEL_SC_NM") val cancelScNm: String = "",
    @SerialName("GAME_INN_NO") val inning: Int = 0,
    @SerialName("GAME_TB_SC") val topBottom: String = "",
    @SerialName("T_SCORE_CN") val awayScore: String = "",
    @SerialName("B_SCORE_CN") val homeScore: String = "",
    @SerialName("STRIKE_CN") val strike: Int = 0,
    @SerialName("BALL_CN") val ball: Int = 0,
    @SerialName("OUT_CN") val out: Int = 0,
    @SerialName("B1_BAT_ORDER_NO") val base1Order: Int = 0,
    @SerialName("B2_BAT_ORDER_NO") val base2Order: Int = 0,
    @SerialName("B3_BAT_ORDER_NO") val base3Order: Int = 0,
    @SerialName("T_P_NM") val awaySidePlayer: String = "",
    @SerialName("B_P_NM") val homeSidePlayer: String = "",
    @SerialName("TV_IF") val broadChannel: String = "",
    @SerialName("HEADER_NO") val headerNo: Int = 0,
) {
    val isTopInning: Boolean get() = !topBottom.equals("B", ignoreCase = true)

    /** GAME_STATE_SC: 1 예정 / 2·5 진행 / 3 종료 / 4 취소·서스펜디드 */
    fun status(): GameStatus = when {
        gameState == 4 || cancelScId >= 1 -> GameStatus.CANCELED
        gameState == 3 -> GameStatus.ENDED
        gameState == 2 || gameState == 5 -> GameStatus.LIVE
        else -> GameStatus.BEFORE
    }

    /** 취소·순연 등 정상경기가 아닌 경우의 사유 라벨 */
    fun cancelReasonLabel(): String? {
        val name = cancelScNm.trim()
        if (name.isBlank() || name == "정상경기") return null
        if (cancelScId != 0 || gameState == 4) return name
        return null
    }

    /** 네이버 스포츠 gameId는 KBO G_ID 뒤에 시즌을 붙인 형태다. */
    fun naverGameId(): String = "$gameId$seasonId"

    fun matchKey(): String = "${awayId.trim().uppercase()}_${homeId.trim().uppercase()}"

    fun involvesLotte(): Boolean =
        awayId.equals(LOTTE_TEAM_CODE, true) || homeId.equals(LOTTE_TEAM_CODE, true)

    /** 이 경기의 진행 상태를 사람이 읽는 한 줄로 (예: "7회말", "취소 (폭염)") */
    fun statusText(): String = when (status()) {
        GameStatus.BEFORE -> startTime
        GameStatus.LIVE -> if (inning > 0) {
            "${inning}회${if (isTopInning) "초" else "말"}"
        } else {
            "진행 중"
        }
        GameStatus.ENDED -> "종료"
        GameStatus.CANCELED -> cancelDisplayLabel(normalizeCancelReason(cancelReasonLabel()))
    }

    private fun score(raw: String): Int = raw.trim().toIntOrNull() ?: 0

    fun toMiniGame(): MiniGame = MiniGame(
        gameId = naverGameId(),
        homeName = homeName.trim(),
        awayName = awayName.trim(),
        homeScore = score(homeScore),
        awayScore = score(awayScore),
        status = status(),
        statusText = statusText(),
        stadium = stadium.trim(),
        startTime = startTime.trim(),
        homeLogoUrl = teamLogoUrl(homeId.trim().uppercase()),
        awayLogoUrl = teamLogoUrl(awayId.trim().uppercase()),
        homeStarter = homeStarter.trim(),
        awayStarter = awayStarter.trim(),
        broadChannel = broadChannel.trim(),
        winPitcherName = winPitcher.trim(),
        losePitcherName = losePitcher.trim(),
        gameDate = isoDate(),
        homeTeamCode = homeId.trim().uppercase(),
        awayTeamCode = awayId.trim().uppercase(),
    )

    /** G_DT(yyyyMMdd) → yyyy-MM-dd */
    fun isoDate(): String =
        if (gameDate.length == 8) {
            "${gameDate.substring(0, 4)}-${gameDate.substring(4, 6)}-${gameDate.substring(6, 8)}"
        } else {
            gameDate
        }

    fun toLotteBase(): LotteGameInfo {
        val isHome = homeId.equals(LOTTE_TEAM_CODE, true)
        val oppCode = (if (isHome) awayId else homeId).trim().uppercase()
        val state = status()
        val live = state == GameStatus.LIVE
        val reason = if (state == GameStatus.CANCELED) {
            normalizeCancelReason(cancelReasonLabel())
        } else {
            ""
        }
        // 초 공격이면 원정팀이 타석에 있다.
        val lotteBatting = if (isTopInning) !isHome else isHome
        val batter = if (isTopInning) awaySidePlayer else homeSidePlayer
        val pitcher = if (isTopInning) homeSidePlayer else awaySidePlayer

        return LotteGameInfo(
            gameId = naverGameId(),
            gameDate = isoDate(),
            startTime = startTime.trim(),
            stadium = stadium.trim(),
            isHome = isHome,
            opponentCode = oppCode,
            opponentName = (if (isHome) awayName else homeName).trim(),
            opponentLogoUrl = teamLogoUrl(oppCode),
            lotteScore = score(if (isHome) homeScore else awayScore),
            opponentScore = score(if (isHome) awayScore else homeScore),
            status = state,
            statusText = statusText(),
            cancelReason = reason,
            broadChannel = broadChannel.trim(),
            inning = if (live) inning else 0,
            isTopInning = isTopInning,
            strike = if (live) strike else 0,
            ball = if (live) ball else 0,
            out = if (live) out else 0,
            onBase1 = live && base1Order > 0,
            onBase2 = live && base2Order > 0,
            onBase3 = live && base3Order > 0,
            currentPitcherName = if (live) pitcher.trim() else "",
            currentBatterName = if (live) batter.trim() else "",
            isLotteBatting = live && lotteBatting,
            lotteStartingPitcher = (if (isHome) homeStarter else awayStarter).trim(),
            opponentStartingPitcher = (if (isHome) awayStarter else homeStarter).trim(),
            winPitcherName = winPitcher.trim(),
            losePitcherName = losePitcher.trim(),
            savePitcherName = savePitcher.trim(),
        )
    }
}
