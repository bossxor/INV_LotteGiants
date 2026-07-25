package com.bossxor.lottegiants.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NaverSportsApi {

    @GET("schedule/games")
    suspend fun getGames(
        @Query("fromDate") fromDate: String,
        @Query("toDate") toDate: String,
        @Query("upperCategoryId") upperCategoryId: String = "kbaseball",
        @Query("size") size: Int = 500,
        @Query("fields") fields: String = SCHEDULE_FIELDS,
    ): ScheduleResponse

    @GET("schedule/games/{gameId}/relay")
    suspend fun getRelay(@Path("gameId") gameId: String): RelayResponse

    @GET("statistics/categories/kbo/seasons/{season}/teams")
    suspend fun getStandings(@Path("season") season: String): StandingsResponse

    companion object {
        const val SCHEDULE_FIELDS =
            "basic,superCategoryId,categoryName,stadium,statusNum,gameOnAir,hasVideo,title," +
                "specialMatchInfo,roundCode,seriesOutcome,seriesGameNo,homeStarterName," +
                "awayStarterName,winPitcherName,losePitcherName,homeCurrentPitcherName," +
                "awayCurrentPitcherName,broadChannel"

        private const val BASE_URL = "https://api-gw.sports.naver.com/"

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        fun create(): NaverSportsApi {
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
                            .header("Referer", "https://m.sports.naver.com/")
                            .build()
                    )
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(NaverSportsApi::class.java)
        }
    }
}
