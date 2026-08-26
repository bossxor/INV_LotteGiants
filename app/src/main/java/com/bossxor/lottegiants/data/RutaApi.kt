package com.bossxor.lottegiants.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 루타(ruta) 백엔드 클라이언트.
 * 베이스: https://ruta-api.sports2i.com/api/v1
 * 대부분 엔드포인트는 Bearer 토큰이 필요하며, 미인증 시 호출부는 Naver fallback을 사용한다.
 */
interface RutaApi {

    @POST("auth/register")
    suspend fun register(@Body body: RutaAuthRequest): RutaAuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: RutaAuthRequest): RutaAuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RutaRefreshRequest): RutaAuthResponse

    @GET("game/{gameId}")
    suspend fun getGame(
        @Path("gameId") gameId: String,
        @Header("Authorization") authorization: String,
    ): JsonObject

    @GET("game/{gameId}/highlight")
    suspend fun getGameHighlight(
        @Path("gameId") gameId: String,
        @Header("Authorization") authorization: String,
    ): JsonObject

    @GET("game/all/winRate")
    suspend fun getWinRate(
        @Query("gameId") gameId: String,
        @Header("Authorization") authorization: String,
    ): JsonObject

    companion object {
        const val BASE_URL = "https://ruta-api.sports2i.com/api/v1/"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        private val tokenRef = AtomicReference<String?>(null)

        fun currentToken(): String? = tokenRef.get()

        fun setToken(token: String?) {
            tokenRef.set(token)
        }

        fun bearerOrNull(): String? = tokenRef.get()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

        /**
         * 게스트/익명 토큰 발급을 한 번 시도한다.
         * 루타 서버가 스키마를 거부하면 false — 호출부는 Naver fallback을 쓴다.
         */
        suspend fun tryEnsureGuestToken(api: RutaApi, deviceId: String): Boolean {
            if (!bearerOrNull().isNullOrBlank()) return true
            val req = RutaAuthRequest(provider = "guest", deviceId = deviceId, anonymous = true)
            val token = runCatching {
                api.register(req).resolvedAccessToken()
            }.getOrElse {
                runCatching { api.login(req).resolvedAccessToken() }.getOrDefault("")
            }
            if (token.isBlank()) return false
            setToken(token)
            return true
        }

        fun create(): RutaApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .apply {
                            bearerOrNull()?.let { header("Authorization", it) }
                        }
                        .build()
                    chain.proceed(req)
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(RutaApi::class.java)
        }
    }
}

@Serializable
data class RutaAuthRequest(
    val provider: String = "guest",
    val deviceId: String = "",
    val anonymous: Boolean = true,
)

@Serializable
data class RutaRefreshRequest(
    val refreshToken: String = "",
)

@Serializable
data class RutaAuthResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val token: String? = null,
    val data: RutaAuthData? = null,
)

@Serializable
data class RutaAuthData(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

fun RutaAuthResponse.resolvedAccessToken(): String =
    accessToken?.takeIf { it.isNotBlank() }
        ?: token?.takeIf { it.isNotBlank() }
        ?: data?.accessToken.orEmpty()

/** 루타 응답에서 추출한 고급 지표 (스키마 변동에 느슨하게 대응) */
data class RutaGameExtras(
    val connected: Boolean = false,
    val winProbSeries: List<com.bossxor.lottegiants.domain.WinProbPoint> = emptyList(),
    val highlightText: String = "",
    val highlightUrl: String = "",
)

fun parseRutaWinRate(obj: JsonObject?, isHome: Boolean): List<com.bossxor.lottegiants.domain.WinProbPoint> {
    if (obj == null) return emptyList()
    val points = mutableListOf<com.bossxor.lottegiants.domain.WinProbPoint>()
    fun norm(v: Double): Double = (if (v > 1.5) v / 100.0 else v).coerceIn(0.0, 1.0)
    fun addRate(seq: Int, label: String, home: Double?, away: Double?) {
        val raw = if (isHome) home else away
        val rate = raw ?: home ?: return
        points.add(
            com.bossxor.lottegiants.domain.WinProbPoint(
                seq = seq,
                label = label,
                homeProb = norm(rate),
            ),
        )
    }
    val data = obj["data"] as? JsonObject ?: obj
    val listKeys = listOf("winRates", "winRate", "series", "points", "list", "items")
    for (key in listKeys) {
        val arr = data[key]
        if (arr is kotlinx.serialization.json.JsonArray) {
            arr.forEachIndexed { i, el ->
                val o = el as? JsonObject ?: return@forEachIndexed
                val home = o.jsonDouble("homeTeamWinRate", "homeWinRate", "home", "homeProb")
                val away = o.jsonDouble("awayTeamWinRate", "awayWinRate", "away", "awayProb")
                addRate(i, o.jsonString("label", "inn", "inning") ?: "$i", home, away)
            }
            if (points.isNotEmpty()) return points
        }
    }
    val home = data.jsonDouble("homeTeamWinRate", "homeWinRate", "home")
    val away = data.jsonDouble("awayTeamWinRate", "awayWinRate", "away")
    if (home != null || away != null) addRate(0, "현재", home, away)
    return points
}

private fun JsonObject.jsonDouble(vararg keys: String): Double? {
    for (k in keys) {
        val el = this[k] ?: continue
        val p = el as? kotlinx.serialization.json.JsonPrimitive ?: continue
        if (!p.isString) {
            runCatching { p.content.toDouble() }.getOrNull()?.let { return it }
        }
        p.content.toDoubleOrNull()?.let { return it }
    }
    return null
}

private fun JsonObject.jsonString(vararg keys: String): String? {
    for (k in keys) {
        val el = this[k] ?: continue
        val p = el as? kotlinx.serialization.json.JsonPrimitive ?: continue
        p.content.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

