package com.bossxor.lottegiants.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 복구 필드는 이름과 타입으로 관리하며 2.0.59 JSON/기존 파이프 형식도 읽는다. */
@Serializable
data class LiveEventCursor(
    val gameId: String = "",
    val seqno: Int = -1,
    val focusScore: Int = -1,
    val opponentScore: Int = -1,
    val basesKey: String = "",
    val pitcherCode: String = "",
    val favoriteBatterCode: String = "",
    val status: GameStatus? = null,
    val chanceBatter: String = "",
    val seenPitcherCodes: Set<String> = emptySet(),
    val chanceBases: NamedBases = NamedBases(null, null, null),
    val inning: Int? = null,
    val isTop: Boolean? = null,
    val focusScores: ScoreLedger = ScoreLedger(),
    val opponentScores: ScoreLedger = ScoreLedger(),
) {
    constructor(parts: List<String>, focusScores: ScoreLedger = ScoreLedger(), opponentScores: ScoreLedger = ScoreLedger()) : this(
        gameId = parts.firstOrNull().orEmpty(), seqno = parts.getOrNull(1)?.toIntOrNull() ?: -1,
        focusScore = parts.getOrNull(2)?.toIntOrNull() ?: -1, opponentScore = parts.getOrNull(3)?.toIntOrNull() ?: -1,
        basesKey = parts.getOrNull(4).orEmpty(), pitcherCode = parts.getOrNull(5).orEmpty(),
        favoriteBatterCode = parts.getOrNull(6).orEmpty(),
        status = parseCursorStatus(parts.getOrNull(7)),
        chanceBatter = parts.getOrNull(8).orEmpty(),
        seenPitcherCodes = parts.getOrNull(9).orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet(),
        chanceBases = NamedBases.decode(parts.getOrNull(10).orEmpty()),
        inning = parts.getOrNull(11)?.toIntOrNull()?.takeIf { it > 0 },
        isTop = parts.getOrNull(12)?.toBooleanStrictOrNull(), focusScores = focusScores, opponentScores = opponentScores,
    )

    fun encode(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json { ignoreUnknownKeys = true }
        fun decode(raw: String): LiveEventCursor = runCatching {
            if (!raw.trimStart().startsWith("{")) LiveEventCursor(raw.split('|'))
            else if ("parts" in codec.parseToJsonElement(raw).jsonObject) {
                val old = codec.decodeFromString(LegacyCursor.serializer(), raw)
                LiveEventCursor(old.parts, old.focusScores, old.opponentScores)
            } else codec.decodeFromString(serializer(), raw)
        }.getOrDefault(LiveEventCursor())
    }
}

@Serializable
private data class LegacyCursor(val parts: List<String> = emptyList(),
    val focusScores: ScoreLedger = ScoreLedger(), val opponentScores: ScoreLedger = ScoreLedger())

private fun parseCursorStatus(raw: String?): GameStatus? =
    raw?.let { runCatching { GameStatus.valueOf(it) }.getOrNull() }
