package com.bossxor.lottegiants.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface KboMobileApi {

    @Headers(
        "Content-Type: application/json; charset=utf-8",
        "Accept: application/json, text/javascript, */*; q=0.01",
        "X-Requested-With: XMLHttpRequest",
        "Origin: https://m.koreabaseball.com",
        "Referer: https://m.koreabaseball.com/Kbo/PlayerAdd.aspx",
    )
    @POST("ws/Kbo.asmx/GetRoster")
    suspend fun getRoster(@Body body: KboRosterRequest): KboRosterResponse

    companion object {
        fun create(): KboMobileApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
                        )
                        .build()
                    val res = chain.proceed(req)
                    val body = res.body ?: return@addInterceptor res
                    val raw = body.string()
                    val jsonOnly = extractJsonObject(raw)
                    res.newBuilder()
                        .body(jsonOnly.toResponseBody(body.contentType() ?: "application/json".toMediaType()))
                        .build()
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://m.koreabaseball.com/")
                .client(client)
                .addConverterFactory(
                    NaverSportsApi.json.asConverterFactory("application/json".toMediaType()),
                )
                .build()
                .create(KboMobileApi::class.java)
        }

        /** JSON 뒤에 붙는 HTML 제거 */
        fun extractJsonObject(raw: String): String {
            val start = raw.indexOf('{')
            if (start < 0) return raw
            var depth = 0
            var inString = false
            var escape = false
            for (i in start until raw.length) {
                val c = raw[i]
                when {
                    escape -> escape = false
                    c == '\\' && inString -> escape = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> depth++
                    !inString && c == '}' -> {
                        depth--
                        if (depth == 0) return raw.substring(start, i + 1)
                    }
                }
            }
            return raw.substring(start)
        }
    }
}

@Serializable
data class KboRosterRequest(
    val season_id: String,
    val g_dt: String,
    val t_id: String,
)

@Serializable
data class KboRosterResponse(
    val tableKboY: String = "",
    val tableKboN: String = "",
    val code: String = "",
    val msg: String = "",
)

object KboRosterParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parsePlayers(tableJson: String): List<ParsedRosterPlayer> {
        if (tableJson.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(tableJson).jsonObject }.getOrNull() ?: return emptyList()
        val rows = root["rows"]?.jsonArray ?: return emptyList()
        return rows.mapNotNull { rowEl ->
            val cells = when (rowEl) {
                is JsonObject -> rowEl["row"]?.jsonArray
                else -> null
            } ?: return@mapNotNull null
            val texts = cells.map { cellText(it) }
            if (texts.size < 2 || texts[1].isBlank()) return@mapNotNull null
            ParsedRosterPlayer(
                backNumber = texts.getOrElse(0) { "" },
                name = texts.getOrElse(1) { "" },
                position = texts.getOrElse(2) { "" },
                batsThrows = texts.getOrElse(3) { "" },
            )
        }
    }

    private fun cellText(el: JsonElement): String = when (el) {
        is JsonObject -> el["Text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        is JsonPrimitive -> el.contentOrNull.orEmpty()
        is JsonArray -> el.joinToString("") { cellText(it) }
    }
}

data class ParsedRosterPlayer(
    val backNumber: String,
    val name: String,
    val position: String,
    val batsThrows: String,
)
