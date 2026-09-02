package com.bossxor.lottegiants.domain

/** 승리 확률 파싱·표시 (네이버·루타·알림·요약 공용) */
object WinProb {

    /**
     * API 승률 값을 0~1 비율로 변환한다.
     * - (1, 100] → 퍼센트 정수 (예: 45 → 0.45, 99 → 0.99)
     * - [0, 1] → 이미 비율 (예: 0.45)
     */
    fun normalizeWinRatePercent(v: Double): Double? {
        if (v.isNaN() || v < 0) return null
        return when {
            v > 1.0 && v <= 100.0 -> (v / 100.0).coerceIn(0.0, 1.0)
            v <= 1.0 -> v.coerceIn(0.0, 1.0)
            else -> null
        }
    }

    /** 홈·원정 승률이 함께 오면 합이 100% 근처인지 검증한 뒤 정규화한다. */
    fun normalizeWinRatePair(home: Double?, away: Double?): Pair<Double, Double>? {
        if (home == null || away == null) return null
        val usePercentScale = home > 1.0 || away > 1.0 || (home + away in 90.0..110.0)
        val h = if (usePercentScale) {
            (home / 100.0).coerceIn(0.0, 1.0)
        } else {
            normalizeWinRatePercent(home) ?: return null
        }
        val a = if (usePercentScale) {
            (away / 100.0).coerceIn(0.0, 1.0)
        } else {
            normalizeWinRatePercent(away) ?: return null
        }
        val sum = h + a
        if (sum !in 0.85..1.15) return null
        return if (sum == 0.0) 0.5 to 0.5 else (h / sum) to (a / sum)
    }

    /** 포커스(롯데) 승률. 한쪽만 오면 보완한다. */
    fun focusWinProb(home: Double?, away: Double?, lotteIsHome: Boolean): Double? {
        normalizeWinRatePair(home, away)?.let { (h, a) ->
            return if (lotteIsHome) h else a
        }
        val single = (if (lotteIsHome) home else away) ?: home ?: away ?: return null
        // 한쪽만 올 때 1~100 정수는 퍼센트 (1 → 1%, 55 → 55%)
        if (single in 1.0..100.0 && (home == null || away == null)) {
            return (single / 100.0).coerceIn(0.0, 1.0)
        }
        return normalizeWinRatePercent(single)
    }

    /**
     * 네이버 중계 → 루타 → 점수 추정 순. 시계열이 긴 쪽을 우선한다.
     */
    fun pickSeries(
        naver: List<WinProbPoint>,
        ruta: List<WinProbPoint>,
        estimated: List<WinProbPoint>,
    ): List<WinProbPoint> {
        data class Src(val points: List<WinProbPoint>, val priority: Int)
        val candidates = listOf(
            Src(naver, 3),
            Src(ruta, 2),
            Src(estimated, 1),
        ).filter { it.points.isNotEmpty() }
        return candidates.maxWith(
            compareBy<Src> { it.points.size }.thenBy { it.priority },
        )?.points ?: emptyList()
    }

    fun sanitizeSeries(game: LotteGameInfo?, series: List<WinProbPoint>): List<WinProbPoint> {
        if (game == null) return series
        return series.map { p -> p.copy(homeProb = clampForDisplay(game, p.homeProb)) }
    }

    /** 알림·요약에 쓸 퍼센트 (합 100). */
    fun displayPercents(awayProb: Double, homeProb: Double): Pair<Int, Int> {
        val away = (awayProb.coerceIn(0.0, 1.0) * 100).toInt().coerceIn(0, 100)
        return away to (100 - away)
    }

    fun shouldShowWinProbBar(game: LotteGameInfo): Boolean =
        game.status != GameStatus.CANCELED && game.status != GameStatus.BEFORE

    fun clampForDisplay(game: LotteGameInfo, focusProb: Double): Double {
        if (game.status == GameStatus.ENDED || game.status == GameStatus.CANCELED) {
            return estimateLotteWinProb(game)
        }
        return focusProb.coerceIn(0.03, 0.97)
    }

    fun resolveDisplayFocusProb(game: LotteGameInfo, seriesProb: Double?): Double? {
        if (!shouldShowWinProbBar(game)) return null
        val raw = seriesProb ?: estimateLotteWinProb(game)
        return clampForDisplay(game, raw)
    }

    fun awayHomeFromFocus(game: LotteGameInfo, focusProb: Double): Pair<Double, Double> {
        val away = if (game.isHome) 1.0 - focusProb else focusProb
        return away to (1.0 - away)
    }
}
