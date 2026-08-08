package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.LeaderPlayer
import com.bossxor.lottegiants.domain.LotteTeamCard
import com.bossxor.lottegiants.domain.RankPoint
import com.bossxor.lottegiants.domain.RosterMove
import com.bossxor.lottegiants.domain.WeeklyPoint
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface KeuboApi {

    @GET("api/stats")
    suspend fun getStats(
        @Query("type") type: String,
        @Query("season") season: Int,
    ): KeuboStatsResponse

    @GET("api/team-card")
    suspend fun getTeamCard(@Query("team") team: String = "lotte"): KeuboTeamCardResponse

    @GET("api/roster-moves")
    suspend fun getRosterMoves(@Query("teamId") teamId: Int = 7): KeuboRosterMovesResponse

    companion object {
        const val LOTTE_TEAM_ID = 7
        const val LOTTE_SLUG = "lotte"

        fun create(): KeuboApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Accept", "application/json")
                            .build()
                    )
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://keubo.fan/")
                .client(client)
                .addConverterFactory(
                    NaverSportsApi.json.asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(KeuboApi::class.java)
        }
    }
}

@Serializable
data class KeuboStatsResponse(
    val stats: List<KeuboStatDto> = emptyList(),
    val type: String = "",
    val count: Int = 0,
)

@Serializable
data class KeuboStatDto(
    val rank: Int = 0,
    val name: String = "",
    val team: String = "",
    val avg: String? = null,
    val era: String? = null,
    val games: Int = 0,
    val hits: Int = 0,
    val hr: Int = 0,
    val rbi: Int = 0,
    val ops: String? = null,
    val obp: String? = null,
    val slg: String? = null,
    val sb: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val saves: Int = 0,
    val holds: Int = 0,
    val so: Int = 0,
    val whip: String? = null,
    val ip: String? = null,
    val kboId: String? = null,
    val playerId: String? = null,
    val qualifiedRate: Double = 0.0,
)

@Serializable
data class KeuboTeamCardResponse(
    val team: String = "",
    val standing: KeuboStandingDto? = null,
    val recentForm: List<String> = emptyList(),
    val rankHistory: List<KeuboRankPointDto> = emptyList(),
    val weeklyBatting: List<KeuboWeeklyAvgDto> = emptyList(),
    val weeklyPitching: List<KeuboWeeklyEraDto> = emptyList(),
    val weeklyBattingRank: Int? = null,
    val weeklyPitchingRank: Int? = null,
)

@Serializable
data class KeuboStandingDto(
    val rank: Int = 0,
    val gamesBehind: Double = 0.0,
    val streak: String = "",
)

@Serializable
data class KeuboRankPointDto(
    val date: String = "",
    val rank: Int = 0,
)

@Serializable
data class KeuboWeeklyAvgDto(
    val week: String = "",
    val avg: Double = 0.0,
)

@Serializable
data class KeuboWeeklyEraDto(
    val week: String = "",
    val era: Double = 0.0,
)

@Serializable
data class KeuboRosterMovesResponse(
    val teamId: Int = 0,
    val seasonStart: String = "",
    val moves: List<KeuboMoveDto> = emptyList(),
)

@Serializable
data class KeuboMoveDto(
    val kboPlayerId: String = "",
    val playerName: String = "",
    val moveType: String = "",
    val moveDate: String = "",
)

fun KeuboStatDto.toLeader(isPitcher: Boolean) = LeaderPlayer(
    rank = rank,
    name = name,
    team = team,
    isLotte = team.contains("롯데") || team.equals("LT", ignoreCase = true),
    isPitcher = isPitcher,
    avg = avg.orEmpty(),
    era = era.orEmpty(),
    games = games,
    hits = hits,
    hr = hr,
    rbi = rbi,
    ops = ops.orEmpty(),
    obp = obp.orEmpty(),
    slg = slg.orEmpty(),
    sb = sb,
    wins = wins,
    losses = losses,
    saves = saves,
    holds = holds,
    so = so,
    whip = whip.orEmpty(),
    ip = ip.orEmpty(),
    playerCode = kboId ?: playerId.orEmpty(),
    qualified = qualifiedRate >= 1.0,
)

fun KeuboTeamCardResponse.toDomain() = LotteTeamCard(
    currentRank = standing?.rank ?: 0,
    gamesBehind = standing?.gamesBehind ?: 0.0,
    streak = standing?.streak.orEmpty(),
    recentForm = recentForm,
    rankHistory = rankHistory.map { RankPoint(it.date, it.rank) },
    weeklyBatting = weeklyBatting.map { WeeklyPoint(it.week, it.avg) },
    weeklyPitching = weeklyPitching.map { WeeklyPoint(it.week, it.era) },
    weeklyBattingRank = weeklyBattingRank,
    weeklyPitchingRank = weeklyPitchingRank,
)

fun KeuboMoveDto.toDomain() = RosterMove(
    playerCode = kboPlayerId,
    playerName = playerName,
    moveType = moveType,
    moveDate = moveDate,
    isRegister = moveType.equals("register", ignoreCase = true),
)
