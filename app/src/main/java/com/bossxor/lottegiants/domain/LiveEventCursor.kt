package com.bossxor.lottegiants.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 서비스·알람·워커가 공유하는 복구 상태. 기존 파이프 형식도 읽는다. */
@Serializable
data class LiveEventCursor(val parts: List<String> = emptyList(),
    val focusScores: ScoreLedger = ScoreLedger(), val opponentScores: ScoreLedger = ScoreLedger()) {
    val gameId: String get() = parts.firstOrNull().orEmpty()
    val inning: Int? get() = parts.getOrNull(11)?.toIntOrNull()?.takeIf { it > 0 }
    fun encode(): String = codec.encodeToString(serializer(), this)
    companion object {
        private val codec = Json { ignoreUnknownKeys = true }
        fun decode(raw: String): LiveEventCursor =
            if (raw.startsWith("{")) runCatching { codec.decodeFromString(serializer(), raw) }.getOrDefault(LiveEventCursor())
            else LiveEventCursor(raw.split('|'))
    }
}
