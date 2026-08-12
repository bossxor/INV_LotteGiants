package com.bossxor.lottegiants.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * KBO 공식 일정 API — 취소 사유(CANCEL_SC_NM: 우천취소, 폭염취소, 그라운드사정 …) 조회용.
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

@Serializable
data class KboOfficialGame(
    @SerialName("G_DT") val gameDate: String = "",
    @SerialName("G_ID") val gameId: String = "",
    @SerialName("AWAY_ID") val awayId: String = "",
    @SerialName("HOME_ID") val homeId: String = "",
    @SerialName("GAME_STATE_SC") val gameState: Int = 0,
    @SerialName("CANCEL_SC_ID") val cancelScId: Int = 0,
    @SerialName("CANCEL_SC_NM") val cancelScNm: String = "",
) {
    /** 취소·순연 등 정상경기가 아닌 경우의 사유 라벨 */
    fun cancelReasonLabel(): String? {
        val name = cancelScNm.trim()
        if (name.isBlank() || name == "정상경기") return null
        if (cancelScId != 0 || gameState == 4) return name
        return null
    }

    fun matchKey(): String = "${awayId.trim().uppercase()}_${homeId.trim().uppercase()}"
}
