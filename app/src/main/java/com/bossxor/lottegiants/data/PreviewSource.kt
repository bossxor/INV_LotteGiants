package com.bossxor.lottegiants.data

internal class PreviewSource(private val api: NaverSportsApi) {
    private val cache = TimedSourceCache<String, PreviewData>()
    suspend fun get(gameId: String): PreviewData = cache.get(gameId, 300_000L) {
        api.getPreview(gameId).result?.previewData ?: error("프리뷰 응답 누락")
    }
}
