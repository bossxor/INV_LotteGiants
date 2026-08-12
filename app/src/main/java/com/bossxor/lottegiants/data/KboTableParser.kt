package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.KeyPlay
import com.bossxor.lottegiants.domain.TeamStanding
import com.bossxor.lottegiants.domain.teamNameToCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KBO 공식 사이트 S2iGridTable JSON 파서.
 * GetTeamRank / GetScoreBoardScroll / GetBoxScoreScroll 응답의 table* 필드를 처리한다.
 */
object KboTableParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseStandings(grid: KboGridTableResponse): List<TeamStanding> {
        val rows = grid.rows.map { row -> row.row.map { stripHtml(it.text) } }
        return parseStandingsRows(rows)
    }

    fun parseStandings(raw: String): List<TeamStanding> {
        val grid = parseGrid(raw) ?: return emptyList()
        return parseStandingsRows(grid.rows)
    }

    private fun parseStandingsRows(rows: List<List<String>>): List<TeamStanding> =
        rows.mapNotNull { cells ->
            if (cells.size < 6) return@mapNotNull null
            val rank = cells[0].toIntOrNull() ?: return@mapNotNull null
            val teamName = extractTeamName(cells[1])
            val teamId = teamNameToCode(teamName).ifBlank { return@mapNotNull null }
            val games = cells.getOrNull(2)?.toIntOrNull() ?: 0
            val win = cells.getOrNull(3)?.toIntOrNull() ?: 0
            val lose = cells.getOrNull(4)?.toIntOrNull() ?: 0
            val draw = cells.getOrNull(5)?.toIntOrNull() ?: 0
            val wra = cells.getOrNull(6)?.toDoubleOrNull() ?: 0.0
            val gb = cells.getOrNull(7)?.takeIf { it != "-" }?.toDoubleOrNull() ?: 0.0
            val streak = cells.getOrNull(8).orEmpty()
            TeamStanding(
                teamId = teamId,
                teamName = teamName,
                ranking = rank,
                wra = wra,
                gameCount = games,
                win = win,
                draw = draw,
                lose = lose,
                gameBehind = gb,
                streak = streak,
                lastFive = "",
            )
        }.sortedBy { it.ranking }

    data class InningBoard(
        val awayScores: List<String> = emptyList(),
        val homeScores: List<String> = emptyList(),
        val awayRuns: Int = 0,
        val homeRuns: Int = 0,
        val awayHits: Int = 0,
        val homeHits: Int = 0,
        val awayErrors: Int = 0,
        val homeErrors: Int = 0,
        val awayWalks: Int = 0,
        val homeWalks: Int = 0,
    )

    fun parseInningBoard(table2Json: String, table3Json: String, maxInning: Int): InningBoard {
        val t2 = parseGrid(table2Json)
        val t3 = parseGrid(table3Json)
        val awayLine = t2?.rows?.getOrNull(0).orEmpty()
        val homeLine = t2?.rows?.getOrNull(1).orEmpty()
        val inn = maxInning.coerceAtLeast(9)
        fun inningCells(line: List<String>) = line.take(inn).map { c ->
            when {
                c == "-" || c.isBlank() -> ""
                else -> c
            }
        }
        val awayRhe = t3?.rows?.getOrNull(0).orEmpty()
        val homeRhe = t3?.rows?.getOrNull(1).orEmpty()
        return InningBoard(
            awayScores = inningCells(awayLine),
            homeScores = inningCells(homeLine),
            awayRuns = awayRhe.getOrNull(0)?.toIntOrNull() ?: 0,
            awayHits = awayRhe.getOrNull(1)?.toIntOrNull() ?: 0,
            awayErrors = awayRhe.getOrNull(2)?.toIntOrNull() ?: 0,
            awayWalks = awayRhe.getOrNull(3)?.toIntOrNull() ?: 0,
            homeRuns = homeRhe.getOrNull(0)?.toIntOrNull() ?: 0,
            homeHits = homeRhe.getOrNull(1)?.toIntOrNull() ?: 0,
            homeErrors = homeRhe.getOrNull(2)?.toIntOrNull() ?: 0,
            homeWalks = homeRhe.getOrNull(3)?.toIntOrNull() ?: 0,
        )
    }

    fun parseKeyPlays(tableEtcJson: String): List<KeyPlay> {
        val grid = parseGrid(tableEtcJson) ?: return emptyList()
        return grid.rows.mapNotNull { cells ->
            if (cells.size < 2) return@mapNotNull null
            val label = cells[0]
            val detail = cells[1]
            if (detail.isBlank()) return@mapNotNull null
            val text = if (label.isBlank()) detail else "$label: $detail"
            KeyPlay(
                text = text,
                isScoring = label.contains("홈런") || label.contains("결승") ||
                    detail.contains("득점") || detail.contains("홈런"),
            )
        }
    }

    private fun parseGrid(raw: String): ParsedGrid? {
        if (raw.isBlank()) return null
        return runCatching {
            val dto = json.decodeFromString(KboGridDto.serializer(), raw)
            ParsedGrid(
                rows = dto.rows.map { row ->
                    row.row.map { stripHtml(it.text) }
                },
            )
        }.getOrNull()
    }

    private fun extractTeamName(cell: String): String {
        val m = Regex("class=['\"]team-name['\"]>([^<]+)").find(cell)
        return m?.groupValues?.getOrNull(1)?.trim() ?: stripHtml(cell)
    }

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .trim()

    private data class ParsedGrid(val rows: List<List<String>>)

    @Serializable
    private data class KboGridDto(
        val rows: List<KboGridRowDto> = emptyList(),
    )

    @Serializable
    private data class KboGridRowDto(
        val row: List<KboGridCellDto> = emptyList(),
    )

    @Serializable
    private data class KboGridCellDto(
        @SerialName("Text") val text: String = "",
    )
}
